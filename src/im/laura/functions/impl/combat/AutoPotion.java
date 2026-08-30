package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.StopWatch;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.Hand;

import java.util.List;

@FunctionRegister(name = "AutoPotion", type = Category.Combat)
@SuppressWarnings({"Beta", "Duplicates"})
public class AutoPotion extends Function {
    private final StopWatch throwTimer = new StopWatch();
    private final StopWatch drinkTimer = new StopWatch();

    private final BooleanSetting drinkStrength = new BooleanSetting("Сила", true);
    private final BooleanSetting drinkSpeed = new BooleanSetting("Скорость", true);
    private final BooleanSetting drinkFireResist = new BooleanSetting("Огнеупорность", true);
    private final BooleanSetting drinkHeal = new BooleanSetting("Исцеление", false);
    private final SliderSetting healThreshold = new SliderSetting("HP для исцеления", 10.0f, 5.0f, 20.0f, 0.5f).setVisible(() -> drinkHeal.get());
    private final SliderSetting throwDelay = new SliderSetting("Задержка выкидывания", 100f, 50f, 500f, 25f);
    private final SliderSetting drinkDelay = new SliderSetting("Задержка питья", 250f, 100f, 1000f, 50f);

    public AutoPotion() {
        addSettings(drinkStrength, drinkSpeed, drinkFireResist, drinkHeal, healThreshold, throwDelay, drinkDelay);
    }

    @Subscribe
    public void onUpdate(EventUpdate event) {
        processPotions(true);
    }

    public boolean isActive() {
        return processPotions(false);
    }

    private boolean processPotions(boolean execute) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof SplashPotionItem)) continue;

            List<EffectInstance> effects = PotionUtils.getEffectsFromStack(stack);
            if (effects.isEmpty()) continue;

            EffectInstance effect = effects.get(0);
            boolean isUseful = isUsefulPotion(effect.getPotion());
            boolean hasEffect = mc.player.isPotionActive(effect.getPotion());

            if (isUseful && !hasEffect) {
                if (execute && drinkTimer.isReached(drinkDelay.get().longValue())) {
                    handleSlot(i, true);
                    drinkTimer.reset();
                    return true;
                }
                return true;
            }

            if (!isUseful && execute && throwTimer.isReached(throwDelay.get().longValue())) {
                handleSlot(i, false);
                throwTimer.reset();
                return true;
            }
        }
        return false;
    }

    private boolean isUsefulPotion(net.minecraft.potion.Effect potion) {
        if (potion == Effects.STRENGTH) return drinkStrength.get();
        if (potion == Effects.SPEED) return drinkSpeed.get();
        if (potion == Effects.FIRE_RESISTANCE) return drinkFireResist.get();
        if (potion == Effects.INSTANT_HEALTH) {
            return drinkHeal.get() && mc.player.getHealth() <= healThreshold.get();
        }
        return false;
    }

    private void handleSlot(int slot, boolean use) {
        int hotbarSlot = moveToHotbar(slot);
        if (hotbarSlot == -1) return;

        mc.player.connection.sendPacket(new CHeldItemChangePacket(hotbarSlot));

        if (use) {
            mc.player.connection.sendPacket(new CPlayerTryUseItemPacket(Hand.MAIN_HAND));
            mc.player.swingArm(Hand.MAIN_HAND);
        } else {
            mc.playerController.windowClick(0, hotbarSlot + 36, 1, ClickType.THROW, mc.player);
        }

        mc.player.connection.sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
    }

    private int moveToHotbar(int slot) {
        if (slot < 9) return slot;

        int freeSlot = findFreeHotbarSlot();
        if (freeSlot == -1) return -1;

        moveItem(slot, freeSlot + 36);
        return freeSlot;
    }

    private int findFreeHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof SplashPotionItem)) continue;

            List<EffectInstance> effects = PotionUtils.getEffectsFromStack(stack);
            if (effects.isEmpty()) return i;

            for (EffectInstance effect : effects) {
                if (!isUsefulPotion(effect.getPotion())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void moveItem(int from, int to) {
        mc.playerController.windowClick(0, from, 0, ClickType.PICKUP, mc.player);
        mc.playerController.windowClick(0, to, 0, ClickType.PICKUP, mc.player);
    }

    @Override
    public boolean onDisable() {
        throwTimer.reset();
        drinkTimer.reset();
        super.onDisable();
        return false;
    }
}
