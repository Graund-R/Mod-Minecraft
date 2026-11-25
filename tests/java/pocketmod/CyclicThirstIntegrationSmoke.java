package pocketmod;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.monster.*;
import net.minecraft.nbt.*;
import net.minecraft.util.DamageSource;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import net.minecraftforge.event.entity.living.*;

/** Production entry points with real entities in an in-memory world; no client/server process. */
public final class CyclicThirstIntegrationSmoke {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static void near(double a,double b,String text){check(Math.abs(a-b)<.00001,text+": "+a+" / "+b);}
    static class Terrain extends EncounterWaveSmoke.Terrain {@Override public int getActualHeight(){return 256;}}
    static class Player extends EncounterWaveSmoke.Player {
        boolean sleeping;
        @Override public boolean isPlayerSleeping(){return sleeping;}
    }
    static class Attempt extends LivingAttackEvent {
        Attempt(EntityLivingBase target,DamageSource source,float damage){super(target,source,damage);}
        @Override public boolean isCancelable(){return true;}
    }
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        ExileConfig.cyclicThirst=true;ExileConfig.discoveryChance=0;
        Terrain world=(Terrain)TrialPlacementSmoke.unsafe.allocateInstance(Terrain.class);
        world.blocks=new HashMap<>();world.chunks=new HashMap<>();world.spawned=new ArrayList<>();world.storage=new MapStorage(null);world.time=1000;
        world.info=new WorldInfo(new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.parseWorldType("exile")),"cycle-test");
        BloodthirstSmoke.field(world,World.class,"provider",new WorldProviderSurface());BloodthirstSmoke.field(world,World.class,"worldInfo",world.info);
        BloodthirstSmoke.field(world,World.class,"profiler",new net.minecraft.profiler.Profiler());BloodthirstSmoke.field(world,World.class,"rand",new Random(7));
        Player player=(Player)TrialPlacementSmoke.unsafe.allocateInstance(Player.class);player.world=world;player.posY=65;player.data=new NBTTagCompound();player.capabilities=new PlayerCapabilities();
        player.attributes=new AttributeMap();player.attributes.registerAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20);
        player.attributes.registerAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.1);player.attributes.registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4);
        net.minecraft.network.datasync.EntityDataManager manager=new net.minecraft.network.datasync.EntityDataManager(player);
        BloodthirstSmoke.field(player,Entity.class,"dataManager",manager);
        java.lang.reflect.Field health=EntityLivingBase.class.getDeclaredField("HEALTH");health.setAccessible(true);
        manager.register((net.minecraft.network.datasync.DataParameter<Float>)health.get(null),20f);
        BloodthirstSmoke.field(player,Entity.class,"rand",new Random(3));BloodthirstSmoke.field(world,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(player)));
        NBTTagCompound state=ExileMode.state(player);state.setInteger("thirst",240);
        ExileSimpleThirst.ensureModel(player);
        check(state.getBoolean("thirst_cyclic_active")&&!state.getBoolean("thirst_simple_active"),"default runtime dispatch selects cyclic model");
        near(ExileCyclicThirst.percent(state),50,"runtime migration keeps the old bar fraction");
        ExileBloodthirst.advance(player);check(ExileCyclicThirst.percent(state)>50&&ExileCyclicThirst.habit(state)>0,"actual accumulation entry point advances new formula");
        player.sleeping=true;NBTTagCompound before=state.copy();ExileBloodthirst.advance(player);check(before.equals(state),"sleep freezes habit, calm and all random streams");player.sleeping=false;
        player.dimension=100;before=state.copy();ExileBloodthirst.advance(player);check(before.equals(state),"pocket freezes the full new model");player.dimension=0;
        state.setBoolean("cycle_arena_paused",true);before=state.copy();ExileBloodthirst.advance(player);check(before.equals(state),"protected arena freezes the full new model");state.setBoolean("cycle_arena_paused",false);
        player.creative=true;before=state.copy();ExileBloodthirst.advance(player);check(before.equals(state),"creative does not accumulate habit or random time");player.creative=false;
        state.setBoolean("cured",true);before=state.copy();ExileBloodthirst.advance(player);check(before.equals(state),"cure freezes the model");state.setBoolean("cured",false);
        state.setBoolean("debug_enabled",true);state.setBoolean("debug_thirst_pause",true);before=state.copy();ExileBloodthirst.advance(player);check(before.equals(state),"F8 pause freezes the new model");state.setBoolean("debug_thirst_pause",false);
        state.setDouble("cycle_thirst",72.4);ExileCyclicThirst.canonical(state);double habit=ExileCyclicThirst.habit(state);long random=state.getLong("cycle_rng_rate");
        state.setInteger("debug_thirst_model",1);ExileSimpleThirst.ensureModel(player);state.setInteger("simple_points",7);ExileSimpleThirst.canonical(state);
        state.setInteger("debug_thirst_model",2);ExileSimpleThirst.ensureModel(player);state.setInteger("thirst",250);
        state.setInteger("debug_thirst_model",3);ExileSimpleThirst.ensureModel(player);
        near(ExileCyclicThirst.percent(state),72.4,"returning from test models restores independent cyclic progress");
        near(ExileCyclicThirst.habit(state),habit,"test-model switching preserves habit");check(state.getLong("cycle_rng_rate")==random,"test-model switching cannot reroll rate randomness");
        state.setInteger("debug_thirst_model",2);ExileSimpleThirst.ensureModel(player);check(state.getInteger("thirst")==250,"legacy state remains independent");
        state.setInteger("debug_thirst_model",1);ExileSimpleThirst.ensureModel(player);check(state.getInteger("simple_points")==7,"simple test state remains independent");
        state.setInteger("debug_thirst_model",3);ExileSimpleThirst.ensureModel(player);
        ExileSimpleThirst.snapshot(player);check(state.getDouble("cycle_rate")>0&&state.getDouble("cycle_ordinary_relief")>0,"live test-tool values use new formula");
        EntitySkeleton archer=new EntitySkeleton(world);archer.setPosition(2,65,0);
        double initialDanger=ExileBloodthirst.danger(archer);
        Attempt blocked=new Attempt(player,DamageSource.causeMobDamage(archer),14);blocked.setCanceled(true);ExileBloodthirst.aggression(blocked);
        check(archer.getEntityData().getDouble("pm_attack_potential")==14&&ExileBloodthirst.danger(archer)>initialDanger,"blocked raw attack increases danger without requiring lost health");
        near(player.getHealth(),20,"danger evaluation does not damage the player");
        ExileBloodthirst.damage(new LivingDamageEvent(archer,DamageSource.causePlayerDamage(player),8));
        check(ExileBloodthirst.cyclicCredit(player,archer,false),"meaningful combat contribution credits an indirect finish");
        world.time=1601;check(!ExileBloodthirst.cyclicCredit(player,archer,false),"old combat cannot credit a later automated kill");world.time=1000;
        net.minecraft.entity.passive.EntityCow cow=new net.minecraft.entity.passive.EntityCow(world);cow.setPosition(2,65,0);
        check(!ExileBloodthirst.cyclicCredit(player,cow,true),"safe passive animals grant no relief");
        state.setDouble("cycle_thirst",80);state.setDouble("cycle_habit_seconds",3600);ExileCyclicThirst.canonical(state);
        ExileBloodthirst.rewardCyclic(player,archer);check(ExileCyclicThirst.percent(state)<80&&ExileCyclicThirst.habit(state)==2,"runtime ordinary reward lowers thirst but keeps habit");
        EntityRiftWalker boss=new EntityRiftWalker(world);boss.setPosition(2,10,0);world.spawned.add(boss);
        check(ExileBossArenas.protects(player),"undefeated arena protects thirst on another floor");
        check(ExileBloodthirst.rewardCyclic(player,boss),"actual boss reward recognizes first victory");
        check(ExileCyclicThirst.percent(state)==0&&ExileCyclicThirst.habit(state)==0&&state.getInteger("cycle_calm_remaining")==180,"actual boss reward resets bar and habit and grants calm");
        check(ExileWorldData.get(world).bossDefeats.getBoolean("pocketmod:rift_walker")&&!ExileBossArenas.protects(player),"repeat summon cannot restore arena protection");
        ExileWorldData restored=new ExileWorldData("reload");restored.readFromNBT(ExileWorldData.get(world).writeToNBT(new NBTTagCompound()));
        check(restored.bossDefeats.getBoolean("pocketmod:rift_walker"),"arena victory persists through save/reload");
        state.setDouble("cycle_thirst",95);state.setDouble("cycle_habit_seconds",3600);state.setInteger("cycle_calm_remaining",0);ExileCyclicThirst.canonical(state);
        check(!ExileBloodthirst.rewardCyclic(player,boss)&&ExileCyclicThirst.habit(state)==2,"repeat boss gives relief without resetting habit");
        state.setDouble("cycle_thirst",100);state.setInteger("cycle_kill_grace",0);state.setInteger("cycle_calm_remaining",0);ExileCyclicThirst.canonical(state);state.setInteger("thirst_damage_seconds",0);
        ExileBloodthirst.advance(player);ExileBloodthirst.advance(player);
        check(player.damageCalls==1&&player.getHealth()==19,"full cyclic bar retains half-heart damage every two seconds");
        check(player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getModifier(ExileBloodthirst.STRENGTH)!=null,"full cyclic bar activates existing frenzy");
        state.setDouble("cycle_habit_seconds",10800);
        ExileCyclicThirst.kill(state,1);ExileBloodthirst.refreshModifiers(player);
        int hits=player.damageCalls;
        for(int i=0;i<10;i++)ExileBloodthirst.advance(player);
        check(player.damageCalls==hits,"all ten seconds after kill suppress thirst damage");
        check(player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getModifier(ExileBloodthirst.STRENGTH)!=null,"banked relief keeps strength during grace");
        ExileBloodthirst.advance(player);ExileBloodthirst.advance(player);
        check(player.damageCalls==hits+1,"damage resumes after grace expires");
        ExileCyclicThirst.kill(state,4);ExileBloodthirst.refreshModifiers(player);
        check(state.getInteger("thirst_damage_seconds")==0&&player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getModifier(ExileBloodthirst.STRENGTH)==null,"relief clears damage cadence and frenzy");
        player.data=new NBTTagCompound();NBTTagCompound old=ExileMode.state(player);
        old.setBoolean("done_boss_minecraft:wither",true);old.setInteger("thirst",240);
        ExileSimpleThirst.ensureModel(player);
        check(ExileWorldData.get(world).bossDefeats.getBoolean("minecraft:wither"),"old boss achievements also migrate arena protection history");
        System.out.println("CyclicThirstIntegrationSmoke: "+checks+" runtime, combat, boss, pause and UI-data checks passed; no game launched");
    }
}
