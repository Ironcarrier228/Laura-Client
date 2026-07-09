package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventPacket;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

@FunctionRegister(name = "Velocity", type = Category.Combat)
@SuppressWarnings({"unused", "Beta"})
public class Velocity extends Function {

    private final ModeSetting mode = new ModeSetting("Режим", "Cancel", "Cancel", "Grim Skip", "Grim Cancel", "Funtime", "Reduced");
    private final SliderSetting horizontal = new SliderSetting("Горизонтально", 0f, 0f, 100f, 5f).setVisible(() -> mode.is("Reduced"));
    private final SliderSetting vertical = new SliderSetting("Вертикально", 0f, 0f, 100f, 5f).setVisible(() -> mode.is("Reduced"));
    private final BooleanSetting onlyOnGround = new BooleanSetting("Только на земле", false);

    private int skip = 0;
    private boolean cancel;
    boolean damaged;

    public Velocity() {
        addSettings(mode, horizontal, vertical, onlyOnGround);
    }


    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null) return;
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;
        
        if (e.isReceive()) {
            if (e.getPacket() instanceof SEntityVelocityPacket p && p.getEntityID() != mc.player.getEntityId()) return;
            
            switch (mode.get()) {
                case "Cancel" -> {
                    if (e.getPacket() instanceof SEntityVelocityPacket) e.cancel();
                }
                case "Grim Skip" -> handleGrimSkip(e);
                case "Grim Cancel" -> handleGrimCancel(e);
                case "Funtime" -> handleFuntime(e);
                case "Reduced" -> handleReduced(e);
            }
        }
    }

    private void handleGrimSkip(EventPacket e) {
        if (e.getPacket() instanceof SEntityVelocityPacket) {
            skip = 6;
            e.cancel();
        }
        if (e.getPacket() instanceof CPlayerPacket && skip > 0) {
            skip--;
            e.cancel();
        }
    }

    private void handleGrimCancel(EventPacket e) {
        if (e.getPacket() instanceof SEntityVelocityPacket) {
            e.cancel();
            cancel = true;
        }
        if (e.getPacket() instanceof SPlayerPositionLookPacket) {
            skip = 3;
        }
        if (e.getPacket() instanceof CPlayerPacket) {
            skip--;
            if (cancel && skip <= 0) {
                BlockPos blockPos = new BlockPos(mc.player.getPositionVec());
                mc.player.connection.sendPacket(new CPlayerPacket.PositionRotationPacket(
                        mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ(),
                        mc.player.rotationYaw, mc.player.rotationPitch, mc.player.isOnGround()));
                mc.player.connection.sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
                cancel = false;
            }
        }
    }

    private void handleFuntime(EventPacket e) {
        if (e.getPacket() instanceof SEntityVelocityPacket p) {
            if (skip >= 2) return;
            if (p.getEntityID() != mc.player.getEntityId()) return;
            e.cancel();
            damaged = true;
        }
        if (e.getPacket() instanceof SPlayerPositionLookPacket) {
            skip = 3;
        }
    }

    private void handleReduced(EventPacket e) {
        if (e.getPacket() instanceof SEntityVelocityPacket p) {
            e.cancel();
            
            double motionX = p.getMotionX() * (horizontal.get() / 100f);
            double motionY = p.getMotionY() * (vertical.get() / 100f);
            double motionZ = p.getMotionZ() * (horizontal.get() / 100f);
            
            mc.player.setMotion(motionX, motionY, motionZ);
        }
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (!mode.is("Funtime")) return;
        
        skip--;
        if (damaged) {
            BlockPos blockPos = mc.player.getPosition();
            mc.player.connection.sendPacketWithoutEvent(new CPlayerPacket.PositionRotationPacket(
                    mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ(),
                    mc.player.rotationYaw, mc.player.rotationPitch, mc.player.isOnGround()));
            mc.player.connection.sendPacketWithoutEvent(new CPlayerDiggingPacket(
                    CPlayerDiggingPacket.Action.STOP_DESTROY_BLOCK, blockPos, Direction.UP));
            damaged = false;
        }
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        skip = 0;
        cancel = false;
        damaged = false;
        return false;
    }
}