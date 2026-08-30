package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventPostMotion;
import im.laura.events.EventUpdate;
import im.laura.events.MovingEvent;
import im.laura.events.TickEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "Scaffold", type = Category.Movement)
public class Scaffold extends Function {

    private final ModeSetting    mode      = new ModeSetting   ("Режим",        "NCP",     "NCP", "StrictNCP", "Grim");
    private final ModeSetting    switchMode = new ModeSetting  ("Свич",         "Silent",  "Normal", "Silent", "Inventory", "None");
    private final BooleanSetting safeWalk  = new BooleanSetting("SafeWalk",     true);
    private final BooleanSetting tower     = new BooleanSetting("Башня",        true);
    private final BooleanSetting lockY     = new BooleanSetting("LockY",        false);
    private final BooleanSetting autoJump  = new BooleanSetting("АвтоПрыжок",  false);
    private final BooleanSetting rotate    = new BooleanSetting("Ротация",      true);
    private final BooleanSetting render    = new BooleanSetting("Рендер",       true);

    private int blockSlot = -1;
    private int savedSlot = -1;
    private boolean placing;
    private float savedYaw, savedPitch;

    public Scaffold() {
        addSettings(mode, switchMode, safeWalk, tower, lockY, autoJump, rotate, render);
    }

    @Override
    public boolean onDisable() {
        restoreSlot();
        placing = false;
        return false;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        if (fullNullCheck()) return;

        blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        // SafeWalk — предотвращает падение с края
        if (safeWalk.get()) {
            mc.player.setSneaking(mc.player.isOnGround() &&
                    !mc.gameSettings.keyBindSneak.isKeyDown());
        }

        // Авто прыжок (башня)
        if (tower.get() && mc.gameSettings.keyBindJump.isKeyDown()
                && mc.player.isOnGround() && !mc.player.isInWater()) {
            if (mode.is("NCP") || mode.is("StrictNCP")) {
                mc.player.setMotion(mc.player.getMotion().x, 0.42, mc.player.getMotion().z);
            } else if (mode.is("Grim")) {
                mc.player.setMotion(mc.player.getMotion().x, 0.42, mc.player.getMotion().z);
            }
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;
        if (blockSlot == -1) { blockSlot = findBlockSlot(); }
        if (blockSlot == -1) return;

        BlockPos below = new BlockPos(mc.player.getPosX(),
                mc.player.getPosY() - 1,
                mc.player.getPosZ());

        if (mc.world.getBlockState(below).getBlock() != Blocks.AIR) return;

        BlockPos target = findPlaceable();
        if (target == null) return;

        placing = true;
        placeBlock(target);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onPostMotion(EventPostMotion e) {
        if (!placing) return;
        placing = false;
        restoreSlot();
        if (rotate.get()) {
            mc.player.rotationYaw   = savedYaw;
            mc.player.rotationPitch = savedPitch;
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onMove(MovingEvent e) {
        if (fullNullCheck()) return;
        if (!safeWalk.get()) return;
        if (blockSlot == -1) return;

        ClientPlayerEntity p = mc.player;
        if (!p.isOnGround()) return;

        // Обнуляем движение если игрок на краю блока
        double nextX = p.getPosX() + e.getX();
        double nextZ = p.getPosZ() + e.getZ();
        BlockPos nextBelow = new BlockPos(nextX, p.getPosY() - 0.01, nextZ);
        if (mc.world.getBlockState(nextBelow).getBlock() == Blocks.AIR) {
            e.setX(0);
            e.setZ(0);
        }
    }

    // ─── Вспомогательные методы ─────────────────────────────────────────────

    private void placeBlock(BlockPos pos) {
        // Найти поверхность для размещения
        Direction face = Direction.UP;
        BlockPos neighbor = pos.offset(face);
        if (!mc.world.getBlockState(pos).isAir()) return;

        // Пробуем снизу вверх
        for (Direction dir : Direction.values()) {
            BlockPos adj = pos.offset(dir);
            BlockState adjState = mc.world.getBlockState(adj);
            if (!adjState.isAir() && adjState.getBlock() != Blocks.WATER
                    && adjState.getBlock() != Blocks.LAVA) {
                face = dir.getOpposite();

                if (rotate.get()) {
                    savedYaw   = mc.player.rotationYaw;
                    savedPitch = mc.player.rotationPitch;
                    float[] angles = getRotation(new Vector3d(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    mc.player.rotationYaw   = angles[0];
                    mc.player.rotationPitch = angles[1];
                }

                switchToBlock();

                Vector3d hitVec = new Vector3d(
                        adj.getX() + 0.5 + face.getXOffset() * 0.5,
                        adj.getY() + 0.5 + face.getYOffset() * 0.5,
                        adj.getZ() + 0.5 + face.getZOffset() * 0.5
                );

                mc.player.connection.sendPacket(new CPlayerTryUseItemOnBlockPacket(
                        Hand.MAIN_HAND,
                        new BlockRayTraceResult(hitVec, face, adj, false)
                ));
                mc.player.connection.sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));

                mc.player.swingArm(Hand.MAIN_HAND);
                return;
            }
        }
    }

    private BlockPos findPlaceable() {
        // Ищем позицию под ногами и рядом
        BlockPos[] candidates = {
                new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 1, mc.player.getPosZ()),
                new BlockPos(mc.player.getPosX() + 0.3, mc.player.getPosY() - 1, mc.player.getPosZ()),
                new BlockPos(mc.player.getPosX() - 0.3, mc.player.getPosY() - 1, mc.player.getPosZ()),
                new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 1, mc.player.getPosZ() + 0.3),
                new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 1, mc.player.getPosZ() - 0.3),
        };

        for (BlockPos pos : candidates) {
            if (mc.world.getBlockState(pos).isAir()) {
                for (Direction dir : Direction.values()) {
                    BlockState adj = mc.world.getBlockState(pos.offset(dir));
                    if (!adj.isAir() && adj.getBlock() != Blocks.WATER
                            && adj.getBlock() != Blocks.LAVA) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private void switchToBlock() {
        if (switchMode.is("None")) return;
        if (blockSlot == mc.player.inventory.currentItem) return;

        if (switchMode.is("Normal")) {
            savedSlot = mc.player.inventory.currentItem;
            mc.player.inventory.currentItem = blockSlot;
            mc.player.connection.sendPacket(new CPlayerDiggingPacket(
                    CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
        } else if (switchMode.is("Silent") || switchMode.is("Inventory")) {
            savedSlot = mc.player.inventory.currentItem;
            mc.player.inventory.currentItem = blockSlot;
        }
    }

    private void restoreSlot() {
        if (savedSlot == -1) return;
        if (switchMode.is("None")) return;
        mc.player.inventory.currentItem = savedSlot;
        savedSlot = -1;
    }

    private float[] getRotation(Vector3d target) {
        double diffX = target.x - mc.player.getPosX();
        double diffY = target.y - (mc.player.getPosY() + mc.player.getEyeHeight());
        double diffZ = target.z - mc.player.getPosZ();
        double dist  = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw   = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        return new float[]{
                MathHelper.clamp(yaw,   -180f, 180f),
                MathHelper.clamp(pitch, -90f,   90f)
        };
    }
}