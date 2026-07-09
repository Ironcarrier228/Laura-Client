package im.laura.ui.display.impl;

import com.mojang.blaze3d.matrix.MatrixStack;
import im.laura.events.EventDisplay;
import im.laura.ui.display.ElementRenderer;
import im.laura.utils.drag.Dragging;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.DisplayUtils;
import im.laura.utils.render.Scissor;
import im.laura.utils.render.font.Fonts;
import im.laura.utils.text.GradientUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectUtils;
import net.minecraft.util.text.ITextComponent;

@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class PotionRenderer implements ElementRenderer {


    final Dragging dragging;

    float iconSizeX = 10;

    float width;
    float height;

    @Override
    public void render(EventDisplay eventDisplay) {
        MatrixStack ms = eventDisplay.getMatrixStack();

        float posX = dragging.getX();
        float posY = dragging.getY();
        float fontSize = 6.5f;
        float padding = 5;

        ITextComponent name = GradientUtil.gradient("Potions");
        String namemod = "Potions";

        float finalPosY = posY;
        drawStyledRect(posX, finalPosY, width, height, 7);

        float imagePosX = posX + width - iconSizeX - padding;
        Fonts.icons2.drawText(ms, "E", imagePosX + 2f, posY + 7f, ColorUtils.rgb(255, 255, 255), fontSize);

        Scissor.push();
        Scissor.setFromComponentCoordinates(posX, posY, width, height);
        Fonts.sfui.drawText(ms, namemod, posX + padding, posY + padding + 1, ColorUtils.rgb(255, 255, 255), fontSize);

        posY += fontSize + padding * 2;

        float maxWidth = Fonts.sfMedium.getWidth(name, fontSize) + padding * 2;
        float localHeight = fontSize + padding * 2;

        for (EffectInstance ef : mc.player.getActivePotionEffects()) {
            int amp = ef.getAmplifier();

            String ampStr = "";

            if (amp >= 1 && amp <= 9) {
                ampStr = " " + I18n.format("enchantment.level." + (amp + 1));
            }
            String nameText = I18n.format(ef.getEffectName()) + ampStr;
            float nameWidth = Fonts.sfMedium.getWidth(nameText, fontSize);

            String bindText = EffectUtils.getPotionDurationString(ef, 1);
            float bindWidth = Fonts.sfMedium.getWidth(bindText, fontSize);

            float localWidth = nameWidth + bindWidth + padding * 3;

            Fonts.sfui.drawText(ms, nameText, posX + padding, posY 
                    + 1, ColorUtils.rgba(255, 255, 255, 255), 
                    fontSize - 0.5f);
            Fonts.sfui.drawText(ms, bindText, posX + width - padding - bindWidth + 3, posY 
                    + 1, ColorUtils.rgba(255, 255,
                    255, 255), fontSize - 0.5f);

            if (localWidth > maxWidth) {
                maxWidth = localWidth;
            }
           

            posY += (fontSize + padding);
            localHeight += (fontSize + padding);
        }

        width = Math.max(maxWidth, 80);
        height = localHeight + 2.5f;
        dragging.setWidth(width);
        dragging.setHeight(height);

        Scissor.unset();
        Scissor.pop();
    }

    private void drawStyledRect(float x,
            float y,
            float width,
            float height,
            float radius) {

        DisplayUtils.drawRoundedRect(x - 0.5f, y - 0.5f, width + 1, height + 1, radius + 0.5f,
                ColorUtils.setAlpha(ColorUtils.rgb(10, 15, 13), 90));
        DisplayUtils.drawRoundedRect(x, y, width, height, radius, ColorUtils.rgba(10, 15, 13, 90));
        DisplayUtils.drawShadow(x + 5, y + 5, width, height, 7, ColorUtils.rgba(10, 15, 13, 15));

    }
}
