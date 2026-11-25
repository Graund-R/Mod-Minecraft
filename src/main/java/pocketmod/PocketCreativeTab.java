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
            for (ResourceLocation id : net.minecraft.entity.EntityList.ENTITY_EGGS.keySet()) {
                if (!PocketMod.MODID.equals(id.getResourceDomain())) continue;
                ItemStack egg = new ItemStack(Items.SPAWN_EGG);
                ItemMonsterPlacer.applyEntityIdToItemStack(egg, id);
                items.add(egg);
            }
            // Include future items even if their author forgot to assign the creative tab.
            for (net.minecraft.item.Item item : net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS) {
                if(item instanceof TemporalDialItem)continue;
                if (item.getRegistryName()==null || !PocketMod.MODID.equals(item.getRegistryName().getResourceDomain())) continue;
                boolean present=false;
                for(ItemStack stack:items) if(stack.getItem()==item){present=true;break;}
                if(!present) {
                    NonNullList<ItemStack> variants=NonNullList.create();
                    item.getSubItems(CreativeTabs.SEARCH,variants);
                    if(variants.isEmpty())variants.add(new ItemStack(item));
                    items.addAll(variants);
                }
            }
        }
    };
    private PocketCreativeTab() {}
}
