package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.command.friends.FriendStorage;
import im.laura.events.EventChangeWorld;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.render.font.Fonts;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@FunctionRegister(name = "VisualRange", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class VisualRange extends Function {

    private final SliderSetting radius = new SliderSetting("Радиус", 64.0f, 16.0f, 256.0f, 8.0f);
    private final BooleanSetting showNotification = new BooleanSetting("Уведомления", true);
    private final BooleanSetting showInvisible = new BooleanSetting("Невидимые", true);
    private final BooleanSetting showFriends = new BooleanSetting("Друзья", true);
    private final BooleanSetting chatMessages = new BooleanSetting("Сообщения в чат", false);

    private final Map<UUID, VisualRangeData> visiblePlayers = new ConcurrentHashMap<>();
    private final Set<UUID> notifiedEnter = ConcurrentHashMap.newKeySet();
    private final Set<UUID> notifiedLeave = ConcurrentHashMap.newKeySet();

    public VisualRange() {
        addSettings(radius, showNotification, showInvisible, showFriends, chatMessages);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onWorldLoad(EventChangeWorld e) {
        visiblePlayers.clear();
        notifiedEnter.clear();
        notifiedLeave.clear();
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        List<PlayerEntity> currentVisible = new ArrayList<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;

            if (player.isInvisible() && !showInvisible.get()) continue;

            if (FriendStorage.isFriend(player.getName().getString()) && !showFriends.get()) continue;

            double dist = mc.player.getPositionVec().squareDistanceTo(player.getPositionVec());
            if (dist > radius.get() * radius.get()) continue;

            if (!mc.player.canEntityBeSeen(player)) continue;

            currentVisible.add(player);
            UUID uuid = player.getUniqueID();

            if (!visiblePlayers.containsKey(uuid)) {
                visiblePlayers.put(uuid, new VisualRangeData(
                    uuid,
                    player.getName().getString(),
                    player.getPositionVec(),
                    System.currentTimeMillis()
                ));

                if (showNotification.get() && !notifiedEnter.contains(uuid)) {
                    if (chatMessages.get()) {
                        print(TextFormatting.GREEN + player.getName().getString() + TextFormatting.GRAY + " появился в видимости!");
                    }
                    notifiedEnter.add(uuid);
                    notifiedLeave.remove(uuid);
                }
            } else {
                visiblePlayers.put(uuid, new VisualRangeData(
                    uuid,
                    player.getName().getString(),
                    player.getPositionVec(),
                    System.currentTimeMillis()
                ));
            }
        }

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, VisualRangeData> entry : visiblePlayers.entrySet()) {
            UUID uuid = entry.getKey();
            boolean stillVisible = false;

            for (PlayerEntity player : currentVisible) {
                if (player.getUniqueID().equals(uuid)) {
                    stillVisible = true;
                    break;
                }
            }

            if (!stillVisible && !notifiedLeave.contains(uuid)) {
                VisualRangeData data = entry.getValue();

                if (showNotification.get()) {
                    if (chatMessages.get()) {
                        print(TextFormatting.RED + data.name + TextFormatting.GRAY + " пропал из видимости!");
                    }
                }

                notifiedLeave.add(uuid);
                notifiedEnter.remove(uuid);
                toRemove.add(uuid);
            }
        }

        for (UUID uuid : toRemove) {
            visiblePlayers.remove(uuid);
        }

        if (showNotification.get()) {
            renderHUD(e.getMatrixStack());
        }
    }

    @SuppressWarnings({"deprecation", "SameParameterValue"})
    private void renderHUD(MatrixStack matrixStack) {
        if (visiblePlayers.isEmpty()) return;

        int x = 10;
        int y = 100;
        int lineHeight = 12;

        int height = visiblePlayers.size() * lineHeight + 4;
        drawRect(matrixStack, x - 2, y - 2, 150, height, 0x60000000);

        Fonts.montserrat.drawText(matrixStack, TextFormatting.AQUA + "В видимости: " + TextFormatting.WHITE + visiblePlayers.size(),
                             x, y, 0xFFFFFFFF, 0.5f);
        y += lineHeight;

        for (VisualRangeData data : visiblePlayers.values()) {
            long seconds = (System.currentTimeMillis() - data.time) / 1000;
            String timeStr = formatTime(seconds);

            String text = TextFormatting.GREEN + data.name + TextFormatting.GRAY + " [" + timeStr + "]";
            Fonts.montserrat.drawText(matrixStack, text, x, y, 0xFFFFFFFF, 0.5f);
            y += lineHeight;
        }
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "с";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "м" + (secs > 0 ? secs + "с" : "");
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "ч" + (minutes > 0 ? minutes + "м" : "");
        }
    }

    @SuppressWarnings({"deprecation", "SameParameterValue"})
    private void drawRect(MatrixStack matrixStack, float x, float y, float width, float height, int color) {
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        RenderSystem.color4f(r, g, b, a);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION);
        buffer.pos(x, y + height, 0).endVertex();
        buffer.pos(x + width, y + height, 0).endVertex();
        buffer.pos(x + width, y, 0).endVertex();
        buffer.pos(x, y, 0).endVertex();
        tessellator.draw();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static class VisualRangeData {
        public final UUID uuid;
        public final String name;
        public final Vector3d lastPos;
        public final long time;

        public VisualRangeData(UUID uuid, String name, Vector3d lastPos, long time) {
            this.uuid = uuid;
            this.name = name;
            this.lastPos = lastPos;
            this.time = time;
        }
    }
}
