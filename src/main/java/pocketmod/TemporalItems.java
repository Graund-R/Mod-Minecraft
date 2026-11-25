package pocketmod;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class TemporalItems {
    public static final Item DIAL=new TemporalDialItem();
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e) { e.getRegistry().register(DIAL); }
}
