package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effects;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "TargetStrafe", type = Category.Combat)
public class TargetStrafe extends Function {

    private final SliderSetting  radius      = new SliderSetting ("Радиус",      2.5f,  1.0f, 6.0f, 0.1f);
    private final SliderSetting  strafeSpeed = new SliderSetting ("Скорость",    0.28f, 0.05f, 1.0f, 0.01f);
    private final BooleanSetting autoSwitch  = new BooleanSetting("Авто смена",  true);
    private final BooleanSetting onlyAura    = new BooleanSetting("Только аура", true);
    private final BooleanSetting jumpBoost   = new BooleanSetting("Прыжок",      false);
    private final ModeSetting    direction   = new ModeSetting   ("Сторона",     "Right", "Right", "Left");

    private boolean dirRight  = true;
    private int     stuckTicks = 0;
    private double  lastX, lastZ;

    public TargetStrafe() {
        addSettings(radius, strafeSpeed, autoSwitch, onlyAura, jumpBoost, direction);
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        dirRight   = direction.is("Right");
        stuckTicks = 0;
        return false;
    }

    @Override
    public boolean onDisable() {
        try { super.onDisable(); } catch (Exception ignored) {}
        stuckTicks = 0;
        lastX = 0;
        lastZ = 0;
        return false;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onUpdate(EventUpdate e) {
        if (fullNullCheck()) return;

        LivingEntity target = getTarget();
        if (target == null) return;

        if (onlyAura.get()) {
            KillAura ka = getKillAura();
            if (ka == null || !ka.isState()) return;
        }

        if (mc.player.isInWater() || mc.player.isInLava()) return;

        // Авто-смена направления если застряли
        if (autoSwitch.get()) {
            double dx    = mc.player.getPosX() - lastX;
            double dz    = mc.player.getPosZ() - lastZ;
            double moved = Math.sqrt(dx * dx + dz * dz);
            if (moved < 0.01 && mc.player.isOnGround()) {
                stuckTicks++;
                if (stuckTicks >= 8) { dirRight = !dirRight; stuckTicks = 0; }
            } else {
                stuckTicks = 0;
            }
        }
        lastX = mc.player.getPosX();
        lastZ = mc.player.getPosZ();

        // Угол от игрока до цели
        double angleToTarget = Math.atan2(
                target.getPosZ() - mc.player.getPosZ(),
                target.getPosX() - mc.player.getPosX()
        );

        // Перпендикуляр — орбита
        double strafeAngle = angleToTarget + (dirRight ? Math.PI / 2.0 : -Math.PI / 2.0);

        double spd = getSpeed();
        double moveX = Math.cos(strafeAngle) * spd;
        double moveZ = Math.sin(strafeAngle) * spd;

        // Корректируем дистанцию
        double dist = mc.player.getDistance(target);
        double diff = dist - radius.get();
        if (Math.abs(diff) > 0.5) {
            double factor = diff * 0.15;
            moveX += Math.cos(angleToTarget) * factor;
            moveZ += Math.sin(angleToTarget) * factor;
        }

        mc.player.setMotion(moveX, mc.player.getMotion().y, moveZ);

        // Поворачиваем голову к цели
        mc.player.rotationYaw = (float) Math.toDegrees(Math.atan2(
                target.getPosZ() - mc.player.getPosZ(),
                target.getPosX() - mc.player.getPosX()
        )) - 90f;

        // Прыжок при столкновении
        if (jumpBoost.get() && mc.player.isOnGround() && mc.player.collidedHorizontally) {
            mc.player.setMotion(mc.player.getMotion().x, 0.42, mc.player.getMotion().z);
        }
    }

    private double getSpeed() {
        double spd = strafeSpeed.get();
        if (mc.player.isPotionActive(Effects.SPEED)) {
            int amp = mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier();
            spd *= 1.0 + 0.2 * (amp + 1);
        }
        return spd;
    }

    private LivingEntity getTarget() {
        KillAura ka = getKillAura();
        return ka == null ? null : ka.getTarget();
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