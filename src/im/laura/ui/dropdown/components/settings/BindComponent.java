package im.laura.ui.dropdown.components.settings;

import com.mojang.blaze3d.matrix.MatrixStack;
import im.laura.functions.settings.impl.BindSetting;
import im.laura.ui.dropdown.impl.Component;
import im.laura.utils.client.KeyStorage;
import im.laura.utils.math.MathUtil;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.Cursors;
import im.laura.utils.render.DisplayUtils;
import im.laura.utils.render.font.Fonts;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class BindComponent extends Component {

    final BindSetting setting;

    public BindComponent(BindSetting setting) {
        this.setting = setting;
        this.setHeight(16);
    }

    boolean activated;
    boolean hovered = false;

    @Override
    public void render(MatrixStack stack, float mouseX, float mouseY) {
        super.render(stack, mouseX, mouseY);
        Fonts.montserrat.drawText(stack, setting.getName(), getX() + 5, getY() + 6.5f / 2f + 1, ColorUtils.rgb(160, 163, 175), 6.5f, 0.05f);
        String bind = KeyStorage.getKey(setting.get());

        if (bind == null || setting.get() == -1) {
            bind = "Нету";
        }
        boolean next = Fonts.montserrat.getWidth(bind, 5.5f, activated ? 0.1f : 0.05f) >= 16;
        float x = next ? getX() + 5 : getX() + getWidth() - 7 - Fonts.montserrat.getWidth(bind, 5.5f, activated ? 0.1f : 0.05f);
        float y = getY() + 5.5f / 2f + (5.5f / 2f) + (next ? 8 : 0);
        DisplayUtils.drawShadow(getX() + 5, getY() + 9, Fonts.montserrat.getWidth(bind, 5.5f, activated ? 0.1f : 0.05f) + 4, 5.5f + 4, 10, ColorUtils.rgba(25, 26, 40, 45));

        DisplayUtils.drawRoundedRect(getX() + 5, getY() + 9,Fonts.montserrat.getWidth(bind, 5.5f, activated ? 0.1f : 0.05f) + 4, 5.5f + 4, 2, ColorUtils.rgba(25, 26, 40, 45));
        Fonts.montserrat.drawText(stack, bind, x, y, activated ? -1 : ColorUtils.rgb(255, 255, 255), 5.5f, activated ? 0.1f : 0.05f);

        if (isHovered(mouseX, mouseY)) {
            if (MathUtil.isHovered(mouseX, mouseY, x - 2 + 0.5F, y - 2, Fonts.montserrat.getWidth(bind, 5.5f, activated ? 0.1f : 0.05f) + 4, 5.5f + 4)) {
                if (!hovered) {
                    GLFW.glfwSetCursor(Minecraft.getInstance().getMainWindow().getHandle(), Cursors.HAND);
                    hovered = true;
                }
            } else {
                if (hovered) {
                    GLFW.glfwSetCursor(Minecraft.getInstance().getMainWindow().getHandle(), Cursors.ARROW);
                    hovered = false;
                }
            }
        }
        setHeight(next ? 20 : 16);
    }

    @Override
    public void keyPressed(int key, int scanCode, int modifiers) {
        // TODO Auto-generated method stub
        if (activated) {
            if (key == GLFW.GLFW_KEY_DELETE) {
                setting.set(-1);
                activated = false;
                return;
            }
            setting.set(key);
            activated = false;
        }
        super.keyPressed(key, scanCode, modifiers);
    }


    @Override
    public void mouseClick(float mouseX, float mouseY, int mouse) {
        if (isHovered(mouseX, mouseY) && mouse == 0) {
            activated = !activated;
        }

        if (activated && mouse >= 1) {
            System.out.println(-100 + mouse);
            setting.set(-100 + mouse);
            activated = false;
        }

        super.mouseClick(mouseX, mouseY, mouse);
    }

    @Override
    public void mouseRelease(float mouseX, float mouseY, int mouse) {
        super.mouseRelease(mouseX, mouseY, mouse);
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
