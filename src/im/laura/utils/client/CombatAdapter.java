package im.laura.utils.client;

import im.laura.Laura;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Адаптер боевых механик для совместимости между версиями
 * Автоматически подстраивается под версию сервера через VersionManager
 */
@UtilityClass
public class CombatAdapter {
    
    private static final Minecraft mc = Minecraft.getInstance();
    
    /**
     * Получить кулдаун атаки с учётом версии
     */
    public static float getAttackCooldown(float tickDelta) {
        if (mc.player == null) {
            return 1.0f;
        }
        return mc.player.getCooledAttackStrength(tickDelta);
    }
    
    /**
     * Проверка готовности атаки с учётом версии
     */
    public static boolean isAttackReady(float threshold) {
        return getAttackCooldown(1.5f) >= threshold;
    }
    
    /**
     * Атака сущности с адаптацией под версию
     */
    public static void attackEntity(LivingEntity target) {
        if (mc.player == null || target == null) {
            return;
        }

        if (mc.playerController != null) {
            mc.playerController.attackEntity(mc.player, target);
        }

        mc.player.swingArm(net.minecraft.util.Hand.MAIN_HAND);
    }
    
    /**
     * Получить дистанцию до сущности с учётом версии
     */
    public static double getDistance(LivingEntity entity) {
        if (mc.player == null || entity == null) {
            return 0.0;
        }

        return mc.player.getDistanceEyePos(entity);
    }
    
    /**
     * Получить позицию глаз с учётом версии
     */
    public static Vector3d getEyePosition(float tickDelta) {
        if (mc.player == null) {
            return new Vector3d(0, 0, 0);
        }
        
        return mc.player.getEyePosition(tickDelta);
    }
    
    /**
     * Установить угол поворота с адаптацией под версию
     */
    public static void setRotation(float yaw, float pitch) {
        if (mc.player == null) {
            return;
        }
        
        mc.player.rotationYawHead = yaw;
        mc.player.renderYawOffset = yaw;
        mc.player.rotationPitch = pitch;
    }
    
    /**
     * Получить угол поворота головы по Y
     */
    public static float getYawHead() {
        if (mc.player == null) {
            return 0.0f;
        }
        
        return mc.player.rotationYawHead;
    }
    
    /**
     * Получить угол поворота головы по X
     */
    public static float getPitch() {
        if (mc.player == null) {
            return 0.0f;
        }
        
        return mc.player.rotationPitch;
    }
    
    /**
     * Установить yaw offset с учётом версии
     */
    public static void setYawOffset(float yaw) {
        if (mc.player == null) {
            return;
        }
        
        mc.player.rotationYawOffset = yaw;
    }
    
    /**
     * Сбросить yaw offset
     */
    public static void resetYawOffset() {
        if (mc.player == null) {
            return;
        }
        
        mc.player.rotationYawOffset = Integer.MIN_VALUE;
    }
    
    /**
     * Проверка на блокирование щитом (с учётом версии)
     */
    public static boolean isBlocking(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        
        return entity.isBlocking();
    }
    
    /**
     * Получить броню игрока (адаптация под версию)
     */
    public static double getArmorValue(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) {
            return 0.0;
        }
        
        return player.getTotalArmorValue();
    }
    
    /**
     * Получить здоровье сущности (адаптация под версию)
     */
    public static float getHealth(LivingEntity entity) {
        if (entity == null) {
            return 0.0f;
        }
        
        return entity.getHealth();
    }
    
    /**
     * Получить максимальное здоровье (адаптация под версию)
     */
    public static float getMaxHealth(LivingEntity entity) {
        if (entity == null) {
            return 1.0f;
        }
        
        return entity.getMaxHealth();
    }
    
    /**
     * Получить абсорбцию (адаптация под версию)
     */
    public static float getAbsorption(LivingEntity entity) {
        if (entity == null) {
            return 0.0f;
        }
        
        return entity.getAbsorptionAmount();
    }
    
    /**
     * Получить общее HP (здоровье + абсорбция)
     */
    public static float getTotalHealth(LivingEntity entity) {
        if (entity == null) {
            return 0.0f;
        }
        
        return getHealth(entity) + getAbsorption(entity);
    }
    
    /**
     * Проверка на невидимость (адаптация под версию)
     */
    public static boolean isInvisible(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        
        return entity.isInvisible();
    }
    
    /**
     * Проверка на жизнь сущности
     */
    public static boolean isAlive(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        
        return entity.isAlive();
    }
    
    /**
     * Получить расстояние до точки (адаптация под версию)
     */
    public static double getDistanceToEye(Vector3d pos) {
        if (mc.player == null || pos == null) {
            return 0.0;
        }
        
        Vector3d eyePos = getEyePosition(1.0F);
        return eyePos.distanceTo(pos);
    }
    
    /**
     * Получить версию протокола в удобном формате
     */
    public static String getProtocolVersionString() {
        VersionManager versionManager = Laura.getInstance().getVersionManager();
        return versionManager.getVersionName() + " (протокол " + versionManager.getServerProtocolVersion() + ")";
    }
    
    /**
     * Проверка на строгий античит (адаптация под версию)
     */
    public static boolean isStrictAntiCheat() {
        VersionManager versionManager = Laura.getInstance().getVersionManager();
        
        // Новые версии имеют более строгие античиты
        return versionManager.isModernVersion();
    }
}
