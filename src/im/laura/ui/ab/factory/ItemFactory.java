package im.laura.ui.ab.factory;

import im.laura.ui.ab.model.IItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;

import java.util.Map;

public interface ItemFactory {
    IItem createNewItem(Item item, int price, int quantity, int damage, Map<Enchantment, Integer> enchantments);
}
