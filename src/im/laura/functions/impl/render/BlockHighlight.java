package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.WorldEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ColorSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.render.DisplayUtils;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@FunctionRegister(name = "BlockHighlight", type = Category.Render)
@SuppressWarnings({"unused", "Guava", "BetaApi"})
public class BlockHighlight extends Function {

    private final ColorSetting blockColor = new ColorSetting("Цвет блока", 0x806495ED);
    private final ColorSetting outlineColor = new ColorSetting("Цвет контура", 0xFF6495ED);
    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность заливки", 50, 0, 255, 5);
    private final BooleanSetting showOutline = new BooleanSetting("Показать контур", true);
    private final BooleanSetting showFill = new BooleanSetting("Показать заливку", true);
    private final SliderSetting lineWidth = new SliderSetting("Толщина линии", 1.5f, 0.5f, 3.0f, 0.5f);

    public BlockHighlight() {
        addSettings(blockColor, outlineColor, fillAlpha, showOutline, showFill, lineWidth);
    }

    @Subscribe
    public void onRenderWorld(WorldEvent e) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        RayTraceResult result = mc.objectMouseOver;
        if (result == null || result.getType() != RayTraceResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = ((BlockRayTraceResult) result).getPos();
        BlockState state = mc.world.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        renderBlockHighlight(pos);
    }

    @SuppressWarnings("deprecation")
    private void renderBlockHighlight(BlockPos pos) {
        Vector3d renderPos = mc.gameRenderer.getActiveRenderInfo().getProjectedView();

        AxisAlignedBB bb = new AxisAlignedBB(
                pos.getX() - renderPos.getX(),
                pos.getY() - renderPos.getY(),
                pos.getZ() - renderPos.getZ(),
                pos.getX() + 1 - renderPos.getX(),
                pos.getY() + 1 - renderPos.getY(),
                pos.getZ() + 1 - renderPos.getZ()
        );

        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(lineWidth.get());

        if (showFill.get()) {
            Color fillColor = new Color(blockColor.get(), true);
            fillColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), (int) (float) fillAlpha.get());
            DisplayUtils.drawFilledBox(bb, fillColor);
        }

        if (showOutline.get()) {
            Color outline = new Color(outlineColor.get(), true);
            DisplayUtils.drawOutlinedBox(bb, outline);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
    }
}