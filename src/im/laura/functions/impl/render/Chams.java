package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.projections.ProjectionUtil;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@FunctionRegister(name = "Chams", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class Chams extends Function {

    private static final float SCREEN_MARGIN = 50f;
    private static final int MAX_RENDER_ERRORS = 50;

    private final BooleanSetting players = new BooleanSetting("Игроки", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", true);
    private final ColorSetting color = new ColorSetting("Цвет", 0x80FF0000);
    private final SliderSetting opacity = new SliderSetting("Прозрачность", 128, 0, 255, 1);
    private final BooleanSetting wireframe = new BooleanSetting("Каркас", true);
    private final ColorSetting wireframeColor = new ColorSetting("Цвет каркаса", 0xFFFFFFFF);

    // true = ProjectionUtil отдаёт пиксели framebuffer (нужно делить на gui scale)
    private Boolean framebufferCoords = null;
    private int renderErrors = 0;

    public Chams() {
        addSettings(players, throughWalls, color, opacity, wireframe, wireframeColor);
    }

    @Subscribe
    public void onRender(EventDisplay e) {
        if (e.getType() != EventDisplay.Type.POST) return;
        if (mc.world == null || mc.player == null) return;
        if (renderErrors > MAX_RENDER_ERRORS) return; // после серии ошибок не рисуем — не крашим игру

        try {
            detectProjectionSpace();
            render(e.getMatrixStack());
        } catch (Exception ex) {
            renderErrors++;
            if (renderErrors == 1) {
                System.err.println("[Chams] render error: " + ex);
                try {
                    mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(
                            "§c[Chams]§7 ошибка рендера, рисовка отключена (см. лог)"));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void render(MatrixStack matrixStack) {
        if (!players.get()) return;

        float partial = mc.getRenderPartialTicks();

        List<PlayerEntity> targets = new ArrayList<>();
        for (Entity entity : mc.world.getAllEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player || player.isSpectator()) continue;
            if (!throughWalls.get() && !mc.player.canEntityBeSeen(player)) continue;
            targets.add(player);
        }
        if (targets.isEmpty()) return;

        // дальние первыми — ближние рисуются поверх
        targets.sort((p1, p2) -> Double.compare(
                mc.player.getDistanceSq(p2), mc.player.getDistanceSq(p1)));

        int fillAlpha = Math.round(opacity.get());
        List<Box> boxes = new ArrayList<>(targets.size());
        for (PlayerEntity player : targets) {
            Box box = projectBox(player, partial, fillAlpha);
            if (box != null) boxes.add(box);
        }
        if (!boxes.isEmpty()) drawBoxes(boxes, matrixStack);
    }

    private Box projectBox(PlayerEntity player, float partial, int fillAlpha) {
        Vector3d current = player.getPositionVec();
        double ix = player.lastTickPosX + (player.getPosX() - player.lastTickPosX) * partial;
        double iy = player.lastTickPosY + (player.getPosY() - player.lastTickPosY) * partial;
        double iz = player.lastTickPosZ + (player.getPosZ() - player.lastTickPosZ) * partial;

        // хитбокс с учётом интерполяции (учитывает sneaking/позу)
        AxisAlignedBB bb = player.getBoundingBox().offset(
                ix - current.x, iy - current.y, iz - current.z);

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? bb.minX : bb.maxX;
            double cy = (i & 2) == 0 ? bb.minY : bb.maxY;
            double cz = (i & 4) == 0 ? bb.minZ : bb.maxZ;

            Vector2f p = ProjectionUtil.project(cx, cy, cz);
            if (p == null) return null; // часть хитбокса за камерой
            p = toGui(p);

            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }

        float sw = mc.getMainWindow().getScaledWidth();
        float sh = mc.getMainWindow().getScaledHeight();

        // полностью за экраном
        if (maxX < -SCREEN_MARGIN || minX > sw + SCREEN_MARGIN
                || maxY < -SCREEN_MARGIN || minY > sh + SCREEN_MARGIN) {
            return null;
        }

        // обрезаем гигантские боксы (игрок впритык к краю экрана)
        minX = Math.max(minX, -SCREEN_MARGIN);
        minY = Math.max(minY, -SCREEN_MARGIN);
        maxX = Math.min(maxX, sw + SCREEN_MARGIN);
        maxY = Math.min(maxY, sh + SCREEN_MARGIN);

        int fill = (color.get() & 0x00FFFFFF) | ((fillAlpha & 0xFF) << 24);
        return new Box(minX, minY, maxX - minX, maxY - minY, fill, wireframeColor.get());
    }

    private void drawBoxes(List<Box> boxes, MatrixStack matrixStack) {
        Matrix4f matrix = matrixStack.getLast().getMatrix();

        boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullWas = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Box box : boxes) {
            putQuad(buffer, matrix, box, box.fillColor());
        }
        tessellator.draw();

        if (wireframe.get()) {
            GL11.glLineWidth(1.5f);
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (Box box : boxes) {
                putOutline(buffer, matrix, box, box.lineColor());
            }
            tessellator.draw();
            GL11.glLineWidth(1.0f);
        }

        RenderSystem.depthMask(true);
        if (depthWas) RenderSystem.enableDepthTest();
        if (cullWas) RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }

    // ==================== хелперы ====================

    private void detectProjectionSpace() {
        if (framebufferCoords != null) return;
        try {
            Vector3d eye = mc.player.getEyePosition(1.0f);
            Vector3d look = mc.player.getLookVec();
            Vector2f p = ProjectionUtil.project(
                    eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2);
            if (p == null) return;
            framebufferCoords = p.x > mc.getMainWindow().getScaledWidth() * 0.75f;
        } catch (Exception ex) {
            framebufferCoords = false;
        }
    }

    private Vector2f toGui(Vector2f p) {
        if (Boolean.TRUE.equals(framebufferCoords)) {
            float gs = (float) mc.getMainWindow().getGuiScaleFactor();
            return new Vector2f(p.x / gs, p.y / gs);
        }
        return p;
    }

    private void putQuad(BufferBuilder buffer, Matrix4f matrix, Box box, int color) {
        float a = (color >>> 24) / 255f, r = ((color >> 16) & 0xFF) / 255f,
                g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        buffer.pos(matrix, box.x(), box.y(), 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, box.x(), box.y() + box.h(), 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, box.x() + box.w(), box.y() + box.h(), 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, box.x() + box.w(), box.y(), 0).color(r, g, b, a).endVertex();
    }

    private void putOutline(BufferBuilder buffer, Matrix4f matrix, Box box, int color) {
        float a = (color >>> 24) / 255f, r = ((color >> 16) & 0xFF) / 255f,
                g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        float x1 = box.x(), y1 = box.y(), x2 = box.x() + box.w(), y2 = box.y() + box.h();
        line(buffer, matrix, x1, y1, x2, y1, r, g, b, a);
        line(buffer, matrix, x2, y1, x2, y2, r, g, b, a);
        line(buffer, matrix, x2, y2, x1, y2, r, g, b, a);
        line(buffer, matrix, x1, y2, x1, y1, r, g, b, a);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix,
                      float x1, float y1, float x2, float y2,
                      float r, float g, float b, float a) {
        buffer.pos(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
    }

    private record Box(float x, float y, float w, float h, int fillColor, int lineColor) {}
}