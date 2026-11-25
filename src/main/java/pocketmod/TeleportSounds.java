package pocketmod;

import net.minecraft.util.*;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class TeleportSounds {
    private static SoundEvent sound(String name){return new SoundEvent(new ResourceLocation(PocketMod.MODID,name)).setRegistryName(PocketMod.MODID,name);}
    public static final SoundEvent FOLD=sound("teleport_fold"),UNFOLD=sound("teleport_unfold");
    @SubscribeEvent public static void register(RegistryEvent.Register<SoundEvent> e){e.getRegistry().registerAll(FOLD,UNFOLD);}
}
