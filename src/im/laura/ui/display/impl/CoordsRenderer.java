package im.laura.ui.display.impl;

import im.laura.ui.display.ElementRenderer;
import im.laura.events.EventDisplay;
import im.laura.utils.player.MoveUtils;
import im.laura.utils.render.ColorUtils;
import im.laura.utils.render.font.Fonts;
import lombok.RequiredArgsConstructor;
import im.laura.utils.render.DisplayUtils;

@RequiredArgsConstructor
public class CoordsRenderer implements ElementRenderer {


    @Override
    public void render(EventDisplay eventDisplay) {
        float offset = 3;
        float fontSize = 7;
        float fontHeight = Fonts.sfui.getHeight(fontSize);

        float posX = offset;
        float posY = window.getScaledHeight() - offset - fontHeight;

        float stringWidth = Fonts.sfui.getWidth("XYZ: ", fontSize);

        Fonts.sfui.drawText(eventDisplay.getMatrixStack(), "XYZ: ", posX, posY, -1, fontSize, 0.05f);

        Fonts.sfui.drawText(eventDisplay.getMatrixStack(), (int) mc.player.getPosX() + ", "
                + (int) mc.player.getPosY() + ", " + (int) mc.player.getPosZ(), posX + stringWidth, posY, ColorUtils.rgb(158, 255, 185), fontSize, 0.05f);

        posY -= 12;
        stringWidth = Fonts.sfui.getWidth("BPS: ", fontSize);

        Fonts.sfui.drawText(eventDisplay.getMatrixStack(), "BPS: ", posX, posY, -1, fontSize, 0.05f);

        Fonts.sfui.drawText(eventDisplay.getMatrixStack(), String.format("%.2f", Math.hypot(mc.player.prevPosX - mc.player.getPosX(), mc.player.prevPosZ - mc.player.getPosZ()) * 20), posX + stringWidth, posY, ColorUtils.rgb(158, 255, 185), fontSize, 0.05f);

    }
}