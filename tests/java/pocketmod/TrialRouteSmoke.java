package pocketmod;

import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import sun.misc.Unsafe;

/** Pure route/persistence checks. No client or server is started. */
public final class TrialRouteSmoke {
    static class SeedWorld extends WorldServer {
        long seed;
        SeedWorld() { super(null, null, null, 0, null); }
        @Override public long getSeed() { return seed; }
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe)f.get(null);
        SeedWorld world = (SeedWorld)unsafe.allocateInstance(SeedWorld.class);
        BlockPos anchor = new BlockPos(-1999, 70, 2999);
        for (int seed = 0; seed < 1000; seed++) {
            world.seed = seed;
            for (int stage = 0; stage < 3; stage++) {
                BlockPos target = ExileTrials.destination(world, stage, anchor, seed % 17);
                double distance = Math.sqrt(target.distanceSq(anchor.getX(), 64, anchor.getZ()));
                int min = stage == 0 ? ExileConfig.firstTrialMin : ExileConfig.nextTrialMin;
                int max = stage == 0 ? ExileConfig.firstTrialMax : ExileConfig.nextTrialMax;
                check(distance >= min && distance <= max, "distance bounds at " + seed + "/" + stage);
                check((target.getX() & 15) == 8 && (target.getZ() & 15) == 8, "chunk centre");
                check(target.equals(ExileTrials.destination(world, stage, anchor, seed % 17)), "determinism");
                anchor = target;
            }
        }
        ExileWorldData original = new ExileWorldData("test");
        original.route.setLong("target_0", anchor.toLong());
        original.route.setBoolean("built_0", true);
        original.route.setInteger("attempt_1", 12);
        ExileWorldData loaded = new ExileWorldData("test");
        loaded.readFromNBT(original.writeToNBT(new NBTTagCompound()));
        check(loaded.route.getLong("target_0") == anchor.toLong(), "target persists");
        check(loaded.route.getBoolean("built_0"), "built marker persists");
        check(loaded.route.getInteger("attempt_1") == 12, "search persists");
        System.out.println("TrialRouteSmoke: 9003 checks passed");
    }
}
