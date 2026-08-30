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
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.renderer.WorldRenderer.frustum;

@FunctionRegister(name = "Chams", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class Chams extends Function {

    private final BooleanSetting players = new BooleanSetting("Игроки", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", true);
    private final ColorSetting color = new ColorSetting("Цвет", 0x80FF0000);
    private final SliderSetting opacity = new SliderSetting("Прозрачность", 128, 0, 255, 1);
    private final BooleanSetting wireframe = new BooleanSetting("Каркас", false);
    private final ColorSetting wireframeColor = new ColorSetting("Цвет каркаса", 0x80FFFFFF);

    public Chams() {
        addSettings(players, throughWalls, color, opacity, wireframe, wireframeColor);
    }

    @SuppressWarnings({"BetaApi", "deprecation"})
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        List<PlayerEntity> targets = new ArrayList<>();

        for (Entity entity : mc.world.getAllEntities()) {
            if (entity instanceof PlayerEntity player) {
                if (player == mc.player) continue;
                if (!players.get()) continue;
                if (player.isSpectator()) continue;
                if (!throughWalls.get() && !mc.player.canEntityBeSeen(player)) continue;
                if (!frustum.isBoundingBoxInFrustum(entity.getBoundingBox())) continue;

                targets.add(player);
            }
        }

        for (PlayerEntity target : targets) {
            renderChams(target, e.getMatrixStack());
        }
    }

    @SuppressWarnings("deprecation")
    private void renderChams(PlayerEntity entity, MatrixStack matrixStack) {
        double x = entity.lastTickPosX + (entity.getPosX() - entity.lastTickPosX) * mc.getRenderPartialTicks();
        double y = entity.lastTickPosY + (entity.getPosY() - entity.lastTickPosY) * mc.getRenderPartialTicks();
        double z = entity.lastTickPosZ + (entity.getPosZ() - entity.lastTickPosZ) * mc.getRenderPartialTicks();

        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) x, (float) y, (float) z);

        float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * mc.getRenderPartialTicks();
        RenderSystem.rotatef(180 - yaw, 0, 1, 0);

        float scale = 0.5f;
        RenderSystem.scalef(scale, scale, scale);

        if (wireframe.get()) {
            renderWireframe(entity, matrixStack);
        } else {
            renderSolid(entity, matrixStack);
        }

        RenderSystem.popMatrix();
    }

    @SuppressWarnings("deprecation")
    private void renderSolid(PlayerEntity entity, MatrixStack matrixStack) {
        int colorVal = color.get();
        float alpha = opacity.get();

        setupRenderState();

        RenderSystem.color4f(
            ((colorVal >> 16) & 0xFF) / 255.0f,
            ((colorVal >> 8) & 0xFF) / 255.0f,
            (colorVal & 0xFF) / 255.0f,
            alpha / 255.0f
        );

        renderModel(entity, matrixStack);

        cleanupRenderState();
    }

    @SuppressWarnings("deprecation")
    private void renderWireframe(PlayerEntity entity, MatrixStack matrixStack) {
        int colorVal = wireframeColor.get();

        RenderSystem.lineWidth(2.0f);
        setupRenderState();

        RenderSystem.color4f(
            ((colorVal >> 16) & 0xFF) / 255.0f,
            ((colorVal >> 8) & 0xFF) / 255.0f,
            (colorVal & 0xFF) / 255.0f,
            1.0f
        );

        renderModel(entity, matrixStack);

        cleanupRenderState();
    }

    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();
        RenderSystem.depthMask(false);

        if (throughWalls.get()) {
            RenderSystem.disableDepthTest();
        }
    }

    private void cleanupRenderState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();

        if (throughWalls.get()) {
            RenderSystem.enableDepthTest();
        }
    }

    private void renderModel(PlayerEntity entity, MatrixStack matrixStack) {
        PlayerModel<PlayerEntity> model = new PlayerModel<>(0.0f, false);
        model.setRotationAngles(entity, 0, 0, 0, 0, 0);

        IRenderTypeBuffer.Impl buffer = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
        model.render(matrixStack, buffer.getBuffer(RenderType.getEntitySolid(AtlasTexture.LOCATION_BLOCKS_TEXTURE)), 15728880, 0, 1, 1, 1, 1);
    }
}
