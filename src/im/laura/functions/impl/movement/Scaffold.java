package im.laura.functions.impl.movement;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventMotion;
import im.laura.events.EventPostMotion;
import im.laura.events.EventUpdate;
import im.laura.events.TickEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;

@SuppressWarnings("UnstableApiUsage")
@FunctionRegister(name = "Scaffold", type = Category.Movement)
public class Scaffold extends Function {

    private final ModeSetting switchMode = new ModeSetting("Свич", "Silent", "Normal", "Silent", "None");
    private final SliderSetting expand = new SliderSetting("Расширение", 1, 1, 5, 0.1f);
    private final BooleanSetting rotate = new BooleanSetting("Ротация", true);
    private final BooleanSetting tower = new BooleanSetting("Башня", false);

    private int blockSlot = -1;
    private int savedSlot = -1;
    private boolean placing;
    private boolean switched;
    private float savedYaw, savedPitch;

    public Scaffold() {
        addSettings(switchMode, expand, rotate, tower);
    }

    @Override
    public boolean onEnable() {
        blockSlot = -1;
        savedSlot = -1;
        placing = false;
        switched = false;
        return super.onEnable();
    }

    @Override
    public boolean onDisable() {
        if (mc.player != null && switched && savedSlot != -1) {
            mc.player.inventory.currentItem = savedSlot;
            mc.player.connection.sendPacket(new CHeldItemChangePacket(savedSlot));
        }
        placing = false;
        switched = false;
        savedSlot = -1;
        return super.onDisable();
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (fullNullCheck()) return;

        blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        if (tower.get() && mc.gameSettings.keyBindJump.isKeyDown() && mc.player.isOnGround()) {
            BlockPos feet = new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 1, mc.player.getPosZ());
            if (getBlock(feet) != Blocks.AIR) {
                mc.player.setMotion(mc.player.getMotion().x, 0.42, mc.player.getMotion().z);
            }
        }
    }

    @Subscribe
    public void onMotion(EventMotion event) {
        if (fullNullCheck()) return;

        if (blockSlot == -1) {
            blockSlot = findBlockSlot();
        }
        if (blockSlot == -1) return;

        BlockPos target = findPlaceable();
        if (target == null) return;

        placing = true;

        if (rotate.get()) {
            savedYaw = mc.player.rotationYaw;
            savedPitch = mc.player.rotationPitch;
            float[] rot = getRotations(target);
            event.setYaw(rot[0]);
            event.setPitch(rot[1]);
            mc.player.rotationYawHead = rot[0];
            mc.player.renderYawOffset = rot[0];
        }

        doSwitch();

        Direction face = findFace(target);
        if (face == null) return;

        BlockPos neighbor = target.offset(face);
        Vector3d hitVec = new Vector3d(
                target.getX() + 0.5 + face.getOpposite().getXOffset() * 0.5,
                target.getY() + 0.5 + face.getOpposite().getYOffset() * 0.5,
                target.getZ() + 0.5 + face.getOpposite().getZOffset() * 0.5
        );

        mc.player.connection.sendPacket(new CPlayerTryUseItemOnBlockPacket(
                Hand.MAIN_HAND,
                new BlockRayTraceResult(hitVec, face.getOpposite(), neighbor, false)
        ));
        mc.player.connection.sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
        mc.player.swingArm(Hand.MAIN_HAND);
    }

    @Subscribe
    public void onPostMotion(EventPostMotion event) {
        if (!placing) return;

        if (switched && savedSlot != -1) {
            mc.player.inventory.currentItem = savedSlot;
            mc.player.connection.sendPacket(new CHeldItemChangePacket(savedSlot));
            switched = false;
            savedSlot = -1;
        }

        if (rotate.get()) {
            mc.player.rotationYaw = savedYaw;
            mc.player.rotationPitch = savedPitch;
            mc.player.rotationYawHead = savedYaw;
            mc.player.renderYawOffset = savedYaw;
        }

        placing = false;
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        if (fullNullCheck()) return;
        if (blockSlot == -1) return;

        ItemStack stack = mc.player.inventory.getStackInSlot(blockSlot);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            blockSlot = findBlockSlot();
        }
    }

    private Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    private boolean isAir(BlockPos pos) {
        return getBlock(pos) == Blocks.AIR;
    }

    private boolean isSolid(BlockPos pos) {
        Block block = getBlock(pos);
        return block != Blocks.AIR && block != Blocks.WATER && block != Blocks.LAVA;
    }

    private BlockPos findPlaceable() {
        double px = mc.player.getPosX();
        double py = mc.player.getPosY() - 1;
        double pz = mc.player.getPosZ();
        double exp = expand.get();

        BlockPos feet = new BlockPos(px, py, pz);
        if (isAir(feet) && findFace(feet) != null) {
            return feet;
        }

        for (double x = -exp; x <= exp; x += 0.5) {
            for (double z = -exp; z <= exp; z += 0.5) {
                if (x == 0 && z == 0) continue;
                BlockPos pos = new BlockPos(px + x, py, pz + z);
                if (isAir(pos) && findFace(pos) != null) {
                    return pos;
                }
            }
        }

        return null;
    }

    private Direction findFace(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (isSolid(pos.offset(dir))) {
                return dir;
            }
        }
        return null;
    }

    private int findBlockSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private void doSwitch() {
        if (switchMode.is("None")) return;
        if (blockSlot == -1) return;
        if (blockSlot == mc.player.inventory.currentItem) return;

        savedSlot = mc.player.inventory.currentItem;
        mc.player.inventory.currentItem = blockSlot;
        mc.player.connection.sendPacket(new CHeldItemChangePacket(blockSlot));
        switched = true;
    }

    private float[] getRotations(BlockPos target) {
        double diffX = target.getX() + 0.5 - mc.player.getPosX();
        double diffY = target.getY() + 0.5 - (mc.player.getPosY() + mc.player.getEyeHeight());
        double diffZ = target.getZ() + 0.5 - mc.player.getPosZ();
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90f, 90f)};
    }
}