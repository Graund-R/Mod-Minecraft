package pocketmod;

import java.util.*;
import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import sun.misc.Unsafe;

/** In-memory flat terrain + real navigation. No Minecraft client or server is started. */
public final class EncounterWaveSmoke {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static class Terrain extends TrialPlacementSmoke.Terrain {
        Map<String,Chunk> chunks;
        List<EntityLiving> spawned;
        long time;
        boolean blockGround;
        @Override public long getTotalWorldTime(){return time;}
        @Override public long getWorldTime(){return 6000;}
        @Override public java.util.Calendar getCurrentDate(){return new java.util.GregorianCalendar(2026,0,1);}
        @Override public EnumDifficulty getDifficulty(){return EnumDifficulty.NORMAL;}
        @Override public DifficultyInstance getDifficultyForLocation(BlockPos pos){return new DifficultyInstance(EnumDifficulty.NORMAL,6000,0,0);}
        @Override public boolean isBlockLoaded(BlockPos pos){return true;}
        @Override public boolean isAreaLoaded(BlockPos a,BlockPos b){return true;}
        @Override public Chunk getChunkFromBlockCoords(BlockPos pos){return getChunkFromChunkCoords(pos.getX()>>4,pos.getZ()>>4);}
        @Override public Chunk getChunkFromChunkCoords(int x,int z){
            String key=x+":"+z;
            if(chunks.containsKey(key))return chunks.get(key);
            Chunk c=new Chunk(this,x,z);
            ExtendedBlockStorage ground=new ExtendedBlockStorage(64,true);
            for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)ground.set(dx,0,dz,Blocks.GRASS.getDefaultState());
            c.getBlockStorageArray()[4]=ground;
            chunks.put(key,c);return c;
        }
        @Override public WorldBorder getWorldBorder(){return new WorldBorder();}
        @Override public boolean checkNoEntityCollision(AxisAlignedBB box){return true;}
        @Override public List<AxisAlignedBB> getCollisionBoxes(Entity entity,AxisAlignedBB box){return blockGround&&!(entity instanceof net.minecraft.entity.monster.EntityVex)?Collections.singletonList(box):Collections.emptyList();}
        @Override public boolean containsAnyLiquid(AxisAlignedBB box){return false;}
        @Override public RayTraceResult rayTraceBlocks(Vec3d a,Vec3d b,boolean liquid,boolean noBox,boolean last){return null;}
        @Override public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type,AxisAlignedBB box,Predicate<? super T> filter){
            List<T> result=new ArrayList<>();
            for(EntityLiving mob:spawned)if(type.isInstance(mob)){
                T cast=type.cast(mob);if(filter==null||filter.apply(cast))result.add(cast);
            }
            return result;
        }
        @Override public boolean spawnEntity(Entity entity){spawned.add((EntityLiving)entity);return true;}
    }
    static class Player extends BloodthirstSmoke.Player {
        @Override public float getEyeHeight(){return 1.62f;}
        @Override public UUID getUniqueID(){return new UUID(0,1);}
        @Override public AxisAlignedBB getEntityBoundingBox(){return new AxisAlignedBB(-0.3,65,-0.3,0.3,66.8,0.3);}
        @Override public boolean canUseCommand(int level,String command){return true;}
        @Override public MinecraftServer getServer(){return null;}
    }
    public static void main(String[] args)throws Exception{
        TrialPlacementSmoke.main(args);
        Terrain w=(Terrain)TrialPlacementSmoke.unsafe.allocateInstance(Terrain.class);
        w.blocks=new HashMap<>();w.chunks=new HashMap<>();w.spawned=new ArrayList<>();w.storage=new MapStorage(null);
        w.info=new WorldInfo(new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.parseWorldType("exile")),"fixture");
        BloodthirstSmoke.field(w,World.class,"provider",new WorldProviderSurface());
        BloodthirstSmoke.field(w,World.class,"worldInfo",w.info);
        BloodthirstSmoke.field(w,World.class,"profiler",new net.minecraft.profiler.Profiler());
        BloodthirstSmoke.field(w,World.class,"rand",new Random(3));
        Player p=(Player)TrialPlacementSmoke.unsafe.allocateInstance(Player.class);
        p.world=w;p.data=new NBTTagCompound();p.posY=65;
        net.minecraft.network.datasync.EntityDataManager manager=new net.minecraft.network.datasync.EntityDataManager(p);
        BloodthirstSmoke.field(p,Entity.class,"dataManager",manager);
        java.lang.reflect.Field health=net.minecraft.entity.EntityLivingBase.class.getDeclaredField("HEALTH");health.setAccessible(true);
        manager.register((net.minecraft.network.datasync.DataParameter<Float>)health.get(null),20f);
        BloodthirstSmoke.field(w,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(p)));
        ExileMode.state(p).setBoolean("debug_enabled",true);
        EntityLiving skeleton=ExileEncounters.createMob(w,1);
        check(skeleton.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue()>=64,"navigation range set BEFORE path lookup");
        ExileEncounters.spawn(p,3,true);
        check(w.spawned.isEmpty(),"request never spawns instantly");
        check(ExileMode.state(p).getCompoundTag("encounter_queue").getLong("due")==100,"initial five second grace");
        w.time=99;
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.isEmpty(),"no wave at 4.95 seconds");
        w.time=100;
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        NBTTagCompound report=ExileDebug.report(p);
        System.out.println("Real navigation wave report: "+report);
        check(report.getInteger("wave_1")>0,"archer really spawned on flat terrain");
        check(report.getInteger("wave_2")>0,"wither skeleton really spawned on flat terrain");
        check(report.getInteger("wave_0")>0,"mixed wave retains a Vex slot");
        check(w.spawned.size()==3,"fixed three-type quota fills the wave");
        ExileEncounters.spawn(p,3,true);
        long due=ExileMode.state(p).getCompoundTag("encounter_queue").getLong("due");
        ExileEncounters.spawn(p,3,true);
        check(ExileMode.state(p).getCompoundTag("encounter_queue").getLong("due")==due,"repeated clicks neither stack waves nor reset countdown");
        check(due>=200&&due>=100+ExileConfig.spawnInterval*20L,"repeat wave respects grace and configured interval");
        check(ExileEncounters.deadline(0,0,false)==100,"first deadline");
        int old=ExileConfig.spawnInterval;ExileConfig.spawnInterval=1;
        check(ExileEncounters.deadline(101,100,true)>=201,"short configuration cannot bypass five seconds");
        ExileConfig.spawnInterval=old;
        ExileEncounters.cancel(p);
        check(!ExileMode.state(p).getCompoundTag("encounter_queue").getBoolean("pending"),"cleared queue is cancelled");
        report.setInteger("quota_0",0);report.setInteger("quota_1",0);
        w.spawned.clear();w.blockGround=true;
        ExileEncounters.spawnWave(p,3,true);
        check(ExileDebug.report(p).getInteger("wave_0")==1,"blocked ground slots are never replaced by extra Vex");
        check(ExileDebug.report(p).getString("result").equals("partial"),"partial wave reported honestly");
        w.spawned.clear();w.blockGround=false;
        ExileEncounters.spawnWave(p,2,true);
        check(ExileDebug.report(p).getInteger("wave_1")==1&&ExileDebug.report(p).getInteger("wave_2")==0,"second stage creates half the archer slots, with no early wither skeletons");
        w.spawned.clear();report.setInteger("quota_1",0);
        ExileEncounters.spawnWave(p,1,true);
        check(ExileDebug.report(p).getInteger("wave_1")==2&&ExileDebug.report(p).getInteger("wave_0")==0&&ExileDebug.report(p).getInteger("wave_2")==0,"first stage creates only archers with halved frequency");
        w.spawned.clear();w.time=1000;
        ExileMode.state(p).removeTag("encounter_last_attempt");
        ExileEncounters.spawn(p,3,false);w.time=1100;
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.isEmpty()&&!ExileMode.state(p).getCompoundTag("encounter_queue").getBoolean("pending"),"leaving into clean terrain cancels an automatic wave");
        ExileWorldData.get(w).setExposure(0,0,0,ExileConfig.firstSeconds);
        ExileEncounters.spawn(p,1,false);w.time=1120;
        ExileWorldData.get(w).setExposure(0,0,0,ExileConfig.witherSeconds);
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(ExileMode.state(p).getCompoundTag("encounter_queue").getInteger("stage")==3,"stage change updates queued type");
        check(ExileMode.state(p).getCompoundTag("encounter_queue").getLong("due")>=1220,"stage change gets a fresh five-second grace");
        p.dimension=1;ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(!ExileMode.state(p).getCompoundTag("encounter_queue").getBoolean("pending"),"dimension change cannot move a queued wave");
        p.dimension=0;
        ExileEncounters.spawn(p,3,true);
        ExileEncounters.logout(new net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent(p));
        check(!ExileMode.state(p).getCompoundTag("encounter_queue").getBoolean("pending"),"logout cancels queued reinforcement");
        // Fill the first-stage population, then kill two natural pursuers at different times.
        w.spawned.clear();w.blockGround=false;w.time=2000;
        ExileWorldData.get(w).setExposure(0,0,0,ExileConfig.firstSeconds);
        report.setInteger("quota_1",0);
        for(int i=0;i<20;i++) ExileEncounters.spawnWave(p,1,true);
        check(w.spawned.size()==ExileEncounters.cap(1),"first stage is full before the deaths");
        EntityLiving first=w.spawned.get(0),second=w.spawned.get(1);
        first.getEntityData().setBoolean("pm_exile_debug",false);
        second.getEntityData().setBoolean("pm_exile_debug",false);
        first.setHealth(0);
        net.minecraftforge.event.entity.living.LivingDeathEvent death=new net.minecraftforge.event.entity.living.LivingDeathEvent(first,net.minecraft.util.DamageSource.GENERIC);
        ExileEncounters.death(death);ExileEncounters.death(death);first.setDead();
        check(ExileMode.state(p).getTagList("encounter_respawns",10).tagCount()==1,"duplicate death event creates exactly one replacement");
        check(ExileMode.state(p).getTagList("encounter_respawns",10).getCompoundTagAt(0).getLong("due")==2300,"stage one waits fifteen seconds FROM death");
        w.time=2100;second.setHealth(0);
        ExileEncounters.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(second,net.minecraft.util.DamageSource.GENERIC));second.setDead();
        check(ExileMode.state(p).getTagList("encounter_respawns",10).getCompoundTagAt(0).getLong("due")==2300,"another death does not postpone an existing countdown");
        for(int i=0;i<20;i++) ExileEncounters.spawnWave(p,1,false);
        check(w.spawned.size()==6,"ordinary waves cannot refill the two reserved slots");
        w.time=2299;ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.size()==6,"no replacement just before the deadline");
        ExileMode.state(p).setBoolean("debug_waves_pause",true);
        w.time=2300;ExileEncounters.spawn(p,1,false);
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.size()==6&&!ExileMode.state(p).getCompoundTag("encounter_queue").getBoolean("pending"),"tool pause stops both ordinary waves and mature replacements");
        ExileMode.state(p).setBoolean("debug_waves_pause",false);
        w.time=2300;ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.size()==7&&ExileMode.state(p).getTagList("encounter_respawns",10).tagCount()==1,"only the first replacement appears at its deadline");
        w.time=2399;ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.size()==7,"second independent timer is still pending");
        w.time=2400;ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.size()==8&&ExileMode.state(p).getTagList("encounter_respawns",10).tagCount()==0,"second replacement restores the cap at its own deadline");
        // All species use the same stage delay, irrespective of their introduction order.
        for(int stage=1;stage<=4;stage++) {
            double exposure=stage==1?ExileConfig.firstSeconds:stage==2?ExileConfig.fleeSeconds:stage==3?ExileConfig.witherSeconds:ExileConfig.fullSeconds;
            ExileWorldData.get(w).setExposure(0,0,0,exposure);w.time=3000;
            ExileMode.state(p).removeTag("encounter_respawns");
            for(int k=0;k<3;k++) {
                EntityLiving mob=ExileEncounters.createMob(w,k);
                mob.getEntityData().setBoolean(ExileMode.PURSUER,true);
                mob.getEntityData().setUniqueId("pm_exile_owner",p.getUniqueID());
                ExileEncounters.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(mob,net.minecraft.util.DamageSource.GENERIC));
                long expected=3000+(stage==1?300:stage==2?200:100);
                check(ExileMode.state(p).getTagList("encounter_respawns",10).getCompoundTagAt(k).getLong("due")==expected,"stage "+stage+" controls timer of kind "+k);
            }
        }
        // A failed placement retains its ticket; clean terrain never triggers a replacement.
        ExileMode.state(p).removeTag("encounter_respawns");w.spawned.clear();w.time=4000;
        ExileWorldData.get(w).setExposure(0,0,0,ExileConfig.firstSeconds);
        EntityLiving blocked=ExileEncounters.createMob(w,1);
        blocked.getEntityData().setBoolean(ExileMode.PURSUER,true);
        blocked.getEntityData().setUniqueId("pm_exile_owner",p.getUniqueID());
        ExileEncounters.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(blocked,net.minecraft.util.DamageSource.GENERIC));
        w.time=4300;ExileWorldData.get(w).setExposure(0,0,0,0);
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.isEmpty()&&ExileMode.state(p).getTagList("encounter_respawns",10).tagCount()==1,"clean terrain holds a mature ticket without spawning");
        ExileWorldData.get(w).setExposure(0,0,0,ExileConfig.firstSeconds);w.blockGround=true;
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.isEmpty()&&ExileMode.state(p).getTagList("encounter_respawns",10).tagCount()==1,"failed placement keeps the replacement");
        w.blockGround=false;w.time=4319;
        ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.isEmpty(),"failed placement retries at most once a second");
        w.time=4320;ExileEncounters.queueTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        check(w.spawned.size()==1&&ExileMode.state(p).getTagList("encounter_respawns",10).tagCount()==0,"retry succeeds without losing or duplicating a ticket");
        System.out.println("EncounterWaveSmoke: "+checks+" checks passed; no game launched");
    }
}
