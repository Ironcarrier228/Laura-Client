package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.Laura;
import im.laura.command.friends.FriendStorage;
import im.laura.events.EventInput;
import im.laura.events.EventMotion;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeListSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.client.CombatAdapter;
import im.laura.utils.math.SensUtils;
import im.laura.utils.math.StopWatch;
import im.laura.utils.player.InventoryUtil;
import im.laura.utils.player.MouseUtil;
import im.laura.utils.player.MoveUtils;
import lombok.Getter;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static java.lang.Math.hypot;
import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@FunctionRegister(name = "KillAura", type = Category.Combat)
@SuppressWarnings({"unused", "SameParameterValue"})
public class KillAura extends Function {
    @Getter
    private final ModeSetting type = new ModeSetting("Тип", "Плавная", 
            "Плавная", "Резкая", "Snap", "Интеллектуальная", "Matrix", "Экспоненциальная", "Human", "Silent");
    
    private final SliderSetting attackRange = new SliderSetting("Дистанция атаки", 3f, 3f, 6f, 0.1f);
    @Getter
    private final SliderSetting rotationRange = new SliderSetting("Дистанция ротации", 1f, 0.5f, 3f, 0.1f);
    @Getter
    private final SliderSetting elytraRange = new SliderSetting("Элитра ротация", 10f, 1f, 30f, 0.1f);

    final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", false),
            new BooleanSetting("Голые невидимки", true),
            new BooleanSetting("Невидимки", true));

    @Getter
    final ModeListSetting options = new ModeListSetting("Опции",
            new BooleanSetting("Только криты", true),
            new BooleanSetting("Ломать щит", true),
            new BooleanSetting("Отжимать щит", true),
            new BooleanSetting("Ускорять ротацию при атаке", false),
            new BooleanSetting("Синхронизировать атаку с ТПС", false),
            new BooleanSetting("Фокусировать одну цель", true),
            new BooleanSetting("Коррекция движения", true),
            new BooleanSetting("RayTrace проверка", true),
            new BooleanSetting("Silent Aim", false),
            new BooleanSetting("Human Rotation", true),
            new BooleanSetting("Пост-ротация", true));

    final ModeSetting serverMode = new ModeSetting("Режим сервера", "Vanilla",
            "Vanilla", "FunTime", "SkyTime", "HollyWorld", "ReallyWorld", "SpookyTime", 
            "Matrix", "Vulcan", "Grim", "NCP", "Watchdog", "Watchdog-Tap");

    final ModeSetting correctionType = new ModeSetting("Тип коррекции", "Незаметный", 
            "Незаметный", "Сфокусированный", "Пенить", "Smooth");

    @Getter
    private final StopWatch stopWatch = new StopWatch();
    private Vector2f rotateVector = new Vector2f(0, 0);
    @Getter
    private LivingEntity target;
    private Entity selected;

    int ticks = 0;
    boolean isRotated;

    // Для обходов
    private final Random random = new Random();
    private long lastAttackTime = 0;
    private long humanReactionDelay = 0;
    private float postRotationYaw = 0;
    private float postRotationPitch = 0;
    private boolean isPostRotating = false;
    private long postRotationStartTime = 0;

    // Gaussian random для human-like ротации
    private double nextGaussian = 0;
    private boolean haveNextGaussian = false;

    final AutoPotion autoPotion;

    public KillAura(AutoPotion autoPotion) {
        this.autoPotion = autoPotion;
        addSettings(type, attackRange, rotationRange, elytraRange, targets, options, serverMode, correctionType);
    }

    @Subscribe
    public void onInput(EventInput eventInput) {
        if (options.getValueByName("Коррекция движения").get() && target != null && mc.player != null) {
            if (correctionType.is("Пенить")) {
                LivingEntity nearestTarget = getNearestValidTarget();
                if (nearestTarget != null && nearestTarget != target) {
                    target = nearestTarget;
                    Vector3d vec = nearestTarget.getPositionVec().subtract(mc.player.getEyePosition(1.0F));
                    float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90);
                    float pitch = (float) (-Math.toDegrees(Math.atan2(vec.y, hypot(vec.x, vec.z))));
                    rotateVector = new Vector2f(yaw, pitch);
                    mc.player.rotationYawOffset = yaw;
                }
                MoveUtils.fixMovement(eventInput, rotateVector.x);
            } else if (correctionType.is("Незаметный")) {
                MoveUtils.fixMovement(eventInput, rotateVector.x);
            } else if (correctionType.is("Сфокусированный")) {
                MoveUtils.fixMovement(eventInput, rotateVector.x);
            } else if (correctionType.is("Smooth")) {
                // Плавная коррекция движения
                float smoothedYaw = smoothRotation(mc.player.rotationYaw, rotateVector.x, 10.0f);
                MoveUtils.fixMovement(eventInput, smoothedYaw);
            }
        }
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        // Режим "Пенить" всегда переключается между целями
        if (correctionType.is("Пенить")) {
            updateTarget();
            LivingEntity nearestTarget = getNearestValidTarget();
            if (nearestTarget != null && nearestTarget != target) {
                target = nearestTarget;
            }
        } else if (options.getValueByName("Фокусировать одну цель").get() && (target == null || !isValid(target)) || !options.getValueByName("Фокусировать одну цель").get()) {
            updateTarget();
        }

        // Пост-ротация (возврат взгляда после атаки)
        if (options.getValueByName("Пост-ротация").get() && isPostRotating) {
            handlePostRotation();
        }

        if (target != null && !(autoPotion.isState() && autoPotion.isActive())) {
            isRotated = false;

            // Human-like задержка перед атакой
            long delay = getAttackDelay();
            if (options.getValueByName("Human Rotation").get()) {
                delay += humanReactionDelay;
            }

            if (shouldPlayerFalling() && (System.currentTimeMillis() - lastAttackTime >= delay)) {
                updateAttack();
                lastAttackTime = System.currentTimeMillis();
                
                // Генерируем новую human-like задержку
                if (options.getValueByName("Human Rotation").get()) {
                    humanReactionDelay = 150 + random.nextInt(150); // 150-300ms человеческая реакция
                    isPostRotating = true;
                    postRotationStartTime = System.currentTimeMillis();
                    postRotationYaw = rotateVector.x;
                    postRotationPitch = rotateVector.y;
                }
                
                ticks = 2;
            }

            if (type.is("Snap")) {
                if (ticks > 0) {
                    updateRotation(true, 180, 90);
                    ticks--;
                } else {
                    reset();
                }
            } else if (type.is("Резкая")) {
                if (ticks > 0) {
                    updateRotation(true, 120, 60);
                    ticks--;
                } else {
                    reset();
                }
            } else if (type.is("Human")) {
                // Human режим - максимально естественная ротация
                if (!isRotated) {
                    updateRotation(false, 60, 30);
                }
            } else if (type.is("Silent")) {
                // Silent Aim - только пакеты, без визуальной ротации
                if (!isRotated) {
                    updateRotation(false, 50, 25);
                }
            } else {
                if (!isRotated) {
                    updateRotation(false, 80, 35);
                }
            }

        } else {
            stopWatch.setLastMS(0);
            reset();
        }
    }

    private void handlePostRotation() {
        // Плавный возврат взгляда после атаки
        long elapsed = System.currentTimeMillis() - postRotationStartTime;
        if (elapsed > 300) { // 300ms на пост-ротацию
            isPostRotating = false;
            return;
        }

        float progress = (float) elapsed / 300f;
        float originalYaw = mc.player.rotationYaw;
        float originalPitch = mc.player.rotationPitch;

        // Интерполяция обратно к нормальному углу
        float targetYaw = originalYaw + (postRotationYaw - originalYaw) * (1 - progress);
        float targetPitch = originalPitch + (postRotationPitch - originalPitch) * (1 - progress);

        rotateVector = new Vector2f(targetYaw, targetPitch);
    }

    private long getAttackDelay() {
        // Задержки для разных серверов с обходом античитов
        return switch (serverMode.get()) {
            case "FunTime" -> 480 + random.nextInt(70);
            case "SkyTime" -> 420 + random.nextInt(80);
            case "HollyWorld" -> 500 + random.nextInt(80);
            case "ReallyWorld" -> 520 + random.nextInt(60);
            case "SpookyTime" -> 400 + random.nextInt(100);
            case "Matrix" -> 450 + random.nextInt(90);
            case "Vulcan" -> 470 + random.nextInt(75);
            case "Grim" -> 380 + random.nextInt(60);
            case "NCP" -> 500 + random.nextInt(50);
            case "Watchdog" -> 450 + random.nextInt(100);
            case "Watchdog-Tap" -> 500 + random.nextInt(80); // Tap-boost режим
            default -> 500;
        };
    }

    @Subscribe
    private void onWalking(EventMotion e) {
        if (target == null || autoPotion.isState() && autoPotion.isActive()) return;

        float yaw = rotateVector.x;
        float pitch = rotateVector.y;

        // Silent Aim - ротация только для сервера
        if (options.getValueByName("Silent Aim").get() && type.is("Silent")) {
            // Отправляем пакеты с ротацией, но визуально не поворачиваемся
            e.setYaw(yaw);
            e.setPitch(pitch);
        } else {
            e.setYaw(yaw);
            e.setPitch(pitch);
            mc.player.rotationYawHead = yaw;
            mc.player.renderYawOffset = yaw;
            mc.player.rotationPitchHead = pitch;
        }
    }

    private void updateTarget() {
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && isValid(living)) {
                // RayTrace проверка
                if (options.getValueByName("RayTrace проверка").get()) {
                    if (!canSeeEntity(living)) {
                        continue;
                    }
                }
                targets.add(living);
            }
        }

        if (targets.isEmpty()) {
            target = null;
            return;
        }

        if (targets.size() == 1) {
            target = targets.get(0);
            return;
        }

        // Умная сортировка с приоритетами
        targets.sort(Comparator.comparingDouble(this::getTargetPriority).reversed());

        target = targets.get(0);
    }

    /**
     * Приоритет цели - умный выбор
     */
    private double getTargetPriority(LivingEntity entity) {
        double priority = 0;

        // Приоритет по расстоянию (ближе = выше приоритет)
        double distance = mc.player.getDistance(entity);
        priority += (10.0 - Math.min(distance, 10.0)) * 2.0;

        // Приоритет по видимости (RayTrace)
        if (canSeeEntity(entity)) {
            priority += 15.0;
        }

        // Приоритет по углу обзора (в поле зрения = выше приоритет)
        float angleToEntity = getAngleToEntity(entity);
        if (angleToEntity < 90) {
            priority += (90 - angleToEntity) * 0.1;
        }

        // Приоритет по броне (меньше брони = выше приоритет)
        if (entity instanceof PlayerEntity) {
            priority += (10.0 - getEntityArmor((PlayerEntity) entity)) * 0.5;
        }

        // Приоритет по HP (меньше HP = выше приоритет)
        double health = getEntityHealth(entity);
        priority += (20.0 - Math.min(health, 20.0)) * 0.3;

        return priority;
    }

    /**
     * Получить угол между взглядом игрока и сущностью
     */
    private float getAngleToEntity(LivingEntity entity) {
        Vector3d playerLook = mc.player.getLook(1.0F);
        Vector3d toEntity = entity.getPositionVec().subtract(mc.player.getEyePosition(1.0F)).normalize();
        
        double dot = playerLook.dotProduct(toEntity);
        return (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    /**
     * RayTrace проверка видимости сущности
     */
    private boolean canSeeEntity(LivingEntity entity) {
        if (mc.player == null || entity == null) return false;

        Vector3d eyePos = mc.player.getEyePosition(1.0F);
        Vector3d entityPos = entity.getPositionVec().add(0, entity.getEyeHeight() / 2, 0);

        // Проверка через RayTraceContext - только блоки
        RayTraceContext context = new RayTraceContext(
            eyePos,
            entityPos,
            RayTraceContext.BlockMode.COLLIDER,
            RayTraceContext.FluidMode.NONE,
            mc.player
        );
        
        RayTraceResult rayTrace = mc.world.rayTraceBlocks(context);

        // Если не попали в блок - сущность видна
        return rayTrace.getType() == RayTraceResult.Type.MISS;
    }

    /**
     * Получить ближайшую валидную цель (для режима "Пенить")
     */
    private LivingEntity getNearestValidTarget() {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && isValid(living)) {
                // RayTrace проверка
                if (options.getValueByName("RayTrace проверка").get()) {
                    if (!canSeeEntity(living)) {
                        continue;
                    }
                }
                
                double dist = mc.player.getDistance(living);
                if (dist < nearestDistance) {
                    nearestDistance = dist;
                    nearest = living;
                }
            }
        }

        return nearest;
    }

    float lastYaw, lastPitch;

    private void updateRotation(boolean attack, float rotationYawSpeed, float rotationPitchSpeed) {
        Vector3d vec = target.getPositionVec().add(0, clamp(mc.player.getPosYEye() - target.getPosY(),
                        0, target.getHeight() * (mc.player.getDistanceEyePos(target) / attackRange.get())), 0)
                .subtract(mc.player.getEyePosition(1.0F));

        isRotated = true;

        float yawToTarget = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90);
        float pitchToTarget = (float) (-Math.toDegrees(Math.atan2(vec.y, hypot(vec.x, vec.z))));

        // Human-like ротация с гауссовским шумом
        if (options.getValueByName("Human Rotation").get() || shouldApplyRotationRandomness()) {
            float noiseYaw = (float) nextGaussian() * getRotationRandomness();
            float noisePitch = (float) nextGaussian() * getRotationRandomness() * 0.5f;
            yawToTarget += noiseYaw;
            pitchToTarget += noisePitch;
        }

        // Дополнительное микро-дрожание для обхода
        if (shouldApplyMicroShake()) {
            yawToTarget += (random.nextFloat() - 0.5f) * getMicroShakeAmount();
            pitchToTarget += (random.nextFloat() - 0.5f) * getMicroShakeAmount() * 0.3f;
        }

        float yawDelta = (wrapDegrees(yawToTarget - rotateVector.x));
        float pitchDelta = (wrapDegrees(pitchToTarget - rotateVector.y));

        switch (type.get()) {
            case "Плавная" -> {
                float speedMultiplier = getRotationSpeedMultiplier();
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.5f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.5f), rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                // Добавляем микро-дрожание для некоторых серверов
                float microShake = (random.nextFloat() - 0.5f) * getMicroShakeAmount();
                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw) + microShake;
                
                // Плавная ротация на 360 градусов без ограничений по pitch
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Резкая" -> {
                float speedMultiplier = getRotationSpeedMultiplier();
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 5.0f), rotationYawSpeed * 1.5f * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 3.0f), rotationPitchSpeed * 1.3f * speedMultiplier);

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Snap" -> {
                float speedMultiplier = getRotationSpeedMultiplier();
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.2f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.2f), rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -360.0F, 360.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Интеллектуальная" -> {
                double distance = mc.player.getDistance(target);
                float speedMultiplier = (float) Math.min(distance / 3.0, 1.5) * getRotationSpeedMultiplier();

                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.5f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.5f), rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 2f;
                }

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Matrix" -> {
                float randomFactor = 0.8f + random.nextFloat() * 0.4f;
                float speedMultiplier = getRotationSpeedMultiplier();

                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 1.0f), rotationYawSpeed * randomFactor * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.0f), rotationPitchSpeed * randomFactor * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                float microShake = (random.nextFloat() - 0.5f) * getMicroShakeAmount();

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw) + microShake;
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Экспоненциальная" -> {
                float yawProgress = 1.0f - (Math.abs(yawDelta) / 180.0f);
                float pitchProgress = 1.0f - (Math.abs(pitchDelta) / 90.0f);

                float yawAccel = (float) Math.pow(yawProgress, 2);
                float pitchAccel = (float) Math.pow(pitchProgress, 2);

                float speedMultiplier = getRotationSpeedMultiplier();
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.5f), rotationYawSpeed * (1.0f + yawAccel) * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.5f), rotationPitchSpeed * (1.0f + pitchAccel) * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 2.5f;
                }

                float microShake = (random.nextFloat() - 0.5f) * getMicroShakeAmount();
                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw) + microShake;
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Human" -> {
                // Human режим - максимально естественная ротация
                float speedMultiplier = 0.7f + random.nextFloat() * 0.3f; // 0.7-1.0
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 2.0f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.5f), rotationPitchSpeed * speedMultiplier);

                // Замедление к концу для естественности
                if (Math.abs(yawDelta) < 10) {
                    clampedYaw *= 0.5f;
                }
                if (Math.abs(pitchDelta) < 5) {
                    clampedPitch *= 0.5f;
                }

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                if (options.getValueByName("Коррекция движения").get()) {
                    mc.player.rotationYawOffset = yaw;
                }
            }
            case "Silent" -> {
                // Silent Aim - только для сервера
                float speedMultiplier = getRotationSpeedMultiplier() * 0.9f;
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 1.0f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.0f), rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = clamp(rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch), -89.0F, 89.0F);

                float gcd = SensUtils.getGCDValue();
                yaw -= (yaw - rotateVector.x) % gcd;
                pitch -= (pitch - rotateVector.y) % gcd;

                rotateVector = new Vector2f(yaw, pitch);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
                // Silent Aim не меняет rotationYawOffset
            }
        }
    }

    /**
     * Гауссовский случайный шум для human-like ротации
     */
    private double nextGaussian() {
        if (haveNextGaussian) {
            haveNextGaussian = false;
            return nextGaussian;
        } else {
            double v1, v2, s;
            do {
                v1 = 2 * random.nextDouble() - 1;
                v2 = 2 * random.nextDouble() - 1;
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            
            double multiplier = Math.sqrt(-2.0 * Math.log(s) / s);
            nextGaussian = v1 * multiplier;
            haveNextGaussian = true;
            return v2 * multiplier;
        }
    }

    private boolean shouldApplyRotationRandomness() {
        return switch (serverMode.get()) {
            case "SkyTime", "SpookyTime", "ReallyWorld", "Matrix", "Vulcan", "Grim", "NCP" -> true;
            case "Watchdog" -> true;
            case "Watchdog-Tap" -> true;
            default -> false;
        };
    }

    private boolean shouldApplyMicroShake() {
        return switch (serverMode.get()) {
            case "Matrix", "Vulcan", "SkyTime", "SpookyTime", "Grim" -> true;
            case "Watchdog" -> true;
            case "Watchdog-Tap" -> true;
            default -> false;
        };
    }

    private float getRotationRandomness() {
        return switch (serverMode.get()) {
            case "SkyTime" -> 0.8f;
            case "SpookyTime" -> 1.2f;
            case "ReallyWorld" -> 0.6f;
            case "Matrix" -> 1.0f;
            case "Vulcan" -> 0.5f;
            case "Grim" -> 0.7f;
            case "NCP" -> 0.4f;
            case "Watchdog" -> 0.9f;
            case "Watchdog-Tap" -> 0.9f;
            default -> 0.0f;
        };
    }

    public String getServerBypassDescription() {
        return switch (serverMode.get()) {
            case "FunTime" -> "Обход: Random delay 480-550ms + sprint keep";
            case "SkyTime" -> "Обход: Fast attack 420-500ms + gaussian rotation";
            case "HollyWorld" -> "Обход: Strict rotation 500-580ms + sprint keep";
            case "ReallyWorld" -> "Обход: Strict anti-cheat 520-580ms + minimal randomness";
            case "SpookyTime" -> "Обход: Aggressive 400-500ms + high randomness";
            case "Matrix" -> "Обход: Matrix mode 450-540ms + micro shake";
            case "Vulcan" -> "Обход: Vulcan mode 470-545ms + low randomness";
            case "Grim" -> "Обход: GrimAC 380-440ms + smooth rotation";
            case "NCP" -> "Обход: NCP strict 500-550ms + low randomness";
            case "Watchdog" -> "Обход: Watchdog 450-550ms + gaussian rotation";
            case "Watchdog-Tap" -> "Обход: Watchdog TAP 500-580ms + human delay";
            default -> "Обход: Vanilla 500ms";
        };
    }

    private float getRotationSpeedMultiplier() {
        return switch (serverMode.get()) {
            case "SkyTime" -> 1.3f;
            case "SpookyTime" -> 1.4f;
            case "FunTime" -> 1.1f;
            case "HollyWorld" -> 1.0f;
            case "ReallyWorld" -> 0.9f;
            case "Matrix" -> 1.0f;
            case "Vulcan" -> 0.95f;
            case "Grim" -> 1.2f;
            case "NCP" -> 0.85f;
            case "Watchdog" -> 1.05f;
            case "Watchdog-Tap" -> 1.05f;
            default -> 1.0f;
        };
    }

    private float getMicroShakeAmount() {
        return switch (serverMode.get()) {
            case "Matrix" -> 0.4f;
            case "Vulcan" -> 0.2f;
            case "SkyTime" -> 0.3f;
            case "SpookyTime" -> 0.3f;
            case "Grim" -> 0.25f;
            case "Watchdog" -> 0.35f;
            case "Watchdog-Tap" -> 0.35f;
            default -> 0.0f;
        };
    }

    private void updateAttack() {
        selected = MouseUtil.getMouseOver(target, rotateVector.x, rotateVector.y, attackRange.get());

        if (options.getValueByName("Ускорять ротацию при атаке").get()) {
            updateRotation(true, 60, 35);
        }

        if ((selected == null || selected != target) && !mc.player.isElytraFlying()) {
            if (CombatAdapter.isStrictAntiCheat()) {
                return;
            }
        }

        if (mc.player.isBlocking() && options.getValueByName("Отжимать щит").get()) {
            mc.playerController.onStoppedUsingItem(mc.player);
        }

        boolean wasSprinting = mc.player.isSprinting();

        stopWatch.setLastMS(500);
        CombatAdapter.attackEntity(target);

        if (shouldKeepSprint() && wasSprinting) {
            mc.player.setSprinting(true);
        }

        if (target instanceof PlayerEntity player && options.getValueByName("Ломать щит").get()) {
            breakShieldPlayer(player);
        }
    }

    private boolean isStrictAntiCheat() {
        return switch (serverMode.get()) {
            case "ReallyWorld", "HollyWorld", "Vulcan", "NCP", "Grim", "Watchdog", "Watchdog-Tap" -> true;
            default -> false;
        };
    }

    private boolean shouldKeepSprint() {
        return switch (serverMode.get()) {
            case "FunTime", "SkyTime", "HollyWorld", "SpookyTime", "Watchdog", "Watchdog-Tap" -> true;
            default -> false;
        };
    }

    private boolean shouldPlayerFalling() {
        boolean cancelReason = (mc.player.isInWater() && mc.player.areEyesInFluid(FluidTags.WATER))
                || mc.player.isInLava()
                || mc.player.isOnLadder()
                || mc.player.isPassenger()
                || mc.player.abilities.isFlying;

        if (cancelReason) return false;

        float attackStrength = CombatAdapter.getAttackCooldown(options.getValueByName("Синхронизировать атаку с ТПС").get()
                ? Laura.getInstance().getTpsCalc().getAdjustTicks() : 1.5f);

        if (attackStrength < 0.92f) {
            return false;
        }

        if (options.getValueByName("Только криты").get()) {
            return !mc.player.isOnGround() && mc.player.fallDistance > 0;
        }

        return true;
    }

    private boolean isValid(LivingEntity entity) {
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity.ticksExisted < 3) return false;
        if (CombatAdapter.getDistance(entity) > attackRange.get()) return false;

        if (entity instanceof PlayerEntity p) {
            if (AntiBot.isBot(entity)) {
                return false;
            }
            if (!targets.getValueByName("Друзья").get() && FriendStorage.isFriend(p.getName().getString())) {
                return false;
            }
            if (p.getName().getString().equalsIgnoreCase(mc.player.getName().getString())) return false;
        }

        if (entity instanceof PlayerEntity && !targets.getValueByName("Игроки").get()) {
            return false;
        }
        if (entity instanceof PlayerEntity && entity.getTotalArmorValue() == 0 && !targets.getValueByName("Голые").get()) {
            return false;
        }
        if (entity instanceof PlayerEntity && entity.isInvisible() && entity.getTotalArmorValue() == 0 && !targets.getValueByName("Голые невидимки").get()) {
            return false;
        }
        if (entity instanceof PlayerEntity && entity.isInvisible() && !targets.getValueByName("Невидимки").get()) {
            return false;
        }

        if (entity instanceof MonsterEntity && !targets.getValueByName("Мобы").get()) {
            return false;
        }
        if (entity instanceof AnimalEntity && !targets.getValueByName("Животные").get()) {
            return false;
        }

        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
    }

    private void breakShieldPlayer(PlayerEntity entity) {
        if (entity.isBlocking()) {
            int invSlot = InventoryUtil.getInstance().getAxeInInventory(false);
            int hotBarSlot = InventoryUtil.getInstance().getAxeInInventory(true);

            if (hotBarSlot == -1 && invSlot != -1) {
                int bestSlot = InventoryUtil.getInstance().findBestSlotInHotBar();
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);

                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CHeldItemChangePacket(bestSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CHeldItemChangePacket(mc.player.inventory.currentItem));

                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
            }

            if (hotBarSlot != -1) {
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CHeldItemChangePacket(hotBarSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CHeldItemChangePacket(mc.player.inventory.currentItem));
            }
        }
    }

    private void reset() {
        if (options.getValueByName("Коррекция движения").get()) {
            CombatAdapter.resetYawOffset();
        }
        rotateVector = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
    }

    /**
     * Плавная интерполяция угла
     */
    private float smoothRotation(float current, float target, float maxChange) {
        float delta = wrapDegrees(target - current);
        if (Math.abs(delta) > maxChange) {
            delta = maxChange * Math.signum(delta);
        }
        return wrapDegrees(current + delta);
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        reset();
        target = null;
        lastAttackTime = 0;
        humanReactionDelay = 0;
        isPostRotating = false;
        haveNextGaussian = false;
        return false;
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        reset();
        stopWatch.setLastMS(0);
        target = null;
        isPostRotating = false;
        return false;
    }

    private double getEntityArmor(PlayerEntity entityPlayer2) {
        double d2 = 0.0;
        for (int i2 = 0; i2 < 4; ++i2) {
            ItemStack is = entityPlayer2.inventory.armorInventory.get(i2);
            if (is.isEmpty() || !(is.getItem() instanceof net.minecraft.item.ArmorItem)) continue;
            d2 += getProtectionLvl(is);
        }
        return d2;
    }

    private double getProtectionLvl(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.ArmorItem i)) {
            return 0;
        }
        double damageReduceAmount = i.getDamageReduceAmount();
        if (stack.isEnchanted()) {
            damageReduceAmount += (double) EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, stack) * 0.25;
        }
        return damageReduceAmount;
    }

    private double getEntityHealth(LivingEntity ent) {
        if (!CombatAdapter.isAlive(ent)) {
            return 0.0;
        }

        float health = CombatAdapter.getHealth(ent);
        float absorption = CombatAdapter.getAbsorption(ent);

        return health + absorption;
    }

    public LivingEntity getCurrentTarget() {
        return target;
    }

    public String getTargetHealthFormatted() {
        if (target == null) return "0.0";

        double totalHealth = getEntityHealth(target);
        return String.format("%.1f", totalHealth);
    }

    public float getTargetHealthPercent() {
        if (target == null) return 0.0f;

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float absorption = target.getAbsorptionAmount();

        return Math.min(1.0f, (health + absorption) / maxHealth);
    }

    public void pause() {
        // Метод для временной паузы ауры (заглушка)
    }

    public float getAttackCooldown() {
        return CombatAdapter.getAttackCooldown(1.5f);
    }
}
