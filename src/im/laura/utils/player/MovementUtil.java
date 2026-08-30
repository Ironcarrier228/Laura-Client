package im.laura.utils.player;

import im.laura.utils.client.IMinecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;

public class MovementUtil implements IMinecraft {

    public static boolean isMoving() {
        return mc.player != null && (mc.player.getPosX() != mc.player.lastTickPosX || mc.player.getPosZ() != mc.player.lastTickPosZ);
    }

    public static boolean isMoving(ClientPlayerEntity player) {
        return player != null && (player.getPosX() != player.lastTickPosX || player.getPosZ() != player.lastTickPosZ);
    }
}
