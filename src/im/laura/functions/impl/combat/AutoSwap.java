package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventCooldown;
import im.laura.events.EventKey;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BindSetting;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.StopWatch;
import im.laura.utils.player.InventoryUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.*;
import net.minecraft.potion.Effects;

@FieldDefaults(level = AccessLevel.PRIVATE)
@FunctionRegister(name = "AutoSwap", type = Category.Combat)
public class AutoSwap extends Function {
    final ModeSetting swapMode = new ModeSetting("Тип", "Умный", "Умный", "По бинду");
    final ModeSetting itemType = new ModeSetting("Предмет", "Щит", "Щит", "Геплы", "Тотем", "Шар");
    final ModeSetting swapType = new ModeSetting("Свапать на", "Геплы", "Щит", "Геплы", "Тотем", "Шар");

    final BindSetting keyToSwap = new BindSetting("Кнопка", -1).setVisible(() -> swapMode.is("По бинду"));
    final SliderSetting health = new SliderSetting("Здоровье", 11.0F, 5.0F, 19.0F, 0.5F).setVisible(() -> swapMode.is("Умный"));
    final SliderSetting swapDelay = new SliderSetting("Задержка", 200f, 0f, 1000f, 50f);
    final BooleanSetting preferEnchanted = new BooleanSetting("Зачарованное", true);
    final BooleanSetting autoReturn = new BooleanSetting("Авто возврат", true);

    final StopWatch stopWatch = new StopWatch();
    boolean shieldIsCooldown;
    int oldItem = -1;

    final AutoTotem autoTotem;

    public AutoSwap(AutoTotem autoTotem) {
        this.autoTotem = autoTotem;
        addSettings(swapMode, itemType, swapType, keyToSwap, health, swapDelay, preferEnchanted, autoReturn);
    }

    @Subscribe
    public void onEventKey(EventKey e) {
        if (!swapMode.is("По бинду")) return;

        ItemStack offhandItemStack = mc.player.getHeldItemOffhand();
        boolean isOffhandNotEmpty = !(offhandItemStack.getItem() instanceof AirItem);

        if (e.isKeyDown(keyToSwap.get()) && stopWatch.isReached(swapDelay.get().longValue())) {
            Item currentItem = offhandItemStack.getItem();
            boolean isHoldingSwapItem = currentItem == getSwapItem();
            boolean isHoldingSelectedItem = currentItem == getSelectedItem();
            int selectedItemSlot = getSlot(getSelectedItem());
            int swapItemSlot = getSlot(getSwapItem());

            if (selectedItemSlot >= 0 && !isHoldingSelectedItem) {
                InventoryUtil.moveItem(selectedItemSlot, 45, isOffhandNotEmpty);
                stopWatch.reset();
                return;
            }
            if (swapItemSlot >= 0 && !isHoldingSwapItem) {
                InventoryUtil.moveItem(swapItemSlot, 45, isOffhandNotEmpty);
                stopWatch.reset();
            }
        }
    }

    @Subscribe
    private void onCooldown(EventCooldown e) {
        shieldIsCooldown = isCooldown(e);
    }

    @Subscribe
    private void onUpdate(EventUpdate e) {
        if (!swapMode.is("Умный")) return;

        Item currentItem = mc.player.getHeldItemOffhand().getItem();

        if (stopWatch.isReached(swapDelay.get().longValue())) {
            swapIfShieldIsBroken(currentItem);
            swapIfHealthToLow(currentItem);
            stopWatch.reset();
        }

        if (currentItem == Items.GOLDEN_APPLE && 
            !mc.player.getCooldownTracker().hasCooldown(Items.GOLDEN_APPLE) &&
            mc.gameSettings.keyBindUseItem.isKeyDown()) {
            stopWatch.reset();
        }
    }

    @Override
    public boolean onDisable() {
        shieldIsCooldown = false;
        oldItem = -1;
        super.onDisable();
        return false;
    }

    private void swapIfHealthToLow(Item currentItem) {
        boolean isOffhandNotEmpty = !(currentItem instanceof AirItem);
        boolean isHoldingGoldenApple = currentItem == getSwapItem();
        boolean isHoldingSelectedItem = currentItem == getSelectedItem();
        boolean gappleIsNotCooldown = !mc.player.getCooldownTracker().hasCooldown(Items.GOLDEN_APPLE);

        int goldenAppleSlot = getSlot(getSwapItem());

        if (shieldIsCooldown || !gappleIsNotCooldown) {
            return;
        }

        if (isLowHealth() && !isHoldingGoldenApple && isHoldingSelectedItem) {
            InventoryUtil.moveItem(goldenAppleSlot, 45, isOffhandNotEmpty);
            if (isOffhandNotEmpty && oldItem == -1) {
                oldItem = goldenAppleSlot;
            }
        } else if (!isLowHealth() && isHoldingGoldenApple && oldItem >= 0) {
            InventoryUtil.moveItem(oldItem, 45, isOffhandNotEmpty);
            oldItem = -1;
        }
    }

    private void swapIfShieldIsBroken(Item currentItem) {
        boolean isOffhandNotEmpty = !(currentItem instanceof AirItem);
        boolean isHoldingGoldenApple = currentItem == getSwapItem();
        boolean isHoldingSelectedItem = currentItem == getSelectedItem();
        boolean gappleIsNotCooldown = !mc.player.getCooldownTracker().hasCooldown(Items.GOLDEN_APPLE);
        int goldenAppleSlot = getSlot(getSwapItem());

        if (shieldIsCooldown && !isHoldingGoldenApple && isHoldingSelectedItem && gappleIsNotCooldown) {
            InventoryUtil.moveItem(goldenAppleSlot, 45, isOffhandNotEmpty);
            if (isOffhandNotEmpty && oldItem == -1) {
                oldItem = goldenAppleSlot;
            }
            print(shieldIsCooldown + "");
        } else if (!shieldIsCooldown && isHoldingGoldenApple && oldItem >= 0) {
            InventoryUtil.moveItem(oldItem, 45, isOffhandNotEmpty);
            oldItem = -1;
        }
    }

    private boolean isLowHealth() {
        float currentHealth = mc.player.getHealth() + (mc.player.isPotionActive(Effects.ABSORPTION) ? mc.player.getAbsorptionAmount() : 0.0f);
        return currentHealth <= health.get();
    }

    private boolean isCooldown(EventCooldown cooldown) {
        Item item = cooldown.getItem();


        if (!itemType.is("Shield")) {
            return false;
        } else {
            return cooldown.isAdded() && item instanceof ShieldItem;
        }
    }

    private Item getSwapItem() {
        return getItemByType(swapType.get());
    }

    private Item getSelectedItem() {
        return getItemByType(itemType.get());
    }

    private Item getItemByType(String itemType) {
        return switch (itemType) {
            case "Щит" -> Items.SHIELD;
            case "Тотем" -> Items.TOTEM_OF_UNDYING;
            case "Геплы" -> Items.GOLDEN_APPLE;
            case "Шар" -> Items.PLAYER_HEAD;
            default -> Items.AIR;
        };
    }

    private int getSlot(Item item) {
        int finalSlot = -1;
        
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.getItem() != item) continue;
            
            if (preferEnchanted.get() && stack.isEnchanted()) {
                finalSlot = i;
                break;
            } else {
                finalSlot = i;
            }
        }
        
        return finalSlot < 9 && finalSlot != -1 ? finalSlot + 36 : finalSlot;
    }
}