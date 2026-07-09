package im.laura.functions.impl.combat;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.BooleanSetting;
import im.laura.functions.settings.impl.ModeSetting;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.StopWatch;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;

@FieldDefaults(level = AccessLevel.PRIVATE)
@FunctionRegister(name = "AutoArmor", type = Category.Combat)
public class AutoArmor extends Function {
    final SliderSetting delay = new SliderSetting("Задержка", 50.0f, 0.0f, 500.0f, 10.0f);
    final BooleanSetting moving = new BooleanSetting("В движении", true);
    final BooleanSetting autoHelmet = new BooleanSetting("Авто Шлем", false);
    final ModeSetting priority = new ModeSetting("Приоритет", "Защита", "Защита", "Прочность", "Очки брони");

    final StopWatch stopWatch = new StopWatch();

    public AutoArmor() {
        addSettings(delay, moving, autoHelmet, priority);
    }

    @Subscribe
    private void onUpdate(EventUpdate event) {
        if (!moving.get() && isMoving()) {
            return;
        }

        if (!stopWatch.isReached(delay.get().longValue())) {
            return;
        }

        PlayerInventory inventoryPlayer = mc.player.inventory;
        int[] bestIndexes = new int[4];
        int[] bestValues = new int[4];

        for (int i = 0; i < 4; ++i) {
            bestIndexes[i] = -1;
            ItemStack stack = inventoryPlayer.armorItemInSlot(i);

            if (!isValid(stack)) continue;

            bestValues[i] = calculateArmorValue(stack);
        }

        for (int i = 0; i < 36; ++i) {
            ItemStack stack = inventoryPlayer.getStackInSlot(i);

            if (!isValid(stack) || !(stack.getItem() instanceof ArmorItem armorItem)) continue;

            int armorTypeIndex = armorItem.getSlot().getIndex();
            int value = calculateArmorValue(stack);

            if (value <= bestValues[armorTypeIndex]) continue;

            bestIndexes[armorTypeIndex] = i;
            bestValues[armorTypeIndex] = value;
        }

        ArrayList<Integer> slots = new ArrayList<>();
        slots.add(3); slots.add(2); slots.add(1); slots.add(0);

        if (autoHelmet.get()) {
            Collections.shuffle(slots);
        }

        for (int slot : slots) {
            int bestIndex = bestIndexes[slot];

            if (bestIndex == -1) continue;

            if (isValid(inventoryPlayer.armorItemInSlot(slot)) && inventoryPlayer.getFirstEmptyStack() == -1) {
                continue;
            }

            int clickIndex = bestIndex < 9 ? bestIndex + 36 : bestIndex;
            ItemStack armorStack = inventoryPlayer.armorItemInSlot(slot);

            if (isValid(armorStack)) {
                mc.playerController.windowClick(0, 8 - slot, 0, ClickType.QUICK_MOVE, mc.player);
            }

            mc.playerController.windowClick(0, clickIndex, 0, ClickType.QUICK_MOVE, mc.player);
            stopWatch.reset();
            break;
        }
    }

    private boolean isValid(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
    }

    private int calculateArmorValue(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return 0;

        int protection = EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, stack);
        int toughness = (int) (armor.getToughness() * 2);
        int damageReduce = armor.getArmorMaterial().getDamageReductionAmount(armor.getEquipmentSlot());
        int baseArmor = armor.getDamageReduceAmount();

        return switch (priority.get()) {
            case "Прочность" -> stack.getMaxDamage() - stack.getDamage();
            case "Очки брони" -> baseArmor * 20 + toughness * 2 + damageReduce * 5;
            default -> (baseArmor * 20 + protection * 12 + toughness * 2 + damageReduce * 5) >> 3;
        };
    }

    private boolean isMoving() {
        return mc.player.movementInput.moveForward != 0 || mc.player.movementInput.moveStrafe != 0;
    }
}