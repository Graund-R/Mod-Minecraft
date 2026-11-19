package pocketmod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * Assigns one persistent Forge dimension to each player.
 *
 * The mapping is saved in the Overworld, while each dimension has its own
 * MapStorage and therefore its own PocketData, blocks and entities.
 */
public final class PocketDimensionManager {

    private static final String DATA_ID = "pocketmod_personal_dimensions";
    private static final int FIRST_PERSONAL_DIMENSION_ID = 101;

    private static final Map<Integer, DimensionType> TYPES = new LinkedHashMap<Integer, DimensionType>();
    private static DimensionRegistryData registry;

    private PocketDimensionManager() {
    }

    public static void load(WorldServer overworld) {
        MapStorage storage = overworld.getMapStorage();
        registry = (DimensionRegistryData) storage.getOrLoadData(DimensionRegistryData.class, DATA_ID);
        if (registry == null) {
            registry = new DimensionRegistryData();
            storage.setData(DATA_ID, registry);
        }

        for (Map.Entry<String, Integer> entry : registry.ownerDimensions.entrySet()) {
            register(entry.getValue().intValue());
        }
    }

    public static int getOrCreateDimension(MinecraftServer server, UUID owner) {
        WorldServer overworld = server.getWorld(0);
        if (registry == null) {
            load(overworld);
        }

        String key = owner.toString();
        Integer existing = registry.ownerDimensions.get(key);
        final int dimensionId;
        if (existing != null) {
            dimensionId = existing.intValue();
            register(dimensionId);
        } else {
            dimensionId = nextDimensionId();
            registry.ownerDimensions.put(key, Integer.valueOf(dimensionId));
            registry.markDirty();
            register(dimensionId);
        }

        if (server.getWorld(dimensionId) == null) {
            DimensionManager.initDimension(dimensionId);
        }
        return dimensionId;
    }

    public static int getDimensionForOwner(MinecraftServer server, UUID owner) {
        WorldServer overworld = server.getWorld(0);
        if (registry == null) load(overworld);
        Integer id = registry.ownerDimensions.get(owner.toString());
        return id == null ? -1 : id.intValue();
    }

    public static UUID getOwnerForDimension(MinecraftServer server, int dimensionId) {
        WorldServer overworld = server.getWorld(0);
        if (registry == null) load(overworld);
        for (Map.Entry<String, Integer> entry : registry.ownerDimensions.entrySet()) {
            if (entry.getValue().intValue() == dimensionId) {
                try { return UUID.fromString(entry.getKey()); }
                catch (IllegalArgumentException ignored) { return null; }
            }
        }
        return null;
    }

    public static boolean isPocketDimension(int dimensionId) {
        return dimensionId == PocketMod.DIM_ID || TYPES.containsKey(Integer.valueOf(dimensionId));
    }

    public static DimensionType typeFor(int dimensionId) {
        DimensionType type = TYPES.get(Integer.valueOf(dimensionId));
        return type == null ? PocketMod.POCKET_TYPE : type;
    }

    private static int nextDimensionId() {
        int next = FIRST_PERSONAL_DIMENSION_ID;
        for (Integer id : registry.ownerDimensions.values()) {
            next = Math.max(next, id.intValue() + 1);
        }
        while (DimensionManager.isDimensionRegistered(next)) {
            next++;
        }
        return next;
    }

    private static void register(int dimensionId) {
        if (TYPES.containsKey(Integer.valueOf(dimensionId))) {
            return;
        }
        String suffix = "_pocket_" + dimensionId;
        DimensionType type = DimensionType.register("pocket_player_" + dimensionId,
                suffix, dimensionId, PocketWorldProvider.class, true);
        TYPES.put(Integer.valueOf(dimensionId), type);
        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
            DimensionManager.registerDimension(dimensionId, type);
        }
    }

    public static class DimensionRegistryData extends WorldSavedData {
        private final Map<String, Integer> ownerDimensions = new LinkedHashMap<String, Integer>();

        public DimensionRegistryData() {
            super(DATA_ID);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            ownerDimensions.clear();
            NBTTagList list = nbt.getTagList("owners", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                ownerDimensions.put(entry.getString("uuid"), Integer.valueOf(entry.getInteger("dimension")));
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, Integer> entry : ownerDimensions.entrySet()) {
                NBTTagCompound value = new NBTTagCompound();
                value.setString("uuid", entry.getKey());
                value.setInteger("dimension", entry.getValue().intValue());
                list.appendTag(value);
            }
            nbt.setTag("owners", list);
            return nbt;
        }
    }
}
