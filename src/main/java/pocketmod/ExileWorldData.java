package pocketmod;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Состояние режима принадлежит сохранению мира, а не клиенту или игровому режиму игрока. */
public final class ExileWorldData extends WorldSavedData {
    public static final String ID = "pocketmod_exile";
    public boolean enabled;
    public long seconds;
    public NBTTagCompound trials = new NBTTagCompound();
    private NBTTagList resurrectionTotems = new NBTTagList();
    public NBTTagCompound pendingResurrections = new NBTTagCompound();
    private final Map<String, Area> areas = new HashMap<>();

    public static final class Area {
        public double exposure;
        double peak;
        long lastVisit;
        long lastUpdate;
        long addedAt = -1;
        double addedThisSecond;
    }

    public ExileWorldData(String name) {
        super(name);
    }

    public static ExileWorldData get(World world) {
        World overworld =
                world.provider.getDimension() == 0 ? world : world.getMinecraftServer().getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        ExileWorldData data = (ExileWorldData) storage.getOrLoadData(ExileWorldData.class, ID);
        if (data == null) {
            data = new ExileWorldData(ID);
            data.enabled = "exile".equals(overworld.getWorldInfo().getTerrainType().getName());
            storage.setData(ID, data);
            data.markDirty();
        }
        return data;
    }

    private String key(int dim, int x, int z) {
        return dim + ":" + x + ":" + z;
    }

    public double exposure(int dim, int x, int z) {
        Area a = areas.get(key(dim, x, z));
        if (a == null) return 0;
        // Восстановление считается лениво, в том числе для выгруженных чанков.
        long recoveryStart = Math.max(a.lastUpdate, a.lastVisit + ExileConfig.recoveryDelay);
        if (seconds > recoveryStart) {
            double floor =
                    a.peak >= ExileConfig.fullSeconds
                            ? ExileConfig.fullSeconds * 0.75
                            : a.peak * 0.25;
            a.exposure =
                    Math.max(
                            floor,
                            a.exposure - (seconds - recoveryStart) * ExileConfig.recoveryRate);
            a.lastUpdate = seconds;
            markDirty();
        }
        return a.exposure;
    }

    public void visit(int dim, int x, int z) {
        contaminate(dim, x, z, 1);
    }

    public void contaminate(int dim, int x, int z, double amount) {
        String key = key(dim, x, z);
        exposure(dim, x, z);
        Area a = areas.get(key);
        if (a == null) {
            a = new Area();
            areas.put(key, a);
        }
        // Несколько игроков в одной области не ускоряют таймер заражения.
        if (a.addedAt != seconds) {
            a.addedAt = seconds;
            a.addedThisSecond = 0;
        }
        double increment = Math.max(0, amount - a.addedThisSecond);
        a.exposure = Math.min(ExileConfig.fullSeconds, a.exposure + increment);
        a.peak = Math.max(a.peak, a.exposure);
        a.addedThisSecond = Math.max(amount, a.addedThisSecond);
        a.lastVisit = seconds;
        a.lastUpdate = seconds;
        markDirty();
    }

    public void addResurrectionTotem(java.util.UUID owner, BlockPos pos) {
        removeResurrectionTotem(pos);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setUniqueId("owner", owner);
        entry.setLong("pos", pos.toLong());
        resurrectionTotems.appendTag(entry);
        markDirty();
    }

    public void removeResurrectionTotem(BlockPos pos) {
        for (int i = resurrectionTotems.tagCount() - 1; i >= 0; i--)
            if (resurrectionTotems.getCompoundTagAt(i).getLong("pos") == pos.toLong())
                resurrectionTotems.removeTag(i);
        markDirty();
    }

    public BlockPos takeResurrectionTotem(
            net.minecraft.world.WorldServer world, java.util.UUID owner) {
        for (int i = resurrectionTotems.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound entry = resurrectionTotems.getCompoundTagAt(i);
            if (!owner.equals(entry.getUniqueId("owner"))) continue;
            BlockPos pos = BlockPos.fromLong(entry.getLong("pos"));
            // Загружается выбранный чанк, поэтому тотемы работают и вдали от игрока.
            world.getChunkFromBlockCoords(pos);
            boolean valid = world.getBlockState(pos).getBlock() == ExileItems.RESURRECTION_TOTEM;
            resurrectionTotems.removeTag(i);
            markDirty();
            if (valid) return pos;
        }
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        enabled = nbt.getBoolean("enabled");
        seconds = Math.max(0, nbt.getLong("seconds"));
        trials = nbt.getCompoundTag("trials");
        resurrectionTotems = nbt.getTagList("resurrectionTotems", 10);
        pendingResurrections = nbt.getCompoundTag("pendingResurrections");
        areas.clear();
        NBTTagList list = nbt.getTagList("areas", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            Area a = new Area();
            a.exposure = Math.max(0, c.getDouble("exposure"));
            a.peak = Math.max(a.exposure, c.getDouble("peak"));
            a.lastVisit = c.getLong("visit");
            a.lastUpdate = c.getLong("update");
            areas.put(c.getString("key"), a);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("trials", trials.copy());
        nbt.setTag("resurrectionTotems", resurrectionTotems.copy());
        nbt.setTag("pendingResurrections", pendingResurrections.copy());
        nbt.setBoolean("enabled", enabled);
        nbt.setLong("seconds", seconds);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Area> entry : areas.entrySet()) {
            Area a = entry.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setString("key", entry.getKey());
            c.setDouble("exposure", a.exposure);
            c.setDouble("peak", a.peak);
            c.setLong("visit", a.lastVisit);
            c.setLong("update", a.lastUpdate);
            list.appendTag(c);
        }
        nbt.setTag("areas", list);
        return nbt;
    }
}
