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

    private ExileSounds() {}

    @SubscribeEvent
    public static void sounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().register(HEARTBEAT);
    }
}
