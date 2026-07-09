package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.command.friends.FriendStorage;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.impl.combat.AntiBot;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.ModeListSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.projections.ProjectionUtil;
import im.laura.utils.render.font.Fonts;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.renderer.WorldRenderer.frustum;

@FunctionRegister(name = "Nametags", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class Nametags extends Function {

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

    private final SliderSetting scale = new SliderSetting("Масштаб", 0.03f, 0.01f, 0.1f, 0.005f);
    private final BooleanSetting showHealth = new BooleanSetting("Показать HP", true);
    private final BooleanSetting showArmor = new BooleanSetting("Показать броню", true);
    private final BooleanSetting showInvisible = new BooleanSetting("Метка невидимых", true);
    private final BooleanSetting showPing = new BooleanSetting("Показать пинг", false);
    private final BooleanSetting showDistance = new BooleanSetting("Показать дистанцию", true);
    private final BooleanSetting background = new BooleanSetting("Фон", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", false);

    public Nametags() {
        addSettings(targets, playersColor, friendsColor, mobsColor, animalsColor,
                   scale, showHealth, showArmor, showInvisible, showPing, showDistance, background, throughWalls);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        List<Entity> entities = new ArrayList<>();
        for (Entity entity : mc.world.getAllEntities()) {
            entities.add(entity);
        }
        entities.sort((e1, e2) -> {
            double dist1 = mc.player.getPositionVec().squareDistanceTo(e1.getPositionVec());
            double dist2 = mc.player.getPositionVec().squareDistanceTo(e2.getPositionVec());
            return Double.compare(dist2, dist1);
        });

        for (Entity entity : entities) {
            if (!shouldRender(entity)) continue;
            if (!throughWalls.get() && !mc.player.canEntityBeSeen(entity)) continue;

            renderNametag(entity, e.getMatrixStack());
        }
    }

    private boolean shouldRender(Entity entity) {
        if (entity == mc.player) return false;
        if (entity.isSpectator()) return false;
        if (!(entity instanceof LivingEntity)) return false;

        if (AntiBot.isBot(entity)) {
            return false;
        }

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

    @SuppressWarnings({"BetaApi", "deprecation"})
    private void renderNametag(Entity entity, MatrixStack matrixStack) {
        String name = getEntityName(entity);
        Color color = getColorForEntity(entity);

        double x = entity.lastTickPosX + (entity.getPosX() - entity.lastTickPosX) * mc.getRenderPartialTicks();
        double y = entity.lastTickPosY + (entity.getPosY() - entity.lastTickPosY) * mc.getRenderPartialTicks() + entity.getHeight() + 0.5;
        double z = entity.lastTickPosZ + (entity.getPosZ() - entity.lastTickPosZ) * mc.getRenderPartialTicks();

        Vector2f pos = ProjectionUtil.project(x, y, z);
        if (pos == null) return;

        List<String> info = buildInfo(entity, name);

        float textWidth = 0;
        float scaleVal = scale.get() * 50;
        for (String line : info) {
            float lineWidth = Fonts.montserrat.getWidth(line, scaleVal);
            if (lineWidth > textWidth) {
                textWidth = lineWidth;
            }
        }

        float fontHeight = Fonts.montserrat.getHeight(scaleVal);
        float totalHeight = info.size() * (fontHeight + 2);
        float renderX = pos.x - textWidth / 2;
        float renderY = pos.y - totalHeight;

        if (background.get()) {
            drawRect(matrixStack, renderX - 2, renderY - 2, textWidth + 4, totalHeight + 4, 0x90000000);
        }

        float currentY = renderY;
        for (String line : info) {
            float lineWidth = Fonts.montserrat.getWidth(line, scaleVal);
            float offsetX = (textWidth - lineWidth) / 2;
            Fonts.montserrat.drawText(matrixStack, line, renderX + offsetX, currentY, color.getRGB(), scaleVal);
            currentY += fontHeight + 2;
        }
    }

    private List<String> buildInfo(Entity entity, String name) {
        List<String> info = new ArrayList<>();
        info.add(name);

        if (showHealth.get() && entity instanceof LivingEntity living) {
            float health = living.getHealth();
            float maxHealth = living.getMaxHealth();
            info.add(TextFormatting.RED + String.format("%.1f", health) + TextFormatting.WHITE + "/" + String.format("%.1f", maxHealth));
        }

        if (showDistance.get()) {
            double dist = mc.player.getPositionVec().squareDistanceTo(entity.getPositionVec());
            info.add(TextFormatting.WHITE + String.format("%.1f", Math.sqrt(dist)) + TextFormatting.GRAY + "м");
        }

        if (showInvisible.get() && entity.isInvisible()) {
            info.add(TextFormatting.DARK_GRAY + "[Невидим]");
        }

        if (showPing.get() && entity instanceof PlayerEntity player) {
            var playerInfo = mc.getConnection().getPlayerInfo(player.getUniqueID());
            if (playerInfo != null) {
                int ping = playerInfo.getResponseTime();
                String pingColor = ping < 50 ? "§a" : ping < 100 ? "§e" : ping < 200 ? "§c" : "§4";
                info.add(pingColor + ping + "§7ms");
            }
        }

        return info;
    }

    private String getEntityName(Entity entity) {
        if (entity.hasCustomName()) {
            return entity.getDisplayName().getString();
        }
        return entity.getType().getTranslationKey().replaceFirst("^minecraft:", "").replace('_', ' ').toUpperCase();
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

    @SuppressWarnings("deprecation")
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
}
