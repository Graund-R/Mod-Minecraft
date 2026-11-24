package pocketmod;

import java.util.*;
import com.mojang.authlib.GameProfile;
import com.google.common.base.Predicate;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.*;

/** Real vanilla bed entry/wakeup and Forge hooks in an in-memory world; no game process. */
public final class ExileBedsSmoke {
    static int checks;
    static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}
    static class Server extends net.minecraft.server.dedicated.DedicatedServer {
        BedWorld outside,pocket;
        Server(){super(null,null,null,null,null,null);}
        @Override public WorldServer getWorld(int dimension){return dimension==0?outside:pocket;}
    }
    static class BedWorld extends EncounterWaveSmoke.Terrain {
        long day=14000,elapsed=1000;
        Server server;
        @Override public long getWorldTime(){return day;}
        @Override public long getTotalWorldTime(){return elapsed;}
        @Override public void setWorldTime(long time){day=time;}
        @Override public boolean isDaytime(){return !ExileBeds.night(day);}
        @Override public MinecraftServer getMinecraftServer(){return server;}
        @Override public int getActualHeight(){return 256;}
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){blocks.put(pos.toImmutable(),state);return true;}
        @Override public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type,AxisAlignedBB box,Predicate<? super T> filter){
            List<T> result=new ArrayList<>();
            for(EntityLiving mob:spawned)if(type.isInstance(mob)&&box.intersects(mob.getEntityBoundingBox())){
                T cast=type.cast(mob);if(filter==null||filter.apply(cast))result.add(cast);
            }
            return result;
        }
    }
    static class Spawn extends PlayerSetSpawnEvent {
        Spawn(EntityPlayer player,BlockPos pos,boolean forced){super(player,pos,forced);}
        @Override public boolean isCancelable(){return true;}
    }
    static class Click extends PlayerInteractEvent.RightClickBlock {
        Click(EntityPlayer player,BlockPos pos){super(player,EnumHand.MAIN_HAND,pos,EnumFacing.UP,new Vec3d(pos).addVector(.5,.5,.5));}
        @Override public boolean isCancelable(){return true;}
    }
    static class Player extends EntityPlayer {
        String lastMessage="";
        Player(World world){super(world,new GameProfile(new UUID(0,42),"sleeper"));}
        // Forge normally wires these hooks through transformed events. Invoke the same handlers
        // directly here so the test needs neither LaunchWrapper nor a running server.
        @Override public SleepResult trySleep(BlockPos pos) {
            PlayerSleepInBedEvent event=new PlayerSleepInBedEvent(this,pos);ExileBeds.enter(event);
            return event.getResultStatus()==null?super.trySleep(pos):event.getResultStatus();
        }
        @Override public void wakeUpPlayer(boolean immediate,boolean update,boolean spawn) {
            ExileBeds.wake(new PlayerWakeUpEvent(this,immediate,update,spawn));
            super.wakeUpPlayer(immediate,update,spawn);
        }
        @Override public void setSpawnPoint(BlockPos pos,boolean forced) {
            PlayerSetSpawnEvent event=new Spawn(this,pos,forced);ExileBeds.spawn(event);
            if(!event.isCanceled())super.setSpawnPoint(pos,forced);
        }
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isCreative(){return false;}
        @Override public void addStat(net.minecraft.stats.StatBase stat,int value){}
        @Override public void sendStatusMessage(net.minecraft.util.text.ITextComponent text,boolean actionBar){lastMessage=text.getUnformattedText();}
    }
    static BedWorld world(Server server,int dimension)throws Exception {
        BedWorld w=(BedWorld)TrialPlacementSmoke.unsafe.allocateInstance(BedWorld.class);
        w.day=14000;w.elapsed=1000;w.server=server;w.blocks=new HashMap<>();w.chunks=new HashMap<>();w.spawned=new ArrayList<>();w.storage=new MapStorage(null);
        w.info=new WorldInfo(new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.parseWorldType("exile")),"bed-test");
        WorldProvider provider=dimension==0?new WorldProviderSurface():new PocketWorldProvider();provider.setDimension(dimension);
        BloodthirstSmoke.field(provider,WorldProvider.class,"world",w);
        BloodthirstSmoke.field(w,World.class,"provider",provider);BloodthirstSmoke.field(w,World.class,"worldInfo",w.info);
        BloodthirstSmoke.field(w,World.class,"profiler",new net.minecraft.profiler.Profiler());BloodthirstSmoke.field(w,World.class,"rand",new Random(3));
        BloodthirstSmoke.field(w,World.class,"playerEntities",new ArrayList<EntityPlayer>());
        return w;
    }
    static void bed(BedWorld w,BlockPos head,EnumFacing direction){
        w.blocks.put(head,Blocks.BED.getDefaultState().withProperty(BlockBed.PART,BlockBed.EnumPartType.HEAD).withProperty(BlockBed.FACING,direction));
        w.blocks.put(head.offset(direction.getOpposite()),Blocks.BED.getDefaultState().withProperty(BlockBed.PART,BlockBed.EnumPartType.FOOT).withProperty(BlockBed.FACING,direction));
    }
    static Player player(BedWorld w,BlockPos head){Player p=new Player(w);p.setPosition(head.getX()+.5,head.getY(),head.getZ()+.5);w.playerEntities.add(p);return p;}
    static void fullSleep(Player p)throws Exception {BloodthirstSmoke.field(p,EntityPlayer.class,"sleepTimer",100);}
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);

        Server server=(Server)TrialPlacementSmoke.unsafe.allocateInstance(Server.class);
        BedWorld w=world(server,0);server.outside=w;
        ExileWorldData.get(w).enabled=true;ExileWorldData.get(w).seconds=1234;
        BlockPos head=new BlockPos(0,65,0);bed(w,head,EnumFacing.NORTH);Player p=player(w,head);
        NBTTagCompound state=ExileMode.state(p);
        state.setInteger("thirst",359);
        check(ExileBeds.blocker(p,head).isEmpty(),"74.8 percent thirst permits sleep");
        state.setInteger("thirst",360);check(ExileBeds.blocker(p,head).equals("thirst"),"exactly 75 percent thirst denies sleep");state.setInteger("thirst",100);
        ExileWorldData.get(w).setExposure(0,0,0,299);check(ExileBeds.blocker(p,head).isEmpty(),"below 50 percent contamination permits sleep");
        ExileWorldData.get(w).setExposure(0,0,0,300);check(ExileBeds.blocker(p,head).equals("infection"),"exactly 50 percent contamination denies sleep");
        ExileWorldData.get(w).setExposure(0,0,0,0);
        state.setBoolean("trial_active",true);check(ExileBeds.blocker(p,head).equals("boss"),"active trial cannot be bypassed by bed");state.setBoolean("trial_active",false);
        EntityRiftWalker boss=new EntityRiftWalker(w);boss.setPosition(30,10,0);w.spawned.add(boss);
        check(ExileBeds.blocker(p,head).equals("boss"),"boss arena blocks sleep on a different floor");w.spawned.clear();
        EntityLiving pursuer=new net.minecraft.entity.monster.EntitySkeleton(w);pursuer.setPosition(12,65,0);pursuer.getEntityData().setBoolean(ExileMode.PURSUER,true);w.spawned.add(pursuer);
        check(ExileBeds.blocker(p,head).equals("pursuers"),"near tagged pursuer blocks sleep beyond vanilla radius");pursuer.setPosition(50,65,0);
        check(ExileBeds.blocker(p,head).isEmpty(),"distant pursuers do not block every bed");w.spawned.clear();
        BlockPos seam=new BlockPos(15,65,0);bed(w,seam,EnumFacing.WEST);p.setPosition(15.5,65,.5);ExileWorldData.get(w).setExposure(0,1,0,300);
        check(ExileBeds.blocker(p,seam).equals("infection"),"both halves of a bed count across chunk boundaries");ExileWorldData.get(w).setExposure(0,1,0,0);p.setPosition(.5,65,.5);
        w.day=6000;check(p.trySleep(head)==EntityPlayer.SleepResult.NOT_POSSIBLE_NOW,"daytime sleep denied through real vanilla entry");
        w.day=14000;p.setHealth(12);p.getFoodStats().setFoodLevel(20);
        check(p.trySleep(head)==EntityPlayer.SleepResult.OK&&p.isPlayerSleeping(),"safe night enters real vanilla sleep");
        p.wakeUpPlayer(true,true,false);check(p.getHealth()==12&&p.getFoodStats().getFoodLevel()==20,"manual early wake gives no healing");
        state.setInteger("shelter",321);state.setInteger("outside",123);state.setInteger("simple_accel",3);state.setDouble("simple_clock",27);
        NBTTagCompound before=state.copy(),terrain=ExileWorldData.get(w).writeToNBT(new NBTTagCompound());
        check(p.trySleep(head)==EntityPlayer.SleepResult.OK,"second night attempt is allowed after early wake");fullSleep(p);w.day=24000;p.wakeUpPlayer(false,false,true);
        check(p.getHealth()==16&&p.getFoodStats().getFoodLevel()==16,"completed sleep restores two hearts and pays four food points");
        check(state.getInteger("thirst")==before.getInteger("thirst")&&state.getInteger("simple_accel")==3&&state.getDouble("simple_clock")==27,"sleep retains thirst and accumulated acceleration");
        check(state.getInteger("shelter")==321&&state.getInteger("outside")==123,"sleep does not reset shelter duration or cooldown");
        check(terrain.equals(ExileWorldData.get(w).writeToNBT(new NBTTagCompound()))&&w.elapsed==1000,"morning skip adds no elapsed infection time or recovery");
        check(p.getBedLocation()==null,"bed cannot replace resurrection totem spawn");
        p.setHealth(12);w.day=14000;check(p.trySleep(head)==EntityPlayer.SleepResult.OK,"clock manipulation can re-enter sleep but not duplicate reward");fullSleep(p);w.day=24000;p.wakeUpPlayer(false,false,true);
        check(p.getHealth()==12&&p.getFoodStats().getFoodLevel()==16,"same night reward cannot be farmed");
        p.getFoodStats().setFoodLevel(6);w.day=62000;p.trySleep(head);fullSleep(p);w.day=72000;p.wakeUpPlayer(false,false,true);
        check(p.getHealth()==12&&p.getFoodStats().getFoodLevel()==6,"sleep remains possible without spare food but gives no free healing");
        p.setHealth(19);p.getFoodStats().setFoodLevel(20);check(ExileBeds.recover(p)==1&&p.getHealth()==20&&p.getFoodStats().getFoodLevel()==19,"healing and food cost respect missing health");
        PlayerSetSpawnEvent command=new Spawn(p,head,true);ExileBeds.spawn(command);check(!command.isCanceled(),"explicit forced spawn remains available");
        PlayerSetSpawnEvent plain=new Spawn(p,new BlockPos(100,65,100),false);ExileBeds.spawn(plain);check(!plain.isCanceled(),"non-bed spawn sources remain available");
        BedWorld pocket=world(server,100);server.pocket=pocket;BlockPos pocketBed=new BlockPos(0,65,0);bed(pocket,pocketBed,EnumFacing.NORTH);Player q=player(pocket,pocketBed);q.setHealth(12);q.getFoodStats().setFoodLevel(20);
        w.day=14000;pocket.day=14000;
        check(!pocket.provider.isSurfaceWorld(),"pocket stays non-surface outside bed interaction");
        PlayerInteractEvent.RightClickBlock click=new Click(q,pocketBed);
        PocketRules.onBedClick(click);
        check(click.isCanceled()&&click.getCancellationResult()==EnumActionResult.SUCCESS&&q.isPlayerSleeping(),"pocket bed uses real vanilla setup without exploding");
        check(!pocket.provider.isSurfaceWorld()&&!ExileBeds.pocketSurfaceCheck(),"pocket surface override never leaks after interaction");
        fullSleep(q);
        ExileBeds.prepareWorld(pocket);
        check(!pocket.areAllPlayersAsleep(),"private pocket cannot skip its night independently");
        check(!ExileBeds.finishNight(Arrays.asList(w,pocket))&&w.day==14000&&q.isPlayerSleeping(),"awake outside player prevents a private pocket from skipping the shared night");
        check(p.trySleep(head)==EntityPlayer.SleepResult.OK,"outside player can join pocket sleeper");fullSleep(p);
        w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
        check(!ExileBeds.finishNight(Arrays.asList(w,pocket))&&w.day==14000,"frozen daylight cannot grant a completed night");
        w.getGameRules().setOrCreateGameRule("doDaylightCycle","true");
        check(ExileBeds.finishNight(Arrays.asList(w,pocket)),"night completes when outside and pocket players are fully asleep");
        check(w.day==24000&&pocket.day==24000&&w.elapsed==1000,"pocket sleep advances outside daylight without elapsed-time jump");
        check(q.getHealth()==16&&q.getBedLocation(100)==null,"pocket sleep heals but never becomes a respawn point");
        Click denied=new Click(q,pocketBed);denied.setCanceled(true);PocketRules.onBedClick(denied);
        check(!q.isPlayerSleeping()&&!ExileBeds.pocketSurfaceCheck(),"canceled access cannot be bypassed by manual pocket bed activation");
        w.day=14000;pocket.day=6000;
        SleepingTimeCheckEvent pocketTime=new SleepingTimeCheckEvent(q,pocketBed);ExileBeds.time(pocketTime);
        check(pocketTime.getResult()==net.minecraftforge.fml.common.eventhandler.Event.Result.ALLOW,"pocket sleep follows outside night rather than its artificial daylight");
        ExileWorldData.get(w).enabled=false;
        PlayerSleepInBedEvent legacy=new PlayerSleepInBedEvent(q,pocketBed);PocketRules.onSleep(legacy);check(legacy.getResultStatus()==EntityPlayer.SleepResult.NOT_POSSIBLE_HERE,"normal mode retains pocket sleep restriction");

        p.setHealth(10);w.day=14000;
        check(p.trySleep(head)==EntityPlayer.SleepResult.OK,"ordinary world keeps vanilla sleep when Exile is disabled");
        fullSleep(p);w.day=24000;p.wakeUpPlayer(false,false,true);
        check(head.equals(p.getBedLocation())&&p.getHealth()==10,"normal-mode bed spawn and lack of bonus healing are preserved");
        System.out.println("ExileBedsSmoke: "+checks+" bed entry, healing, pocket, spawn and clock checks passed; no game launched");
    }
}
