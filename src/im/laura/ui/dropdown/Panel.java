package im.laura.ui.dropdown;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;

import im.laura.Laura;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.ui.dropdown.components.ModuleComponent;
import im.laura.ui.dropdown.impl.Component;
import im.laura.ui.dropdown.impl.IBuilder;
import im.laura.utils.math.MathUtil;
import im.laura.utils.math.Vector4i;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.DisplayUtils;
import im.laura.utils.render.Scissor;
import im.laura.utils.render.Stencil;
import im.laura.utils.render.font.Fonts;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector4f;

@Getter
@Setter
public class Panel implements IBuilder {

    private final Category category;
    protected float x;
    protected float y;
    protected final float width = 105f;
    protected final float height = 220f;


    private List<ModuleComponent> modules = new ArrayList<>();
    private float scroll, animatedScrool;


    public Panel(Category category) {
        this.category = category;

        for (Function function : Laura.getInstance().getFunctionRegistry().getFunctions()) {
            if (function.getCategory() == category) {
                ModuleComponent component = new ModuleComponent(function);
                component.setPanel(this);
                modules.add(component);
            }
        }

    }

    @Override
    public void render(MatrixStack stack, float mouseX, float mouseY) {

        animatedScrool = MathUtil.fast(animatedScrool, scroll, 10);
        float header = 20;
        float headerFont = 8;


        DisplayUtils.drawRoundedRect(x, y, width, height, 13, ColorUtils.rgba(25, 26, 40, 165));

        DisplayUtils.drawRoundedRect(x + 3.8f, y + 3.5f, width - 8, height - 7, 12,
                ColorUtils.rgba(25, 26, 40, 125));

        DisplayUtils.drawShadow(x + 3.8f, y + 3.5f, width - 8, height - 7, 12,
                ColorUtils.rgba(25, 26, 40, 125));


        Fonts.montserrat.drawCenteredText(stack, category.name(), x + width / 2f,
                y + header / 2f - Fonts.montserrat.getHeight(headerFont) / 2f - 1 + 5, ColorUtils.rgb(255, 255, 255),
                headerFont, 0.1f);

        drawComponents(stack, mouseX, mouseY);


        DisplayUtils.drawRoundedRect(x, y + height - header - 1, width, header, new Vector4f(0, 15, 0, 15),
                new Vector4i(Color.TRANSLUCENT, ColorUtils.rgba(23, 23, 23, (int) (255 * 0.5)), Color.TRANSLUCENT,
                        ColorUtils.rgba(23, 23, 23, (int) (255 * 0.5))));


    }


    float max = 0;

    private void drawComponents(MatrixStack stack, float mouseX, float mouseY) {
        float animationValue = (float) DropDown.getAnimation().getValue() * DropDown.scale;

        float halfAnimationValueRest = (1 - animationValue) / 2f;
        float height = getHeight();
        float testX = getX() + (getWidth() * halfAnimationValueRest);
        float testY = getY() + 25 + (height * halfAnimationValueRest);
        float testW = getWidth() * animationValue;
        float testH = height * animationValue - 33;

        testX = testX * animationValue + ((Minecraft.getInstance().getMainWindow().getScaledWidth() - testW) *
                halfAnimationValueRest);

        Scissor.push();
        Scissor.setFromComponentCoordinates(testX, testY, testW, testH);
        float offset = -1;
        float header = 25;

        if (max > height - header - 10) {
            scroll = MathHelper.clamp(scroll, -max + height - header - 10, 0);
            animatedScrool = MathHelper.clamp(animatedScrool, -max + height - header - 10, 0);
        }
        else {
            scroll = 0;
            animatedScrool = 0;
        }

        for (ModuleComponent component : modules) {
            component.setX(getX() + 5);
            component.setY(getY() + header + offset + 6 + animatedScrool);
            component.setWidth(getWidth() - 10);
            component.setHeight(20);
            component.animation.update();
            if (component.animation.getValue() > 0) {
                float componentOffset = 0;
                for (Component component2 : component.getComponents()) {
                    if (component2.isVisible())
                        componentOffset += component2.getHeight();
                }
                componentOffset *= component.animation.getValue();
                component.setHeight(component.getHeight() + componentOffset);
            }
            component.render(stack, mouseX, mouseY);
            offset += component.getHeight() + 3.5f;
        }
        max = offset;

        Scissor.unset();
        Scissor.pop();

    }

    @Override
    public void mouseClick(float mouseX, float mouseY, int button) {
        for (ModuleComponent component : modules) {
            component.mouseClick(mouseX, mouseY, button);
        }
    }

    @Override
    public void keyPressed(int key, int scanCode, int modifiers) {
        for (ModuleComponent component : modules) {
            component.keyPressed(key, scanCode, modifiers);
        }
    }

    @Override
    public void charTyped(char codePoint, int modifiers) {
        for (ModuleComponent component : modules) {
            component.charTyped(codePoint, modifiers);
        }
    }

    @Override
    public void mouseRelease(float mouseX, float mouseY, int button) {
        for (ModuleComponent component : modules) {
            component.mouseRelease(mouseX, mouseY, button);
        }
    }

}
