package pocketmod;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Что можно превратить в ману и сколько оно стоит.
 * Слитки (oreDict "ingot*"), алмаз, изумруд.
 */
public class ManaItems {

    public static boolean isManaItem(ItemStack stack) {
        return manaValue(stack) > 0;
    }

    public static int manaValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        Item item = stack.getItem();

        // Алмаз и изумруд — явные
        if (item == Items.DIAMOND) {
            return PocketConfig.valueDiamond;
        }
        if (item == Items.EMERALD) {
            return PocketConfig.valueEmerald;
        }

        // Слитки через OreDictionary
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            if (name.startsWith("ingot")) {
                Integer special = PocketConfig.ingotValues.get(name);
                if (special != null) {
                    return special;
                }
                // Дефолты
                if ("ingotIron".equals(name)) return PocketConfig.valueIron;
                if ("ingotGold".equals(name)) return PocketConfig.valueGold;
                return PocketConfig.manaPerIngot; // любой другой слиток
            }
        }
        return 0;
    }

    /** Для обратной совместимости со старым кодом */
    public static boolean isIngot(ItemStack stack) {
        return isManaItem(stack);
    }
}
