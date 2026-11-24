package pocketmod;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileSounds {
    public static final SoundEvent HEARTBEAT =
            new SoundEvent(new ResourceLocation(PocketMod.MODID, "bloodthirst_heartbeat"))
                    .setRegistryName(PocketMod.MODID, "bloodthirst_heartbeat");

    private static SoundEvent infection(String name) {
        return new SoundEvent(new ResourceLocation(PocketMod.MODID,name)).setRegistryName(PocketMod.MODID,name);
    }
    public static final SoundEvent INFECTION_WIND=infection("infection_wind");
    public static final SoundEvent INFECTION_WHISPER=infection("infection_whisper");
    public static final SoundEvent INFECTION_SURGE=infection("infection_surge");
    private ExileSounds() {}

    @SubscribeEvent
    public static void sounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(HEARTBEAT,INFECTION_WIND,INFECTION_WHISPER,INFECTION_SURGE);
    }
}
