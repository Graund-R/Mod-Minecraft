package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Копирует persistent NBT (мана, способности, кредит) при смерти/клоне.
 * Сами данные лежат в entityData; здесь только перенос.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class PlayerData {

    private static final String[] KEYS = {
        "pocketmod_mana", "pocketmod_price_mult", "pocketmod_credit",
        "pocketmod_credit_due", "pocketmod_free_entry", "pocketmod_abilities",
        "pocketmod_invis", "pocketmod_slow", "pocketmod_slow_factor"
    };

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        EntityPlayer old = event.getOriginal();
        EntityPlayer now = event.getEntityPlayer();
        for (String k : KEYS) {
            if (old.getEntityData().hasKey(k)) {
                now.getEntityData().setTag(k, old.getEntityData().getTag(k).copy());
            }
        }
    }
}
