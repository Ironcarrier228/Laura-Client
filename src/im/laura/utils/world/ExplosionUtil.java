package im.laura.utils.world;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;

public class ExplosionUtil {

    public static double getAutoCrystalDamage(Vector3d explosionPos, Entity entity, double entityScale, boolean ignoreArmor) {
        double dist = entity.getDistanceSq(explosionPos.x, explosionPos.y, explosionPos.z);
        double exposure = getExposure(explosionPos, entity);
        double impact = (1.0 - dist / 144.0) * exposure;
        double damage = impact * 6.0 * entityScale;
        damage = applyArmor(damage, ignoreArmor);
        damage = applyEnchantments(damage, entity, ignoreArmor);
        return Math.max(damage, 0.0);
    }

    private static double getExposure(Vector3d source, Entity entity) {
        double d = 1.0 / ((entity.getWidth() * 2.0 + 1.0) * entity.getHeight() * 2.0 + 1.0);
        double d1 = 1.0 / d;
        int i = (int) (d1 * 30.0);
        double d2 = 0.0;
        for (int j = 0; j <= i; ++j) {
            for (int k = 0; k <= i; ++k) {
                if (rayTrace(source, entity, j, k, i)) {
                    d2 += 1.0;
                }
            }
        }
        return d2 / d1;
    }

    private static boolean rayTrace(Vector3d source, Entity entity, int x, int y, int steps) {
        double d = 1.0 / (steps - 1);
        double d1 = entity.getPosX() - source.x;
        double d2 = entity.getPosY() - source.y;
        double d3 = entity.getPosZ() - source.z;
        Vector3d vec3d = new Vector3d(source.x + d1 * x * d, source.y + d2 * y * d, source.z + d3 * y * d);
        RayTraceContext context = new RayTraceContext(vec3d, source.add(d1, d2, d3), RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, entity);
        BlockRayTraceResult result = entity.world.rayTraceBlocks(context);
        return result.getType() == RayTraceResult.Type.MISS;
    }

    private static double applyArmor(double damage, boolean ignoreArmor) {
        if (ignoreArmor) return damage;
        return damage * 0.75;
    }

    private static double applyEnchantments(double damage, Entity entity, boolean ignoreArmor) {
        return damage;
    }
}
