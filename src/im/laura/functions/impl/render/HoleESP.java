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
import im.laura.utils.render.DisplayUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;

@FunctionRegister(name = "HoleESP", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava"})
public class HoleESP extends Function {

    private final SliderSetting radius = new SliderSetting("Радиус", 20.0f, 5.0f, 50.0f, 1.0f);
    private final ModeSetting mode = new ModeSetting("Режим", "Both", "Fill", "Outline", "Both");
    private final ColorSetting holeColor = new ColorSetting("Цвет хола", 0x8000FF00);
    private final ColorSetting bedrockColor = new ColorSetting("Цвет бедрока", 0x80FF0000);
    private final ColorSetting obsidianColor = new ColorSetting("Цвет обсида", 0x809400D3);
    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность", 80.0f, 0.0f, 255.0f, 5.0f);
    private final BooleanSetting showUnsafe = new BooleanSetting("Небезопасные", false);

    private final ConcurrentHashMap<BlockPos, HoleType> holes = new ConcurrentHashMap<>();

    public HoleESP() {
        addSettings(radius, mode, holeColor, bedrockColor, obsidianColor, fillAlpha, showUnsafe);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        if (mc.player.ticksExisted % 5 == 0) {
            findHoles();
        }

        renderHoles(e.getMatrixStack());
    }

    private void findHoles() {
        holes.clear();

        int r = Math.round(radius.get());
        BlockPos playerPos = mc.player.getPosition();

        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++) {
            for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                for (int y = playerPos.getY() + 5; y >= playerPos.getY() - 5; y--) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (isHole(pos)) {
                        HoleType type = getHoleType(pos);
                        if (type != HoleType.NONE) {
                            holes.put(pos, type);
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean isHole(BlockPos pos) {
        BlockPos down = pos.down();
        BlockState below = mc.world.getBlockState(down);
        if (below.isAir()) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir()) return false;

        BlockPos up = pos.up();
        BlockState above = mc.world.getBlockState(up);
        if (!above.isAir()) return false;

        BlockPos up2 = pos.up(2);
        BlockState above2 = mc.world.getBlockState(up2);
        if (!above2.isAir()) return false;

        return isSolid(pos.north()) &&
               isSolid(pos.south()) &&
               isSolid(pos.east()) &&
               isSolid(pos.west());
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock() == Blocks.OBSIDIAN ||
               state.getBlock() == Blocks.BEDROCK ||
               state.getBlock() == Blocks.NETHERITE_BLOCK ||
               state.getBlock() == Blocks.ANCIENT_DEBRIS;
    }

    private HoleType getHoleType(BlockPos pos) {
        boolean allBedrock = true;
        boolean allObsidian = true;
        boolean hasUnsafe = false;

        BlockPos[] surrounds = {pos.north(), pos.south(), pos.east(), pos.west(), pos.down()};

        for (BlockPos p : surrounds) {
            BlockState state = mc.world.getBlockState(p);

            if (state.getBlock() == Blocks.BEDROCK) {
                allObsidian = false;
            } else if (state.getBlock() == Blocks.OBSIDIAN ||
                      state.getBlock() == Blocks.NETHERITE_BLOCK ||
                      state.getBlock() == Blocks.ANCIENT_DEBRIS) {
                allBedrock = false;
            } else {
                allBedrock = false;
                allObsidian = false;
                hasUnsafe = true;
            }
        }

        if (allBedrock) return HoleType.BEDROCK;
        if (allObsidian) return HoleType.OBSIDIAN;
        if (!hasUnsafe || showUnsafe.get()) return HoleType.UNSAFE;

        return HoleType.NONE;
    }

    @SuppressWarnings("deprecation")
    private void renderHoles(MatrixStack matrixStack) {
        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();

        double radiusSq = radius.get() * radius.get();
        Vector3d playerPos = mc.player.getPositionVec();

        for (var entry : holes.entrySet()) {
            BlockPos pos = entry.getKey();
            HoleType type = entry.getValue();

            if (playerPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > radiusSq) continue;

            Color color = getColorForType(type);
            if (color == null) continue;

            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(fillAlpha.get()));

            AxisAlignedBB bb = new AxisAlignedBB(pos);

            if (mode.get().equals("Fill") || mode.get().equals("Both")) {
                DisplayUtils.drawFilledBox(bb, color);
            }

            if (mode.get().equals("Outline") || mode.get().equals("Both")) {
                DisplayUtils.drawOutlinedBox(bb, color);
            }
        }

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
    }

    private Color getColorForType(HoleType type) {
        return switch (type) {
            case BEDROCK -> new Color(bedrockColor.get(), true);
            case OBSIDIAN -> new Color(obsidianColor.get(), true);
            case UNSAFE -> new Color(holeColor.get(), true);
            default -> null;
        };
    }

    private enum HoleType {
        NONE,
        BEDROCK,
        OBSIDIAN,
        UNSAFE
    }
}
