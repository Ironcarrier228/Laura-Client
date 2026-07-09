package im.laura.ui.display;

import im.laura.events.EventUpdate;
import im.laura.utils.client.IMinecraft;

public interface ElementUpdater extends IMinecraft {

    void update(EventUpdate e);
}
