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
    public NBTTagCompound route = new NBTTagCompound();
    public NBTTagCompound trials = new NBTTagCompound();
    private NBTTagList resurrectionTotems = new NBTTagList();
    public NBTTagCompound pendingResurrections = new NBTTagCompound();
    private final Map<String, Area> areas = new HashMap<>();

    public static final class Area {
        public double exposure;
        int dim,x,z;
        boolean full;
        long fullSince;
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
            data.enabled = "exile".equals(overworld.getWorldInfo().getTerrainType().getName())
                    || overworld.getGameRules().getBoolean(ExileWorldCreation.RULE);
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
            a = new Area();a.dim=dim;a.x=x;a.z=z;
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
        if(!a.full&&a.exposure>=ExileConfig.fullSeconds) {a.full=true;a.fullSince=seconds;}
        a.addedThisSecond = Math.max(amount, a.addedThisSecond);
        a.lastVisit = seconds;
        a.lastUpdate = seconds;
        markDirty();
    }

    public void setExposure(int dim, int x, int z, double value) {
        Area a = new Area();a.dim=dim;a.x=x;a.z=z;
        a.exposure = Math.max(0, Math.min(ExileConfig.fullSeconds, value));
        a.peak = a.exposure;
        a.full=a.exposure>=ExileConfig.fullSeconds;a.fullSince=seconds;
        a.lastVisit = a.lastUpdate = seconds;
        a.addedAt = seconds;
        a.addedThisSecond = 1;
        areas.put(key(dim, x, z), a);
        markDirty();
    }

    public int fullMinutes(int dim,int x,int z) {
        Area a=areas.get(key(dim,x,z));
        return a==null||!a.full?0:(int)Math.min(1000000,Math.max(0,seconds-a.fullSince)/60);
    }
    public int blackPercent(int dim,int x,int z) {
        return Math.min(100,fullMinutes(dim,x,z)*100/Math.max(1,ExileConfig.blackeningMinutes));
    }
    public double spreadRate(int dim,int x,int z) {
        return 0.25*(1+Math.min(ExileConfig.blackeningMinutes,fullMinutes(dim,x,z)));
    }
    public void ageFull(int dim,int x,int z,int minutes) {
        Area a=areas.get(key(dim,x,z));
        if(a==null||!a.full)return;
        a.fullSince-=Math.max(0,Math.min(60,minutes))*60L;markDirty();
    }
    /** Snapshot only known infected terrain; it never loads or generates chunks. */
    public NBTTagCompound terrain(int dim,int x,int z,int radius) {
        NBTTagCompound result=new NBTTagCompound();result.setInteger("dimension",dim);
        NBTTagList chunks=new NBTTagList();
        for(Area a:areas.values()) {
            if(a.dim!=dim||Math.abs((long)a.x-x)>radius||Math.abs((long)a.z-z)>radius)continue;
            int percent=(int)Math.min(100,exposure(dim,a.x,a.z)*100/Math.max(1,ExileConfig.fullSeconds));
            int gray=Math.max(0,Math.min(100,(percent-10)*100/90)),black=blackPercent(dim,a.x,a.z);
            if(gray==0&&black==0)continue;
            NBTTagCompound c=new NBTTagCompound();c.setInteger("x",a.x);c.setInteger("z",a.z);
            c.setInteger("gray",gray);c.setInteger("black",black);chunks.appendTag(c);
        }
        result.setTag("chunks",chunks);return result;
    }
    /** One simultaneous step, using max contribution so crowds/sources do not multiply speed. */
    public void spread(int dim,java.util.function.BiPredicate<Integer,Integer> loaded) {
        Map<String,double[]> pending=new HashMap<>();
        for(Area a:areas.values()) {
            if(a.dim!=dim||!a.full||!loaded.test(a.x,a.z))continue;
            double rate=spreadRate(dim,a.x,a.z);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                if(dx==0&&dz==0)continue;
                int x=a.x+dx,z=a.z+dz;if(!loaded.test(x,z))continue;
                String key=key(dim,x,z);double[] old=pending.get(key);
                if(old==null||old[2]<rate)pending.put(key,new double[]{x,z,rate});
            }
        }
        for(double[] next:pending.values())contaminate(dim,(int)next[0],(int)next[1],next[2]);
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
        route = nbt.getCompoundTag("trial_route");
        resurrectionTotems = nbt.getTagList("resurrectionTotems", 10);
        pendingResurrections = nbt.getCompoundTag("pendingResurrections");
        areas.clear();
        NBTTagList list = nbt.getTagList("areas", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            Area a = new Area();
            String[] position=c.getString("key").split(":");
            if(position.length!=3)continue;
            try {a.dim=Integer.parseInt(position[0]);a.x=Integer.parseInt(position[1]);a.z=Integer.parseInt(position[2]);}
            catch(NumberFormatException ignored){continue;}
            a.exposure = Math.max(0, c.getDouble("exposure"));
            a.peak = Math.max(a.exposure, c.getDouble("peak"));
            a.full=c.hasKey("full")?c.getBoolean("full"):a.peak>=ExileConfig.fullSeconds;
            a.fullSince=c.hasKey("fullSince")?c.getLong("fullSince"):seconds;
            a.lastVisit = c.getLong("visit");
            a.lastUpdate = c.getLong("update");
            areas.put(c.getString("key"), a);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("trials", trials.copy());
        nbt.setTag("trial_route", route.copy());
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
            c.setBoolean("full",a.full);c.setLong("fullSince",a.fullSince);
            c.setLong("visit", a.lastVisit);
            c.setLong("update", a.lastUpdate);
            list.appendTag(c);
        }
        nbt.setTag("areas", list);
        return nbt;
    }
}
