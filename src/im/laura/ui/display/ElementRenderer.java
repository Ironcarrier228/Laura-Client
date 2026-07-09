package im.laura.ui.display;

import im.laura.events.EventDisplay;
import im.laura.utils.client.IMinecraft;

public interface ElementRenderer extends IMinecraft {
    void render(EventDisplay eventDisplay);
}
