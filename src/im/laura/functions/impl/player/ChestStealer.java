package im.laura.functions.impl.player;

import com.google.common.eventbus.Subscribe;
import im.laura.events.EventUpdate;
import im.laura.functions.api.Category;
import im.laura.functions.api.Function;
import im.laura.functions.api.FunctionRegister;
import im.laura.functions.settings.impl.SliderSetting;
import im.laura.utils.math.StopWatch;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.*;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@FunctionRegister(name = "ChestStealer", type = Category.Player)
public class ChestStealer extends Function {

    final SliderSetting delay = new SliderSetting("Задержка", 100.0f, 0.0f, 1000.0f, 1.0f);

    public ChestStealer() {
        addSettings(delay);
    }

    final StopWatch stopWatch = new StopWatch();

    final List<Item> ingotItemList = List.of(Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.NETHERITE_INGOT,
            Items.DIAMOND,
            Items.NETHERITE_SCRAP);

    @Subscribe
    public void onUpdate(EventUpdate e) {
        if (mc.player.openContainer instanceof ChestContainer container) {
            IInventory lowerChestInventory = container.getLowerChestInventory();
            for (int index = 0; index < lowerChestInventory.getSizeInventory(); ++index) {
                ItemStack stack = lowerChestInventory.getStackInSlot(index);
                if (!shouldMoveItem(container, index)) {
                    continue;
                }
                if (isContainerEmpty(stack)) {
                    continue;
                }
                if (delay.get() == 0.0f) {
                    moveItem(container, index, lowerChestInventory.getSizeInventory());
                } else {
                    if (stopWatch.isReached(delay.get().longValue())) {
                        mc.playerController.windowClick(container.windowId, index, 0, ClickType.QUICK_MOVE, mc.player);
                        stopWatch.reset();
                    }
                }
            }
        }
    }

    private boolean shouldMoveItem(ChestContainer container, int index) {
        ItemStack itemStack = container.getLowerChestInventory().getStackInSlot(index);
        return itemStack.getItem() != Item.getItemById(0);
    }

    private void moveItem(ChestContainer container, int index, int multi) {
        for (int i = 0; i < multi; i++) {
            mc.playerController.windowClick(container.windowId, index + i, 0, ClickType.QUICK_MOVE, mc.player);
        }
    }

    public boolean isWhiteListItem(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return (itemStack.isFood()
                || itemStack.isEnchanted()
                || ingotItemList.contains(item)
                || item == Items.PLAYER_HEAD
                || item instanceof ArmorItem
                || item instanceof EnderPearlItem
                || item instanceof SwordItem
                || item instanceof ToolItem
                || item instanceof PotionItem
                || item instanceof ArrowItem
                || item instanceof SkullItem
                || item.getGroup() == ItemGroup.COMBAT
        );
    }

    private boolean isContainerEmpty(ItemStack stack) {
        return !isWhiteListItem(stack);
    }

}
