package pocketmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public class PocketDoorData extends WorldSavedData {
    private static final String ID = "pocketmod_doors";
    private static PocketDoorData instance;
    private final Map<String, String> owners = new HashMap<String, String>();

    public PocketDoorData() { super(ID); }

    public static PocketDoorData get(WorldServer world) {
        WorldServer overworld = world.getMinecraftServer().getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        if (instance == null) {
            instance = (PocketDoorData) storage.getOrLoadData(PocketDoorData.class, ID);
            if (instance == null) {
                instance = new PocketDoorData();
                storage.setData(ID, instance);
            }
        }
        return instance;
    }

    private static String key(int dim, long pos) { return dim + ":" + pos; }

    public void register(int dim, long pos, UUID owner) {
        owners.put(key(dim, pos), owner.toString());
        markDirty();
    }

    public UUID owner(int dim, long pos) {
        String value = owners.get(key(dim, pos));
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    public void remove(int dim, long pos) {
        if (owners.remove(key(dim, pos)) != null) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        owners.clear();
        NBTTagList list = nbt.getTagList("doors", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            owners.put(e.getString("pos"), e.getString("owner"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, String> e : owners.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("pos", e.getKey());
            tag.setString("owner", e.getValue());
            list.appendTag(tag);
        }
        nbt.setTag("doors", list);
        return nbt;
    }
}
