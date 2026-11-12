package pocketmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class ServerTick {

    /** Время прошлого тика игрока (для кредитного таймера: пауза и меню не считаются). */
    private static final Map<UUID, Long> lastTick = new HashMap<UUID, Long>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        long now = System.nanoTime() / 1000000L;
        Long prev = lastTick.put(player.getUniqueID(), now);
        long delta = prev == null ? 50L : Math.max(0L, Math.min(100L, now - prev.longValue()));

        Abilities.tickInvisibility(player);
        Abilities.tickSlow(player);
        Mana.tickCredit(player, delta);
    }
}
