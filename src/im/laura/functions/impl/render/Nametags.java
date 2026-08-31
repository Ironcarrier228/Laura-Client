package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.command.friends.FriendStorage;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.ModeListSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.projections.ProjectionUtil;
import im.laura.utils.render.font.Fonts;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector2f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@FunctionRegister(name = "Nametags", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class Nametags extends Function {

    /**
     * Если теги рисуются не над сущностями (смещены к краям экрана) —
     * поставь true: значит твой ProjectionUtil возвращает экранные пиксели, а не GUI-координаты.
     */
    private static final boolean DIVIDE_BY_GUI_SCALE = false;

    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int BAR_TRACK_COLOR = 0xFF262626;
    private static final int ARMOR_BAR_COLOR = 0xFFE9C46A;
    private static final int INFO_TEXT_COLOR = 0xFFD8D8D8;
    private static final int INVISIBLE_TEXT_COLOR = 0xFF909090;

    private final ModeListSetting targets = new ModeListSetting("Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", true),
            new BooleanSetting("Невидимые", false));

    private final ColorSetting playersColor = new ColorSetting("Цвет игроков", 0xFFFFFFFF);
    private final ColorSetting friendsColor = new ColorSetting("Цвет друзей", 0xFF00FF00);
    private final ColorSetting mobsColor = new ColorSetting("Цвет мобов", 0xFFFF00FF);
    private final ColorSetting animalsColor = new ColorSetting("Цвет животных", 0xFF00FFFF);

    private final SliderSetting fontSize = new SliderSetting("Размер шрифта", 9.0f, 5.0f, 22.0f, 0.5f);
    private final BooleanSetting dynamicSize = new BooleanSetting("Уменьшать вдали", false);
    private final BooleanSetting showHealth = new BooleanSetting("Показать HP", true);
    private final BooleanSetting showArmor = new BooleanSetting("Показать броню", true);
    private final BooleanSetting showInvisible = new BooleanSetting("Метка невидимых", true);
    private final BooleanSetting showPing = new BooleanSetting("Показать пинг", false);
    private final BooleanSetting showDistance = new BooleanSetting("Показать дистанцию", true);
    private final BooleanSetting background = new BooleanSetting("Фон", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", false);

    public Nametags() {
        addSettings(targets, playersColor, friendsColor, mobsColor, animalsColor,
                fontSize, dynamicSize, showHealth, showArmor, showInvisible,
                showPing, showDistance, background, throughWalls);
    }

    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        List<LivingEntity> entities = new ArrayList<>();
        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && !entity.isSpectator()) {
                entities.add(living);
            }
        }

        // дальние рисуем первыми, ближние — поверх них
        entities.sort((e1, e2) -> Double.compare(
                mc.player.getDistanceSq(e2),
                mc.player.getDistanceSq(e1)));

        for (LivingEntity entity : entities) {
            if (!shouldRender(entity)) continue;
            if (!throughWalls.get() && !mc.player.canEntityBeSeen(entity)) continue;

            renderNametag(entity, e.getMatrixStack());
        }
    }

    private boolean shouldRender(LivingEntity entity) {
        if (entity == mc.player) return false;

        if (entity instanceof PlayerEntity player) {
            if (FriendStorage.isFriend(player.getName().getString())) {
                return targets.getValueByName("Друзья").get();
            }
            if (player.isInvisible()) {
                return targets.getValueByName("Невидимые").get();
            }
            return targets.getValueByName("Игроки").get();
        }

        if (entity instanceof IMob) {
            return targets.getValueByName("Мобы").get();
        }

        if (entity instanceof AnimalEntity) {
            return targets.getValueByName("Животные").get();
        }

        return false;
    }

    private void renderNametag(LivingEntity entity, MatrixStack matrixStack) {
        float partial = mc.getRenderPartialTicks();

        double x = entity.lastTickPosX + (entity.getPosX() - entity.lastTickPosX) * partial;
        double y = entity.lastTickPosY + (entity.getPosY() - entity.lastTickPosY) * partial
                + entity.getHeight() + 0.5;
        double z = entity.lastTickPosZ + (entity.getPosZ() - entity.lastTickPosZ) * partial;

        Vector2f pos = ProjectionUtil.project(x, y, z);
        if (pos == null) return;

        if (DIVIDE_BY_GUI_SCALE) {
            float guiScale = (float) mc.getMainWindow().getGuiScaleFactor();
            pos = new Vector2f(pos.x / guiScale, pos.y / guiScale);
        }

        double distance = mc.player.getDistance(entity);
        float size = getTagSize(distance);

        float screenW = mc.getMainWindow().getScaledWidth();
        float screenH = mc.getMainWindow().getScaledHeight();
        if (pos.x < -60 || pos.x > screenW + 60 || pos.y < -60 || pos.y > screenH + 60) {
            return; // сущность за пределами экрана
        }

        String name = getEntityName(entity);
        int nameColor = getColorForEntity(entity).getRGB();

        float maxHealth = Math.max(entity.getMaxHealth(), 1f);
        float health = entity.getHealth();
        int hpColor = healthColor(health, maxHealth);

        boolean drawHealth = showHealth.get();
        boolean drawArmor = showArmor.get() && entity.getTotalArmorValue() > 0;

        String hpText = drawHealth ? String.format(Locale.US, "%.1f", health) : null;
        List<Seg> info = buildInfoLine(entity, distance);

        float fontHeight = Fonts.montserrat.getHeight(size);
        float lineGap = 1.5f;
        float pad = 2.5f;

        float nameWidth = Fonts.montserrat.getWidth(name, size);
        float hpWidth = hpText != null ? Fonts.montserrat.getWidth(hpText, size) : 0;

        float infoWidth = 0;
        for (Seg seg : info) {
            infoWidth += Fonts.montserrat.getWidth(seg.text(), size);
        }

        float barHeight = Math.max(3f, size * 0.4f);
        float armorBarHeight = Math.max(2f, size * 0.28f);

        float topLineWidth = nameWidth + (drawHealth ? hpWidth + 4f : 0f);
        float tagWidth = Math.max(25f, Math.max(topLineWidth, infoWidth));

        float tagHeight = fontHeight
                + (drawHealth ? lineGap + barHeight : 0f)
                + (drawArmor ? lineGap + armorBarHeight : 0f)
                + (info.isEmpty() ? 0f : lineGap + fontHeight);

        float left = pos.x - tagWidth / 2f;
        float top = pos.y - tagHeight - pad;
        if (top < 2f) top = 2f;

        if (background.get()) {
            drawRect(matrixStack, left - pad, top - pad,
                    tagWidth + pad * 2f, tagHeight + pad * 2f, BACKGROUND_COLOR);
        }

        // строка 1: имя + HP
        float cursorY = top;
        float nameX = left + (tagWidth - topLineWidth) / 2f;
        Fonts.montserrat.drawText(matrixStack, name, nameX, cursorY, nameColor, size);
        if (drawHealth) {
            Fonts.montserrat.drawText(matrixStack, hpText, nameX + nameWidth + 4f, cursorY, hpColor, size);
        }
        cursorY += fontHeight + lineGap;

        // строка 2: полоска здоровья
        if (drawHealth) {
            float ratio = clamp01(health / maxHealth);
            drawRect(matrixStack, left, cursorY, tagWidth, barHeight, BAR_TRACK_COLOR);
            if (ratio > 0f) {
                drawRect(matrixStack, left, cursorY, tagWidth * ratio, barHeight, hpColor);
            }
            cursorY += barHeight + lineGap;
        }

        // строка 3: полоска брони
        if (drawArmor) {
            float ratio = clamp01(entity.getTotalArmorValue() / 20f);
            drawRect(matrixStack, left, cursorY, tagWidth, armorBarHeight, BAR_TRACK_COLOR);
            if (ratio > 0f) {
                drawRect(matrixStack, left, cursorY, tagWidth * ratio, armorBarHeight, ARMOR_BAR_COLOR);
            }
            cursorY += armorBarHeight + lineGap;
        }

        // строка 4: дистанция / пинг / невидимость
        if (!info.isEmpty()) {
            float cursorX = left + (tagWidth - infoWidth) / 2f;
            for (Seg seg : info) {
                Fonts.montserrat.drawText(matrixStack, seg.text(), cursorX, cursorY, seg.color(), size);
                cursorX += Fonts.montserrat.getWidth(seg.text(), size);
            }
        }
    }

    private float getTagSize(double distance) {
        float size = fontSize.get();
        if (dynamicSize.get()) {
            float factor = (float) (14.0 / Math.max(distance, 1.0));
            factor = Math.max(0.6f, Math.min(1.0f, factor));
            size *= factor;
        }
        return size;
    }

    private List<Seg> buildInfoLine(LivingEntity entity, double distance) {
        List<Seg> parts = new ArrayList<>();

        if (showDistance.get()) {
            parts.add(new Seg(String.format(Locale.US, "%.1fм", distance), INFO_TEXT_COLOR));
        }

        if (showPing.get() && mc.getConnection() != null && entity instanceof PlayerEntity) {
            NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(entity.getUniqueID());
            if (info != null) {
                int ping = Math.max(0, info.getResponseTime());
                parts.add(new Seg(ping + "ms", pingColor(ping)));
            }
        }

        if (showInvisible.get() && entity.isInvisible()) {
            parts.add(new Seg("невид.", INVISIBLE_TEXT_COLOR));
        }

        List<Seg> result = new ArrayList<>(parts.size() * 2);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) result.add(new Seg("   ", 0xFFFFFFFF));
            result.add(parts.get(i));
        }
        return result;
    }

    private int pingColor(int ping) {
        if (ping < 50) return 0xFF6BD66B;
        if (ping < 100) return 0xFFE2E267;
        if (ping < 200) return 0xFFE29A54;
        return 0xFFE26767;
    }

    private int healthColor(float health, float maxHealth) {
        float ratio = clamp01(health / maxHealth);
        // красный -> жёлтый -> зелёный
        return Color.HSBtoRGB(ratio / 3f, 0.75f, 0.95f);
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private String getEntityName(Entity entity) {
        String name = entity.getDisplayName().getString();
        if (name == null || name.isEmpty()) {
            name = entity.getName().getString();
        }
        // кастомный шрифт не рендерит §-коды — вырезаем их
        name = name.replaceAll("§.", "");
        if (name.length() > 16) {
            name = name.substring(0, 15) + "..";
        }
        return name;
    }

    private Color getColorForEntity(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (FriendStorage.isFriend(player.getName().getString())) {
                return new Color(friendsColor.get(), true);
            }
            return new Color(playersColor.get(), true);
        }
        if (entity instanceof IMob) {
            return new Color(mobsColor.get(), true);
        }
        if (entity instanceof AnimalEntity) {
            return new Color(animalsColor.get(), true);
        }
        return Color.WHITE;
    }

    private void drawRect(MatrixStack matrixStack, float x, float y, float width, float height, int color) {
        Matrix4f matrix = matrixStack.getLast().getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();

        float alpha = (color >>> 24) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(matrix, x, y, 0).color(red, green, blue, alpha).endVertex();
        buffer.pos(matrix, x, y + height, 0).color(red, green, blue, alpha).endVertex();
        buffer.pos(matrix, x + width, y + height, 0).color(red, green, blue, alpha).endVertex();
        buffer.pos(matrix, x + width, y, 0).color(red, green, blue, alpha).endVertex();
        tessellator.draw();

        // обязательно сбрасываем состояние, иначе сломается всё, что рисуется после
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private record Seg(String text, int color) {}
}