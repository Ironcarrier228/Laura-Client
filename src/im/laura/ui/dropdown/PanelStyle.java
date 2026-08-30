package im.laura.ui.dropdown;

import com.mojang.blaze3d.matrix.MatrixStack;
import im.laura.Laura;
import im.laura.functions.api.Category;
import im.laura.ui.styles.Style;
import im.laura.utils.math.MathUtil;
import im.laura.utils.math.Vector4i;
import im.laura.utils.render.*;
import im.laura.utils.render.font.Fonts;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

@Getter
public class PanelStyle extends Panel {

    public PanelStyle(Category category) {
        super(category);
        // TODO Auto-generated constructor stub
    }

    float max = 0;

    @Override
    public void render(MatrixStack stack, float mouseX, float mouseY) {
        float header = 25;
        float headerFont = 8;
        setAnimatedScrool(MathUtil.fast(getAnimatedScrool(), getScroll(), 10));

        DisplayUtils.drawRoundedRect(x, y + 19, width, height - 45, 13, ColorUtils.rgba(25, 26, 40, 165));

        DisplayUtils.drawRoundedRect(x + 3.8f, y + 22.5f, width - 8, height - 53, 12,
                ColorUtils.rgba(25, 26, 40, 125));
        DisplayUtils.drawShadow(x + 3.8f, y + 22.5f, width - 8, height - 53, 12,
                ColorUtils.rgba(25, 26, 40, 125));

        Fonts.montserrat.drawCenteredText(stack, "Theme Editor", x + width / 2f,
                y + header / 2f - Fonts.montserrat.getHeight(headerFont) / 2f + 18, ColorUtils.rgb(255, 255, 255),
                headerFont - 1.5f, 0.1f);

        if (max > height - 24 * 2) {
            setScroll(MathHelper.clamp(getScroll(), -max + height - header - 10, 0));
            setAnimatedScrool(MathHelper.clamp(getAnimatedScrool(), -max + height - header - 10, 0));
        } else {
            setScroll(0);
            setAnimatedScrool(0);
        }

        float animationValue = (float) DropDown.getAnimation().getValue() * DropDown.scale;

        float halfAnimationValueRest = (1 - animationValue) / 2f;
        float height = getHeight();
        float testX = getX() + (getWidth() * halfAnimationValueRest);
        float testY = getY() + 25 + (height * halfAnimationValueRest);
        float testW = getWidth() * animationValue;
        float testH = height * animationValue - 56;

        testX = testX * animationValue + ((Minecraft.getInstance().getMainWindow().getScaledWidth() - testW) * halfAnimationValueRest);
        Scissor.push();
        Scissor.setFromComponentCoordinates(testX, testY, testW, testH);

        int offset = 1;

        boolean hovered = false;

        float x = this.x + 5;
        float y = this.y + header + 5 + offset + getAnimatedScrool();

        float H = 12;

        for (Style style : Laura.getInstance().getStyleManager().getStyleList()) {

            if (MathUtil.isHovered(mouseX, mouseY, x + 5, y, width - 10 - 10, H)) {
                hovered = true;
            }

            if (Laura.getInstance().getStyleManager().getCurrentStyle() == style) {
                Fonts.montserrat.drawText(stack, style.getStyleName(), x + 0.5f * 1.5f + width / 2 - 28, y + H / 2 + 7 - Fonts.montserrat.getHeight(6) / 2, style.getFirstColor().getRGB(), 6f, 0.05f);
                DisplayUtils.drawRoundedRect(x + 5f, y + 7, width / 2 - 40, H, 10, style.getFirstColor().getRGB());
                DisplayUtils.drawShadow(x + 5f, y + 7.5f, width / 2 - 40, H, 10, style.getFirstColor().getRGB());
            }

            DisplayUtils.drawRoundedRect(x + 5f, y + 7.5f, width / 2 - 40, H, 2, style.getFirstColor().getRGB());

            Fonts.montserrat.drawText(stack, style.getStyleName(), x + 0.5f * 1.5f + width / 2 - 28, y + H / 2 + 7 - Fonts.montserrat.getHeight(6) / 2, -1, 6f, 0.05f);

            y += 5+H;
            offset++;
        }

        if (MathUtil.isHovered(mouseX, mouseY, x, y, width, height)) {
            if (hovered) {
                GLFW.glfwSetCursor(Minecraft.getInstance().getMainWindow().getHandle(), Cursors.HAND);
            } else {
                GLFW.glfwSetCursor(Minecraft.getInstance().getMainWindow().getHandle(), Cursors.ARROW);
            }
        }
        Scissor.unset();
        Scissor.pop();
        max = offset * Laura.getInstance().getStyleManager().getStyleList().size() * 1.21f;
    }

    @Override
    public void keyPressed(int key, int scanCode, int modifiers) {

    }

    @Override
    public void mouseClick(float mouseX, float mouseY, int button) {
        float header = 25;
        int offset = 0;
        float x = this.x + 5;
        float y = this.y + offset + header + 5 + getAnimatedScrool();

        for (Style style : Laura.getInstance().getStyleManager().getStyleList()) {
            float barHeight = 12;
            float barY = y + 7.5f;

            if (MathUtil.isHovered(mouseX, mouseY, x + 5, barY, width / 2 - 40, barHeight)) {
                Laura.getInstance().getStyleManager().setCurrentStyle(style);
            }
            y += 5 + barHeight;
            offset++;
        }
    }

    @Override
    public void mouseRelease(float mouseX, float mouseY, int button) {

    }

}