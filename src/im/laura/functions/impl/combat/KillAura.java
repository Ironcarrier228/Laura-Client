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
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.Math.hypot;
import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@SuppressWarnings({"unused", "SameParameterValue", "UnstableApiUsage"})
@FunctionRegister(name = "KillAura", type = Category.Combat)
public class KillAura extends Function {

    @Getter
    private final ModeSetting type = new ModeSetting("Тип", "Плавная",
            "Плавная", "Резкая", "Snap", "Интеллектуальная", "Matrix",
            "Экспоненциальная", "Human", "Silent", "Predictive", "SmoothAccel", "Jitter");

    private final SliderSetting attackRange = new SliderSetting("Дистанция атаки", 3f, 3f, 6f, 0.1f);
    @Getter
    private final SliderSetting rotationRange = new SliderSetting("Дистанция ротации", 1f, 0.5f, 3f, 0.1f);
    @Getter
    private final SliderSetting elytraRange = new SliderSetting("Элитра ротация", 10f, 1f, 30f, 0.1f);
    private final SliderSetting fov = new SliderSetting("FOV", 180f, 30f, 360f, 1f);
    private final SliderSetting switchDelay = new SliderSetting("Задержка свитча", 0f, 0f, 1000f, 50f);
    private final SliderSetting multiTargetCount = new SliderSetting("Мульти-таргеты", 1, 1, 5, 1);
    private final SliderSetting predictionTicks = new SliderSetting("Предикция тиков", 3, 0, 20, 1);

    final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", false),
            new BooleanSetting("Голые невидимки", true),
            new BooleanSetting("Невидимки", true),
            new BooleanSetting("Спящие", false));

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
            new BooleanSetting("Пост-ротация", true),
            new BooleanSetting("Предикция движения", true),
            new BooleanSetting("Умный выбор точки", true),
            new BooleanSetting("Авто-оружие", true),
            new BooleanSetting("Мульти-таргет", false),
            new BooleanSetting("Ротация через пакеты", false),
            new BooleanSetting("Ignore Walls (RayCast)", false),
            new BooleanSetting("Jitter на атаку", false),
            new BooleanSetting("Не атаковать в воде", true),
            new BooleanSetting("Не атаковать при полёте", false));

    final ModeSetting serverMode = new ModeSetting("Режим сервера", "Vanilla",
            "Vanilla", "FunTime", "SkyTime", "HollyWorld", "ReallyWorld", "SpookyTime",
            "Matrix", "Vulcan", "Grim", "NCP", "Watchdog", "Watchdog-Tap", "Freaky", "Virus");

    final ModeSetting correctionType = new ModeSetting("Тип коррекции", "Незаметный",
            "Незаметный", "Сфокусированный", "Пенить", "Smooth", "ACS");

    final ModeSetting aimPoint = new ModeSetting("Точка прицеливания", "Авто",
            "Авто", "Голова", "Тело", "Ноги", "Ближе к земле", "Дальше от земли");

    final ModeSetting weaponPriority = new ModeSetting("Приоритет оружия", "Меч",
            "Меч", "Топор", "Авто-урон");

    @Getter
    private final StopWatch stopWatch = new StopWatch();
    private Vector2f rotateVector = new Vector2f(0, 0);
    @Getter
    private LivingEntity target;
    @Getter
    private final List<LivingEntity> multiTargets = new CopyOnWriteArrayList<>();
    private Entity selected;

    int ticks = 0;
    boolean isRotated;
    private int currentMultiTargetIndex = 0;
    private long lastSwitchTime = 0;
    private LivingEntity lastTarget = null;

    private final Random random = new Random();
    private long lastAttackTime = 0;
    private long humanReactionDelay = 0;
    private float postRotationYaw = 0;
    private float postRotationPitch = 0;
    private boolean isPostRotating = false;
    private long postRotationStartTime = 0;

    private double nextGaussianValue = 0;
    private boolean haveNextGaussian = false;

    private final Vector3d[] positionHistory = new Vector3d[20];
    private Vector3d predictedPosition = null;
    private int historyIndex = 0;

    private float currentYawSpeed = 0;
    private float currentPitchSpeed = 0;

    private float jitterOffset = 0;
    private float acsCorrection = 0;

    final AutoPotion autoPotion;

    public KillAura(AutoPotion autoPotion) {
        this.autoPotion = autoPotion;
        addSettings(type, attackRange, rotationRange, elytraRange, fov, switchDelay,
                multiTargetCount, predictionTicks, targets, options, serverMode,
                correctionType, aimPoint, weaponPriority);
        Arrays.fill(positionHistory, Vector3d.ZERO);
    }

    @Subscribe
    public void onInput(EventInput eventInput) {
        if (options.getValueByName("Коррекция движения").get() && target != null && mc.player != null) {
            if (correctionType.is("Пенить")) {
                LivingEntity nearestTarget = getNearestValidTarget();
                if (nearestTarget != null && nearestTarget != target) {
                    target = nearestTarget;
                    Vector3d vec = getAimPoint(nearestTarget).subtract(mc.player.getEyePosition(1.0F));
                    float yaw = (float) wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90);
                    float pitch = (float) (-Math.toDegrees(Math.atan2(vec.y, hypot(vec.x, vec.z))));
                    rotateVector = new Vector2f(yaw, pitch);
                    mc.player.rotationYawOffset = yaw;
                }
                MoveUtils.fixMovement(eventInput, rotateVector.x);
            } else if (correctionType.is("ACS")) {
                MoveUtils.fixMovement(eventInput, rotateVector.x + acsCorrection);
            } else {
                MoveUtils.fixMovement(eventInput, rotateVector.x);
            }
        }
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        int maxTargets = multiTargetCount.get().intValue();
        long switchDelayMs = switchDelay.get().longValue();
        int predictTicks = predictionTicks.get().intValue();

        if (options.getValueByName("Мульти-таргет").get()) {
            updateMultiTargets(maxTargets);

            if (multiTargets.isEmpty()) {
                reset();
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSwitchTime > switchDelayMs ||
                    !isValid(multiTargets.get(currentMultiTargetIndex))) {

                lastSwitchTime = currentTime;
                lastTarget = target;

                for (int i = 0; i < multiTargets.size(); i++) {
                    currentMultiTargetIndex = (currentMultiTargetIndex + 1) % multiTargets.size();
                    if (isValid(multiTargets.get(currentMultiTargetIndex))) {
                        break;
                    }
                }
                target = multiTargets.get(currentMultiTargetIndex);
            }
        } else {
            if (correctionType.is("Пенить")) {
                updateTarget();
                LivingEntity nearestTarget = getNearestValidTarget();
                if (nearestTarget != null && nearestTarget != target) {
                    target = nearestTarget;
                }
            } else if (options.getValueByName("Фокусировать одну цель").get() && (target == null || !isValid(target))
                    || !options.getValueByName("Фокусировать одну цель").get()) {
                updateTarget();
            }
        }

        if (target != null) {
            updatePositionHistory(target);
            predictedPosition = options.getValueByName("Предикция движения").get()
                    ? predictPosition(target, predictTicks)
                    : null;
        }

        if (options.getValueByName("Пост-ротация").get() && isPostRotating) {
            handlePostRotation();
        }

        if (correctionType.is("ACS")) {
            updateACSCorrection();
        }

        if (target != null && !(autoPotion.isState() && autoPotion.isActive())) {
            isRotated = false;

            long delay = getAttackDelay();
            if (options.getValueByName("Human Rotation").get()) {
                delay += humanReactionDelay;
            }

            if (lastTarget != target && switchDelayMs > 0) {
                delay += switchDelayMs;
            }

            if (shouldPlayerFalling() && (System.currentTimeMillis() - lastAttackTime >= delay)) {
                if (options.getValueByName("Авто-оружие").get()) {
                    autoWeaponSwap();
                }

                updateAttack();
                lastAttackTime = System.currentTimeMillis();

                if (options.getValueByName("Human Rotation").get()) {
                    humanReactionDelay = 150 + random.nextInt(150);
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
            } else if (type.is("Predictive")) {
                if (!isRotated) {
                    updateRotationPredictive(false, 70, 35);
                }
            } else if (type.is("SmoothAccel")) {
                if (!isRotated) {
                    updateRotationSmoothAccel(false, 100, 50);
                }
            } else if (type.is("Jitter")) {
                if (!isRotated) {
                    updateRotationJitter(false, 80, 40);
                }
            } else if (!isRotated) {
                updateRotation(false, 80, 35);
            }

        } else {
            stopWatch.setLastMS(0);
            reset();
        }
    }

    @Subscribe
    private void onWalking(EventMotion e) {
        if (target == null || autoPotion.isState() && autoPotion.isActive()) return;

        float yaw = rotateVector.x;
        float pitch = rotateVector.y;

        if (options.getValueByName("Jitter на атаку").get() && type.is("Jitter")) {
            yaw += jitterOffset;
            pitch += (random.nextFloat() - 0.5f) * 2f;
        }

        if (options.getValueByName("Ротация через пакеты").get() &&
                (type.is("Silent") || serverMode.is("Matrix") || serverMode.is("Vulcan"))) {
            mc.player.connection.sendPacket(new CPlayerPacket.RotationPacket(yaw, pitch, mc.player.isOnGround()));
            e.setYaw(mc.player.rotationYaw);
            e.setPitch(mc.player.rotationPitch);
        } else if (options.getValueByName("Silent Aim").get() && type.is("Silent")) {
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

    private void updatePositionHistory(LivingEntity entity) {
        positionHistory[historyIndex % 20] = entity.getPositionVec();
        historyIndex++;
    }

    private Vector3d predictPosition(LivingEntity entity, int ticksAhead) {
        if (ticksAhead <= 0 || historyIndex < 3) {
            return entity.getPositionVec();
        }

        int samples = Math.min(historyIndex, 10);
        Vector3d totalVelocity = Vector3d.ZERO;

        for (int i = 1; i < samples; i++) {
            int current = (historyIndex - i) % 20;
            int previous = (historyIndex - i - 1) % 20;

            if (!positionHistory[current].equals(Vector3d.ZERO) && !positionHistory[previous].equals(Vector3d.ZERO)) {
                totalVelocity = totalVelocity.add(positionHistory[current].subtract(positionHistory[previous]));
            }
        }

        Vector3d averageVelocity = totalVelocity.scale(1.0 / (samples - 1));

        double gravityCompensation = 0;
        if (!entity.isOnGround() && !entity.isInWater()) {
            gravityCompensation = -0.08 * ticksAhead * ticksAhead * 0.5;
        }

        double distance = mc.player.getDistance(entity);
        double falloff = Math.max(0.3, 1.0 - (distance / 20.0));

        return entity.getPositionVec()
                .add(averageVelocity.scale(ticksAhead * falloff))
                .add(0, gravityCompensation, 0);
    }

    private Vector3d getAimPoint(LivingEntity entity) {
        Vector3d basePos = predictedPosition != null ? predictedPosition : entity.getPositionVec();

        double heightOffset = switch (aimPoint.get()) {
            case "Голова" -> entity.getEyeHeight() * 0.9;
            case "Тело" -> entity.getEyeHeight() * 0.5;
            case "Ноги" -> entity.getEyeHeight() * 0.1;
            case "Ближе к земле" -> {
                if (mc.player.getPosYEye() > entity.getPosYEye()) {
                    yield entity.getEyeHeight() * 0.3;
                } else {
                    yield entity.getEyeHeight() * 0.7;
                }
            }
            case "Дальше от земли" -> {
                if (mc.player.getPosYEye() < entity.getPosYEye()) {
                    yield entity.getEyeHeight() * 0.8;
                } else {
                    yield entity.getEyeHeight() * 0.2;
                }
            }
            default -> options.getValueByName("Умный выбор точки").get()
                    ? getSmartAimPoint(entity)
                    : entity.getEyeHeight() * 0.5;
        };

        return basePos.add(0, heightOffset, 0);
    }

    private double getSmartAimPoint(LivingEntity entity) {
        double playerEye = mc.player.getPosYEye();
        double targetEye = entity.getPosYEye() + entity.getEyeHeight();
        double targetFeet = entity.getPosY();

        if (playerEye > targetEye) {
            double heightDiff = playerEye - targetEye;
            return heightDiff > 2.0 ? entity.getEyeHeight() * 0.2 : entity.getEyeHeight() * 0.4;
        }

        if (playerEye < targetFeet) {
            return entity.getEyeHeight() * 0.8;
        }

        return entity.getEyeHeight() * 0.5;
    }

    private void updateMultiTargets(int maxTargets) {
        List<LivingEntity> validTargets = new ArrayList<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && isValid(living)) {
                if (options.getValueByName("RayTrace проверка").get() &&
                        !options.getValueByName("Ignore Walls (RayCast)").get()) {
                    if (!canSeeEntity(living)) continue;
                }
                validTargets.add(living);
            }
        }

        validTargets.sort(Comparator.comparingDouble(this::getTargetPriority).reversed());

        multiTargets.clear();
        for (int i = 0; i < Math.min(validTargets.size(), maxTargets); i++) {
            multiTargets.add(validTargets.get(i));
        }

        if (multiTargets.isEmpty()) {
            target = null;
        } else if (target == null || !multiTargets.contains(target)) {
            target = multiTargets.get(0);
            currentMultiTargetIndex = 0;
        }
    }

    private void autoWeaponSwap() {
        int currentSlot = mc.player.inventory.currentItem;
        int bestSlot = -1;
        double bestDamage = getWeaponDamage(mc.player.getHeldItemMainhand());

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            double damage = getWeaponDamage(stack);

            if (target instanceof PlayerEntity targetPlayer && targetPlayer.isBlocking() &&
                    stack.getItem() instanceof AxeItem) {
                damage += 10;
            }

            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }

        if (bestSlot != -1 && bestSlot != currentSlot) {
            mc.player.connection.sendPacket(new CHeldItemChangePacket(bestSlot));
            mc.player.inventory.currentItem = bestSlot;
        }
    }

    private double getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        if (stack.getItem() instanceof SwordItem sword) {
            double damage = sword.getAttackDamage();
            damage += EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, stack) * 0.5;
            damage += EnchantmentHelper.getEnchantmentLevel(Enchantments.FIRE_ASPECT, stack) * 1.5;
            return damage;
        }

        if (stack.getItem() instanceof AxeItem axe) {
            double damage = axe.getAttackDamage();
            damage += EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, stack) * 0.5;
            return damage;
        }

        return 0;
    }

    private void updateRotationPredictive(boolean attack, float rotationYawSpeed, float rotationPitchSpeed) {
        Vector3d vec = getAimPoint(target).subtract(mc.player.getEyePosition(1.0F));
        isRotated = true;

        float[] rotations = calculateRotations(vec, rotationYawSpeed, rotationPitchSpeed);
        float yawToTarget = rotations[0];
        float pitchToTarget = rotations[1];

        float yawDelta = wrapDegrees(yawToTarget - rotateVector.x);
        float pitchDelta = wrapDegrees(pitchToTarget - rotateVector.y);

        double targetSpeed = getTargetSpeed(target);
        float speedBoost = (float) Math.min(targetSpeed * 0.5, 2.0f);

        float speedMultiplier = getRotationSpeedMultiplier() * (1.0f + speedBoost);
        float clampedYaw = clampAbs(yawDelta, 0.5f, rotationYawSpeed * speedMultiplier);
        float clampedPitch = clampAbs(pitchDelta, 0.5f, rotationPitchSpeed * speedMultiplier);

        float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw;
        float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);

        applyGCD(yaw, pitch);
    }

    private void updateRotationSmoothAccel(boolean attack, float maxYawSpeed, float maxPitchSpeed) {
        Vector3d vec = getAimPoint(target).subtract(mc.player.getEyePosition(1.0F));
        isRotated = true;

        float[] rotations = calculateRotations(vec, maxYawSpeed, maxPitchSpeed);
        float yawToTarget = rotations[0];
        float pitchToTarget = rotations[1];

        float yawDelta = wrapDegrees(yawToTarget - rotateVector.x);
        float pitchDelta = wrapDegrees(pitchToTarget - rotateVector.y);

        float accelFactor = 0.15f;
        float decelerationFactor = 0.08f;

        float targetYawSpeed = Math.min(Math.abs(yawDelta) * 0.8f, maxYawSpeed) * getRotationSpeedMultiplier();
        float targetPitchSpeed = Math.min(Math.abs(pitchDelta) * 0.8f, maxPitchSpeed) * getRotationSpeedMultiplier();

        if (Math.abs(yawDelta) > 5) {
            currentYawSpeed = Math.min(currentYawSpeed + targetYawSpeed * accelFactor, targetYawSpeed);
        } else {
            currentYawSpeed = Math.max(currentYawSpeed - currentYawSpeed * decelerationFactor, 1.0f);
        }

        if (Math.abs(pitchDelta) > 3) {
            currentPitchSpeed = Math.min(currentPitchSpeed + targetPitchSpeed * accelFactor, targetPitchSpeed);
        } else {
            currentPitchSpeed = Math.max(currentPitchSpeed - currentPitchSpeed * decelerationFactor, 0.5f);
        }

        float yaw = rotateVector.x + Math.signum(yawDelta) * currentYawSpeed;
        float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * currentPitchSpeed, -89.0F, 89.0F);

        applyGCD(yaw, pitch);
    }

    private void updateRotationJitter(boolean attack, float rotationYawSpeed, float rotationPitchSpeed) {
        Vector3d vec = getAimPoint(target).subtract(mc.player.getEyePosition(1.0F));
        isRotated = true;

        float[] rotations = calculateRotations(vec, rotationYawSpeed, rotationPitchSpeed);
        float yawToTarget = rotations[0];
        float pitchToTarget = rotations[1];

        float yawDelta = wrapDegrees(yawToTarget - rotateVector.x);
        float pitchDelta = wrapDegrees(pitchToTarget - rotateVector.y);

        float speedMultiplier = getRotationSpeedMultiplier();
        float clampedYaw = clampAbs(yawDelta, 2.0f, rotationYawSpeed * speedMultiplier);
        float clampedPitch = clampAbs(pitchDelta, 1.0f, rotationPitchSpeed * speedMultiplier);

        jitterOffset = (random.nextFloat() - 0.5f) * 4f;
        float jitterPitch = (random.nextFloat() - 0.5f) * 2f;

        float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw + jitterOffset;
        float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch + jitterPitch, -89.0F, 89.0F);

        applyGCD(yaw, pitch);
    }

    private float[] calculateRotations(Vector3d vec, float maxYawSpeed, float maxPitchSpeed) {
        float yawToTarget = (float) wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90);
        float pitchToTarget = (float) (-Math.toDegrees(Math.atan2(vec.y, hypot(vec.x, vec.z))));

        if (options.getValueByName("Human Rotation").get() || shouldApplyRotationRandomness()) {
            yawToTarget += (float) nextGaussian() * getRotationRandomness();
            pitchToTarget += (float) nextGaussian() * getRotationRandomness() * 0.5f;
        }

        if (shouldApplyMicroShake()) {
            yawToTarget += (random.nextFloat() - 0.5f) * getMicroShakeAmount();
            pitchToTarget += (random.nextFloat() - 0.5f) * getMicroShakeAmount() * 0.3f;
        }

        return new float[]{yawToTarget, pitchToTarget};
    }

    private float clampAbs(float value, float min, float max) {
        return Math.min(Math.max(Math.abs(value), min), max);
    }

    private void applyGCD(float yaw, float pitch) {
        float gcd = SensUtils.getGCDValue();
        yaw -= (yaw - rotateVector.x) % gcd;
        pitch -= (pitch - rotateVector.y) % gcd;

        rotateVector = new Vector2f(yaw, pitch);
        if (options.getValueByName("Коррекция движения").get()) {
            mc.player.rotationYawOffset = yaw;
        }
    }

    private void updateRotation(boolean attack, float rotationYawSpeed, float rotationPitchSpeed) {
        Vector3d vec = getAimPoint(target).subtract(mc.player.getEyePosition(1.0F));
        isRotated = true;

        float[] rotations = calculateRotations(vec, rotationYawSpeed, rotationPitchSpeed);
        float yawToTarget = rotations[0];
        float pitchToTarget = rotations[1];

        float yawDelta = wrapDegrees(yawToTarget - rotateVector.x);
        float pitchDelta = wrapDegrees(pitchToTarget - rotateVector.y);

        float speedMultiplier = getRotationSpeedMultiplier();
        float microShake = shouldApplyMicroShake() ? (random.nextFloat() - 0.5f) * getMicroShakeAmount() : 0;

        switch (type.get()) {
            case "Плавная" -> {
                float clampedYaw = clampAbs(yawDelta, 0.5f, rotationYawSpeed * speedMultiplier);
                float clampedPitch = clampAbs(pitchDelta, 0.5f, rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw + microShake;
                float pitch = rotateVector.y + Math.signum(pitchDelta) * clampedPitch;
                applyGCD(yaw, pitch);
            }
            case "Резкая" -> {
                float clampedYaw = clampAbs(yawDelta, 5.0f, rotationYawSpeed * 1.5f * speedMultiplier);
                float clampedPitch = clampAbs(pitchDelta, 3.0f, rotationPitchSpeed * 1.3f * speedMultiplier);

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);
                applyGCD(yaw, pitch);
            }
            case "Snap" -> {
                float clampedYaw = clampAbs(yawDelta, 0.2f, rotationYawSpeed * speedMultiplier);
                float clampedPitch = clampAbs(pitchDelta, 0.2f, rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -360.0F, 360.0F);
                applyGCD(yaw, pitch);
            }
            case "Интеллектуальная" -> {
                double distance = mc.player.getDistance(target);
                float distMultiplier = (float) Math.min(distance / 3.0, 1.5);

                float clampedYaw = clampAbs(yawDelta, 0.5f, rotationYawSpeed * distMultiplier * speedMultiplier);
                float clampedPitch = clampAbs(pitchDelta, 0.5f, rotationPitchSpeed * distMultiplier * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 2f;
                }

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);
                applyGCD(yaw, pitch);
            }
            case "Matrix" -> {
                float randomFactor = 0.8f + random.nextFloat() * 0.4f;

                float clampedYaw = clampAbs(yawDelta, 1.0f, rotationYawSpeed * randomFactor * speedMultiplier);
                float clampedPitch = clampAbs(pitchDelta, 1.0f, rotationPitchSpeed * randomFactor * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw + microShake;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);
                applyGCD(yaw, pitch);
            }
            case "Экспоненциальная" -> {
                float yawProgress = 1.0f - (Math.abs(yawDelta) / 180.0f);
                float pitchProgress = 1.0f - (Math.abs(pitchDelta) / 90.0f);
                float yawAccel = yawProgress * yawProgress;
                float pitchAccel = pitchProgress * pitchProgress;

                float clampedYaw = clampAbs(yawDelta, 0.5f, rotationYawSpeed * (1.0f + yawAccel) * speedMultiplier);
                float clampedPitch = clampAbs(pitchDelta, 0.5f, rotationPitchSpeed * (1.0f + pitchAccel) * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 2.5f;
                }

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw + microShake;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);
                applyGCD(yaw, pitch);
            }
            case "Human" -> {
                float humanSpeed = 0.7f + random.nextFloat() * 0.3f;
                float clampedYaw = clampAbs(yawDelta, 2.0f, rotationYawSpeed * humanSpeed);
                float clampedPitch = clampAbs(pitchDelta, 1.5f, rotationPitchSpeed * humanSpeed);

                if (Math.abs(yawDelta) < 10) clampedYaw *= 0.5f;
                if (Math.abs(pitchDelta) < 5) clampedPitch *= 0.5f;

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);
                applyGCD(yaw, pitch);
            }
            default -> {
                float clampedYaw = clampAbs(yawDelta, 1.0f, rotationYawSpeed * speedMultiplier * 0.9f);
                float clampedPitch = clampAbs(pitchDelta, 1.0f, rotationPitchSpeed * speedMultiplier * 0.9f);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + Math.signum(yawDelta) * clampedYaw;
                float pitch = clamp(rotateVector.y + Math.signum(pitchDelta) * clampedPitch, -89.0F, 89.0F);
                applyGCD(yaw, pitch);
            }
        }
    }

    private void updateACSCorrection() {
        if (target == null) {
            acsCorrection = 0;
            return;
        }

        float yawToTarget = (float) wrapDegrees(Math.toDegrees(
                Math.atan2(target.getPosY() - mc.player.getPosYEye(),
                        hypot(target.getPosX() - mc.player.getPosX(),
                                target.getPosZ() - mc.player.getPosZ()))) * -1);

        float targetCorrection = yawToTarget * 0.1f;
        acsCorrection += (targetCorrection - acsCorrection) * 0.15f;
    }

    private void handlePostRotation() {
        long elapsed = System.currentTimeMillis() - postRotationStartTime;
        if (elapsed > 300) {
            isPostRotating = false;
            return;
        }

        float progress = easeOutCubic((float) elapsed / 300f);

        float currentYaw = mc.player.rotationYaw;
        float currentPitch = mc.player.rotationPitch;

        float targetYaw = currentYaw + (postRotationYaw - currentYaw) * (1 - progress);
        float targetPitch = currentPitch + (postRotationPitch - currentPitch) * (1 - progress);

        rotateVector = new Vector2f(targetYaw, targetPitch);
    }

    private float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    private double getTargetSpeed(LivingEntity entity) {
        if (historyIndex < 2) return 0;

        int current = (historyIndex - 1) % 20;
        int previous = (historyIndex - 2) % 20;

        if (positionHistory[current].equals(Vector3d.ZERO) || positionHistory[previous].equals(Vector3d.ZERO)) {
            return 0;
        }

        return positionHistory[current].distanceTo(positionHistory[previous]);
    }

    private double nextGaussian() {
        if (haveNextGaussian) {
            haveNextGaussian = false;
            return nextGaussianValue;
        } else {
            double v1, v2, s;
            do {
                v1 = 2 * random.nextDouble() - 1;
                v2 = 2 * random.nextDouble() - 1;
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);

            double multiplier = Math.sqrt(-2.0 * Math.log(s) / s);
            nextGaussianValue = v1 * multiplier;
            haveNextGaussian = true;
            return v2 * multiplier;
        }
    }

    private long getAttackDelay() {
        return switch (serverMode.get()) {
            case "FunTime" -> 480L + random.nextInt(70);
            case "SkyTime" -> 420L + random.nextInt(80);
            case "HollyWorld" -> 500L + random.nextInt(80);
            case "ReallyWorld" -> 520L + random.nextInt(60);
            case "SpookyTime" -> 400L + random.nextInt(100);
            case "Matrix" -> 450L + random.nextInt(90);
            case "Vulcan" -> 470L + random.nextInt(75);
            case "Grim" -> 380L + random.nextInt(60);
            case "NCP" -> 500L + random.nextInt(50);
            case "Watchdog" -> 450L + random.nextInt(100);
            case "Watchdog-Tap" -> 500L + random.nextInt(80);
            case "Freaky" -> 350L + random.nextInt(100);
            case "Virus" -> 400L + random.nextInt(120);
            default -> 500L;
        };
    }

    private boolean shouldApplyRotationRandomness() {
        String mode = serverMode.get();
        return mode.equals("SkyTime") || mode.equals("SpookyTime") || mode.equals("ReallyWorld")
                || mode.equals("Matrix") || mode.equals("Vulcan") || mode.equals("Grim")
                || mode.equals("NCP") || mode.equals("Watchdog") || mode.equals("Watchdog-Tap")
                || mode.equals("Freaky") || mode.equals("Virus");
    }

    private boolean shouldApplyMicroShake() {
        String mode = serverMode.get();
        return mode.equals("Matrix") || mode.equals("Vulcan") || mode.equals("SkyTime")
                || mode.equals("SpookyTime") || mode.equals("Grim") || mode.equals("Watchdog")
                || mode.equals("Watchdog-Tap") || mode.equals("Freaky");
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
            case "Watchdog", "Watchdog-Tap" -> 0.9f;
            case "Freaky" -> 1.1f;
            case "Virus" -> 0.6f;
            default -> 0.0f;
        };
    }

    private float getRotationSpeedMultiplier() {
        return switch (serverMode.get()) {
            case "SkyTime" -> 1.3f;
            case "SpookyTime" -> 1.4f;
            case "FunTime" -> 1.1f;
            case "ReallyWorld" -> 0.9f;
            case "Matrix" -> 1.0f;
            case "Vulcan" -> 0.95f;
            case "Grim" -> 1.2f;
            case "NCP" -> 0.85f;
            case "Watchdog", "Watchdog-Tap" -> 1.05f;
            case "Freaky" -> 1.3f;
            case "Virus" -> 0.9f;
            default -> 1.0f;
        };
    }

    private float getMicroShakeAmount() {
        return switch (serverMode.get()) {
            case "Matrix" -> 0.4f;
            case "Vulcan" -> 0.2f;
            case "SkyTime", "SpookyTime" -> 0.3f;
            case "Grim" -> 0.25f;
            case "Watchdog", "Watchdog-Tap" -> 0.35f;
            case "Freaky" -> 0.45f;
            default -> 0.0f;
        };
    }

    public String getServerBypassDescription() {
        return switch (serverMode.get()) {
            case "FunTime" -> "Random delay 480-550ms + sprint keep";
            case "SkyTime" -> "Fast attack 420-500ms + normal rotation";
            case "HollyWorld" -> "Strict rotation 500-580ms + sprint keep";
            case "ReallyWorld" -> "Strict anti-cheat 520-580ms + minimal randomness";
            case "SpookyTime" -> "Aggressive 400-500ms + high randomness";
            case "Matrix" -> "Matrix mode 450-540ms + micro shake + packet rotation";
            case "Vulcan" -> "Vulcan mode 470-545ms + low randomness + packet rotation";
            case "Grim" -> "GrimAC 380-440ms + smooth rotation";
            case "NCP" -> "NCP strict 500-550ms + low randomness";
            case "Watchdog" -> "Watchdog 450-550ms + normal rotation";
            case "Watchdog-Tap" -> "Watchdog TAP 500-580ms + human delay";
            case "Freaky" -> "Freaky 350-450ms + high randomness + micro shake";
            case "Virus" -> "Virus 400-520ms + low randomness";
            default -> "Vanilla 500ms";
        };
    }

    private void updateTarget() {
        List<LivingEntity> targetList = new ArrayList<>();
        float fovValue = fov.get();

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && isValid(living)) {
                float angle = getAngleToEntity(living);
                if (angle > fovValue) continue;

                if (options.getValueByName("RayTrace проверка").get() &&
                        !options.getValueByName("Ignore Walls (RayCast)").get()) {
                    if (!canSeeEntity(living)) continue;
                }
                targetList.add(living);
            }
        }

        if (targetList.isEmpty()) {
            target = null;
            return;
        }

        if (targetList.size() == 1) {
            target = targetList.get(0);
            return;
        }

        targetList.sort(Comparator.comparingDouble(this::getTargetPriority).reversed());
        target = targetList.get(0);
    }

    private double getTargetPriority(LivingEntity entity) {
        double priority = 0;

        double distance = mc.player.getDistance(entity);
        priority += (10.0 - Math.min(distance, 10.0)) * 2.0;

        if (canSeeEntity(entity)) {
            priority += 15.0;
        }

        float angleToEntity = getAngleToEntity(entity);
        if (angleToEntity < 90) {
            priority += (90 - angleToEntity) * 0.1;
        }

        if (entity instanceof PlayerEntity player) {
            priority += (10.0 - getEntityArmor(player)) * 0.5;

            if (isLookingAtUs(player)) {
                priority += 8.0;
            }

            double hpPercent = entity.getHealth() / entity.getMaxHealth();
            if (hpPercent < 0.3) {
                priority += 10.0;
            } else if (hpPercent < 0.6) {
                priority += 5.0;
            }

            if (player.isBlocking()) {
                priority -= 3.0;
            }
        }

        double health = getEntityHealth(entity);
        priority += (20.0 - Math.min(health, 20.0)) * 0.3;

        return priority;
    }

    private boolean isLookingAtUs(PlayerEntity entity) {
        Vector3d theirLook = entity.getLook(1.0F);
        Vector3d toUs = mc.player.getPositionVec().subtract(entity.getPositionVec()).normalize();
        double dot = theirLook.dotProduct(toUs);
        return dot > 0.8;
    }

    private float getAngleToEntity(LivingEntity entity) {
        Vector3d playerLook = mc.player.getLook(1.0F);
        Vector3d toEntity = entity.getPositionVec().subtract(mc.player.getEyePosition(1.0F)).normalize();
        double dot = playerLook.dotProduct(toEntity);
        return (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    private boolean canSeeEntity(LivingEntity entity) {
        if (mc.player == null || entity == null) return false;

        Vector3d eyePos = mc.player.getEyePosition(1.0F);
        Vector3d entityPos = getAimPoint(entity);

        RayTraceContext context = new RayTraceContext(
                eyePos,
                entityPos,
                RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE,
                mc.player
        );

        RayTraceResult rayTrace = mc.world.rayTraceBlocks(context);
        return rayTrace.getType() == RayTraceResult.Type.MISS;
    }

    private LivingEntity getNearestValidTarget() {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && isValid(living)) {
                if (options.getValueByName("RayTrace проверка").get() &&
                        !options.getValueByName("Ignore Walls (RayCast)").get()) {
                    if (!canSeeEntity(living)) continue;
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

    private void updateAttack() {
        selected = MouseUtil.getMouseOver(target, rotateVector.x, rotateVector.y, attackRange.get());

        if (options.getValueByName("Ускорять ротацию при атаке").get()) {
            updateRotation(true, 60, 35);
        }

        if ((selected == null || selected != target) && !mc.player.isElytraFlying()) {
            if (isStrictAntiCheat()) {
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
        String mode = serverMode.get();
        return mode.equals("ReallyWorld") || mode.equals("HollyWorld") || mode.equals("Vulcan")
                || mode.equals("NCP") || mode.equals("Grim") || mode.equals("Watchdog")
                || mode.equals("Watchdog-Tap") || mode.equals("Virus");
    }

    private boolean shouldKeepSprint() {
        String mode = serverMode.get();
        return mode.equals("FunTime") || mode.equals("SkyTime") || mode.equals("HollyWorld")
                || mode.equals("SpookyTime") || mode.equals("Watchdog") || mode.equals("Watchdog-Tap")
                || mode.equals("Freaky");
    }

    private boolean shouldPlayerFalling() {
        if (options.getValueByName("Не атаковать в воде").get() &&
                mc.player.isInWater() && mc.player.areEyesInFluid(FluidTags.WATER)) {
            return false;
        }

        if (options.getValueByName("Не атаковать при полёте").get() && mc.player.isElytraFlying()) {
            return false;
        }

        if (mc.player.isInLava() || mc.player.isOnLadder()
                || mc.player.isPassenger() || mc.player.abilities.isFlying) {
            return false;
        }

        float attackStrength = CombatAdapter.getAttackCooldown(
                options.getValueByName("Синхронизировать атаку с ТПС").get()
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

        if (entity instanceof PlayerEntity player) {
            if (AntiBot.isBot(entity)) return false;
            if (!targets.getValueByName("Друзья").get() && FriendStorage.isFriend(player.getName().getString())) {
                return false;
            }
            if (player.getName().getString().equalsIgnoreCase(mc.player.getName().getString())) return false;
            if (!targets.getValueByName("Спящие").get() && player.isSleeping()) return false;
        }

        if (entity instanceof PlayerEntity && !targets.getValueByName("Игроки").get()) return false;
        if (entity instanceof PlayerEntity && entity.getTotalArmorValue() == 0 && !targets.getValueByName("Голые").get()) return false;
        if (entity instanceof PlayerEntity && entity.isInvisible() && entity.getTotalArmorValue() == 0 && !targets.getValueByName("Голые невидимки").get()) return false;
        if (entity instanceof PlayerEntity && entity.isInvisible() && !targets.getValueByName("Невидимки").get()) return false;
        if (entity instanceof MonsterEntity && !targets.getValueByName("Мобы").get()) return false;
        if (entity instanceof AnimalEntity && !targets.getValueByName("Животные").get()) return false;

        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
    }

    private void breakShieldPlayer(PlayerEntity player) {
        if (!player.isBlocking()) return;

        int hotBarSlot = InventoryUtil.getInstance().getAxeInInventory(true);
        int invSlot = InventoryUtil.getInstance().getAxeInInventory(false);

        if (hotBarSlot == -1 && invSlot != -1) {
            int bestSlot = InventoryUtil.getInstance().findBestSlotInHotBar();
            mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);

            mc.player.connection.sendPacket(new CHeldItemChangePacket(bestSlot));
            mc.playerController.attackEntity(mc.player, player);
            mc.player.swingArm(Hand.MAIN_HAND);
            mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));

            mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
        } else if (hotBarSlot != -1) {
            mc.player.connection.sendPacket(new CHeldItemChangePacket(hotBarSlot));
            mc.playerController.attackEntity(mc.player, player);
            mc.player.swingArm(Hand.MAIN_HAND);
            mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
        }
    }

    private void reset() {
        if (options.getValueByName("Коррекция движения").get()) {
            CombatAdapter.resetYawOffset();
        }
        rotateVector = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
        currentYawSpeed = 0;
        currentPitchSpeed = 0;
        jitterOffset = 0;
    }

    @Override
    public boolean onEnable() {
        super.onEnable();
        reset();
        target = null;
        multiTargets.clear();
        lastAttackTime = 0;
        humanReactionDelay = 0;
        isPostRotating = false;
        haveNextGaussian = false;
        currentYawSpeed = 0;
        currentPitchSpeed = 0;
        historyIndex = 0;
        Arrays.fill(positionHistory, Vector3d.ZERO);
        return false;
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        reset();
        stopWatch.setLastMS(0);
        target = null;
        multiTargets.clear();
        isPostRotating = false;
        return false;
    }

    private double getEntityArmor(PlayerEntity player) {
        double armor = 0.0;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = player.inventory.armorInventory.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.ArmorItem armorItem) {
                armor += getProtectionLevel(stack, armorItem);
            }
        }
        return armor;
    }

    private double getProtectionLevel(ItemStack stack, net.minecraft.item.ArmorItem armorItem) {
        double reduction = armorItem.getDamageReduceAmount();
        if (stack.isEnchanted()) {
            reduction += EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, stack) * 0.25;
        }
        return reduction;
    }

    private double getEntityHealth(LivingEntity entity) {
        if (!CombatAdapter.isAlive(entity)) return 0.0;
        return CombatAdapter.getHealth(entity) + CombatAdapter.getAbsorption(entity);
    }

    public LivingEntity getCurrentTarget() {
        return target;
    }

    public String getTargetHealthFormatted() {
        if (target == null) return "0.0";
        return String.format("%.1f", getEntityHealth(target));
    }

    public float getTargetHealthPercent() {
        if (target == null) return 0.0f;
        return Math.min(1.0f, (target.getHealth() + target.getAbsorptionAmount()) / target.getMaxHealth());
    }

    public void pause() {}

    public float getAttackCooldown() {
        return CombatAdapter.getAttackCooldown(1.5f);
    }
}