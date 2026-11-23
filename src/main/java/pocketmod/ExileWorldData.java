package pocketmod;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Состояние режима принадлежит сохранению мира, а не клиенту или игровому режиму игрока. */
public final class ExileWorldData extends WorldSavedData {
    public static final String ID = "pocketmod_exile";
    public boolean enabled;
    public long seconds;
    public NBTTagCompound trials = new NBTTagCompound();
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

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        enabled = nbt.getBoolean("enabled");
        seconds = Math.max(0, nbt.getLong("seconds"));
        trials = nbt.getCompoundTag("trials");
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
