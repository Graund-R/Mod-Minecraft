package pocketmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Keeps each player's pocket chunks active independently of the Overworld player list. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class PocketKeep {

    private static final Map<WorldServer, KeepState> STATES = new HashMap<WorldServer, KeepState>();

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        if (!PocketDimensionManager.isPocketDimension(e.world.provider.getDimension())) return;
        if (!(e.world instanceof WorldServer)) return;
        if (e.world.getTotalWorldTime() % 40 != 0) return;
        try {
            keep((WorldServer) e.world);
        } catch (Throwable t) {
            System.err.println("[PocketMod] PocketKeep error:");
            t.printStackTrace();
        }
    }

    private static void keep(WorldServer world) {
        KeepState state = STATES.get(world);
        if (state == null) {
            state = new KeepState();
            STATES.put(world, state);
        }

        PocketData data = PocketData.get(world);
        Set<ChunkPos> needed = new HashSet<ChunkPos>();
        for (BlockPos pos : data.interior) {
            needed.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        }

        for (ChunkPos chunk : needed) {
            long key = ChunkPos.asLong(chunk.x, chunk.z);
            if (state.forced.contains(key)) continue;

            ForgeChunkManager.Ticket ticket = null;
            for (ForgeChunkManager.Ticket candidate : state.tickets) {
                if (candidate.getChunkList().size() < candidate.getMaxChunkListDepth()) {
                    ticket = candidate;
                    break;
                }
            }
            if (ticket == null) {
                ticket = ForgeChunkManager.requestTicket(PocketMod.instance, world, ForgeChunkManager.Type.NORMAL);
                if (ticket == null) return;
                state.tickets.add(ticket);
            }
            ForgeChunkManager.forceChunk(ticket, chunk);
            state.forced.add(key);
        }
    }

    private static class KeepState {
        final List<ForgeChunkManager.Ticket> tickets = new ArrayList<ForgeChunkManager.Ticket>();
        final Set<Long> forced = new HashSet<Long>();
    }
}
