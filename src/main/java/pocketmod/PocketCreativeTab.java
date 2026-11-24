package pocketmod;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.init.Items;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

public final class PocketCreativeTab {
    public static final CreativeTabs ITEMS = new CreativeTabs("pocketmod") {
        @Override public ItemStack getTabIconItem() { return new ItemStack(ExileItems.RESURRECTION_TOTEM); }
        @Override public void displayAllRelevantItems(NonNullList<ItemStack> items) {
            super.displayAllRelevantItems(items);
            ItemStack egg = new ItemStack(Items.SPAWN_EGG);
            ItemMonsterPlacer.applyEntityIdToItemStack(egg, new ResourceLocation(PocketMod.MODID, "rift_walker"));
            items.add(egg);
        }
    };
    private PocketCreativeTab() {}
}
