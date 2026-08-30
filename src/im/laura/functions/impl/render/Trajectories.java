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
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@FunctionRegister(name = "Trajectories", type = Category.Render)
public class Trajectories extends Function {

    private final ColorSetting color = new ColorSetting("Цвет", 0x8000FF00);
    private final SliderSetting lineWidth = new SliderSetting("Толщина линии", 2.0f, 0.5f, 5.0f, 0.5f);
    private final BooleanSetting showHitPos = new BooleanSetting("Показать точку попадания", true);
    private final ColorSetting hitColor = new ColorSetting("Цвет точки", 0x80FF0000);
    private final BooleanSetting onlyThrowable = new BooleanSetting("Только метательные", true);

    public Trajectories() {
        addSettings(color, lineWidth, showHitPos, hitColor, onlyThrowable);
    }

    @Subscribe
    private void onRender(EventDisplay e) {
        if (mc.player == null || mc.world == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        ItemStack stack = mc.player.getHeldItemMainhand();
        if (stack.isEmpty() || !isThrowable(stack.getItem())) {
            if (!onlyThrowable.get()) {
                stack = mc.player.getHeldItemOffhand();
            } else {
                return;
            }
        }

        if (!isThrowable(stack.getItem()) &&
            !(stack.getItem() instanceof FishingRodItem) &&
            !(stack.getItem() instanceof BowItem) &&
            !(stack.getItem() instanceof CrossbowItem)) {
            return;
        }

        renderTrajectory(e.getMatrixStack());
    }

    private boolean isThrowable(Item item) {
        return item instanceof SnowballItem ||
               item instanceof EggItem ||
               item instanceof EnderPearlItem ||
               item instanceof ExperienceBottleItem;
    }

    private void renderTrajectory(MatrixStack matrixStack) {
        Vector3d cameraPos = mc.getRenderManager().info.getProjectedView();
        double renderX = cameraPos.x;
        double renderY = cameraPos.y;
        double renderZ = cameraPos.z;

        // Получаем начальную позицию и угол
        double posX = mc.player.lastTickPosX + (mc.player.getPosX() - mc.player.lastTickPosX) * mc.getRenderPartialTicks();
        double posY = mc.player.lastTickPosY + (mc.player.getPosY() - mc.player.lastTickPosY) * mc.getRenderPartialTicks() + mc.player.getEyeHeight();
        double posZ = mc.player.lastTickPosZ + (mc.player.getPosZ() - mc.player.lastTickPosZ) * mc.getRenderPartialTicks();

        float yaw = mc.player.prevRotationYaw + (mc.player.rotationYaw - mc.player.prevRotationYaw) * mc.getRenderPartialTicks();
        float pitch = mc.player.prevRotationPitch + (mc.player.rotationPitch - mc.player.prevRotationPitch) * mc.getRenderPartialTicks();

        // Вычисляем начальную скорость
        float velocity = 1.5f;
        ItemStack stack = mc.player.getHeldItemMainhand();

        if (stack.getItem() instanceof BowItem) {
            velocity = 3.0f;
        } else if (stack.getItem() instanceof FishingRodItem) {
            velocity = 1.5f;
        }

        // Вычисляем вектор движения
        float f1 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f3 = -MathHelper.cos(-pitch * 0.017453292F);
        float f4 = MathHelper.sin(-pitch * 0.017453292F);

        double motionX = -f2 * f3 * velocity;
        double motionY = f4 * velocity;
        double motionZ = f1 * f3 * velocity;

        // Симулируем полёт снаряда
        List<Vector3d> path = new ArrayList<>();
        Vector3d currentPosition = new Vector3d(posX, posY, posZ);
        Vector3d motion = new Vector3d(motionX, motionY, motionZ);

        Vector3d hitPos = null;

        for (int i = 0; i < 100; i++) {
            path.add(currentPosition);

            // Проверяем коллизию
            RayTraceResult result = mc.world.rayTraceBlocks(
                new RayTraceContext(
                    currentPosition,
                    currentPosition.add(motion),
                    RayTraceContext.BlockMode.COLLIDER,
                    RayTraceContext.FluidMode.NONE,
                    mc.player
                )
            );

            if (result.getType() != RayTraceResult.Type.MISS) {
                hitPos = result.getHitVec();
                break;
            }

            // Проверяем попадание в сущности
            for (Entity entity : mc.world.getAllEntities()) {
                if (entity == mc.player) continue;

                double dist = entity.getDistanceSq(currentPosition);
                if (dist < 1.0) {
                    hitPos = currentPosition;
                    break;
                }
            }

            if (hitPos != null) break;

            // Применяем гравитацию
            currentPosition = currentPosition.add(motion);
            motion = motion.add(0, -0.03, 0);
            motion = motion.scale(0.99);
        }

        // Рендерим траекторию
        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();
        RenderSystem.lineWidth(lineWidth.get());

        Color lineColor = new Color(color.get(), true);
        RenderSystem.color4f(lineColor.getRed() / 255.0f, lineColor.getGreen() / 255.0f,
                            lineColor.getBlue() / 255.0f, lineColor.getAlpha() / 255.0f);

        // Рисуем линии между точками
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        for (Vector3d point : path) {
            double x = point.x - renderX;
            double y = point.y - renderY;
            double z = point.z - renderZ;
            buffer.pos(x, y, z).endVertex();
        }
        tessellator.draw();

        // Рисуем точку попадания
        if (showHitPos.get() && hitPos != null) {
            Color hitColorVal = new Color(hitColor.get(), true);
            RenderSystem.color4f(hitColorVal.getRed() / 255.0f, hitColorVal.getGreen() / 255.0f,
                                hitColorVal.getBlue() / 255.0f, hitColorVal.getAlpha() / 255.0f);

            double x = hitPos.x - renderX;
            double y = hitPos.y - renderY;
            double z = hitPos.z - renderZ;

            // Рисуем маленький куб в точке попадания
            float size = 0.1f;
            buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
            buffer.pos(x - size, y - size, z - size).endVertex();
            buffer.pos(x + size, y - size, z - size).endVertex();
            buffer.pos(x + size, y + size, z - size).endVertex();
            buffer.pos(x - size, y + size, z - size).endVertex();
            buffer.pos(x - size, y - size, z + size).endVertex();
            buffer.pos(x + size, y - size, z + size).endVertex();
            buffer.pos(x + size, y + size, z + size).endVertex();
            buffer.pos(x - size, y + size, z + size).endVertex();
            tessellator.draw();
        }

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
    }
}
