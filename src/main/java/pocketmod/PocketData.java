package pocketmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public class PocketData extends WorldSavedData {

    public static final String ID = "pocketmod_pocket";

    public final Set<BlockPos> interior = new HashSet<BlockPos>();
    public final Set<BlockPos> walls = new HashSet<BlockPos>();
    public final Map<BlockPos, Double> paid = new HashMap<BlockPos, Double>();
    public boolean built = false;
    public int maxRing = 0;

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
        NBTTagList pl = nbt.getTagList("paidList", 10);
        for (int i = 0; i < pl.tagCount(); i++) {
            NBTTagCompound c = pl.getCompoundTagAt(i);
            paid.put(new BlockPos(c.getInteger("x"), c.getInteger("y"), c.getInteger("z")), c.getDouble("v"));
        }
        built = nbt.getBoolean("built");
        maxRing = nbt.getInteger("maxRing");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setIntArray("interior", writeSet(interior));
        nbt.setIntArray("walls", writeSet(walls));
        NBTTagList pl = new NBTTagList();
        for (Map.Entry<BlockPos, Double> e : paid.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("x", e.getKey().getX());
            c.setInteger("y", e.getKey().getY());
            c.setInteger("z", e.getKey().getZ());
            c.setDouble("v", e.getValue().doubleValue());
            pl.appendTag(c);
        }
        nbt.setTag("paidList", pl);
        nbt.setBoolean("built", built);
        nbt.setInteger("maxRing", maxRing);
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
