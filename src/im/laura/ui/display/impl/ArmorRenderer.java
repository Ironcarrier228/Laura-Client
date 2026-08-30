package im.laura.ui.display.impl;

import com.mojang.blaze3d.matrix.MatrixStack;
import im.laura.ui.display.ElementRenderer;
import im.laura.events.EventDisplay;
import im.laura.utils.drag.Dragging;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.DisplayUtils;
import im.laura.utils.render.font.Fonts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class ArmorRenderer implements ElementRenderer {

    final Dragging dragging;
    float targetWidth = 100;
    float currentWidth = 100;

    @Override
    public void render(EventDisplay eventDisplay) {
        MatrixStack ms = eventDisplay.getMatrixStack();
        Minecraft mc = Minecraft.getInstance();

        float height = 79;

        float iconSizeX = 10;

        float x = dragging.getX();
        float y = dragging.getY();

        float padding = 5;

        boolean hasArmor = false;
        for (ItemStack itemStack : mc.player.getArmorInventoryList()) {
            if (!itemStack.isEmpty()) {
                hasArmor = true;
                break;
            }
        }

        targetWidth = hasArmor ? 100 : 80;

        currentWidth = lerp(currentWidth, targetWidth, 0.1f);

        DisplayUtils.drawRoundedRect(x, y, currentWidth, height + 1, 7, ColorUtils.setAlpha(ColorUtils.rgb(10, 15, 13), 90));
        DisplayUtils.drawRoundedRect(x, y, currentWidth, height + 1, 7, ColorUtils.rgba(10, 15, 13, 90));
        DisplayUtils.drawShadow(x, y, currentWidth, height + 1, 7, ColorUtils.rgba(10, 15, 13, 15));

        float imagePosX = x + currentWidth - iconSizeX - padding;
        Fonts.icons2.drawText(ms, "N", imagePosX, y + 7f, ColorUtils.rgb(255, 255, 255), 6.5f);

        Fonts.sfui.drawText(ms, "Armor", x + 7, y + 7f, -1, 6.5f);

        int posX = (int) x + 3;
        int posY = (int) y + 63;

        for (ItemStack itemStack : mc.player.getArmorInventoryList()) {
            if (!itemStack.isEmpty()) {
                mc.getItemRenderer().renderItemAndEffectIntoGUI(itemStack, posX, posY);

                float damagePercentage = (itemStack.getDamage() * 100.0f) / itemStack.getMaxDamage();
                int red = (int) (255 * (damagePercentage / 100));
                int green = 255 - red;

                int barHeight2 = Math.round((45 * (100 - damagePercentage)) / 100);

                DisplayUtils.drawRoundedRect(posX + 20, posY + 6, 45, 3, 1, ColorUtils.rgb(15, 15, 15));
                DisplayUtils.drawRoundedRect(posX + 20, posY + 6, barHeight2, 3, 1, ColorUtils.rgb(red, green, 0));

                Fonts.sfui.drawCenteredText(ms, (100 - (itemStack.getDamage() * 100) / itemStack.getMaxDamage()) + "%", posX + currentWidth - 20, posY + 4, -1, 6.5f);
            } else {
                Fonts.sfui.drawCenteredText(ms, "-", posX + 8, posY + 2.5f , ColorUtils.rgba(135, 136, 148, 90), 9);

                DisplayUtils.drawRoundedRect(posX + 20, posY + 6, 45, 3, 1, ColorUtils.rgba(135, 136, 148,90));
            }

            posY -= 16;
        }

        dragging.setWidth(currentWidth);
        dragging.setHeight(height);
    }

    private float lerp(float start, float end, float alpha) {
        return start + alpha * (end - start);
    }
}