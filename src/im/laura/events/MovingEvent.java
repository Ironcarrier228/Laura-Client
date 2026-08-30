package im.laura.events;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

@Getter
@Setter
public class MovingEvent {
    private Vector3d from, to, motion;
    private boolean toGround;
    private AxisAlignedBB aabbFrom;
    private boolean ignoreHorizontal, ignoreVertical, collidedHorizontal, collidedVertical;

    public MovingEvent(Vector3d from, Vector3d to, Vector3d motion, boolean toGround,
            boolean isCollidedHorizontal, boolean isCollidedVertical, AxisAlignedBB aabbFrom) {
        this.from = from;
        this.to = to;
        this.motion = motion;
        this.toGround = toGround;
        this.collidedHorizontal = isCollidedHorizontal;
        this.collidedVertical = isCollidedVertical;
        this.aabbFrom = aabbFrom;
    }

    public double getX() {
        return motion.x;
    }

    public double getZ() {
        return motion.z;
    }

    public void setX(double x) {
        this.motion = new Vector3d(x, motion.y, motion.z);
    }

    public void setZ(double z) {
        this.motion = new Vector3d(motion.x, motion.y, z);
    }
}
