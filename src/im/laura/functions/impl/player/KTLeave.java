package im.laura.functions.impl.player;

import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;

@FunctionRegister(name = "KTLeave", type = Category.Player)
public class KTLeave extends Function {

    @Override
    public boolean onEnable() {
        super.onEnable();
       // mc.playerController.processRightClickBlock(mc.player, mc.world, Hand.MAIN_HAND, (BlockRayTraceResult) mc.player.pick(4.5f, 1, false));

        this.setState(false, false);
        return false;
    }
}
