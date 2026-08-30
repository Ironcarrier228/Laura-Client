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
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@FunctionRegister(name = "SkeletonESP", type = Category.Render)
@SuppressWarnings({"unused", "BetaApi", "Guava", "deprecation"})
public class SkeletonESP extends Function {

    private final ModeListSetting targets = new ModeListSetting("Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", true),
            new BooleanSetting("Невидимые", false));

    private final ColorSetting playersColor = new ColorSetting("Цвет игроков", 0xFFFF0000);
    private final ColorSetting friendsColor = new ColorSetting("Цвет друзей", 0xFF00FF00);
    private final ColorSetting mobsColor = new ColorSetting("Цвет мобов", 0xFFFF00FF);
    private final ColorSetting animalsColor = new ColorSetting("Цвет животных", 0xFF00FFFF);

    private final SliderSetting lineWidth = new SliderSetting("Толщина линии", 1.5f, 0.5f, 5.0f, 0.5f);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", true);
    private final BooleanSetting headTrack = new BooleanSetting("Следить за головой", true);

    public SkeletonESP() {
        addSettings(targets, playersColor, friendsColor, mobsColor, animalsColor,
                   lineWidth, throughWalls, headTrack);
    }

    @SuppressWarnings("BetaApi")
    @Subscribe
    public void onRender(EventDisplay e) {
        if (mc.world == null || mc.player == null || e.getType() != EventDisplay.Type.POST) {
            return;
        }

        for (Entity entity : mc.world.getAllEntities()) {
            if (!shouldRender(entity)) continue;
            if (!throughWalls.get() && !mc.player.canEntityBeSeen(entity)) continue;

            renderSkeleton(entity, e.getMatrixStack());
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

    @SuppressWarnings("deprecation")
    private void renderSkeleton(Entity entity, MatrixStack matrixStack) {
        Color color = getColorForEntity(entity);
        if (color == null) return;

        LivingEntity living = (LivingEntity) entity;

        double x = entity.lastTickPosX + (entity.getPosX() - entity.lastTickPosX) * mc.getRenderPartialTicks();
        double y = entity.lastTickPosY + (entity.getPosY() - entity.lastTickPosY) * mc.getRenderPartialTicks();
        double z = entity.lastTickPosZ + (entity.getPosZ() - entity.lastTickPosZ) * mc.getRenderPartialTicks();

        RenderSystem.pushMatrix();

        if (throughWalls.get()) {
            RenderSystem.disableDepthTest();
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableTexture();
        RenderSystem.lineWidth(lineWidth.get());

        Matrix4f matrix = matrixStack.getLast().getMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        float yaw = living.prevRotationYaw + (living.rotationYaw - living.prevRotationYaw) * mc.getRenderPartialTicks();
        if (headTrack.get()) {
            yaw = mc.player.rotationYaw;
        }

        float headX = 0;
        float headY = living.getHeight() - 0.2f;
        float headZ = 0;

        float bodyX = 0;
        float bodyY = living.getHeight() * 0.65f;
        float bodyZ = 0;

        float hipX = 0;
        float hipY = living.getHeight() * 0.4f;
        float hipZ = 0;

        float leftShoulderX = -0.25f;
        float leftShoulderY = living.getHeight() * 0.75f;
        float leftShoulderZ = 0;

        float rightShoulderX = 0.25f;
        float rightShoulderY = living.getHeight() * 0.75f;
        float rightShoulderZ = 0;

        float leftElbowX = -0.35f;
        float leftElbowY = living.getHeight() * 0.55f;
        float leftElbowZ = 0;

        float rightElbowX = 0.35f;
        float rightElbowY = living.getHeight() * 0.55f;
        float rightElbowZ = 0;

        float leftKneeX = -0.12f;
        float leftKneeY = living.getHeight() * 0.25f;
        float leftKneeZ = 0;

        float rightKneeX = 0.12f;
        float rightKneeY = living.getHeight() * 0.25f;
        float rightKneeZ = 0;

        float leftFootX = -0.12f;
        float leftFootY = 0.05f;
        float leftFootZ = 0;

        float rightFootX = 0.12f;
        float rightFootY = 0.05f;
        float rightFootZ = 0;

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        addLine(buffer, matrix, x, y, z, headX, headY, headZ, bodyX, bodyY, bodyZ, color, yaw);
        addLine(buffer, matrix, x, y, z, bodyX, bodyY, bodyZ, hipX, hipY, hipZ, color, yaw);
        addLine(buffer, matrix, x, y, z, leftShoulderX, leftShoulderY, leftShoulderZ, leftElbowX, leftElbowY, leftElbowZ, color, yaw);
        addLine(buffer, matrix, x, y, z, rightShoulderX, rightShoulderY, rightShoulderZ, rightElbowX, rightElbowY, rightElbowZ, color, yaw);
        addLine(buffer, matrix, x, y, z, hipX, hipY, hipZ, leftKneeX, leftKneeY, leftKneeZ, color, yaw);
        addLine(buffer, matrix, x, y, z, leftKneeX, leftKneeY, leftKneeZ, leftFootX, leftFootY, leftFootZ, color, yaw);
        addLine(buffer, matrix, x, y, z, hipX, hipY, hipZ, rightKneeX, rightKneeY, rightKneeZ, color, yaw);
        addLine(buffer, matrix, x, y, z, rightKneeX, rightKneeY, rightKneeZ, rightFootX, rightFootY, rightFootZ, color, yaw);
        addLine(buffer, matrix, x, y, z, leftShoulderX, leftShoulderY, leftShoulderZ, rightShoulderX, rightShoulderY, rightShoulderZ, color, yaw);

        tessellator.draw();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();

        if (throughWalls.get()) {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.popMatrix();
    }

    private void addLine(BufferBuilder buffer, Matrix4f matrix,
                        double baseX, double baseY, double baseZ,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        Color color, float yaw) {
        float cos = (float) Math.cos(-yaw * 0.017453292f - 3.1415927f);
        float sin = (float) Math.sin(-yaw * 0.017453292f - 3.1415927f);

        float rx1 = x1 * cos - z1 * sin;
        float rz1 = x1 * sin + z1 * cos;
        float rx2 = x2 * cos - z2 * sin;
        float rz2 = x2 * sin + z2 * cos;

        buffer.pos(matrix, (float)(baseX + rx1), (float)(baseY + y1), (float)(baseZ + rz1))
              .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();

        buffer.pos(matrix, (float)(baseX + rx2), (float)(baseY + y2), (float)(baseZ + rz2))
              .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
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
}
