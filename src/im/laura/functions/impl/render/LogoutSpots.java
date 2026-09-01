package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.EventChangeWorld;
import im.laura.events.EventDisplay;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.projections.ProjectionUtil;
import im.laura.utils.render.font.Fonts;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@FunctionRegister(name = "LogoutSpots", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class LogoutSpots extends Function {

    // сколько ждём игрока, пропавшего из мира, но оставшегося в тае
    // (после этого считаем, что он просто ушёл за пределы прорисовки)
    private static final long DESPAWN_TIMEOUT_MS = 10_000;
    private static final int MAX_RENDER_ERRORS = 50;
    private static final float FONT_SIZE = 8.0f;
    private static final float BOX_HALF_WIDTH = 0.5f;
    private static final float BOX_HEIGHT = 2.0f;

    private static final int TIME_COLOR = 0xFFAAAAAA;
    private static final int COORDS_COLOR = 0xFFE5C07B;

    // углы: i&1 -> x, (i>>1)&1 -> y, (i>>2)&1 -> z
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 5, 7, 6},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 2, 6, 4}, {1, 3, 7, 5}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private final ColorSetting color = new ColorSetting("Цвет", 0xFFFF0000);
    private final SliderSetting renderDistance = new SliderSetting("Дистанция рендера", 100.0f, 10.0f, 500.0f, 10.0f);
    private final BooleanSetting showName = new BooleanSetting("Показать имя", true);
    private final BooleanSetting showTime = new BooleanSetting("Показать время", true);
    private final BooleanSetting showCoords = new BooleanSetting("Показать координаты", true);
    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final BooleanSetting outline = new BooleanSetting("Контур", true);
    private final BooleanSetting chatMessage = new BooleanSetting("Сообщения в чат", true);

    // игроки, которых видим в мире прямо сейчас
    private final Map<UUID, TrackedPlayer> tracked = new ConcurrentHashMap<>();
    // пропали из мира, но ещё в тае — ждём подтверждения
    private final Map<UUID, PendingPlayer> pending = new ConcurrentHashMap<>();
    // подтверждённые метки логаута
    private final Map<UUID, LogoutData> spots = new ConcurrentHashMap<>();

    private Boolean framebufferCoords = null;
    private int renderErrors = 0;

    public LogoutSpots() {
        addSettings(color, renderDistance, showName, showTime, showCoords, fill, outline, chatMessage);
    }

    @Subscribe
    public void onWorldLoad(EventChangeWorld e) {
        tracked.clear();
        pending.clear();
        spots.clear();
        framebufferCoords = null;
    }

    @Subscribe
    public void onRender(EventDisplay e) {
        if (e.getType() != EventDisplay.Type.POST) return;

        if (mc.world == null || mc.player == null) {
            tracked.clear();
            pending.clear();
            return;
        }
        if (renderErrors > MAX_RENDER_ERRORS) return;

        try {
            detectProjectionSpace();
            scanPlayers();
            renderSpots(e.getMatrixStack());
        } catch (Exception ex) {
            renderErrors++;
            if (renderErrors == 1) {
                System.err.println("[LogoutSpots] render error: " + ex);
            }
        }
    }

    // ==================== ДЕТЕКТ ЛОГАУТА ====================

    private void scanPlayers() {
        long now = System.currentTimeMillis();
        Set<UUID> seenNow = new HashSet<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player || player.isSpectator()) continue;

            UUID uuid = player.getUniqueID();
            // трекаем только реальных игроков (есть в списке сервера);
            // NPC без записи в тае игнорируем
            if (mc.getConnection() == null || mc.getConnection().getPlayerInfo(uuid) == null) continue;

            seenNow.add(uuid);
            String name = player.getName().getString();
            tracked.put(uuid, new TrackedPlayer(name, player.getPositionVec()));
            pending.remove(uuid);

            if (spots.remove(uuid) != null && chatMessage.get()) {
                print(name + " зашёл в игру!");
            }
        }

        // были в мире, пропали — в pending на проверку
        for (UUID uuid : new ArrayList<>(tracked.keySet())) {
            if (seenNow.contains(uuid)) continue;
            TrackedPlayer tp = tracked.remove(uuid);
            if (tp != null) {
                pending.put(uuid, new PendingPlayer(tp.name(), tp.pos(), now));
            }
        }

        // разбор пропавших
        for (UUID uuid : new ArrayList<>(pending.keySet())) {
            if (seenNow.contains(uuid)) continue;
            PendingPlayer pp = pending.get(uuid);
            if (pp == null) continue;

            boolean stillInTab = mc.getConnection() != null
                    && mc.getConnection().getPlayerInfo(uuid) != null;

            if (!stillInTab) {
                // пропал И из мира, И из таа — вышел из игры
                pending.remove(uuid);
                spots.put(uuid, new LogoutData(uuid, pp.name(), pp.pos(), now));
                if (chatMessage.get()) {
                    print(pp.name() + " вышел из игры!");
                }
            } else if (now - pp.since() > DESPAWN_TIMEOUT_MS) {
                // в тае, но давно не виден — ушёл за пределы прорисовки
                pending.remove(uuid);
            }
        }
    }

    // ==================== РЕНДЕР ====================

    private void renderSpots(MatrixStack matrixStack) {
        if (spots.isEmpty()) return;

        boolean drawFill = fill.get();
        boolean drawOutline = outline.get();
        boolean anyText = showName.get() || showTime.get() || showCoords.get();
        if (!drawFill && !drawOutline && !anyText) return;

        double maxDistSq = (double) renderDistance.get() * renderDistance.get();
        long now = System.currentTimeMillis();

        int baseColor = color.get();
        int outlineColor = baseColor | 0xFF000000;
        int fillColor = (baseColor & 0x00FFFFFF) | 0x30000000;

        List<SpotBox> boxes = new ArrayList<>();
        List<SpotLabel> labels = new ArrayList<>();

        for (LogoutData data : spots.values()) {
            if (mc.player.getPositionVec().squareDistanceTo(data.pos()) > maxDistSq) continue;

            if (drawFill || drawOutline) {
                Vector2f[] corners = projectBox(data.pos());
                if (corners != null) boxes.add(new SpotBox(corners));
            }
            if (anyText) {
                Vector2f p = projectPoint(data.pos().x, data.pos().y + BOX_HEIGHT + 0.4, data.pos().z);
                if (p != null) labels.add(new SpotLabel(buildText(data, now), p));
            }
        }

        if (!boxes.isEmpty()) {
            drawSpotBoxes(boxes, matrixStack, drawFill, drawOutline, fillColor, outlineColor);
        }
        for (SpotLabel label : labels) {
            drawLabelText(label, matrixStack);
        }
    }

    private List<Seg> buildText(LogoutData data, long now) {
        List<Seg> parts = new ArrayList<>();
        int nameColor = color.get() | 0xFF000000;

        if (showName.get()) parts.add(new Seg(data.name(), nameColor));
        if (showTime.get()) {
            long seconds = (now - data.time()) / 1000;
            parts.add(new Seg(formatTime(seconds), TIME_COLOR));
        }
        if (showCoords.get()) {
            parts.add(new Seg(String.format(Locale.US, "[%.0f, %.0f, %.0f]",
                    data.pos().x, data.pos().y, data.pos().z), COORDS_COLOR));
        }

        List<Seg> result = new ArrayList<>(parts.size() * 2);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) result.add(new Seg("  ", 0xFFFFFFFF));
            result.add(parts.get(i));
        }
        return result;
    }

    private void drawLabelText(SpotLabel label, MatrixStack matrixStack) {
        List<Seg> segs = label.segs();
        float totalWidth = 0;
        for (Seg seg : segs) {
            totalWidth += Fonts.montserrat.getWidth(seg.text(), FONT_SIZE);
        }
        float x = label.pos().x - totalWidth / 2f;
        for (Seg seg : segs) {
            Fonts.montserrat.drawText(matrixStack, seg.text(), x, label.pos().y, seg.color(), FONT_SIZE);
            x += Fonts.montserrat.getWidth(seg.text(), FONT_SIZE);
        }
    }

    private void drawSpotBoxes(List<SpotBox> boxes, MatrixStack matrixStack,
                               boolean drawFill, boolean drawOutline, int fillColor, int outlineColor) {
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

        if (drawFill) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (SpotBox box : boxes) {
                for (int[] face : FACES) {
                    putVertex(buffer, matrix, box.corners()[face[0]], fillColor);
                    putVertex(buffer, matrix, box.corners()[face[1]], fillColor);
                    putVertex(buffer, matrix, box.corners()[face[2]], fillColor);
                    putVertex(buffer, matrix, box.corners()[face[3]], fillColor);
                }
            }
            tessellator.draw();
        }

        if (drawOutline) {
            GL11.glLineWidth(2.0f);
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (SpotBox box : boxes) {
                for (int[] edge : EDGES) {
                    putVertex(buffer, matrix, box.corners()[edge[0]], outlineColor);
                    putVertex(buffer, matrix, box.corners()[edge[1]], outlineColor);
                }
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

    // ==================== ПРОЕКЦИЯ ====================

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

    private Vector2f projectPoint(double x, double y, double z) {
        try {
            Vector2f p = ProjectionUtil.project(x, y, z);
            return p == null ? null : toGui(p);
        } catch (Exception ex) {
            return null;
        }
    }

    private Vector2f[] projectBox(Vector3d pos) {
        try {
            Vector2f[] pts = new Vector2f[8];
            for (int i = 0; i < 8; i++) {
                double x = pos.x - BOX_HALF_WIDTH + (i & 1) * BOX_HALF_WIDTH * 2;
                double y = pos.y + ((i >> 1) & 1) * BOX_HEIGHT;
                double z = pos.z - BOX_HALF_WIDTH + ((i >> 2) & 1) * BOX_HALF_WIDTH * 2;
                Vector2f v = ProjectionUtil.project(x, y, z);
                if (v == null) return null;
                pts[i] = toGui(v);
            }
            return pts;
        } catch (Exception ex) {
            return null;
        }
    }

    private void putVertex(BufferBuilder buffer, Matrix4f matrix, Vector2f p, int color) {
        buffer.pos(matrix, p.x, p.y, 0)
                .color(((color >> 16) & 0xFF) / 255f,
                        ((color >> 8) & 0xFF) / 255f,
                        (color & 0xFF) / 255f,
                        ((color >>> 24) & 0xFF) / 255f)
                .endVertex();
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

    private record TrackedPlayer(String name, Vector3d pos) {}
    private record PendingPlayer(String name, Vector3d pos, long since) {}
    private record LogoutData(UUID uuid, String name, Vector3d pos, long time) {}
    private record Seg(String text, int color) {}
    private record SpotBox(Vector2f[] corners) {}
    private record SpotLabel(List<Seg> segs, Vector2f pos) {}
}