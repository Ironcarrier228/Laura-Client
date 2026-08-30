package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;

@FunctionRegister(name = "Phase", type = Category.Movement)
public class Phase extends Function {

    @Subscribe
    private void onUpdate(EventUpdate e) {
        if (!collisionPredict()) {
            if (mc.gameSettings.keyBindJump.pressed) {
                mc.player.setOnGround(true);
            }
        }
    }

    public boolean collisionPredict() {
        boolean prevCollision = mc.world
                .getCollisionShapes(mc.player, mc.player.getBoundingBox().shrink(0.0625D)).toList().isEmpty();

        return mc.world.getCollisionShapes(mc.player, mc.player.getBoundingBox().shrink(0.0625D))
                .toList().isEmpty() && prevCollision;
    }
}
