package pocketmod;

import net.minecraft.util.*;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class DashSounds {
    private static SoundEvent sound(String name){return new SoundEvent(new ResourceLocation(PocketMod.MODID,name)).setRegistryName(PocketMod.MODID,name);}
    public static final SoundEvent SNAP=sound("dash_snap"),RUSH=sound("dash_rush"),LAND=sound("dash_land");
    @SubscribeEvent public static void register(RegistryEvent.Register<SoundEvent> event){event.getRegistry().registerAll(SNAP,RUSH,LAND);}
}
