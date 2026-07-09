package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.MathUtil;
import im.laura.utils.math.StopWatch;
import im.laura.utils.player.AttackUtil;
import im.laura.utils.player.InventoryUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector2f;

@FunctionRegister(name = "TriggerBot", type = Category.Combat)
@SuppressWarnings({"unused", "Beta"})
public class TriggerBot extends Function {

    private final BooleanSetting players = new BooleanSetting("Игроки", true);
    private final BooleanSetting mobs = new BooleanSetting("Мобы", false);
    private final BooleanSetting animals = new BooleanSetting("Животные", false);
    private final BooleanSetting friends = new BooleanSetting("Друзья", false);
    private final BooleanSetting onlyCrit = new BooleanSetting("Только криты", false);
    private final BooleanSetting shieldBreak = new BooleanSetting("Ломать щит", true);
    private final BooleanSetting rotate = new BooleanSetting("Ротация", false);
    private final SliderSetting delay = new SliderSetting("Задержка", 250f, 0f, 1000f, 50f);
    private final SliderSetting range = new SliderSetting("Дистанция", 3f, 1f, 6f, 0.1f);

    private final StopWatch stopWatch = new StopWatch();

    public TriggerBot() {
        addSettings(players, mobs, animals, friends, onlyCrit, shieldBreak, rotate, delay, range);
    }

    @Subscribe
    public void onUpdate(EventUpdate e) {
        Entity entity = getValidEntity();
        if (entity == null || mc.player == null) return;

        if (shouldAttack()) {
            if (rotate.get()) {
                Vector2f rotation = MathUtil.rotationToEntity(entity);
                mc.player.rotationYaw = rotation.x;
                mc.player.rotationPitch = rotation.y;
            }
            stopWatch.setLastMS(delay.get().longValue());
            attackEntity(entity);
        }
    }

    private boolean shouldAttack() {
        if (!stopWatch.isReached(delay.get().longValue())) return false;
        if (mc.player.getCooldownTracker().hasCooldown(mc.player.getHeldItemMainhand().getItem())) return false;
        
        if (onlyCrit.get()) {
            return AttackUtil.isPlayerFalling(true, false, false);
        }
        return true;
    }

    private void attackEntity(Entity entity) {
        boolean shouldStopSprinting = false;
        if (onlyCrit.get() && CEntityActionPacket.lastUpdatedSprint) {
            mc.player.connection.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
            shouldStopSprinting = true;
        }

        mc.playerController.attackEntity(mc.player, entity);
        mc.player.swingArm(Hand.MAIN_HAND);
        
        if (shieldBreak.get() && entity instanceof PlayerEntity player && player.isBlocking()) {
            breakShieldPlayer(entity);
        }

        if (shouldStopSprinting) {
            mc.player.connection.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
        }
    }

    private Entity getValidEntity() {
        if (mc.objectMouseOver == null || mc.objectMouseOver.getType() != RayTraceResult.Type.ENTITY) return null;
        
        Entity entity = ((EntityRayTraceResult) mc.objectMouseOver).getEntity();
        if (!(entity instanceof LivingEntity living)) return null;
        if (mc.player.getDistance(entity) > range.get()) return null;
        
        return checkEntity(living) ? entity : null;
    }

    public static void breakShieldPlayer(Entity entity) {
        if (((LivingEntity) entity).isBlocking()) {
            int invSlot = InventoryUtil.getInstance().getAxeInInventory(false);
            int hotBarSlot = InventoryUtil.getInstance().getAxeInInventory(true);

            if (hotBarSlot == -1 && invSlot != -1) {
                int bestSlot = InventoryUtil.getInstance().findBestSlotInHotBar();
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);

                mc.player.connection.sendPacket(new CHeldItemChangePacket(bestSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));

                mc.playerController.windowClick(0, bestSlot + 36, 0, ClickType.PICKUP, mc.player);
                mc.playerController.windowClick(0, invSlot, 0, ClickType.PICKUP, mc.player);
            }

            if (hotBarSlot != -1) {
                mc.player.connection.sendPacket(new CHeldItemChangePacket(hotBarSlot));
                mc.playerController.attackEntity(mc.player, entity);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            }
        }
    }

    private boolean checkEntity(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive()) return false;
        
        AttackUtil entitySelector = new AttackUtil();

        if (players.get()) entitySelector.apply(AttackUtil.EntityType.PLAYERS);
        if (mobs.get()) entitySelector.apply(AttackUtil.EntityType.MOBS);
        if (animals.get()) entitySelector.apply(AttackUtil.EntityType.ANIMALS);

        if (entitySelector.ofType(entity, entitySelector.build()) == null) return false;

        if (!friends.get() && entity instanceof PlayerEntity) {
            if (im.laura.command.friends.FriendStorage.isFriend(entity.getName().getString())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean onDisable() {

        stopWatch.reset();
        super.onDisable();
        return false;
    }
}
