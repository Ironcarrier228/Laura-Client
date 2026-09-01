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
            "Плавная", "Резкая", "Snap", "Интеллектуальная", "Matrix", "Экспоненциальная", "Human", "Silent",
            "Ступенчатая", "Инерционная", "Безье", "Предиктивная");

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

    // === состояние новых режимов ===
    private float angVelYaw = 0;          // Инерционная
    private float angVelPitch = 0;
    private int stepHoldTicks = 0;        // Ступенчатая
    private boolean bezierActive = false; // Безье
    private float bezierT = 0;
    private float bezierStartYaw = 0;
    private float bezierStartPitch = 0;
    private float bezierCtrlYaw = 0;
    private float bezierCtrlPitch = 0;

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
                float smoothedYaw = smoothRotation(mc.player.rotationYaw, rotateVector.x, 10.0f);
                MoveUtils.fixMovement(eventInput, smoothedYaw);
            }
        }
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        // фикс: защита от NPE при выходе из мира
        if (mc.player == null || mc.world == null) {
            target = null;
            selected = null;
            isPostRotating = false;
            return;
        }

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

        // Пост-ротация: идёт ДО основной ротации (раньше её результат сразу затирался)
        if (isPostRotating) {
            if (options.getValueByName("Пост-ротация").get()) {
                handlePostRotation();
            } else {
                isPostRotating = false; // фикс: не зависаем, если опцию выключили посреди возврата
            }
        }

        if (target != null && !(autoPotion.isState() && autoPotion.isActive())) {
            isRotated = false;

            long delay = getAttackDelay();
            if (options.getValueByName("Human Rotation").get()) {
                delay += humanReactionDelay;
            }

            if (shouldPlayerFalling() && (System.currentTimeMillis() - lastAttackTime >= delay)) {
                updateAttack();
                lastAttackTime = System.currentTimeMillis();

                if (options.getValueByName("Human Rotation").get()) {
                    humanReactionDelay = 150 + random.nextInt(150);
                }
                // фикс: пост-ротация привязана к своей опции, а не к Human Rotation
                if (options.getValueByName("Пост-ротация").get()) {
                    isPostRotating = true;
                    postRotationStartTime = System.currentTimeMillis();
                    postRotationYaw = rotateVector.x;
                    postRotationPitch = rotateVector.y;
                }
                ticks = 2;
            }

            // фикс: во время возврата взгляда не перезаписываем ротацию к цели
            if (isPostRotating) return;

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
                if (!isRotated) {
                    updateRotation(false, 60, 30);
                }
            } else if (type.is("Silent")) {
                if (!isRotated) {
                    updateRotation(false, 50, 25);
                }
            } else {
                // Плавная, Интеллектуальная, Matrix, Экспоненциальная,
                // Ступенчатая, Инерционная, Безье, Предиктивная
                if (!isRotated) {
                    updateRotation(false, 80, 35);
                }
            }
        } else {
            stopWatch.setLastMS(0);
            // фикс: даём пост-ротации доиграть, а не сбрасываем мгновенно
            if (!isPostRotating) {
                reset();
            }
        }
    }

    private void handlePostRotation() {
        long elapsed = System.currentTimeMillis() - postRotationStartTime;
        if (elapsed > 300) {
            isPostRotating = false;
            return;
        }

        float progress = (float) elapsed / 300f;
        float eased = progress * progress * (3f - 2f * progress); // smoothstep — плавный старт и финиш

        float cameraYaw = mc.player.rotationYaw;
        float cameraPitch = mc.player.rotationPitch;

        // фикс: wrapDegrees — возврат по кратчайшей дуге (раньше мог крутиться через 180°)
        float yaw = postRotationYaw + wrapDegrees(cameraYaw - postRotationYaw) * eased;
        float pitch = postRotationPitch + (cameraPitch - postRotationPitch) * eased;

        rotateVector = new Vector2f(yaw, pitch);
        if (options.getValueByName("Коррекция движения").get()) {
            mc.player.rotationYawOffset = yaw;
        }
    }

    private long getAttackDelay() {
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
            case "Watchdog-Tap" -> 500 + random.nextInt(80);
            default -> 500;
        };
    }

    @Subscribe
    public void onWalking(EventMotion e) { // фикс: public — EventBus гарантированно видит метод
        if (target == null || autoPotion.isState() && autoPotion.isActive()) return;

        float yaw = rotateVector.x;
        float pitch = rotateVector.y;

        if (options.getValueByName("Silent Aim").get() && type.is("Silent")) {
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

        targets.sort(Comparator.comparingDouble(this::getTargetPriority).reversed());
        target = targets.get(0);
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

        if (entity instanceof PlayerEntity) {
            priority += (10.0 - getEntityArmor((PlayerEntity) entity)) * 0.5;
        }

        double health = getEntityHealth(entity);
        priority += (20.0 - Math.min(health, 20.0)) * 0.3;

        return priority;
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
        Vector3d entityPos = entity.getPositionVec().add(0, entity.getEyeHeight() / 2, 0);

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

        if (options.getValueByName("Human Rotation").get() || shouldApplyRotationRandomness()) {
            yawToTarget += (float) nextGaussian() * getRotationRandomness();
            pitchToTarget += (float) nextGaussian() * getRotationRandomness() * 0.5f;
        }

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

                float microShake = (random.nextFloat() - 0.5f) * getMicroShakeAmount();
                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw) + microShake;
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, false); // без клампа pitch, как в оригинале
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
            }
            case "Резкая" -> {
                float speedMultiplier = getRotationSpeedMultiplier();
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 5.0f), rotationYawSpeed * 1.5f * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 3.0f), rotationPitchSpeed * 1.3f * speedMultiplier);

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
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
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, false); // оригинал допускал pitch ±360
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
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
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
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
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
            }
            case "Экспоненциальная" -> {
                float yawProgress = 1.0f - (Math.abs(yawDelta) / 180.0f);
                float pitchProgress = 1.0f - (Math.abs(pitchDelta) / 90.0f);

                float yawAccel = yawProgress * yawProgress;
                float pitchAccel = pitchProgress * pitchProgress;

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
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
            }
            case "Human" -> {
                float speedMultiplier = 0.7f + random.nextFloat() * 0.3f;
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 2.0f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.5f), rotationPitchSpeed * speedMultiplier);

                if (Math.abs(yawDelta) < 10) clampedYaw *= 0.5f;
                if (Math.abs(pitchDelta) < 5) clampedPitch *= 0.5f;

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
            }
            case "Silent" -> {
                float speedMultiplier = getRotationSpeedMultiplier() * 0.9f;
                float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 1.0f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 1.0f), rotationPitchSpeed * speedMultiplier);

                if (attack && selected != target && options.getValueByName("Ускорять ротацию при атаке").get()) {
                    clampedPitch = Math.max(Math.abs(pitchDelta), 1.0f);
                } else {
                    clampedPitch /= 3f;
                }

                float yaw = rotateVector.x + (yawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = rotateVector.y + (pitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
            }
            // ================== НОВЫЕ РЕЖИМЫ ==================
            case "Ступенчатая" -> {
                // Дискретные шаги с паузами — имитация реальных движений мыши
                if (stepHoldTicks > 0) {
                    stepHoldTicks--;
                    lastYaw = 0;
                    lastPitch = 0;
                } else {
                    float speedMultiplier = getRotationSpeedMultiplier();
                    float yawStep = Math.abs(yawDelta) * (0.22f + random.nextFloat() * 0.18f);
                    float pitchStep = Math.abs(pitchDelta) * (0.26f + random.nextFloat() * 0.18f);
                    yawStep = Math.min(Math.max(yawStep, 0.5f), rotationYawSpeed * speedMultiplier);
                    pitchStep = Math.min(Math.max(pitchStep, 0.5f), rotationPitchSpeed * speedMultiplier);

                    float yaw = rotateVector.x + (yawDelta > 0 ? yawStep : -yawStep);
                    float pitch = rotateVector.y + (pitchDelta > 0 ? pitchStep : -pitchStep);

                    applyRotation(yaw, pitch, true);
                    stepHoldTicks = random.nextInt(3); // пауза 0-2 тика между шагами
                    lastYaw = yawStep;
                    lastPitch = pitchStep;
                }
            }
            case "Инерционная" -> {
                // Угловая скорость: разгон, торможение, лёгкий перелёт с возвратом
                float speedMultiplier = getRotationSpeedMultiplier();
                float accelYaw = rotationYawSpeed * 0.35f * speedMultiplier;
                float accelPitch = rotationPitchSpeed * 0.30f * speedMultiplier;

                angVelYaw += (yawDelta > 0 ? accelYaw : -accelYaw);
                angVelPitch += (pitchDelta > 0 ? accelPitch : -accelPitch);

                angVelYaw *= 0.86f;
                angVelPitch *= 0.86f;

                // не быстрее максимальной скорости режима
                angVelYaw = clamp(angVelYaw, -rotationYawSpeed * speedMultiplier, rotationYawSpeed * speedMultiplier);
                angVelPitch = clamp(angVelPitch, -rotationPitchSpeed * speedMultiplier, rotationPitchSpeed * speedMultiplier);

                // не дальше ~10% оставшегося пути (перелёт допустим, дальше затухает)
                angVelYaw = clamp(angVelYaw, -Math.abs(yawDelta) * 1.1f, Math.abs(yawDelta) * 1.1f);
                angVelPitch = clamp(angVelPitch, -Math.abs(pitchDelta) * 1.1f, Math.abs(pitchDelta) * 1.1f);

                float yaw = rotateVector.x + angVelYaw;
                float pitch = rotateVector.y + angVelPitch;

                applyRotation(yaw, pitch, true);
                lastYaw = Math.abs(angVelYaw);
                lastPitch = Math.abs(angVelPitch);
            }
            case "Безье" -> {
                // Кривая Безье: разгон-торможение по слегка изогнутой дуге
                boolean needNewCurve = !bezierActive || bezierT >= 1f || Math.abs(yawDelta) > 70f;
                if (needNewCurve) {
                    if (Math.abs(yawDelta) < 4f && Math.abs(pitchDelta) < 4f) {
                        // финальное дотягивание без новой кривой
                        applyRotation(rotateVector.x + yawDelta * 0.7f,
                                rotateVector.y + pitchDelta * 0.7f, true);
                        lastYaw = Math.abs(yawDelta);
                        lastPitch = Math.abs(pitchDelta);
                        return;
                    }
                    bezierStartYaw = rotateVector.x;
                    bezierStartPitch = rotateVector.y;
                    bezierCtrlYaw = bezierStartYaw + yawDelta * 0.65f + (random.nextFloat() - 0.5f) * 6f;
                    bezierCtrlPitch = bezierStartPitch + pitchDelta * 0.65f + (random.nextFloat() - 0.5f) * 2f;
                    bezierT = 0f;
                    bezierActive = true;
                }

                bezierT = Math.min(1f, bezierT + 0.14f * getRotationSpeedMultiplier());
                float t = bezierT * bezierT * (3f - 2f * bezierT); // smoothstep
                float u = 1f - t;

                float yaw = u * u * bezierStartYaw + 2f * u * t * bezierCtrlYaw + t * t * yawToTarget;
                float pitch = u * u * bezierStartPitch + 2f * u * t * bezierCtrlPitch + t * t * pitchToTarget;

                float yawMove = wrapDegrees(yaw - rotateVector.x);
                float pitchMove = pitch - rotateVector.y;
                applyRotation(yaw, pitch, true);
                lastYaw = Math.abs(yawMove);
                lastPitch = Math.abs(pitchMove);
            }
            case "Предиктивная" -> {
                // Упреждение: целимся туда, где цель БУДЕТ, а не где она сейчас
                Vector3d motion = target.getMotion();
                double dist = mc.player.getDistance(target);
                double leadTicks = Math.min(6.0, Math.max(0.0, dist * 0.35)); // до ~6 тиков упреждения
                Vector3d pvec = vec.add(motion.scale(leadTicks));

                float pYawToTarget = (float) wrapDegrees(Math.toDegrees(Math.atan2(pvec.z, pvec.x)) - 90);
                float pPitchToTarget = (float) (-Math.toDegrees(Math.atan2(pvec.y, hypot(pvec.x, pvec.z))));

                float pYawDelta = wrapDegrees(pYawToTarget - rotateVector.x);
                float pPitchDelta = wrapDegrees(pPitchToTarget - rotateVector.y);

                float speedMultiplier = getRotationSpeedMultiplier();
                float clampedYaw = Math.min(Math.max(Math.abs(pYawDelta), 0.5f), rotationYawSpeed * speedMultiplier);
                float clampedPitch = Math.min(Math.max(Math.abs(pPitchDelta), 0.5f), rotationPitchSpeed * speedMultiplier);
                clampedPitch /= 3f;

                float yaw = rotateVector.x + (pYawDelta > 0 ? clampedYaw : -clampedYaw);
                float pitch = rotateVector.y + (pPitchDelta > 0 ? clampedPitch : -clampedPitch);

                applyRotation(yaw, pitch, true);
                lastYaw = clampedYaw;
                lastPitch = clampedPitch;
            }
        }
    }

    /**
     * Общая финализация ротации: GCD-коррекция, кламп pitch, смещение движения.
     * Раньше этот код копипастился в каждом режиме (и местами расходился).
     */
    private void applyRotation(float yaw, float pitch, boolean clampPitch) {
        if (clampPitch) {
            pitch = clamp(pitch, -89.0F, 89.0F);
        }
        float gcd = SensUtils.getGCDValue();
        yaw -= (yaw - rotateVector.x) % gcd;
        pitch -= (pitch - rotateVector.y) % gcd;

        rotateVector = new Vector2f(yaw, pitch);
        if (options.getValueByName("Коррекция движения").get() && !type.is("Silent")) {
            mc.player.rotationYawOffset = yaw;
        }
    }

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
        if (mc.player == null) {
            rotateVector = new Vector2f(0, 0);
            return;
        }
        if (options.getValueByName("Коррекция движения").get()) {
            CombatAdapter.resetYawOffset();
        }
        rotateVector = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
        angVelYaw = 0; // инерция глохнет, когда цели нет
        angVelPitch = 0;
    }

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
        resetRotationState();
        return false;
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        reset();
        stopWatch.setLastMS(0);
        target = null;
        isPostRotating = false;
        resetRotationState();
        return false;
    }

    private void resetRotationState() {
        angVelYaw = 0;
        angVelPitch = 0;
        stepHoldTicks = 0;
        bezierActive = false;
        bezierT = 0;
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