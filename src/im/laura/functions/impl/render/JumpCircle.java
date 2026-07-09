package im.laura.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import im.laura.events.JumpEvent;
import im.laura.events.WorldEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.render.ColorUtils;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import ru.hogoshi.Animation;
import ru.hogoshi.util.Easings;

import java.util.concurrent.CopyOnWriteArrayList;

@FunctionRegister(name = "JumpCircle", type = Category.Render)
public class JumpCircle extends Function {

    private final CopyOnWriteArrayList<Circle> circles = new CopyOnWriteArrayList<>();

    @Subscribe
    private void onJump(JumpEvent event) {
        if (mc.player != null) {
            Vector3d playerPos = mc.player.getPositionVec();
            circles.add(new Circle(playerPos));
        }
    }

    private final ModeSetting texture = new ModeSetting("Текстура",
        "Circle 1",
        "Circle 1", "Circle 2", "Circle 4", "Circle 5", "Circle 6", "Circle 7", "Circle 8", "Circle 10", "Random"
    );

    private final SliderSetting size = new SliderSetting("Размер", 2f, 0.5f, 5f, 0.1f);

    private final ResourceLocation[] circlesTextures = new ResourceLocation[] {
        new ResourceLocation("laura/images/circle1.png"),
        new ResourceLocation("laura/images/circle2.png"),
        new ResourceLocation("laura/images/circle4.png"),
        new ResourceLocation("laura/images/circle5.png"),
        new ResourceLocation("laura/images/circle6.png"),
        new ResourceLocation("laura/images/circle7.png"),
        new ResourceLocation("laura/images/circle8.png"),
        new ResourceLocation("laura/images/circle10.png")
    };

    public JumpCircle() {
        addSettings(texture, size);
    }

    private ResourceLocation getTexture(int index) {
        String mode = texture.get();
        if (mode.equals("Random")) {
            return circlesTextures[index % circlesTextures.length];
        }
        int textureIndex = 0;
        switch (mode) {
            case "Circle 2": textureIndex = 1; break;
            case "Circle 4": textureIndex = 2; break;
            case "Circle 5": textureIndex = 3; break;
            case "Circle 6": textureIndex = 4; break;
            case "Circle 7": textureIndex = 5; break;
            case "Circle 8": textureIndex = 6; break;
            case "Circle 10": textureIndex = 7; break;
        }
        return circlesTextures[textureIndex];
    }

    @Subscribe
    private void onRender(WorldEvent e) {

        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(7425);
        GlStateManager.blendFunc(770,771);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.disableDepthTest();
        GlStateManager.translated(-mc.getRenderManager().info.getProjectedView().x, -mc.getRenderManager().info.getProjectedView().y,-mc.getRenderManager().info.getProjectedView().z);

        // render
        {
            int textureIndex = 0;
            for (Circle c : circles) {
                ResourceLocation texture = getTexture(textureIndex);
                mc.getTextureManager().bindTexture(texture);
                textureIndex++;
                if (System.currentTimeMillis() - c.time > 2500) circles.remove(c);
                if (System.currentTimeMillis() - c.time > 2000 && !c.isBack) {
                    c.animation.animate(0, 0.8, Easings.BACK_IN);
                    c.isBack = true;
                }

                c.animation.update();
                float rad = (float) c.animation.getValue();

                Vector3d vector3d = c.vector3d;

                vector3d = vector3d.add(-rad, 0, -rad);

                buffer.begin(6, DefaultVertexFormats.POSITION_COLOR_TEX);
                buffer.pos(vector3d.x, vector3d.y, vector3d.z).color(ColorUtils.setAlpha(ColorUtils.getColor(5), 255)).tex(0,0).endVertex();
                buffer.pos(vector3d.x + rad * 2, vector3d.y, vector3d.z).color(ColorUtils.setAlpha(ColorUtils.getColor(10), 255)).tex(1,0).endVertex();
                buffer.pos(vector3d.x + rad * 2, vector3d.y, vector3d.z + rad * 2).color(ColorUtils.setAlpha(ColorUtils.getColor(15), 255)).tex(1,1).endVertex();
                buffer.pos(vector3d.x, vector3d.y, vector3d.z + rad * 2).color(ColorUtils.setAlpha(ColorUtils.getColor(20), 255)).tex(0,1).endVertex();
                tessellator.draw();
            }

        }

        GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
        GlStateManager.shadeModel(7424);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlphaTest();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }


    private class Circle {

        private final Vector3d vector3d;

        private final long time;
        private final Animation animation = new Animation();
        private boolean isBack;

        public Circle(Vector3d vector3d) {
            this.vector3d = vector3d;
            time = System.currentTimeMillis();
            animation.animate(size.get(), 1.0, Easings.BACK_OUT);
        }

    }

}
