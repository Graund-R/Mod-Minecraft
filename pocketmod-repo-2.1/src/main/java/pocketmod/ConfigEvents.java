package pocketmod;

import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** После «Готово» в экране настроек значения применяются сразу, перезапуск не нужен. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID, value = Side.CLIENT)
public class ConfigEvents {

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if (PocketMod.MODID.equals(e.getModID())) {
            PocketConfig.reload();
        }
    }
}
