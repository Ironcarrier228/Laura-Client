package im.laura.ui.display.impl;

import com.mojang.blaze3d.matrix.MatrixStack;

import im.laura.Laura;
import im.laura.events.EventDisplay;
import im.laura.ui.display.ElementRenderer;
import im.laura.ui.styles.Style;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.DisplayUtils;
import im.laura.utils.render.font.Fonts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.ResourceLocation;
import im.laura.utils.client.ClientUtil;

@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class WatermarkRenderer implements ElementRenderer {

        @Override
        public void render(EventDisplay eventDisplay) {
                MatrixStack ms = eventDisplay.getMatrixStack();

                Style style = Laura.getInstance().getStyleManager().getCurrentStyle();

                float posY = 4;
                float fontSize = 6.5f;
                float iconSizeX = 10;
                float iconSizeY = 10;
                float spacing = 0.02f;
                float additionalSpacing = 5; // Дополнительное расстояние между текстами

                int windowWidth = mc.getMainWindow().getScaledWidth();

                // Тут типа ставьте свой ник там или че хотите
                String username = "Ironcarrier.dev";
                String SERVER = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "Singleplayer";
                String FPS = mc.debugFPS + "fps";

                float usernameWidth = Fonts.sfui.getWidth(username, fontSize);
                float serverWidth = Fonts.sfui.getWidth(SERVER, fontSize);
                float fpsWidth = Fonts.sfui.getWidth(FPS, fontSize);

                float iconSpacing = 2.0f;

                float usernameIconWidth = Fonts.icons2.getWidth("A", fontSize);
                float serverIconWidth = Fonts.icons2.getWidth("B", fontSize);
                float fpsIconWidth = Fonts.icons2.getWidth("C", fontSize);

                float totalTextWidth = usernameWidth + usernameIconWidth + serverWidth + serverIconWidth + fpsWidth
                                + fpsIconWidth + 3 * spacing + 3 * additionalSpacing;
                float rectWidth = 15 + totalTextWidth + iconSizeX + 5;

                float posX = (windowWidth - rectWidth) / 2;

                final ResourceLocation logo = new ResourceLocation("expensive/images/hud/logo.png");

                float rectWidthIco = 15;
                float rectHeightIco = 15;

                float centerX = posX + rectWidthIco / 2;
                float centerY = posY + 5 + rectHeightIco / 2;

                float finalPosXIcon = centerX - iconSizeX / 2;
                float finalPosYIcon = centerY - iconSizeY / 2;

                // Отображение прямоугольника для иконки    
                // Для отображения картинки используйте DisplayUtils.drawImage(logo, finalPosXIcon, finalPosYIcon, iconSizeX, iconSizeY, style.getSecondColor().getRGB());
                // Так-как сейчас иконка отображается шрифтом

                drawStyledRect(posX, posY + 5, rectWidthIco, rectHeightIco, 3);

                Fonts.icons2.drawText(ms, "A", finalPosXIcon + 0.5f, finalPosYIcon + 1.25f,
                                style.getSecondColor().getRGB(), fontSize + 2);                            

                // Отображение прямоугольника для информации
                drawStyledRect(posX + rectWidthIco + 2.5f, posY + 5, rectWidth - rectWidthIco - 5, rectHeightIco, 3);

                float textPosX = posX + rectWidthIco + 7.5f;
                float textPosY = posY + 9.5f;

                // Отображение текста с иконками перед основным текстом
                Fonts.icons2.drawText(ms, "U", textPosX, textPosY + 0.5f, style.getSecondColor().getRGB(), fontSize);
                textPosX += usernameIconWidth + iconSpacing;
                Fonts.sfui.drawText(ms, username, textPosX, textPosY, ColorUtils.rgb(255, 255, 255), fontSize);

                textPosX += usernameWidth + spacing + additionalSpacing;
                Fonts.icons2.drawText(ms, "F", textPosX, textPosY + 0.5f, style.getSecondColor().getRGB(), fontSize);
                textPosX += serverIconWidth + iconSpacing;
                Fonts.sfui.drawText(ms, SERVER, textPosX, textPosY, ColorUtils.rgb(255, 255, 255), fontSize);

                textPosX += serverWidth + spacing + additionalSpacing;
                Fonts.icons2.drawText(ms, "S", textPosX, textPosY + 0.5f, style.getSecondColor().getRGB(), fontSize);
                textPosX += fpsIconWidth + iconSpacing;
                Fonts.sfui.drawText(ms, FPS, textPosX, textPosY, ColorUtils.rgb(255, 255, 255), fontSize);
        }

        private void drawStyledRect(float x, float y, float width, float height, float radius) {
                DisplayUtils.drawRoundedRect(x - 0.5f, y - 0.5f, width + 1, height + 1, radius + 0.5f,
                                ColorUtils.setAlpha(ColorUtils.rgb(10, 15, 13), 90));
                DisplayUtils.drawRoundedRect(x, y, width, height, radius, ColorUtils.rgba(10, 15, 13, 90));
                DisplayUtils.drawShadow(x + 5, y + 5, width, height, 5, ColorUtils.rgba(10, 15, 13, 15));
        }
}