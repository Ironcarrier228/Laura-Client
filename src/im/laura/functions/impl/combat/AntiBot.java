package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import im.laura.events.EventPacket;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import io.netty.util.internal.ConcurrentSet;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPlayerListItemPacket;

import java.util.*;

@FunctionRegister(name = "AntiBot", type = Category.Combat)
@SuppressWarnings({"unused", "Beta", "DataClass"})
public class AntiBot extends Function {

    private final BooleanSetting detectInvalidProfile = new BooleanSetting("Invalid Profile", true);
    private final BooleanSetting detectFullArmor = new BooleanSetting("Full Armor", false);
    private final BooleanSetting detectOfflineUUID = new BooleanSetting("Offline UUID", true);
    private final BooleanSetting detectPing = new BooleanSetting("Zero Ping", true);
    private final BooleanSetting detectNameTags = new BooleanSetting("Name Tags", false);
    private final SliderSetting armorCount = new SliderSetting("Min Armor Pieces", 4f, 1f, 4f, 1f);

    private final Set<UUID> susPlayers = new ConcurrentSet<>();
    private static final Map<UUID, BotData> botsMap = new HashMap<>();

    public AntiBot() {
        addSettings(detectInvalidProfile, detectFullArmor, detectOfflineUUID, detectPing, detectNameTags, armorCount);
    }

    @Subscribe
    private void onUpdate(EventUpdate e) {
        for (UUID susPlayer : susPlayers) {
            PlayerEntity entity = mc.world.getPlayerByUuid(susPlayer);

            if (entity != null) {
                boolean isBot = checkBot(entity);
                botsMap.put(susPlayer, new BotData(isBot, System.currentTimeMillis()));
            }

            susPlayers.remove(susPlayer);
        }

        if (mc.player.ticksExisted % 100 == 0) {
            long now = System.currentTimeMillis();
            botsMap.entrySet().removeIf(entry -> 
                mc.world.getPlayerByUuid(entry.getKey()) == null || 
                now - entry.getValue().timestamp > 300000);
        }
    }

    private boolean checkBot(PlayerEntity entity) {
        int reasons = 0;

        if (detectInvalidProfile.get()) {
            if (entity.getGameProfile().getProperties().isEmpty()) {
                reasons++;
            }
        }

        if (detectFullArmor.get()) {
            int armorCount = 0;
            for (ItemStack stack : entity.getArmorInventoryList()) {
                if (!stack.isEmpty()) armorCount++;
            }
            if (armorCount >= this.armorCount.get()) {
                reasons++;
            }
        }

        if (detectOfflineUUID.get()) {
            if (!entity.getUniqueID().equals(PlayerEntity.getOfflineUUID(entity.getGameProfile().getName()))) {
                reasons++;
            }
        }

        if (detectPing.get()) {
            for (NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
                if (info.getGameProfile().getId().equals(entity.getUniqueID())) {
                    if (info.getResponseTime() <= 0) {
                        reasons++;
                    }
                    break;
                }
            }
        }

        if (detectNameTags.get()) {
            if (entity.hasCustomName() && entity.getCustomName().getString().contains("NPC")) {
                reasons++;
            }
        }

        return reasons >= 1;
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof SPlayerListItemPacket p) {
            if (p.getAction() == SPlayerListItemPacket.Action.ADD_PLAYER) {
                for (SPlayerListItemPacket.AddPlayerData entry : p.getEntries()) {
                    GameProfile profile = entry.getProfile();

                    if (botsMap.containsKey(profile.getId()) || susPlayers.contains(profile.getId())) {
                        continue;
                    }

                    boolean isInvalid = detectInvalidProfile.get() && 
                            profile.getProperties().isEmpty() && 
                            entry.getPing() != 0;

                    if (isInvalid) {
                        susPlayers.add(profile.getId());
                    }
                }
            }
        }
    }

    public static boolean isBot(Entity entity) {
        if (!(entity instanceof PlayerEntity)) return false;
        return botsMap.getOrDefault(entity.getUniqueID(), new BotData(false, 0)).isBot;
    }

    public static boolean isBotU(Entity entity) {
        if (!entity.getUniqueID().equals(PlayerEntity.getOfflineUUID(entity.getName().getString()))) {
            return entity.isInvisible();
        }
        return false;
    }

    public static int getBotCount() {
        return (int) botsMap.values().stream().filter(data -> data.isBot).count();
    }

    @Override
    public boolean onDisable() {
        super.onDisable();
        botsMap.clear();
        susPlayers.clear();
        return false;
    }

    private static class BotData {
        public final boolean isBot;
        public final long timestamp;

        public BotData(boolean isBot, long timestamp) {
            this.isBot = isBot;
            this.timestamp = timestamp;
        }
    }
}
