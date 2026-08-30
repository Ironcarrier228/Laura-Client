package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventMotion;
import im.laura.events.EventUpdate;
import im.laura.events.PlaceObsidianEvent;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.MathUtil;
import im.laura.utils.math.StopWatch;
import im.laura.utils.player.InventoryUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EnderCrystalEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

import java.util.List;
import java.util.stream.Collectors;

@FunctionRegister(name = "AutoExplosion", type = Category.Combat)
public class AutoExplosion extends Function {

    private final BooleanSetting safeYourSelf = new BooleanSetting("Не взрывать себя", true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Авто смена", true);
    private final BooleanSetting rotate = new BooleanSetting("Ротация", true);
    private final SliderSetting minDamage = new SliderSetting("Мин урон", 0f, 0f, 10f, 1f);
    private final SliderSetting attackDelay = new SliderSetting("Задержка атаки", 250f, 0f, 1000f, 50f);

    private Entity crystalEntity = null;
    private BlockPos obsidianPos = null;
    private int oldCurrentSlot = -1;
    private Vector2f rotationVector = new Vector2f(0, 0);
    private final StopWatch attackTimer = new StopWatch();
    private int bestSlot = -1;
    private int oldSlot = -1;

    public AutoExplosion() {
        addSettings(safeYourSelf, autoSwitch, rotate, minDamage, attackDelay);
    }

    @Subscribe
    public void onObsidianPlace(PlaceObsidianEvent e) {
        BlockPos obsidianPos = e.getPos();

        boolean isOffHand = mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL;

        int slotInInventory = InventoryUtil.getInstance().getSlotInInventoryOrHotbar(Items.END_CRYSTAL, false);
        int slotInHotBar = InventoryUtil.getInstance().getSlotInInventoryOrHotbar(Items.END_CRYSTAL, true);
        bestSlot = InventoryUtil.getInstance().findBestSlotInHotBar();
        boolean slotNotNull = mc.player.inventory.getStackInSlot(bestSlot).getItem() != Items.AIR;

        if (isOffHand) {
            if (obsidianPos != null) {
                setAndUseCrystal(bestSlot, obsidianPos);
                this.obsidianPos = obsidianPos;
            }
        }

        if (slotInHotBar == -1 && slotInInventory != -1 && bestSlot != -1) {
            InventoryUtil.moveItem(slotInInventory, bestSlot + 36, slotNotNull);
            if (slotNotNull && oldSlot == -1) {
                oldSlot = slotInInventory;
            }
            if (obsidianPos != null) {
                oldCurrentSlot = mc.player.inventory.currentItem;
                setAndUseCrystal(bestSlot, obsidianPos);
                mc.player.inventory.currentItem = oldCurrentSlot;
                this.obsidianPos = obsidianPos;
            }
            mc.playerController.windowClick(0, oldSlot, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClickFixed(0, oldSlot, 0, ClickType.PICKUP, mc.player, 250);
        } else if (slotInHotBar != -1) {
            if (obsidianPos != null) {
                oldCurrentSlot = mc.player.inventory.currentItem;
                setAndUseCrystal(slotInHotBar, obsidianPos);
                mc.player.inventory.currentItem = oldCurrentSlot;
                this.obsidianPos = obsidianPos;
            }
        }
    }

    @Subscribe
    private void onUpdate(EventUpdate e) {
        if (obsidianPos != null) {
            List<Entity> crystals = findEnderCrystals(obsidianPos);
            for (Entity crystal : crystals) {
                if (isValid(crystal)) {
                    attackCrystal(crystal);
                    break;
                }
            }
        }

        if (crystalEntity != null && !crystalEntity.isAlive()) {
            reset();
        }
    }

    @Subscribe
    private void onMotion(EventMotion e) {
        if (!rotate.get() || !isValid(crystalEntity)) return;

        rotationVector = MathUtil.rotationToEntity(crystalEntity);
        e.setYaw(rotationVector.x);
        e.setPitch(rotationVector.y);
        mc.player.renderYawOffset = rotationVector.x;
        mc.player.rotationYawHead = rotationVector.x;
        mc.player.rotationPitchHead = rotationVector.y;
    }

    @Override
    public boolean onDisable() {
        reset();
        super.onDisable();
        return false;
    }

    private void attackCrystal(Entity entity) {
        if (!isValid(entity)) return;
        if (mc.player.getCooledAttackStrength(1.0f) < 1.0f) return;
        if (!attackTimer.isReached(attackDelay.get().longValue())) return;

        attackTimer.reset();
        mc.playerController.attackEntity(mc.player, entity);
        mc.player.swingArm(Hand.MAIN_HAND);
        crystalEntity = entity;
    }


    private void setAndUseCrystal(int slot, BlockPos pos) {
        boolean isOffHand = mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL;

        Vector3d center = new Vector3d(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
        if (!isOffHand)
            mc.player.inventory.currentItem = slot;


        Hand hand = isOffHand ? Hand.OFF_HAND : Hand.MAIN_HAND;

        if (mc.playerController.processRightClickBlock(
                mc.player, mc.world, hand,
                new BlockRayTraceResult(center, Direction.UP, pos, false)) == ActionResultType.SUCCESS) {
            mc.player.swingArm(Hand.MAIN_HAND);
        }
    }

    private boolean isValid(Entity base) {
        if (base == null) {
            return false;
        }
        if (obsidianPos == null) {
            return false;
        }
        if (safeYourSelf.get() && mc.player.getPosY() > obsidianPos.getY()) {
            return false;
        }
        return isCorrectDistance();
    }

    private boolean isCorrectDistance() {
        if (obsidianPos == null) {
            return false;
        }
        return mc.player.getPositionVec().distanceTo(
                new Vector3d(obsidianPos.getX(),
                        obsidianPos.getY(),
                        obsidianPos.getZ())) <= mc.playerController.getBlockReachDistance();
    }

    public List<Entity> findEnderCrystals(BlockPos position) {
        return mc.world.getEntitiesWithinAABBExcludingEntity(null,
                        new AxisAlignedBB(
                                position.getX(),
                                position.getY(),
                                position.getZ(),
                                position.getX() + 1.0,
                                position.getY() + 2.0,
                                position.getZ() + 1.0))
                .stream()
                .filter(entity -> entity instanceof EnderCrystalEntity)
                .collect(Collectors.toList());
    }

    private void reset() {
        crystalEntity = null;
        obsidianPos = null;
        rotationVector = new Vector2f(mc.player.rotationYaw, mc.player.rotationPitch);
        oldCurrentSlot = -1;
        bestSlot = -1;
    }
}
