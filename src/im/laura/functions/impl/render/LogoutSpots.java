package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.EventChangeWorld;
import im.laura.events.EventDisplay;
import im.laura.events.EventEntityLeave;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.projections.ProjectionUtil;
import im.laura.utils.render.DisplayUtils;
import im.laura.utils.render.font.Fonts;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@FunctionRegister(name = "LogoutSpots", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava"})
public class LogoutSpots extends Function {

    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFF0000);
    private final SliderSetting renderDistance = new SliderSetting("Дистанция рендера", 100.0f, 10.0f, 500.0f, 10.0f);
    private final BooleanSetting showName = new BooleanSetting("Показать имя", true);
    private final BooleanSetting showTime = new BooleanSetting("Показать время", true);
    private final BooleanSetting showCoords = new BooleanSetting("Показать координаты", true);
    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final BooleanSetting outline = new BooleanSetting("Контур", true);
    private final BooleanSetting chatMessage = new BooleanSetting("Сообщения в чат", true);

    private final Map<UUID, LogoutData> logoutData = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSeenNames = new ConcurrentHashMap<>();

    public LogoutSpots() {
        addSettings(color, renderDistance, showName, showTime, showCoords, fill, outline, chatMessage);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onWorldLoad(EventChangeWorld e) {
        logoutData.clear();
        lastSeenNames.clear();
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onEntityLeave(EventEntityLeave e) {
        if (e.getEntity() instanceof PlayerEntity player) {
            if (player != mc.player) {
                UUID uuid = player.getUniqueID();
                String name = lastSeenNames.getOrDefault(uuid, player.getName().getString());

                LogoutData data = new LogoutData(
                    uuid,
                    name,
                    player.getPositionVec(),
                    System.currentTimeMillis()
                );

                logoutData.put(uuid, data);
                lastSeenNames.remove(uuid);

                if (chatMessage.get()) {
                    print(name + " вышел из игры!");
                }
            }
        }
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        for (Map.Entry<UUID, LogoutData> entry : logoutData.entrySet()) {
            LogoutData data = entry.getValue();

            double dist = mc.player.getPositionVec().squareDistanceTo(data.pos);
            if (dist > renderDistance.get() * renderDistance.get()) continue;

            renderLogoutSpot(data, e.getMatrixStack());
        }
    }

    @SuppressWarnings({"deprecation", "DataClass"})
    private void renderLogoutSpot(LogoutData data, MatrixStack matrixStack) {
        Color colorVal = new Color(color.get(), true);

        AxisAlignedBB bb = new AxisAlignedBB(
            data.pos.add(-0.5, 0, -0.5),
            data.pos.add(0.5, 2, 0.5)
        );

        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();

        if (fill.get()) {
            DisplayUtils.drawFilledBox(bb, new Color(colorVal.getRed(), colorVal.getGreen(), colorVal.getBlue(), 30));
        }

        if (outline.get()) {
            DisplayUtils.drawOutlinedBox(bb, colorVal);
        }

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();

        Vector2f pos = ProjectionUtil.project(data.pos.x, data.pos.y + 2.5, data.pos.z);
        StringBuilder text = buildText(data);
        float textWidth = Fonts.montserrat.getWidth(text.toString(), 0.5f);
        Fonts.montserrat.drawText(matrixStack, text.toString(), pos.x - textWidth / 2, pos.y, colorVal.getRGB(), 0.5f);
    }

    private StringBuilder buildText(LogoutData data) {
        StringBuilder text = new StringBuilder();

        if (showName.get()) {
            text.append(TextFormatting.RED).append(data.name);
        }

        if (showTime.get()) {
            if (!text.isEmpty()) text.append(" ");
            long seconds = (System.currentTimeMillis() - data.time) / 1000;
            text.append(TextFormatting.GRAY).append(formatTime(seconds));
        }

        if (showCoords.get()) {
            if (!text.isEmpty()) text.append(" ");
            text.append(TextFormatting.YELLOW)
                .append(String.format("[%.0f, %.0f, %.0f]", data.pos.x, data.pos.y, data.pos.z));
        }

        return text;
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

    private record LogoutData(UUID uuid, String name, Vector3d pos, long time) {
    }
}
