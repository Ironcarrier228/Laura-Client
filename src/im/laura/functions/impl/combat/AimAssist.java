package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventMotion;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeListSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.SensUtils;
import im.laura.utils.math.StopWatch;
import im.laura.utils.player.AttackUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Random;

import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@FunctionRegister(name = "AimAssist", type = Category.Combat)
@SuppressWarnings({"unused", "Beta"})
public class AimAssist extends Function {

    private final SliderSetting range = new SliderSetting("Дистанция", 4f, 3f, 6f, 0.1f);
    private final SliderSetting fov = new SliderSetting("FOV", 180f, 10f, 180f, 5f);
    private final SliderSetting smooth = new SliderSetting("Плавность", 3f, 1f, 10f, 0.1f);
    private final SliderSetting speed = new SliderSetting("Скорость", 80f, 10f, 180f, 1f);

    private final ModeSetting mode = new ModeSetting("Режим", "Легит", "Легит", "Плавный", "Резкий", "Silent");
    private final ModeSetting serverMode = new ModeSetting("Сервер", "Vanilla", "Vanilla", "FunTime", "SkyTime", "Matrix", "Vulcan");

    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", false));

    private final BooleanSetting onlyCrit = new BooleanSetting("Только криты", false);
    private final BooleanSetting silentRotation = new BooleanSetting("Тихая ротация", false);
    private final BooleanSetting correction = new BooleanSetting("Коррекция движения", true);
    private final BooleanSetting autoAttack = new BooleanSetting("Авто атака", true);

    private final Random random = new Random();
    private final StopWatch attackTimer = new StopWatch();

    private LivingEntity target;
    private Vector2f rotation = new Vector2f(0, 0);
    private boolean isActive;

    public AimAssist() {
        addSettings(range, fov, smooth, speed, mode, serverMode, targets, onlyCrit, silentRotation, correction, autoAttack);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        updateTarget();

        if (target != null && isValidTarget()) {
            isActive = true;
            updateRotation();
            
            if (autoAttack.get() && shouldAttack()) {
                attack();
            }
        } else {
            isActive = false;
            reset();
        }
    }

    private boolean shouldAttack() {
        if (!attackTimer.isReached(500)) return false;
        
        if (onlyCrit.get() && mc.player.fallDistance <= 0) return false;
        
        float attackStrength = mc.player.getCooledAttackStrength(0);
        return attackStrength >= 0.9f;
    }

    private void attack() {
        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(Hand.MAIN_HAND);
        attackTimer.reset();
    }

    @Subscribe
    public void onMotion(EventMotion e) {
        if (!isActive || target == null) return;

        if (silentRotation.get()) {
            e.setYaw(rotation.x);
            e.setPitch(rotation.y);
        } else {
            mc.player.rotationYaw = rotation.x;
            mc.player.rotationPitch = rotation.y;
        }
    }

    private void updateTarget() {
        target = null;
        double bestRange = range.get();

        for (Entity entity : mc.world.getAllEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValid(living)) continue;

            double dist = mc.player.getDistance(living);
            if (dist > bestRange) continue;

            if (!isInFOV(living)) continue;

            if (target == null || dist < mc.player.getDistance(target)) {
                target = living;
            }
        }
    }

    private boolean isValid(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive() || entity.isInvulnerable()) return false;
        if (entity.ticksExisted < 3) return false;

        AttackUtil entitySelector = new AttackUtil();

        if (targets.getValueByName("Игроки").get()) {
            entitySelector.apply(AttackUtil.EntityType.PLAYERS);
        }
        if (targets.getValueByName("Мобы").get()) {
            entitySelector.apply(AttackUtil.EntityType.MOBS);
        }
        if (targets.getValueByName("Животные").get()) {
            entitySelector.apply(AttackUtil.EntityType.ANIMALS);
        }

        if (entitySelector.ofType(entity, entitySelector.build()) == null) return false;

        if (!targets.getValueByName("Друзья").get() && entity instanceof PlayerEntity) {
            if (im.laura.command.friends.FriendStorage.isFriend(entity.getName().getString())) {
                return false;
            }
        }

        if (entity instanceof PlayerEntity && AntiBot.isBot(entity)) {
            return false;
        }

        return true;
    }

    private boolean isValidTarget() {
        if (target == null) return false;
        if (mc.player.getDistance(target) > range.get()) return false;
        if (!target.isAlive()) return false;

        if (onlyCrit.get() && mc.player.fallDistance <= 0) return false;

        return true;
    }

    private boolean isInFOV(LivingEntity entity) {
        Vector3d eyePos = mc.player.getEyePosition(1.0F);
        Vector3d entityPos = entity.getPositionVec().add(0, entity.getEyeHeight(), 0);
        Vector3d direction = entityPos.subtract(eyePos).normalize();

        Vector3d lookVec = mc.player.getLook(1.0F);
        double dot = direction.dotProduct(lookVec);
        double angle = Math.toDegrees(Math.acos(dot));

        return angle <= fov.get();
    }

    private void updateRotation() {
        Vector3d vec = target.getPositionVec().add(0, clamp(mc.player.getPosYEye() - target.getPosY(), 0, target.getHeight() * 0.5f), 0)
                .subtract(mc.player.getEyePosition(1.0F));

        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))));

        float yawDelta = wrapDegrees(targetYaw - mc.player.rotationYaw);
        float pitchDelta = wrapDegrees(targetPitch - mc.player.rotationPitch);

        float speedValue = speed.get();
        float smoothValue = smooth.get();
        float yawFactor, pitchFactor;

        switch (mode.get()) {
            case "Легит" -> {
                float randomFactor = 0.7f + random.nextFloat() * 0.6f;
                yawFactor = speedValue * randomFactor / smoothValue;
                pitchFactor = yawFactor / 1.5f;
            }
            case "Плавный" -> {
                yawFactor = speedValue / smoothValue;
                pitchFactor = yawFactor / 2f;
            }
            case "Резкий" -> {
                yawFactor = speedValue * 1.5f / smoothValue;
                pitchFactor = speedValue * 1.3f / smoothValue;
            }
            case "Silent" -> {
                float serverFactor = getServerFactor();
                float microShake = (random.nextFloat() - 0.5f) * getMicroShake();
                targetYaw += microShake;
                yawFactor = speedValue * serverFactor / smoothValue;
                pitchFactor = yawFactor / 1.5f;
            }
            default -> {
                yawFactor = speedValue / smoothValue;
                pitchFactor = yawFactor / 2f;
            }
        }

        float clampedYaw = Math.min(Math.abs(yawDelta), yawFactor);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchFactor);

        float yaw = mc.player.rotationYaw + (yawDelta > 0 ? clampedYaw : -clampedYaw);
        float pitch = clamp(mc.player.rotationPitch + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89f, 89f);

        if (mode.is("Легит") || mode.is("Резкий") || mode.is("Silent")) {
            float gcd = SensUtils.getGCDValue();
            yaw -= (yaw - mc.player.rotationYaw) % gcd;
            pitch -= (pitch - mc.player.rotationPitch) % gcd;
        }

        rotation = new Vector2f(yaw, pitch);
    }

    private float getServerFactor() {
        return switch (serverMode.get()) {
            case "FunTime" -> 0.9f;
            case "SkyTime" -> 1.1f;
            case "Matrix" -> 0.85f;
            case "Vulcan" -> 0.8f;
            default -> 1.0f;
        };
    }

    private float getMicroShake() {
        return switch (serverMode.get()) {
            case "Matrix" -> 0.3f;
            case "Vulcan" -> 0.15f;
            case "SkyTime" -> 0.2f;
            default -> 0.0f;
        };
    }

    private void reset() {
        rotation = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        target = null;
        isActive = false;
        attackTimer.reset();
        return false;
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        rotation = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
        if (correction.get()) {
            mc.player.rotationYawOffset = mc.player.rotationYaw;
        }
        target = null;
        isActive = false;
        attackTimer.reset();
        return false;
    }
}
