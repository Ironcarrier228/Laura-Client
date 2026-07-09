package im.laura.functions.impl.render;


import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import im.laura.events.WorldEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.utils.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

@FunctionRegister(
        name = "ItemRadius",
        type = Category.Render
)
/**
 *@created 13.07.2024
 * Author Cryonee
 */
public class ItemRadius extends Function {
    private final Minecraft mc = Minecraft.getInstance();

    public ItemRadius() {
    }

    @Subscribe
    private void onRender(WorldEvent event) {
        ClientPlayerEntity player = this.mc.player;
        ItemStack mainHandItem = ((PlayerEntity)player).getHeldItemMainhand();
        ItemStack offHandItem = this.mc.player.getHeldItemOffhand();
        float radius;
        Vector3d position;
        double pitch;
        double yaw;
        int x;
        int z;
        float angle;
        float xOffset;
        float zOffset;
        int loopIndex;
        if ((player == null || mainHandItem.isEmpty() || mainHandItem.getItem() != Items.ENDER_EYE) && (offHandItem.isEmpty() || offHandItem.getItem() != Items.ENDER_EYE)) {
            if (player != null && !mainHandItem.isEmpty() && mainHandItem.getItem() == Items.SUGAR || !offHandItem.isEmpty() && offHandItem.getItem() == Items.SUGAR) {
                radius = 10.0F;
                GlStateManager.pushMatrix();
                RenderSystem.translated(-this.mc.getRenderManager().info.getProjectedView().x, -this.mc.getRenderManager().info.getProjectedView().y, -this.mc.getRenderManager().info.getProjectedView().z);
                position = MathUtil.interpolate(this.mc.player.getPositionVec(), new Vector3d(this.mc.player.lastTickPosX, this.mc.player.lastTickPosY, this.mc.player.lastTickPosZ), event.getPartialTicks());
                position = position.add(0.0, -1.4, 0.0);
                RenderSystem.translated(position.x, position.y + (double)this.mc.player.getHeight(), position.z);
                pitch = (double)this.mc.getRenderManager().info.getPitch();
                yaw = (double)this.mc.getRenderManager().info.getYaw();
                GL11.glRotatef((float)(-yaw), 0.0F, 1.0F, 0.0F);
                RenderSystem.translated(-position.x, -(position.y + (double)this.mc.player.getHeight()), -position.z);
                RenderSystem.enableBlend();
                RenderSystem.depthMask(false);
                RenderSystem.disableTexture();
                RenderSystem.disableCull();
                RenderSystem.blendFunc(770, 771);
                RenderSystem.shadeModel(7425);
                RenderSystem.lineWidth(3.0F);
                GL11.glEnable(2848);
                GL11.glHint(3154, 4354);
                buffer.begin(6, DefaultVertexFormats.POSITION_COLOR);
                x = 587202559;
                buffer.pos(position.x, position.y + (double)this.mc.player.getHeight(), position.z).color(x).endVertex();

                for(z = 0; z <= 360; ++z) {
                    angle = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)z)) * radius));
                    xOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)z)) * radius));
                    buffer.pos((double)angle, position.y + (double)this.mc.player.getHeight(), (double)xOffset).color(x).endVertex();
                }

                tessellator.draw();
                buffer.begin(2, DefaultVertexFormats.POSITION_COLOR);
                z = -1996488705;

                for(loopIndex = 0; loopIndex <= 360; ++loopIndex) {
                    xOffset = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)loopIndex)) * radius));
                    zOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)loopIndex)) * radius));
                    buffer.pos((double)xOffset, position.y + (double)this.mc.player.getHeight(), (double)zOffset).color(z).endVertex();
                }

                tessellator.draw();
                GL11.glHint(3154, 4352);
                GL11.glDisable(2848);
                RenderSystem.enableTexture();
                RenderSystem.disableBlend();
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.shadeModel(7424);
                GlStateManager.popMatrix();
            } else if ((player == null || mainHandItem.isEmpty() || mainHandItem.getItem() != Items.FIRE_CHARGE) && (offHandItem.isEmpty() || offHandItem.getItem() != Items.FIRE_CHARGE)) {
                if (player != null && !mainHandItem.isEmpty() && mainHandItem.getItem() == Items.PHANTOM_MEMBRANE || !offHandItem.isEmpty() && offHandItem.getItem() == Items.PHANTOM_MEMBRANE) {
                    radius = 2.0F;
                    GlStateManager.pushMatrix();
                    RenderSystem.translated(-this.mc.getRenderManager().info.getProjectedView().x, -this.mc.getRenderManager().info.getProjectedView().y, -this.mc.getRenderManager().info.getProjectedView().z);
                    position = MathUtil.interpolate(this.mc.player.getPositionVec(), new Vector3d(this.mc.player.lastTickPosX, this.mc.player.lastTickPosY, this.mc.player.lastTickPosZ), event.getPartialTicks());
                    position = position.add(0.0, -1.4, 0.0);
                    RenderSystem.translated(position.x, position.y + (double)this.mc.player.getHeight(), position.z);
                    pitch = (double)this.mc.getRenderManager().info.getPitch();
                    yaw = (double)this.mc.getRenderManager().info.getYaw();
                    GL11.glRotatef((float)(-yaw), 0.0F, 1.0F, 0.0F);
                    RenderSystem.translated(-position.x, -(position.y + (double)this.mc.player.getHeight()), -position.z);
                    RenderSystem.enableBlend();
                    RenderSystem.depthMask(false);
                    RenderSystem.disableTexture();
                    RenderSystem.disableCull();
                    RenderSystem.blendFunc(770, 771);
                    RenderSystem.shadeModel(7425);
                    RenderSystem.lineWidth(3.0F);
                    GL11.glEnable(2848);
                    GL11.glHint(3154, 4354);
                    buffer.begin(6, DefaultVertexFormats.POSITION_COLOR);
                    x = 570425599;
                    buffer.pos(position.x, position.y + (double)this.mc.player.getHeight(), position.z).color(x).endVertex();

                    for(z = 0; z <= 360; ++z) {
                        angle = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)z)) * radius));
                        xOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)z)) * radius));
                        buffer.pos((double)angle, position.y + (double)this.mc.player.getHeight(), (double)xOffset).color(x).endVertex();
                    }

                    tessellator.draw();
                    buffer.begin(2, DefaultVertexFormats.POSITION_COLOR);
                    z = -2013265665;

                    for(loopIndex = 0; loopIndex <= 360; ++loopIndex) {
                        xOffset = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)loopIndex)) * radius));
                        zOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)loopIndex)) * radius));
                        buffer.pos((double)xOffset, position.y + (double)this.mc.player.getHeight(), (double)zOffset).color(z).endVertex();
                    }

                    tessellator.draw();
                    GL11.glHint(3154, 4352);
                    GL11.glDisable(2848);
                    RenderSystem.enableTexture();
                    RenderSystem.disableBlend();
                    RenderSystem.enableCull();
                    RenderSystem.depthMask(true);
                    RenderSystem.shadeModel(7424);
                    GlStateManager.popMatrix();
                }
            } else {
                radius = 10.0F;
                GlStateManager.pushMatrix();
                RenderSystem.translated(-this.mc.getRenderManager().info.getProjectedView().x, -this.mc.getRenderManager().info.getProjectedView().y, -this.mc.getRenderManager().info.getProjectedView().z);
                position = MathUtil.interpolate(this.mc.player.getPositionVec(), new Vector3d(this.mc.player.lastTickPosX, this.mc.player.lastTickPosY, this.mc.player.lastTickPosZ), event.getPartialTicks());
                position = position.add(0.0, -1.4, 0.0);
                RenderSystem.translated(position.x, position.y + (double)this.mc.player.getHeight(), position.z);
                pitch = (double)this.mc.getRenderManager().info.getPitch();
                yaw = (double)this.mc.getRenderManager().info.getYaw();
                GL11.glRotatef((float)(-yaw), 0.0F, 1.0F, 0.0F);
                RenderSystem.translated(-position.x, -(position.y + (double)this.mc.player.getHeight()), -position.z);
                RenderSystem.enableBlend();
                RenderSystem.depthMask(false);
                RenderSystem.disableTexture();
                RenderSystem.disableCull();
                RenderSystem.blendFunc(770, 771);
                RenderSystem.shadeModel(7425);
                RenderSystem.lineWidth(3.0F);
                GL11.glEnable(2848);
                GL11.glHint(3154, 4354);
                buffer.begin(6, DefaultVertexFormats.POSITION_COLOR);
                x = 587169536;
                buffer.pos(position.x, position.y + (double)this.mc.player.getHeight(), position.z).color(x).endVertex();

                for(z = 0; z <= 360; ++z) {
                    angle = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)z)) * radius));
                    xOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)z)) * radius));
                    buffer.pos((double)angle, position.y + (double)this.mc.player.getHeight(), (double)xOffset).color(x).endVertex();
                }

                tessellator.draw();
                buffer.begin(2, DefaultVertexFormats.POSITION_COLOR);
                z = -1996521728;

                for(loopIndex = 0; loopIndex <= 360; ++loopIndex) {
                    xOffset = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)loopIndex)) * radius));
                    zOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)loopIndex)) * radius));
                    buffer.pos((double)xOffset, position.y + (double)this.mc.player.getHeight(), (double)zOffset).color(z).endVertex();
                }

                tessellator.draw();
                GL11.glHint(3154, 4352);
                GL11.glDisable(2848);
                RenderSystem.enableTexture();
                RenderSystem.disableBlend();
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.shadeModel(7424);
                GlStateManager.popMatrix();
            }
        } else {
            radius = 10.0F;
            GlStateManager.pushMatrix();
            RenderSystem.translated(-this.mc.getRenderManager().info.getProjectedView().x, -this.mc.getRenderManager().info.getProjectedView().y, -this.mc.getRenderManager().info.getProjectedView().z);
            position = MathUtil.interpolate(this.mc.player.getPositionVec(), new Vector3d(this.mc.player.lastTickPosX, this.mc.player.lastTickPosY, this.mc.player.lastTickPosZ), event.getPartialTicks());
            position = position.add(0.0, -1.4, 0.0);
            RenderSystem.translated(position.x, position.y + (double)this.mc.player.getHeight(), position.z);
            pitch = (double)this.mc.getRenderManager().info.getPitch();
            yaw = (double)this.mc.getRenderManager().info.getYaw();
            GL11.glRotatef((float)(-yaw), 0.0F, 1.0F, 0.0F);
            RenderSystem.translated(-position.x, -(position.y + (double)this.mc.player.getHeight()), -position.z);
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.disableTexture();
            RenderSystem.disableCull();
            RenderSystem.blendFunc(770, 771);
            RenderSystem.shadeModel(7425);
            RenderSystem.lineWidth(3.0F);
            GL11.glEnable(2848);
            GL11.glHint(3154, 4354);
            buffer.begin(6, DefaultVertexFormats.POSITION_COLOR);
            x = 570490879;
            buffer.pos(position.x, position.y + (double)this.mc.player.getHeight(), position.z).color(x).endVertex();

            for(z = 0; z <= 360; ++z) {
                angle = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)z)) * radius));
                xOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)z)) * radius));
                buffer.pos((double)angle, position.y + (double)this.mc.player.getHeight(), (double)xOffset).color(x).endVertex();
            }

            tessellator.draw();
            buffer.begin(2, DefaultVertexFormats.POSITION_COLOR);
            z = -2013200385;

            for(loopIndex = 0; loopIndex <= 360; ++loopIndex) {
                xOffset = (float)(position.x + (double)(MathHelper.sin((float)Math.toRadians((double)loopIndex)) * radius));
                zOffset = (float)(position.z + (double)(-MathHelper.cos((float)Math.toRadians((double)loopIndex)) * radius));
                buffer.pos((double)xOffset, position.y + (double)this.mc.player.getHeight(), (double)zOffset).color(z).endVertex();
            }

            tessellator.draw();
            GL11.glHint(3154, 4352);
            GL11.glDisable(2848);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.shadeModel(7424);
            GlStateManager.popMatrix();
        }

    }
}