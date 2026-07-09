package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.MovingEvent;
import im.laura.events.SprintEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.player.MoveUtils;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.MathHelper;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "Strafe", type = Category.Movement)
public class Strafe extends Function {

    private final ModeSetting   boost     = new ModeSetting  ("Ускорение", "None",  "None", "Elytra", "Damage");
    private final SliderSetting speed     = new SliderSetting("Скорость",  1.0f, 0.1f, 5.0f, 0.1f);
    private final BooleanSetting inAir    = new BooleanSetting("В воздухе", true);
    private final BooleanSetting onGround = new BooleanSetting("На земле",  false);

    public Strafe() {
        addSettings(boost, speed, inAir, onGround);
    }

    public static boolean disabler() {
        return false;
    }

    public static boolean canStrafe() {
        return mc.player != null && mc.world != null
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !disabler()
                && MoveUtils.isMoving();
    }

    public static double calculateSpeed(double baseSpeed) {
        double speed = baseSpeed;
        if (mc.player.isPotionActive(Effects.SPEED)) {
            int amp = mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier();
            speed *= 1.0 + 0.2 * (amp + 1);
        }
        return speed;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onMove(MovingEvent e) {
        if (fullNullCheck()) return;
        if (!canStrafe()) return;
        if (mc.player.isOnGround() && !onGround.get()) return;
        if (!mc.player.isOnGround() && !inAir.get()) return;

        float yaw = mc.player.rotationYaw;
        float forward = mc.player.movementInput.moveForward;
        float strafe  = mc.player.movementInput.moveStrafe;

        if (forward == 0 && strafe == 0) return;

        double angle = Math.toRadians(yaw) - Math.atan2(strafe, forward);
        double spd = calculateSpeed(speed.get() * mc.player.getAIMoveSpeed());

        e.getMotion().x = -Math.sin(angle) * spd;
        e.getMotion().z = Math.cos(angle) * spd;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onSprint(SprintEvent e) {
        if (fullNullCheck()) return;
        if (!canStrafe()) return;
        if (boost.is("None")) return;

        if (boost.is("Elytra")) {
            if (!mc.player.isElytraFlying()) return;
            float yaw = (float) Math.toRadians(mc.player.rotationYaw);
            mc.player.setMotion(
                    mc.player.getMotion().x - MathHelper.sin(yaw) * 0.1f,
                    mc.player.getMotion().y,
                    mc.player.getMotion().z + MathHelper.cos(yaw) * 0.1f
            );
        }

        if (boost.is("Damage")) {
            if (mc.player.hurtTime != mc.player.maxHurtTime - 1) return;
            float yaw = (float) Math.toRadians(mc.player.rotationYaw);
            mc.player.setMotion(
                    mc.player.getMotion().x - MathHelper.sin(yaw) * 0.6f,
                    mc.player.getMotion().y,
                    mc.player.getMotion().z + MathHelper.cos(yaw) * 0.6f
            );
        }
    }
}