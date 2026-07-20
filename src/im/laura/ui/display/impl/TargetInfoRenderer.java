package im.laura.ui.display.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import im.laura.Laura;
import im.laura.events.EventDisplay;
import im.laura.ui.display.ElementRenderer;
import im.laura.ui.styles.Style;
import im.laura.utils.animations.Animation;
import im.laura.utils.animations.Direction;
import im.laura.utils.animations.impl.EaseBackIn;
import im.laura.utils.drag.Dragging;
import im.laura.utils.math.MathUtil;
import im.laura.utils.math.StopWatch;
import im.laura.utils.render.*;
import im.laura.utils.render.font.Fonts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector4f;
import org.lwjgl.opengl.GL11;

@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class TargetInfoRenderer implements ElementRenderer {
    final StopWatch stopWatch = new StopWatch();
    final Dragging drag;
    LivingEntity entity = null;
    boolean allow;
    final Animation animation = new EaseBackIn(400, 1, 1);
    float healthAnimation = 0.0f;
    float absorptionAnimation = 0.0f;

    @Override
    public void render(EventDisplay eventDisplay) {
        entity = getTarget(entity);

        float rounding = 6;
        boolean out = !allow || stopWatch.isReached(1000);
        animation.setDuration(out ? 400 : 300);
        animation.setDirection(out ? Direction.BACKWARDS : Direction.FORWARDS);

        if (animation.getOutput() == 0.0f) {
            entity = null;
        }

        if (entity != null) {
            float posX = drag.getX();
            float posY = drag.getY();

            float headSize = 28;
            float spacing = 5;

            float width = 210 / 1.75f;
            float height = 85 / 1.75f;
            drag.setWidth(width);
            drag.setHeight(height);

            float hp = entity.getHealth();
            float maxHp = entity.getMaxHealth();

            healthAnimation = MathUtil.fast(healthAnimation, MathHelper.clamp(hp / maxHp, 0, 1), 10);
            absorptionAnimation = MathUtil.fast(absorptionAnimation,
                    MathHelper.clamp(entity.getAbsorptionAmount() / maxHp, 0, 1), 10);

            float animationValue = (float) animation.getOutput();

            float halfAnimationValueRest = (1 - animationValue) / 2f;

            float testX = posX + (width * halfAnimationValueRest);
            float testY = posY + (height * halfAnimationValueRest);
            float testW = width * animationValue;
            float testH = height * animationValue;

            GlStateManager.pushMatrix();

            sizeAnimation(posX + (width / 2), posY + (height / 2), animation.getOutput());
            float finalPosY = posY;
            drawStyledRect(posX, finalPosY, width, height - 4, 9);

            Stencil.initStencilToWrite();
            DisplayUtils.drawRoundedRect(posX + spacing - 1.25f, posY + spacing - 1.5f, 75 / 2f, 75 / 2f, 8,
                    ColorUtils.rgba(25, 26, 40, 165));
            Stencil.readStencilBuffer(1);
            drawTargetHead(entity, posX + spacing - 1.25f, posY + spacing - 1.5f, 75 / 2f, 75 / 2f);
            Stencil.uninitStencilBuffer();
            Scissor.push();

            Scissor.setFromComponentCoordinates(testX, testY, testW - 6, testH);

            Fonts.sfui.drawText(eventDisplay.getMatrixStack(), entity.getName().getString(),
                    posX + headSize + spacing + spacing + 8.75f, posY + spacing + 7.5f, -1, 8);
            Fonts.sfMedium.drawText(eventDisplay.getMatrixStack(),
                    "HP: " + ((int) hp + (int) mc.player.getAbsorptionAmount()),
                    posX + headSize + spacing + spacing
                            + 8.75f,
                    posY + spacing + 17, ColorUtils.rgb(203, 203, 203), 6);

            Scissor.unset();
            Scissor.pop();

            float iconSizeX = 8;
            float iconSizeY = 8;

            Style style = Laura.getInstance().getStyleManager().getCurrentStyle();

            final ResourceLocation logo = new ResourceLocation("laura/images/hud/heart.png");

            DisplayUtils.drawImage(logo, posX + headSize + spacing + spacing + 63.75f, posY + height - spacing * 2
                    - 10, iconSizeX, iconSizeY,
                    style.getSecondColor().getRGB());

            DisplayUtils.drawRoundedRect(posX + headSize + spacing + spacing + 8.75f, posY + height - spacing * 2 - 7,
                    (width - 70), 3, 1, ColorUtils.rgba(
                            203, 203, 203, 45));

            DisplayUtils.drawRoundedRect(posX + headSize + spacing + spacing 
                    + 8.75f, posY + height - spacing * 2 - 7,
                    (width - 70) * healthAnimation, 3, 1, style.getSecondColor().getRGB());

            GlStateManager.popMatrix();
        }
    }

    private LivingEntity getTarget(LivingEntity nullTarget) {
        LivingEntity auraTarget = Laura.getInstance().getFunctionRegistry().getKillAura().getTarget();
        LivingEntity target = nullTarget;
        if (auraTarget != null) {
            stopWatch.reset();
            allow = true;
            target = auraTarget;
        } else if (mc.currentScreen instanceof ChatScreen) {
            stopWatch.reset();
            allow = true;
            target = mc.player;
        } else {
            allow = false;
        }
        return target;
    }

    public void drawTargetHead(LivingEntity entity, float x, float y, float width, float height) {
        if (entity != null) {
            EntityRenderer<? super LivingEntity> rendererManager = mc.getRenderManager().getRenderer(entity);
            drawFace(rendererManager.getEntityTexture(entity), x, y, 8F, 8F, 8F, 8F, width, height, 64F, 64F, entity);
        }
    }

    public static void sizeAnimation(double width, double height, double scale) {
        GlStateManager.translated(width, height, 0);
        GlStateManager.scaled(scale, scale, scale);
        GlStateManager.translated(-width, -height, 0);
    }

    public void drawFace(ResourceLocation res, float d,
            float y,
            float u,
            float v,
            float uWidth,
            float vHeight,
            float width,
            float height,
            float tileWidth,
            float tileHeight,
            LivingEntity target) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        mc.getTextureManager().bindTexture(res);
        float hurtPercent = (target.hurtTime - (target.hurtTime != 0 ? mc.timer.renderPartialTicks : 0.0f)) / 10.0f;
        GL11.glColor4f(1, 1 - hurtPercent, 1 - hurtPercent, 1);
        AbstractGui.drawScaledCustomSizeModalRect(d, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    private void drawStyledRect(float x,
            float y,
            float width,
            float height,
            float radius) {

        DisplayUtils.drawRoundedRect(x - 0.5f, y - 0.5f, width + 1, height + 1, radius + 0.5f,
                ColorUtils.setAlpha(ColorUtils.rgb(10, 15, 13), 90));
        DisplayUtils.drawRoundedRect(x, y, width, height, radius, ColorUtils.rgba(10, 15, 13, 90));
        DisplayUtils.drawShadow(x + 5, y + 5, width, height, 5, ColorUtils.rgba(10, 15, 13, 15));

    }
}
