package pocketmod;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.BlockEventData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Piston/note/chest block events live outside the scheduled tick list in 1.12.2. */
final class TemporalQueues {
    private static final Field QUEUES=ReflectionHelper.findField(WorldServer.class,new String[]{"blockEventQueue","field_147490_S"});
    private static final Field INDEX=ReflectionHelper.findField(WorldServer.class,new String[]{"blockEventCacheIndex","field_147489_T"});
    @SuppressWarnings("unchecked")
    private static List<BlockEventData>[] queues(WorldServer w)throws IllegalAccessException{return (List<BlockEventData>[])QUEUES.get(w);}
    static List<BlockEventData> copy(WorldServer world) {
        try {
            List<BlockEventData>[] q=queues(world);int i=INDEX.getInt(world);
            List<BlockEventData> result=new ArrayList<BlockEventData>(q[i]);result.addAll(q[i^1]);return result;
        }catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static void restore(WorldServer world,List<BlockEventData> events) {
        try {List<BlockEventData>[] q=queues(world);q[0].clear();q[1].clear();q[INDEX.getInt(world)].addAll(events);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
}
