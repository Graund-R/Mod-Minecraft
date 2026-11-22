package pocketmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Защита стен кармана от любых разрушений, не только игроком:
 * 1) взрывы (TNT, криперы, черепа визера и т. д.) не затрагивают блоки стен;
 * 2) самолечение: любая дыра в стене (другой мод, команда, поршень) закрывается обратно.
 * Старые стены другого материала не заменяются: чиним только пустые места.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class PocketProtection {

    /** Сколько блоков стен проверяется за тик (по кругу). */
    private static final int PER_TICK = 256;

    private static List<BlockPos> cache = new ArrayList<BlockPos>();
    private static int index = 0;

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != PocketMod.DIM_ID) {
            return;
        }
        Set<BlockPos> walls = PocketData.get(world).walls;
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            if (walls.contains(it.next())) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || event.world.provider.getDimension() != PocketMod.DIM_ID) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        PocketData data = PocketData.get(world);
        if (!data.built || data.walls.isEmpty()) {
            return;
        }
        IBlockState wall = PocketConfig.wallState();
        for (int i = 0; i < PER_TICK; i++) {
            if (index >= cache.size()) {
                // полный круг пройден: обновляем список (стены меняются при расширении)
                cache = new ArrayList<BlockPos>(data.walls);
                index = 0;
                if (cache.isEmpty()) {
                    return;
                }
            }
            BlockPos p = cache.get(index++);
            if (!data.walls.contains(p) || !world.isBlockLoaded(p)) {
                continue;
            }
            if (world.isAirBlock(p)) {
                world.setBlockState(p, wall, 2);
            }
        }
    }
}
