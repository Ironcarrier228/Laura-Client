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
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.projections.ProjectionUtil;
import im.laura.utils.render.font.Fonts;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@FunctionRegister(name = "HoleESP", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class HoleESP extends Function {

    private static final int SCAN_INTERVAL = 5;
    private static final int SCAN_HEIGHT = 5;

    // индексы углов: i&1 -> x, (i>>1)&1 -> y, (i>>2)&1 -> z
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

    private final SliderSetting radius = new SliderSetting("Радиус", 20.0f, 5.0f, 50.0f, 1.0f);
    private final ModeSetting mode = new ModeSetting("Режим", "Both", "Fill", "Outline", "Both");
    private final ColorSetting holeColor = new ColorSetting("Цвет хола", 0x8000FF00);
    private final ColorSetting bedrockColor = new ColorSetting("Цвет бедрока", 0x80FF0000);
    private final ColorSetting obsidianColor = new ColorSetting("Цвет обсида", 0x809400D3);
    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность", 80.0f, 0.0f, 255.0f, 5.0f);
    private final BooleanSetting showUnsafe = new BooleanSetting("Небезопасные", false);
    private final BooleanSetting debug = new BooleanSetting("Отладка", false);

    private final Map<BlockPos, HoleType> holes = new ConcurrentHashMap<>();
    private int lastScanTick = -1000;

    // автоопределение: true = ProjectionUtil отдаёт пиксели framebuffer (нужно делить на gui scale)
    private Boolean framebufferCoords = null;

    private int lastTotal = -1;
    private int lastRendered = -1;
    private int lastDebugTick = -1000;

    public HoleESP() {
        addSettings(radius, mode, holeColor, bedrockColor, obsidianColor, fillAlpha, showUnsafe, debug);
    }

    @Subscribe
    public void onRender(EventDisplay e) {
        if (e.getType() != EventDisplay.Type.POST) return;

        if (mc.world == null || mc.player == null) {
            holes.clear();
            lastScanTick = -1000;
            framebufferCoords = null;
            return;
        }

        if (mc.player.ticksExisted < lastScanTick) lastScanTick = -1000;

        if (mc.player.ticksExisted - lastScanTick >= SCAN_INTERVAL) {
            lastScanTick = mc.player.ticksExisted;
            findHoles();
        }

        detectProjectionSpace();

        if (debug.get()) drawDebugOverlay(e.getMatrixStack());
        int rendered = renderHoles(e.getMatrixStack());
        if (debug.get()) debugLog(rendered);
    }

    // ==================== СКАН ====================

    private void findHoles() {
        holes.clear();

        int r = Math.round(radius.get());
        int rSq = r * r;
        BlockPos base = mc.player.getPosition();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > rSq) continue;

                for (int dy = SCAN_HEIGHT; dy >= -SCAN_HEIGHT; dy--) {
                    BlockPos floor = base.add(dx, dy, dz);
                    if (!isHoleBlock(mc.world.getBlockState(floor))) continue;

                    BlockPos hole = floor.up();
                    if (!mc.world.isAirBlock(hole)
                            || !mc.world.isAirBlock(hole.up())
                            || !mc.world.isAirBlock(hole.up(2))) continue;

                    if (!isHoleBlock(mc.world.getBlockState(hole.north()))
                            || !isHoleBlock(mc.world.getBlockState(hole.south()))
                            || !isHoleBlock(mc.world.getBlockState(hole.east()))
                            || !isHoleBlock(mc.world.getBlockState(hole.west()))) continue;

                    HoleType type = classifyHole(hole);
                    if (type != null) holes.put(hole, type); // unsafe тоже кладём — видно в отладке
                    break;
                }
            }
        }
    }

    private boolean isHoleBlock(BlockState state) {
        return state.getBlock() == Blocks.OBSIDIAN
                || state.getBlock() == Blocks.BEDROCK
                || state.getBlock() == Blocks.NETHERITE_BLOCK
                || state.getBlock() == Blocks.ANCIENT_DEBRIS;
    }

    private HoleType classifyHole(BlockPos hole) {
        BlockPos[] checks = {hole.down(), hole.north(), hole.south(), hole.east(), hole.west()};

        boolean allBedrock = true;
        for (BlockPos p : checks) {
            BlockState state = mc.world.getBlockState(p);
            if (state.getBlock() == Blocks.BEDROCK) continue;
            allBedrock = false;
            if (!isHoleBlock(state)) return HoleType.UNSAFE;
        }
        return allBedrock ? HoleType.BEDROCK : HoleType.OBSIDIAN;
    }

    // ==================== ПРОЕКЦИЯ ====================

    /**
     * Точка в 2 блока перед глазами всегда в центре экрана.
     * Если её X сильно правее GUI-ширины — ProjectionUtil отдаёт пиксели framebuffer.
     */
    private void detectProjectionSpace() {
        if (framebufferCoords != null) return;
        try {
            Vector3d eye = mc.player.getEyePosition(1.0f);
            Vector3d look = mc.player.getLookVec();
            Vector2f p = ProjectionUtil.project(
                    eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2);
            if (p == null) return; // попробуем в следующем кадре
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

    private Vector2f[] projectBox(BlockPos pos) {
        try {
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            Vector2f[] pts = new Vector2f[8];
            for (int i = 0; i < 8; i++) {
                Vector2f v = ProjectionUtil.project(
                        x + (i & 1), y + ((i >> 1) & 1), z + ((i >> 2) & 1));
                if (v == null) return null; // угол за камерой
                pts[i] = toGui(v);
            }
            return pts;
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================== РЕНДЕР ====================

    private int renderHoles(MatrixStack matrixStack) {
        if (holes.isEmpty()) return 0;

        String m = mode.get();
        boolean fill = m.equals("Fill") || m.equals("Both");
        boolean outline = m.equals("Outline") || m.equals("Both");
        if (!fill && !outline) return 0;

        double radiusSq = (double) radius.get() * radius.get();
        int fillAlphaInt = Math.round(fillAlpha.get());

        List<Box> boxes = new ArrayList<>();
        for (Map.Entry<BlockPos, HoleType> entry : holes.entrySet()) {
            HoleType type = entry.getValue();
            if (type == HoleType.UNSAFE && !showUnsafe.get()) continue;

            BlockPos pos = entry.getKey();
            if (mc.player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > radiusSq) continue;

            Color color = getColorForType(type);
            Vector2f[] corners = projectBox(pos);
            if (corners == null) continue;

            int fillColor = (color.getRGB() & 0x00FFFFFF) | (fillAlphaInt << 24);
            boxes.add(new Box(corners, fillColor, color.getRGB()));
        }
        if (boxes.isEmpty()) return 0;

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

        if (fill) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (Box box : boxes) {
                for (int[] face : FACES) {
                    putVertex(buffer, matrix, box.corners()[face[0]], box.fillColor());
                    putVertex(buffer, matrix, box.corners()[face[1]], box.fillColor());
                    putVertex(buffer, matrix, box.corners()[face[2]], box.fillColor());
                    putVertex(buffer, matrix, box.corners()[face[3]], box.fillColor());
                }
            }
            tessellator.draw();
        }

        if (outline) {
            GL11.glLineWidth(2.0f);
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (Box box : boxes) {
                for (int[] edge : EDGES) {
                    putVertex(buffer, matrix, box.corners()[edge[0]], box.lineColor());
                    putVertex(buffer, matrix, box.corners()[edge[1]], box.lineColor());
                }
            }
            tessellator.draw();
            GL11.glLineWidth(1.0f);
        }

        RenderSystem.depthMask(true);
        if (depthWas) RenderSystem.enableDepthTest();
        if (cullWas) RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.color4f(1f, 1f, 1f, 1f);

        return boxes.size();
    }

    private void putVertex(BufferBuilder buffer, Matrix4f matrix, Vector2f p, int color) {
        buffer.pos(matrix, p.x, p.y, 0)
                .color(((color >> 16) & 0xFF) / 255f,
                        ((color >> 8) & 0xFF) / 255f,
                        (color & 0xFF) / 255f,
                        ((color >>> 24) & 0xFF) / 255f)
                .endVertex();
    }

    private Color getColorForType(HoleType type) {
        return switch (type) {
            case BEDROCK -> new Color(bedrockColor.get(), true);
            case OBSIDIAN -> new Color(obsidianColor.get(), true);
            case UNSAFE -> new Color(holeColor.get(), true);
        };
    }

    // ==================== ОТЛАДКА ====================

    private void drawDebugOverlay(MatrixStack matrixStack) {
        float sw = mc.getMainWindow().getScaledWidth();
        float sh = mc.getMainWindow().getScaledHeight();

        // зелёный квадрат по центру — тем же пайплайном, что и холы
        drawRect(matrixStack, sw / 2f - 15, sh / 2f - 15, 30, 30, 0xFF00FF00);
        // текст по центру — тем же пайплайном, что и Nametags
        Fonts.montserrat.drawText(matrixStack, "TEST", sw / 2f - 12, sh / 2f + 20, 0xFF00FF00, 12);
    }

    private void debugLog(int rendered) {
        int tick = mc.player.ticksExisted;
        int total = holes.size();
        boolean changed = total != lastTotal || rendered != lastRendered;

        if (!changed && tick - lastDebugTick < 100) return; // ничего не изменилось — раз в 5 сек
        if (tick - lastDebugTick < 20) return;              // не чаще раза в секунду

        lastTotal = total;
        lastRendered = rendered;
        lastDebugTick = tick;

        int bedrock = 0, obsidian = 0, unsafe = 0;
        for (HoleType t : holes.values()) {
            switch (t) {
                case BEDROCK -> bedrock++;
                case OBSIDIAN -> obsidian++;
                case UNSAFE -> unsafe++;
            }
        }
        String proj = framebufferCoords == null ? "?" : (framebufferCoords ? "пиксели" : "GUI");

        mc.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(
                String.format("§d[HoleESP]§7 найдено: %d §8(бедрок %d, обси %d, unsafe %d)§7 | рисуется: %d | коорд: %s",
                        total, bedrock, obsidian, unsafe, rendered, proj)));
    }

    private void drawRect(MatrixStack matrixStack, float x, float y, float w, float h, int color) {
        Matrix4f matrix = matrixStack.getLast().getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();

        float a = (color >>> 24) / 255f, r = ((color >> 16) & 0xFF) / 255f,
                g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(matrix, x, y, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x, y + h, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x + w, y + h, 0).color(r, g, b, a).endVertex();
        buffer.pos(matrix, x + w, y, 0).color(r, g, b, a).endVertex();
        tessellator.draw();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }

    private record Box(Vector2f[] corners, int fillColor, int lineColor) {}

    private enum HoleType {
        BEDROCK, OBSIDIAN, UNSAFE
    }
}