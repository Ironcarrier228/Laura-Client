package im.laura.functions.impl.misc;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventPostMotion;
import im.laura.events.EventSpawnEntity;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.impl.combat.KillAura;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.utils.Timer;
import im.laura.utils.player.MovementUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.EyeOfEnderEntity;
import net.minecraft.item.Items;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Comparator;
import java.util.HashMap;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "PearlChaser", type = Category.Misc)
public class PearlChaser extends Function {

    private final BooleanSetting stopMotion   = new BooleanSetting("Стоп при броске",  false);
    private final BooleanSetting legitStop    = new BooleanSetting("Легитный стоп",    false);
    private final BooleanSetting pauseAura    = new BooleanSetting("Пауза ауры",       false);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Только на земле",  false);
    private final BooleanSetting noMove       = new BooleanSetting("Не двигаться",     false);
    private final BooleanSetting onlyTarget   = new BooleanSetting("Только цель",      false);

    private Runnable postSyncAction;
    private final Timer delayTimer = new Timer();
    private BlockPos targetBlock;
    private int lastPearlId;
    private int lastOurPearlId;
    private final HashMap<PlayerEntity, Long> targets = new HashMap<>();

    public PearlChaser() {
        addSettings(stopMotion, legitStop, pauseAura, onlyOnGround, noMove, onlyTarget);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onEntitySpawn(EventSpawnEntity e) {
        if (!(e.getEntity() instanceof EyeOfEnderEntity)) return;
        mc.world.getPlayers().stream()
                .min(Comparator.comparingDouble(p -> p.getDistanceSq(e.getEntity().getPositionVec())))
                .ifPresent(player -> {
                    if (player.equals(mc.player))
                        lastOurPearlId = e.getEntity().getEntityId();
                });
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;

        if (onlyTarget.get()) {
            KillAura ka = getKillAura();
            if (ka != null && ka.isState() && ka.getTarget() instanceof PlayerEntity pl
                    && !targets.containsKey(pl))
                targets.put(pl, System.currentTimeMillis());

            new HashMap<>(targets).forEach((k, v) -> {
                if (System.currentTimeMillis() - v > 10000) targets.remove(k);
            });
        }

        if (mc.player.getHealth() < 5) return;
        if (!delayTimer.passedMs(1000)) return;

        for (Entity ent : mc.world.getAllEntities()) {
            if (!(ent instanceof EyeOfEnderEntity)) continue;
            if (ent.getEntityId() == lastPearlId || ent.getEntityId() == lastOurPearlId) continue;

            mc.world.getPlayers().stream()
                    .filter(p -> targets.containsKey(p) || !onlyTarget.get())
                    .min(Comparator.comparingDouble(p -> p.getDistanceSq(ent.getPositionVec())))
                    .ifPresent(player -> {
                        if (!player.equals(mc.player)) {
                            targetBlock = calcTrajectory(ent);
                            lastPearlId = ent.getEntityId();
                        }
                    });
        }

        if (targetBlock == null) return;
        if (mc.player.getDistanceSq(Vector3d.copyCentered(targetBlock)) < 49) return;

        float rotationPitch = (float) (-Math.toDegrees(calcTrajectory(targetBlock)));
        float rotationYaw   = (float) Math.toDegrees(
                Math.atan2(
                        targetBlock.getZ() + 0.5 - mc.player.getPosZ(),
                        targetBlock.getX() + 0.5 - mc.player.getPosX()
                )
        ) - 90.0f;

        BlockPos tracedBP = checkTrajectory(rotationYaw, rotationPitch);
        if (tracedBP == null || targetBlock.distanceSq(tracedBP) > 36) return;

        if (pauseAura.get()) {
            KillAura ka = getKillAura();
            if (ka != null && ka.isState()) ka.pause();
        }

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;
        if (noMove.get() && MovementUtil.isMoving()) return;

        if (stopMotion.get()) {
            if (!legitStop.get()) mc.player.setMotion(0, 0, 0);
            mc.gameSettings.keyBindForward.setPressed(false);
            mc.gameSettings.keyBindBack.setPressed(false);
            mc.gameSettings.keyBindLeft.setPressed(false);
            mc.gameSettings.keyBindRight.setPressed(false);
            return;
        }

        mc.player.rotationYaw   = rotationYaw;
        mc.player.rotationPitch = MathHelper.clamp(rotationPitch, -89, 89);

        postSyncAction = () -> {
            int epSlot = findEPSlot();
            if (epSlot == -1) return;
            int originalSlot = mc.player.inventory.currentItem;
            mc.player.inventory.currentItem = epSlot;
            mc.player.connection.sendPacket(new CHeldItemChangePacket(epSlot));
            mc.player.connection.sendPacket(new CPlayerTryUseItemPacket(Hand.MAIN_HAND));
            mc.player.connection.sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
            mc.player.inventory.currentItem = originalSlot;
            mc.player.connection.sendPacket(new CHeldItemChangePacket(originalSlot));
        };

        targetBlock = null;
        delayTimer.reset();
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onPostMotion(EventPostMotion e) {
        if (postSyncAction != null) {
            postSyncAction.run();
            postSyncAction = null;
        }
    }

    private int findEPSlot() {
        if (mc.player.getHeldItemMainhand().getItem() == Items.ENDER_PEARL)
            return mc.player.inventory.currentItem;
        for (int i = 0; i < 9; i++)
            if (mc.player.inventory.getStackInSlot(i).getItem() == Items.ENDER_PEARL)
                return i;
        return -1;
    }

    private float calcTrajectory(BlockPos bp) {
        double a = Math.hypot(bp.getX() + 0.5 - mc.player.getPosX(), bp.getZ() + 0.5 - mc.player.getPosZ());
        double y = 6.125 * ((bp.getY() + 1.0) - (mc.player.getPosY() + mc.player.getEyeHeight()));
        y = 0.05000000074505806 * ((0.05000000074505806 * (a * a)) + y);
        y = Math.sqrt(9.37890625 - y);
        double d = 3.0625 - y;
        y = Math.atan2(d * d + y, 0.05000000074505806 * a);
        d = Math.atan2(d, 0.05000000074505806 * a);
        return (float) Math.min(y, d);
    }

    private BlockPos calcTrajectory(Entity e) {
        return traceTrajectory(e.getPosX(), e.getPosY(), e.getPosZ(),
                e.getMotion().x, e.getMotion().y, e.getMotion().z);
    }

    private BlockPos checkTrajectory(float yaw, float pitch) {
        if (Float.isNaN(pitch)) return null;
        float yawRad   = yaw   / 180.0f * (float) Math.PI;
        float pitchRad = pitch / 180.0f * (float) Math.PI;
        double x  = mc.player.getPosX() - MathHelper.cos(yawRad) * 0.16f;
        double y  = mc.player.getPosY() + mc.player.getEyeHeight() - 0.1;
        double z  = mc.player.getPosZ() - MathHelper.sin(yawRad) * 0.16f;
        double mx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad) * 0.4f;
        double my = -MathHelper.sin(pitchRad) * 0.4f;
        double mz =  MathHelper.cos(yawRad)  * MathHelper.cos(pitchRad) * 0.4f;
        double dist = Math.sqrt(mx * mx + my * my + mz * mz);
        mx /= dist; my /= dist; mz /= dist;
        mx *= 1.5; my *= 1.5; mz *= 1.5;
        if (!mc.player.isOnGround()) my += mc.player.getMotion().y;
        return traceTrajectory(x, y, z, mx, my, mz);
    }

    private BlockPos traceTrajectory(double x, double y, double z, double mx, double my, double mz) {
        for (int i = 0; i < 300; i++) {
            Vector3d lastPos = new Vector3d(x, y, z);
            x += mx; y += my; z += mz;
            mx *= 0.99; my *= 0.99; mz *= 0.99;
            my -= 0.03;
            Vector3d pos = new Vector3d(x, y, z);
            RayTraceResult rtr = mc.world.rayTraceBlocks(
                    new RayTraceContext(lastPos, pos,
                            RayTraceContext.BlockMode.OUTLINE,
                            RayTraceContext.FluidMode.NONE, mc.player)
            );
            if (rtr instanceof BlockRayTraceResult && rtr.getType() == RayTraceResult.Type.BLOCK)
                return ((BlockRayTraceResult) rtr).getPos();

            for (Entity ent : mc.world.getAllEntities()) {
                if (ent instanceof AbstractArrowEntity || ent == mc.player
                        || ent instanceof EyeOfEnderEntity) continue;
                if (ent.getBoundingBox().intersects(
                        new AxisAlignedBB(x-0.3, y-0.3, z-0.3, x+0.3, y+0.3, z+0.2)))
                    return null;
            }
            if (y <= -65) break;
        }
        return null;
    }

    private KillAura getKillAura() {
        try {
            return (KillAura) im.laura.Laura.getInstance()
                    .getFunctionRegistry().getFunctions()
                    .stream().filter(f -> f instanceof KillAura)
                    .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }
}