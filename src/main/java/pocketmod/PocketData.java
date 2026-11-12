package pocketmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public class PocketData extends WorldSavedData {

    public static final String ID = "pocketmod_pocket";

    public final Set<BlockPos> interior = new HashSet<BlockPos>();
    public final Set<BlockPos> walls = new HashSet<BlockPos>();
    public final Map<BlockPos, Integer> paid = new HashMap<BlockPos, Integer>();
    public boolean built = false;

    public PocketData(String name) {
        super(name);
    }

    public static PocketData get(World world) {
        MapStorage storage = world.getMapStorage();
        PocketData data = (PocketData) storage.getOrLoadData(PocketData.class, ID);
        if (data == null) {
            data = new PocketData(ID);
            storage.setData(ID, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        interior.clear();
        walls.clear();
        paid.clear();
        readSet(nbt.getIntArray("interior"), interior);
        readSet(nbt.getIntArray("walls"), walls);
        int[] p = nbt.getIntArray("paid");
        for (int i = 0; i + 3 < p.length; i += 4) {
            paid.put(new BlockPos(p[i], p[i + 1], p[i + 2]), p[i + 3]);
        }
        built = nbt.getBoolean("built");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setIntArray("interior", writeSet(interior));
        nbt.setIntArray("walls", writeSet(walls));
        int[] p = new int[paid.size() * 4];
        int i = 0;
        for (Map.Entry<BlockPos, Integer> e : paid.entrySet()) {
            p[i++] = e.getKey().getX();
            p[i++] = e.getKey().getY();
            p[i++] = e.getKey().getZ();
            p[i++] = e.getValue();
        }
        nbt.setIntArray("paid", p);
        nbt.setBoolean("built", built);
        return nbt;
    }

    private static void readSet(int[] a, Set<BlockPos> out) {
        for (int i = 0; i + 2 < a.length; i += 3) {
            out.add(new BlockPos(a[i], a[i + 1], a[i + 2]));
        }
    }

    private static int[] writeSet(Set<BlockPos> in) {
        int[] a = new int[in.size() * 3];
        int i = 0;
        for (BlockPos p : in) {
            a[i++] = p.getX();
            a[i++] = p.getY();
            a[i++] = p.getZ();
        }
        return a;
    }
}
