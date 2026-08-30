package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventMotion;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.*;
import im.laura.utils.math.SensUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@FunctionRegister(name = "BowAimbot", type = Category.Movement)
public class BowAimbot extends Function {

    // ==================== НАСТРОЙКИ ====================
    private final SliderSetting targetRange = new SliderSetting("Радиус поиска", 64.0f, 10.0f, 150.0f, 5.0f);
    private final SliderSetting predictionMultiplier = new SliderSetting("Множитель предсказания", 1.0f, 0.0f, 3.0f, 0.1f);

    private final BooleanSetting silentAim = new BooleanSetting("Silent Aim", true);
    private final BooleanSetting predictMovement = new BooleanSetting("Предсказание движения", true);
    private final BooleanSetting autoShoot = new BooleanSetting("Авто-выстрел", false);
    private final BooleanSetting gravity = new BooleanSetting("Учёт гравитации", true);
    private final BooleanSetting onlyDrawn = new BooleanSetting("Только при натяжении", true);

    private final ModeListSetting targets = new ModeListSetting("Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Невидимые", false)
    );

    // ==================== ПЕРЕМЕННЫЕ СОСТОЯНИЯ ====================
    private final CopyOnWriteArrayList<LivingEntity> targetList = new CopyOnWriteArrayList<>();
    private LivingEntity currentTarget;
    private final List<Vector3d> targetPositions = new ArrayList<>();
    private Vector2f serverRotation = new Vector2f(0, 0);
    private Vector2f previousRotation = new Vector2f(0, 0);

    // Константы баллистики
    private static final float ARROW_VELOCITY = 3.0f; // Скорость стрелы при полном натяжении
    private static final float GRAVITY = 0.05f; // Гравитация стрелы
    private static final float DRAG = 0.99f; // Сопротивление воздуха

    public BowAimbot() {
        this.addSettings(targetRange, predictionMultiplier, silentAim, predictMovement,
                autoShoot, gravity, onlyDrawn, targets);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Subscribe
    public void onUpdate(EventUpdate _event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка, держим ли мы лук/арбалет
        ItemStack heldItem = mc.player.getHeldItemMainhand();
        boolean isHoldingBow = heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem;

        if (!isHoldingBow) {
            currentTarget = null;
            return;
        }

        // Проверка натяжения лука
        if (onlyDrawn.get() && !mc.player.isHandActive()) {
            currentTarget = null;
            return;
        }

        // Поиск целей
        updateTargetList();

        if (targetList.isEmpty()) {
            currentTarget = null;
            return;
        }

        // Выбор ближайшей цели
        currentTarget = targetList.stream()
                .min(Comparator.comparingDouble(ent -> mc.player.getDistance(ent)))
                .orElse(null);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Subscribe
    public void onMotion(EventMotion event) {
        if (currentTarget == null) return;

        ItemStack heldItem = mc.player.getHeldItemMainhand();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) return;

        // Проверка натяжения
        if (onlyDrawn.get() && !mc.player.isHandActive()) return;

        // Расчёт времени натяжения лука
        int useDuration = mc.player.getItemInUseCount();
        float charge = getChargeForBow(useDuration);

        // Расчёт позиции цели с предсказанием
        Vector3d targetPos = calculateTargetPosition(charge);

        // Расчёт углов с учётом баллистики
        Vector2f aimRotation = calculateBallisticAngles(targetPos, charge);

        if (aimRotation == null) {
            // Не можем попасть - слишком далеко
            return;
        }

        // Сохраняем предыдущую ротацию
        previousRotation = new Vector2f(event.getYaw(), event.getPitch());

        if (!silentAim.get()) {
            // Видимый aim
            applyRotation(event, aimRotation);
            mc.player.rotationYawHead = aimRotation.x;
            mc.player.renderYawOffset = aimRotation.x;
        } else {
            // Silent aim - только для пакетов
            serverRotation = aimRotation;
            event.setYaw(aimRotation.x);
            event.setPitch(aimRotation.y);
        }

        // Обновление истории позиций для предсказания
        updateTargetPrediction();

        // Авто-выстрел при полном натяжении
        if (autoShoot.get() && charge >= 1.0f && mc.player.isHandActive()) {
            mc.playerController.onStoppedUsingItem(mc.player);
        }
    }

    // ==================== ОСНОВНЫЕ МЕТОДЫ ====================

    private void updateTargetList() {
        targetList.clear();
        AxisAlignedBB searchBox = new AxisAlignedBB(
                mc.player.getPosX() - targetRange.get(),
                mc.player.getPosY() - targetRange.get(),
                mc.player.getPosZ() - targetRange.get(),
                mc.player.getPosX() + targetRange.get(),
                mc.player.getPosY() + targetRange.get(),
                mc.player.getPosZ() + targetRange.get()
        );

        List<Entity> entities = mc.world.getEntitiesWithinAABBExcludingEntity(mc.player, searchBox);
        for (Entity entity : entities) {
            if (!isValidTarget(entity)) continue;
            targetList.add((LivingEntity) entity);
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (!entity.isAlive()) return false;

        boolean isPlayer = living instanceof PlayerEntity;
        boolean isMob = living instanceof MonsterEntity;
        boolean isAnimal = living instanceof AnimalEntity;

        BooleanSetting playersSetting = targets.getValueByName("Игроки");
        BooleanSetting mobsSetting = targets.getValueByName("Мобы");
        BooleanSetting animalsSetting = targets.getValueByName("Животные");
        BooleanSetting invisibleSetting = targets.getValueByName("Невидимые");

        if (isPlayer && (playersSetting == null || !playersSetting.get())) return false;
        if (isMob && (mobsSetting == null || !mobsSetting.get())) return false;
        if (isAnimal && (animalsSetting == null || !animalsSetting.get())) return false;
        if (living.isInvisible() && (invisibleSetting == null || !invisibleSetting.get())) return false;

        if (isPlayer) {
            PlayerEntity player = (PlayerEntity) living;
            return !player.isCreative() && !player.isSpectator() && player != mc.player;
        }

        return true;
    }

    private Vector3d calculateTargetPosition(float charge) {
        if (currentTarget == null) return Vector3d.ZERO;

        // Базовая позиция - центр массы
        Vector3d basePos = currentTarget.getPositionVec().add(0, currentTarget.getEyeHeight() * 0.5, 0);

        // Предсказание движения на основе истории позиций
        if (predictMovement.get() && targetPositions.size() >= 2) {
            Vector3d latest = targetPositions.get(targetPositions.size() - 1);
            Vector3d previous = targetPositions.get(Math.max(0, targetPositions.size() - 3));

            // Вычисляем скорость цели
            Vector3d velocity = latest.subtract(previous).scale(1.0 / Math.max(1, targetPositions.size() - 1));

            // Расстояние до цели
            double distance = mc.player.getPositionVec().distanceTo(basePos);

            // Время полёта стрелы (приблизительно)
            float arrowSpeed = ARROW_VELOCITY * charge;
            double timeToHit = distance / Math.max(0.1, arrowSpeed);

            // Предсказываем позицию
            Vector3d prediction = velocity.scale(timeToHit * predictionMultiplier.get());
            basePos = basePos.add(prediction);
        }

        return basePos;
    }

    private Vector2f calculateBallisticAngles(Vector3d targetPos, float charge) {
        Vector3d eyesPos = new Vector3d(
                mc.player.getPosX(),
                mc.player.getPosY() + mc.player.getEyeHeight(),
                mc.player.getPosZ()
        );

        Vector3d direction = targetPos.subtract(eyesPos);
        double distance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double height = direction.y;

        float velocity = ARROW_VELOCITY * charge;
        float gravityValue = gravity.get() ? GRAVITY : 0.0f;

        // Расчёт угла для попадания с учётом гравитации
        // Формула баллистической траектории
        double angle;

        if (gravityValue > 0.001f) {
            // Квадратное уравнение для нахождения угла
            double v2 = velocity * velocity;
            double v4 = v2 * v2;
            double g = gravityValue;
            double x = distance;
            double y = height;

            double discriminant = v4 - g * (g * x * x + 2 * y * v2);

            if (discriminant < 0) {
                // Не можем попасть - слишком далеко
                return null;
            }

            // Берём меньший угол (более прямой выстрел)
            angle = Math.atan((v2 - Math.sqrt(discriminant)) / (g * x));
        } else {
            // Без гравитации - прямой выстрел
            angle = Math.atan2(height, distance);
        }

        float pitch = (float) -Math.toDegrees(angle);
        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0f;

        // Ограничение pitch
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

        // Плавная ротация для естественности
        float smoothness = 0.4f;
        yaw = serverRotation.x + MathHelper.wrapDegrees(yaw - serverRotation.x) * smoothness;
        pitch = serverRotation.y + (pitch - serverRotation.y) * smoothness;

        // Применение GCD
        float gcd = SensUtils.getGCDValue();
        yaw -= yaw % gcd;
        pitch -= pitch % gcd;

        return new Vector2f(yaw, pitch);
    }

    private float getChargeForBow(int useDuration) {
        float charge = (float) useDuration / 20.0f;
        charge = (charge * charge + charge * 2.0f) / 3.0f;

        if (charge > 1.0f) {
            charge = 1.0f;
        }

        return charge;
    }

    private void applyRotation(EventMotion e, Vector2f rotation) {
        e.setYaw(rotation.x);
        e.setPitch(rotation.y);
        serverRotation = rotation;
    }

    private void updateTargetPrediction() {
        if (currentTarget == null) {
            targetPositions.clear();
            return;
        }

        targetPositions.add(currentTarget.getPositionVec());
        if (targetPositions.size() > 20) {
            targetPositions.remove(0);
        }
    }

    @Override
    public boolean onDisable() {
        targetList.clear();
        currentTarget = null;
        targetPositions.clear();
        return super.onDisable();
    }
}