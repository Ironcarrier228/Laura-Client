/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.fixes;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import im.laura.utils.client.IMinecraft;
import via.ViaLoadingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RayTraceResult;

public class AttackOrder
implements IMinecraft {
    public static void sendConditionalSwing(RayTraceResult ray, Hand enumHand) {
        if (ray != null && ray.getType() != RayTraceResult.Type.ENTITY) {
           mc.player.swingArm(enumHand);
        }
    }

    public static void sendFixedAttack(PlayerEntity entityIn, Entity target, Hand enumHand) {
        boolean isOldVersion = false;
        
        if (ViaLoadingBase.getInstance() != null && 
            ViaLoadingBase.getInstance().getTargetVersion() != null) {
            var targetVersion = ViaLoadingBase.getInstance().getTargetVersion();
            isOldVersion = targetVersion.getIndex() <= 1; // 1.8 имеет индекс 1
        }
        
        if (isOldVersion) {
           mc.player.swingArm(enumHand);
           mc.playerController.attackEntity(entityIn, target);
        } else {
           mc.playerController.attackEntity(entityIn, target);
           mc.player.swingArm(enumHand);
        }
    }
}

