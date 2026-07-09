package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.EventDisplay;
import im.laura.events.EventUpdate;
import im.laura.events.WorldEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.impl.combat.KillAura;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.utils.animations.Animation;
import im.laura.utils.animations.Direction;
import im.laura.utils.animations.impl.DecelerateAnimation;
import im.laura.utils.math.MathUtil;
import im.laura.utils.math.Vector4i;
import im.laura.utils.projections.ProjectionUtil;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.DisplayUtils;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "TargetESP", type = Category.Render)
public class TargetESP extends Function {

    private final KillAura killAura;

    private final ModeSetting mode = new ModeSetting("Мод", "Ромб", "Ромб", "Кольцо", "Призраки", "Не отображать");
    private final ModeSetting targetStyle = new ModeSetting("Стиль", "target", "target", "target2", "target3", "target4", "target5");
    private final BooleanSetting animka = new BooleanSetting("Статичный", true);

    private final Animation alpha = new DecelerateAnimation(600, 255);
    private LivingEntity currentTarget;

    @SuppressWarnings("FieldCanBeLocal")
    private final long lastTime = System.currentTimeMillis();
    public static long startTime = System.currentTimeMillis();

    private static final ResourceLocation[] targets = {
            new ResourceLocation("laura/images/target.png"),
            new ResourceLocation("laura/images/target2.png"),
            new ResourceLocation("laura/images/target3.png"),
            new ResourceLocation("laura/images/target4.png"),
            new ResourceLocation("laura/images/target5.png")
    };

    public TargetESP(KillAura killAura) {
        this.killAura = killAura;
        addSettings(mode, targetStyle, animka);
    }

    private int getTargetIndex() {
        String[] modes = {"target", "target2", "target3", "target4", "target5"};
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(targetStyle.get())) {
                return i;
            }
        }
        return 0;
    }

    @Subscribe
    @SuppressWarnings("unused")
    private void onUpdate(EventUpdate eventUpdate) {
        if (killAura.getTarget() != null) {
            currentTarget = killAura.getTarget();
        }
        alpha.setDirection(!killAura.isState() || killAura.getTarget() == null ? Direction.BACKWARDS : Direction.FORWARDS);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onRender(WorldEvent e) {
        if (this.alpha.finished(Direction.BACKWARDS) && !mode.is("Кольцо") && !mode.is("Призраки")) {
            return;
        }

        if (mode.is("Призраки")) {
            if (killAura.isState() && killAura.getTarget() != null) {
                LivingEntity targetEntity = killAura.getTarget();

                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                MatrixStack ms = new MatrixStack();

                RenderSystem.pushMatrix();
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.shadeModel(7425);
                RenderSystem.disableCull();
                RenderSystem.disableAlphaTest();
                RenderSystem.blendFuncSeparate(770, 1, 0, 1);

                float time = (float) ((System.currentTimeMillis() - startTime) / 1500.0F + Math.sin((float) (System.currentTimeMillis() - startTime) / 1500.0F) / 15.0);

                Vector3d vector3d = MathUtil.interpolate(targetEntity.getPositionVec(),
                        new Vector3d(targetEntity.lastTickPosX, targetEntity.lastTickPosY, targetEntity.lastTickPosZ), e.getPartialTicks());

                ActiveRenderInfo camera = mc.getRenderManager().info;
                mc.getTextureManager().bindTexture(new ResourceLocation("laura/images/glow.png"));

                boolean alternate = true;

                for (int iteration = 0; iteration < 3; iteration++) {
                    float offset = (iteration - 1) * 0.5F;

                    for (float i = time * 360.0F; i < time * 360.0F + 80.0F; i += 2.0F) {
                        float max = time * 360.0F + 40.0F;
                        float angleProgress = MathUtil.normalize(i, time * 360.0F, max);

                        float radius = 0.75F;

                        double radians = Math.toRadians(i);
                        double offsetY = Math.sin(radians * 1.1F) * 0.4F + offset;

                        float sizeMultiplier = (!alternate ? 0.22F : 0.12F)
                                * (Math.max(alternate ? 0.22F : 0.12F, alternate ? angleProgress : (1.0F + (0.4F - angleProgress)) / 2.0F) + 0.4F);
                        float size = sizeMultiplier * 1.1F;

                        ms.push();

                        double x = vector3d.x + Math.cos(radians) * radius - camera.getProjectedView().getX();
                        double y = vector3d.y + targetEntity.getHeight() / 2.0F + offsetY - camera.getProjectedView().getY();
                        double z = vector3d.z + Math.sin(radians) * radius - camera.getProjectedView().getZ();

                        ms.translate(x, y, z);
                        ms.rotate(camera.getRotation());

                        int color = HUD.getColor((int) i);
                        int alphaVal = (int) (alpha.getOutput());

                        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR_TEX);
                        Matrix4f matrix = ms.getLast().getMatrix();

                        buffer.pos(matrix, -size / 2f, -size / 2f, 0).color(ColorUtils.setAlpha(color, alphaVal)).tex(0, 0).endVertex();
                        buffer.pos(matrix, size / 2f, -size / 2f, 0).color(ColorUtils.setAlpha(color, alphaVal)).tex(1, 0).endVertex();
                        buffer.pos(matrix, size / 2f, size / 2f, 0).color(ColorUtils.setAlpha(color, alphaVal)).tex(1, 1).endVertex();
                        buffer.pos(matrix, -size / 2f, size / 2f, 0).color(ColorUtils.setAlpha(color, alphaVal)).tex(0, 1).endVertex();
                        tessellator.draw();

                        ms.pop();
                    }

                    time *= -1.3F;
                    alternate = !alternate;
                }

                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                RenderSystem.popMatrix();
            }
        }

        if (mode.is("Кольцо")) {
            if (killAura.isState() && killAura.getTarget() != null) {
                ActiveRenderInfo rm = mc.getRenderManager().info;
                LivingEntity target = killAura.getTarget();

                double x = target.lastTickPosX + (target.getPosX() - target.lastTickPosX) * e.getPartialTicks() - rm.getProjectedView().getX();
                double y = target.lastTickPosY + (target.getPosY() - target.lastTickPosY) * e.getPartialTicks() - rm.getProjectedView().getY();
                double z = target.lastTickPosZ + (target.getPosZ() - target.lastTickPosZ) * e.getPartialTicks() - rm.getProjectedView().getZ();
                float height = target.getHeight();

                double duration = 2000.0;
                double elapsed = System.currentTimeMillis() % duration;
                boolean side = elapsed > duration / 2.0;
                double progress = elapsed / (duration / 2.0);
                progress = side ? --progress : 1.0 - progress;
                progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
                double eased = height / 2.0F * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1);

                RenderSystem.pushMatrix();
                GL11.glDepthMask(false);
                GL11.glEnable(2848);
                GL11.glHint(3154, 4354);
                RenderSystem.disableTexture();
                RenderSystem.enableBlend();
                RenderSystem.disableAlphaTest();
                RenderSystem.shadeModel(7425);
                RenderSystem.disableCull();
                RenderSystem.lineWidth(1.5F);

                float glowAlpha = 145F;
                float coreAlpha = 17.1F;

                RenderSystem.color4f(6.0F, 6.0F, 6.0F, glowAlpha);
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(8, DefaultVertexFormats.POSITION_COLOR);

                float[] colors = IntColor.rgb(HUD.getColor(0));
                int i;
                for (i = 0; i <= 360; ++i) {
                    buffer.pos(x + cos(Math.toRadians(i)) * target.getWidth() * 0.85, y + height * progress, z + sin(Math.toRadians(i)) * target.getWidth() * 0.85)
                            .color(colors[0], colors[1], colors[2], glowAlpha).endVertex();
                    buffer.pos(x + cos(Math.toRadians(i)) * target.getWidth() * 0.85, y + height * progress + eased * 1.5, z + sin(Math.toRadians(i)) * target.getWidth() * 0.85)
                            .color(colors[0], colors[1], colors[2], coreAlpha).endVertex();
                }
                buffer.finishDrawing();
                WorldVertexBufferUploader.draw(buffer);

                RenderSystem.color4f(0.5F, 0.5F, 0.5F, coreAlpha);
                buffer.begin(2, DefaultVertexFormats.POSITION_COLOR);
                for (i = 0; i <= 360; ++i) {
                    buffer.pos(x + cos(Math.toRadians(i)) * target.getWidth() * 0.85, y + height * progress, z + sin(Math.toRadians(i)) * target.getWidth() * 0.85)
                            .color(colors[0], colors[1], colors[2], coreAlpha).endVertex();
                }
                buffer.finishDrawing();
                WorldVertexBufferUploader.draw(buffer);

                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.enableTexture();
                RenderSystem.enableAlphaTest();
                GL11.glDepthMask(true);
                GL11.glDisable(2848);
                GL11.glHint(3154, 4354);
                RenderSystem.shadeModel(7424);
                RenderSystem.popMatrix();
            }
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    private void onDisplay(EventDisplay e) {
        if (this.alpha.finished(Direction.BACKWARDS) && !mode.is("Кольцо")) {
            return;
        }

        if (e.getType() != EventDisplay.Type.PRE) {
            return;
        }

        if (mode.is("Ромб")) {
            if (this.currentTarget != null && this.currentTarget != mc.player) {
                double sin = Math.sin((double) System.currentTimeMillis() / 1000.0);
                Vector3d interpolated = this.currentTarget.getPositon(e.getPartialTicks());
                float size;

                if (animka.get()) {
                    size = (float) this.getScale(interpolated, 16);
                } else {
                    size = 150;
                }

                Vector2f pos = ProjectionUtil.project(
                        interpolated.x,
                        interpolated.y + (double) (this.currentTarget.getHeight() / 1.95F),
                        interpolated.z
                );

                e.getMatrixStack().push();
                e.getMatrixStack().translate(pos.x, pos.y, 0.0);
                e.getMatrixStack().rotate(Vector3f.ZP.rotationDegrees((float) sin * 360.0F));
                e.getMatrixStack().translate(-pos.x, -pos.y, 0.0);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                int alphaValue = (int) this.alpha.getOutput();
                DisplayUtils.drawImageAlpha(
                        targets[getTargetIndex()],
                        pos.x - size / 2.0F,
                        pos.y - size / 2.0F,
                        size,
                        size,
                        new Vector4i(
                                ColorUtils.setAlpha(HUD.getColor(0, 1.0F), alphaValue),
                                ColorUtils.setAlpha(HUD.getColor(90, 1.0F), alphaValue),
                                ColorUtils.setAlpha(HUD.getColor(180, 1.0F), alphaValue),
                                ColorUtils.setAlpha(HUD.getColor(270, 1.0F), alphaValue)
                        )
                );

                RenderSystem.disableBlend();
                e.getMatrixStack().pop();
            }
        }
    }

    public double getScale(Vector3d position, double size) {
        Vector3d cam = mc.getRenderManager().info.getProjectedView();
        double distance = cam.distanceTo(position);
        double fov = mc.gameRenderer.getFOVModifier(mc.getRenderManager().info, mc.getRenderPartialTicks(), true);
        return Math.max(10.0, 1000.0 / distance) * (size / 30.0) / (fov == 70.0 ? 1.0 : fov / 70.0);
    }

    public static class IntColor {
        public static float[] rgb(int color) {
            return new float[]{(color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, (color >> 24 & 0xFF) / 255f};
        }

        public static int rgba(int r, int g, int b, int a) {
            return a << 24 | r << 16 | g << 8 | b;
        }

        public static int getRed(int hex) {
            return hex >> 16 & 255;
        }

        public static int getGreen(int hex) {
            return hex >> 8 & 255;
        }

        public static int getBlue(int hex) {
            return hex & 255;
        }

        public static int getAlpha(final int hex) {
            return hex >> 24 & 255;
        }
    }
}
