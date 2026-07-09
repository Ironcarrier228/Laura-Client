package im.laura.utils.client;

import im.laura.Laura;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.StringTextComponent;
import via.ViaLoadingBase;
import via.model.ComparableProtocolVersion;

/**
 * Менеджер версий для автоматической адаптации клиента под версию сервера
 */
public class VersionManager {
    
    @Getter
    private static VersionManager instance;
    
    private final Minecraft mc = Minecraft.getInstance();
    
    /**
     * Версии протоколов
     */
    public static final int PROTOCOL_1_16_5 = 754;
    public static final int PROTOCOL_1_16_4 = 754;
    public static final int PROTOCOL_1_16_2 = 754;
    public static final int PROTOCOL_1_16_1 = 753;
    public static final int PROTOCOL_1_15_2 = 578;
    public static final int PROTOCOL_1_14_4 = 498;
    public static final int PROTOCOL_1_13_2 = 404;
    public static final int PROTOCOL_1_12_2 = 340;
    public static final int PROTOCOL_1_11_2 = 316;
    public static final int PROTOCOL_1_10_2 = 210;
    public static final int PROTOCOL_1_9_4 = 110;
    public static final int PROTOCOL_1_8 = 47;
    public static final int PROTOCOL_1_17 = 755;
    public static final int PROTOCOL_1_17_1 = 756;
    public static final int PROTOCOL_1_18 = 757;
    public static final int PROTOCOL_1_18_2 = 758;
    public static final int PROTOCOL_1_19 = 759;
    public static final int PROTOCOL_1_19_1 = 760;
    public static final int PROTOCOL_1_19_3 = 761;
    public static final int PROTOCOL_1_19_4 = 762;
    public static final int PROTOCOL_1_20 = 763;
    public static final int PROTOCOL_1_20_1 = 763;
    public static final int PROTOCOL_1_20_2 = 764;
    public static final int PROTOCOL_1_20_4 = 765;
    public static final int PROTOCOL_1_21 = 766;

    /**
     * Текущая версия сервера
     */
    @Getter
    private int serverProtocolVersion = PROTOCOL_1_16_5;

    /**
     * Флаг использования новой версии (1.17+)
     */
    @Getter
    private boolean isNewVersion = false;

    /**
     * Флаг использования версии 1.20+
     */
    @Getter
    private boolean isModernVersion = false;
    
    public VersionManager() {
        instance = this;
    }
    
    /**
     * Обновить версию на основе текущей настройки ViaVersion
     */
    public void updateVersion() {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }
        
        try {
            // Получаем версию из ViaVersion
            ComparableProtocolVersion viaVersion = ViaLoadingBase.getInstance().getTargetVersion();
            if (viaVersion != null) {
                this.serverProtocolVersion = viaVersion.getVersion();
            } else {
                // Фоллбэк на нативную версию
                this.serverProtocolVersion = PROTOCOL_1_16_5;
            }
        } catch (Exception e) {
            this.serverProtocolVersion = PROTOCOL_1_16_5;
        }
        
        // Определяем тип версии
        this.isNewVersion = this.serverProtocolVersion >= PROTOCOL_1_17;
        this.isModernVersion = this.serverProtocolVersion >= PROTOCOL_1_20;
    }
    
    /**
     * Проверка на конкретную версию
     */
    @SuppressWarnings("unused")
    public boolean isVersion(int protocolVersion) {
        return this.serverProtocolVersion == protocolVersion;
    }
    
    /**
     * Проверка на версию выше или равно
     */
    @SuppressWarnings("unused")
    public boolean isVersionOrHigher(int protocolVersion) {
        return this.serverProtocolVersion >= protocolVersion;
    }
    
    /**
     * Проверка на версию ниже или равно
     */
    @SuppressWarnings("unused")
    public boolean isVersionOrLower(int protocolVersion) {
        return this.serverProtocolVersion <= protocolVersion;
    }
    
    /**
     * Получить название версии
     */
    public String getVersionName() {
        return switch (serverProtocolVersion) {
            case 766 -> "1.21";
            case 765 -> "1.20.4";
            case 764 -> "1.20.2";
            case 763 -> "1.20.1";
            case 762 -> "1.19.4";
            case 761 -> "1.19.3";
            case 760 -> "1.19.1";
            case 759 -> "1.19";
            case 758 -> "1.18.2";
            case 757 -> "1.18";
            case 756 -> "1.17.1";
            case 755 -> "1.17";
            case 754 -> "1.16.5";
            case 753 -> "1.16.1";
            case 578 -> "1.15.2";
            case 498 -> "1.14.4";
            case 404 -> "1.13.2";
            case 340 -> "1.12.2";
            case 316 -> "1.11.2";
            case 210 -> "1.10.2";
            case 110 -> "1.9.4";
            case 47 -> "1.8";
            default -> "Unknown";
        };
    }
    
    /**
     * Автоматически определить версию при подключении к серверу
     */
    public void onServerConnect() {
        updateVersion();
        
        if (Laura.getInstance() != null) {
            String message = "§7[§bVersionManager§7] §fВерсия сервера: §a" + getVersionName() + 
                           " §7(протокол " + serverProtocolVersion + ")";
            if (isNewVersion) {
                message += " §e(новая версия, возможна адаптация KillAura)";
            }
            if (mc.player != null) {
                mc.player.sendMessage(new StringTextComponent(message), net.minecraft.util.Util.DUMMY_UUID);
            }
        }
    }
}
