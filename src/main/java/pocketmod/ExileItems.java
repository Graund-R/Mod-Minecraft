package pocketmod;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileItems {
    public static final Block RESURRECTION_TOTEM = new ResurrectionTotemBlock();
    public static final Item DISCOVERY = new AbilityDiscoveryItem();
    public static final Item TOTEM_ITEM = new ResurrectionTotemBlock.TotemItem(RESURRECTION_TOTEM);

    private ExileItems() {}

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(RESURRECTION_TOTEM);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(DISCOVERY, TOTEM_ITEM);
    }
}
