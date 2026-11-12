package pocketmod;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class ManaItems {

    public static boolean isManaItem(ItemStack stack) {
        return manaValue(stack) > 0;
    }

    public static int manaValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        Item item = stack.getItem();

        if (item == Items.DIAMOND) {
            return PocketConfig.valueDiamond;
        }
        if (item == Items.EMERALD) {
            return PocketConfig.valueEmerald;
        }

        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            if (name.startsWith("ingot")) {
                Integer special = PocketConfig.ingotValues.get(name);
                if (special != null) {
                    return special;
                }
                if ("ingotIron".equals(name)) return PocketConfig.valueIron;
                if ("ingotGold".equals(name)) return PocketConfig.valueGold;
                return PocketConfig.manaPerIngot;
            }
        }
        return 0;
    }

    public static boolean isIngot(ItemStack stack) {
        return isManaItem(stack);
    }
}
