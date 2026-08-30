package im.laura.functions.impl.misc;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventPacket;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import net.minecraft.network.play.client.CCloseWindowPacket;
import im.laura.functions.api.FunctionRegister;

@FunctionRegister(name = "xCarry", type = Category.Misc)
public class xCarry extends Function {

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null) return;

        if (e.getPacket() instanceof CCloseWindowPacket) {
            e.cancel();
        }
    }
}
