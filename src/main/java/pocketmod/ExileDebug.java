package pocketmod;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

/** Temporary tools. All edits are validated and performed on the server. */
public final class ExileDebug {
    private ExileDebug() {}
    public static boolean enabled(EntityPlayer p) {
        return ExileConfig.debugTools && ExileMode.state(p).getBoolean("debug_enabled");
    }
    public static boolean paused(EntityPlayer p) {
        return enabled(p) && ExileMode.state(p).getBoolean("debug_pause");
    }
    public static boolean reinforcementsPaused(EntityPlayer p) {
        return enabled(p) && ExileMode.state(p).getBoolean("debug_waves_pause");
    }
    public static NBTTagCompound report(EntityPlayer p) {
        NBTTagCompound s = ExileMode.state(p);
        if (!s.hasKey("spawn_report", 10)) s.setTag("spawn_report", new NBTTagCompound());
        return s.getCompoundTag("spawn_report");
    }
    public static boolean mayEdit(EntityPlayerMP p) {
        return p.canUseCommand(2, "exiledebug") || (p.getServer() != null
                && p.getServer().isSinglePlayer() && p.getName().equals(p.getServer().getServerOwner()));
    }
    static int stageExposure(int stage) {
        switch (stage) {
            case 1: return ExileConfig.firstSeconds;
            case 2: return ExileConfig.fleeSeconds;
            case 3: return ExileConfig.witherSeconds;
            case 4: return ExileConfig.fullSeconds;
            default: return 0;
        }
    }
    public static void action(EntityPlayerMP p, int action, int stage, boolean area) {
        action(p, action, stage, area ? 1 : 0);
    }
    public static void action(EntityPlayerMP p, int action, int stage, int radius) {
        if (!ExileConfig.debugTools || !ExileMode.enabled(p.world)) return;
        NBTTagCompound s = ExileMode.state(p);
        if (action == 0) {
            s.setBoolean("debug_enabled", !s.getBoolean("debug_enabled"));
            if (!s.getBoolean("debug_enabled")) { s.removeTag("debug_pause"); s.removeTag("debug_thirst_pause"); s.removeTag("debug_waves_pause"); s.removeTag("debug_snapshot"); }
            NetworkHandler.sync(p); return;
        }
        if (!enabled(p) || !mayEdit(p) || radius < 0 || radius > 4) return;
        if (action >= 10 && action <= 19) {
            ExileSimpleThirst.debug(p,action,stage);
            NetworkHandler.sync(p); return;
        }
        if (p.dimension != 0) return;
        if (action == 1) {
            if (stage < 0 || stage > 4) return;
            BlockPos pos = p.getPosition();
            ExileWorldData data = ExileWorldData.get(p.world);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
                data.setExposure(p.dimension, (pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, stageExposure(stage));
            s.setInteger("stage", stage);
            s.setInteger("contamination", stageExposure(stage)*100/Math.max(1, ExileConfig.fullSeconds));
        } else if (action == 2) {
            if (stage < 1 || stage > 4) return;
            ExileEncounters.spawn(p, stage, true);
        } else if (action == 3) {
            ExileEncounters.cancel(p);
            s.removeTag("encounter_respawns");
            for (EntityLiving mob : p.world.getEntitiesWithinAABB(EntityLiving.class,
                    p.getEntityBoundingBox().grow(128), e -> e.getEntityData().getBoolean(ExileMode.PURSUER)
                            && !e.getEntityData().hasKey("pm_exile_trial")
                            && p.getUniqueID().equals(e.getEntityData().getUniqueId("pm_exile_owner")))) mob.setDead();
        } else if (action == 4) s.setBoolean("debug_pause", !s.getBoolean("debug_pause"));
        else if(action==5) {
            if(stage!=1&&stage!=5)return;
            BlockPos pos=p.getPosition();ExileWorldData data=ExileWorldData.get(p.world);
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)data.ageFull(p.dimension,(pos.getX()>>4)+dx,(pos.getZ()>>4)+dz,stage);
        }
        if (action == 6) {
            s.setBoolean("debug_waves_pause", !s.getBoolean("debug_waves_pause"));
            if (s.getBoolean("debug_waves_pause")) ExileEncounters.cancel(p);
        } else if (action == 7) {
            ExileEncounters.cancel(p);
            s.removeTag("encounter_respawns");
        } else if (action == 8) {
            if (stage < -300 || stage > 300) return;
            BlockPos pos = p.getPosition();
            ExileWorldData data = ExileWorldData.get(p.world);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                int x = (pos.getX() >> 4) + dx, z = (pos.getZ() >> 4) + dz;
                data.setExposure(p.dimension, x, z, data.exposure(p.dimension, x, z) + stage);
            }
        }
        s.removeTag("terrain_snapshot");
        NetworkHandler.sync(p);
    }
    public static void snapshot(EntityPlayerMP p) {
        NBTTagCompound s = ExileMode.state(p);
        s.setBoolean("debug_available", ExileConfig.debugTools);
        if (!enabled(p)) { s.removeTag("debug_snapshot"); return; }
        NBTTagCompound d = new NBTTagCompound();
        d.setBoolean("edit", mayEdit(p));
        String reason=s.getBoolean("cured") ? "cured" : PocketDimensionManager.isPocketDimension(p.dimension) ? "shelter"
                : p.isCreative() ? "creative" : p.isSpectator() && !Spector.isActive(p) ? "spectator"
                : s.getInteger("stage")==0 && s.getInteger("thirst")<ExileConfig.thirstSeconds ? "clean" : "ready";
        d.setString("reason",reason);
        d.setInteger("full", ExileConfig.fullSeconds);
        d.setInteger("interval", Math.max(5,ExileConfig.spawnInterval));
        NBTTagCompound queue=s.getCompoundTag("encounter_queue");
        d.setInteger("pending_seconds",queue.getBoolean("pending")?(int)Math.max(0,(queue.getLong("due")-p.world.getTotalWorldTime()+19)/20):0);
        d.setInteger("pending_stage",queue.getBoolean("pending")?queue.getInteger("stage"):0);
        d.setInteger("respawn_delay", ExileEncounters.respawnSeconds(s.getInteger("stage")));
        NBTTagList respawns = s.getTagList("encounter_respawns", 10);
        int pending = 0; long earliest = Long.MAX_VALUE;
        for (int i = 0; i < respawns.tagCount(); i++) {
            NBTTagCompound ticket = respawns.getCompoundTagAt(i);
            if (ticket.getInteger("dimension") != p.dimension) continue;
            pending++; earliest = Math.min(earliest, ticket.getLong("due"));
        }
        d.setInteger("respawn_count", pending);
        d.setInteger("respawn_next", pending == 0 ? 0 : (int)Math.max(0, (earliest-p.world.getTotalWorldTime()+19)/20));
        d.setInteger("batch", ExileConfig.spawnBatch);
        d.setInteger("cap", ExileEncounters.cap(s.getInteger("stage")));
        if (p.dimension == 0) {
            ExileWorldData world = ExileWorldData.get(p.world);
            int cx = p.getPosition().getX() >> 4, cz = p.getPosition().getZ() >> 4;
            d.setInteger("full_minutes",world.fullMinutes(0,cx,cz));d.setInteger("black",world.blackPercent(0,cx,cz));
            d.setDouble("spread",world.spreadRate(0,cx,cz));
            NBTTagList chunks = new NBTTagList();
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                double value = world.exposure(0, cx+dx, cz+dz);
                NBTTagCompound c = new NBTTagCompound();
                c.setInteger("x", cx+dx); c.setInteger("z", cz+dz);
                c.setInteger("seconds", (int)value);
                c.setInteger("percent", (int)(100*value/Math.max(1, ExileConfig.fullSeconds)));
                c.setInteger("stage", ExileMode.stage(value));
                chunks.appendTag(c);
                if(dx==0 && dz==0) { s.setInteger("stage",c.getInteger("stage")); s.setInteger("contamination",c.getInteger("percent")); }
            }
            d.setTag("chunks", chunks);
        }
        java.util.Map<String,Integer> mobs = new java.util.TreeMap<>();
        for (EntityLiving mob : p.world.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(128),
                e -> e.isEntityAlive() && e.getEntityData().getBoolean(ExileMode.PURSUER))) {
            net.minecraft.util.ResourceLocation id = net.minecraft.entity.EntityList.getKey(mob);
            String name = id == null ? mob.getClass().getSimpleName() : id.toString();
            if (mob.getEntityData().hasKey("pm_exile_trial")) name += " (trial)";
            mobs.put(name, mobs.getOrDefault(name, 0)+1);
        }
        NBTTagList counts = new NBTTagList();
        for (java.util.Map.Entry<String,Integer> entry : mobs.entrySet()) {
            NBTTagCompound c = new NBTTagCompound(); c.setString("id", entry.getKey()); c.setInteger("count", entry.getValue()); counts.appendTag(c);
        }
        d.setTag("mobs", counts);
        d.setLong("now", p.world.getTotalWorldTime());
        s.setTag("debug_snapshot", d);
    }
}
