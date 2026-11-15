package pocketmod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Карман живёт, пока ты в обычном мире: чанки с внутренними клетками принудительно загружены
 * (иначе мир без игроков через 15 секунд перестаёт обновлять сущности и печки).
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class PocketKeep {

    private static final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
    private static final Set<Long> forced = new HashSet<Long>();
    private static WorldServer owner;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        if (e.world.provider.getDimension() != PocketMod.DIM_ID) return;
        if (!(e.world instanceof WorldServer)) return;
        if (e.world.getTotalWorldTime() % 40 != 0) return;
        try {
            keep((WorldServer) e.world);
        } catch (Throwable t) {
            System.err.println("[PocketMod] PocketKeep error:");
            t.printStackTrace();
        }
    }

    private static void keep(WorldServer w) {
        if (owner != w) {
            tickets.clear();
            forced.clear();
            owner = w;
        }
        PocketData d = PocketData.get(w);
        Set<ChunkPos> need = new HashSet<ChunkPos>();
        for (BlockPos p : d.interior) {
            need.add(new ChunkPos(p.getX() >> 4, p.getZ() >> 4));
        }
        for (ChunkPos cp : need) {
            long key = ChunkPos.asLong(cp.x, cp.z);
            if (forced.contains(key)) continue;
            ForgeChunkManager.Ticket t = null;
            for (ForgeChunkManager.Ticket c : tickets) {
                if (c.getChunkList().size() < c.getMaxChunkListDepth()) {
                    t = c;
                    break;
                }
            }
            if (t == null) {
                t = ForgeChunkManager.requestTicket(PocketMod.instance, w, ForgeChunkManager.Type.NORMAL);
                if (t == null) return;
                tickets.add(t);
            }
            ForgeChunkManager.forceChunk(t, cp);
            forced.add(key);
        }
    }
}
