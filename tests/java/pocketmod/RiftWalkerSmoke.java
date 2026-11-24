package pocketmod;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;

/** In-memory entity lifecycle, reward and attack boundaries; never starts the game. */
public final class RiftWalkerSmoke {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static class Boss extends EntityRiftWalker {
        ItemStack reward = ItemStack.EMPTY;
        Boss(World w){super(w);}
        @Override protected ResourceLocation getLootTable(){return null;}
        @Override public EntityItem entityDropItem(ItemStack stack,float offset){reward=stack;return null;}
        void reward(boolean player){dropLoot(player,0,net.minecraft.util.DamageSource.GENERIC);}
    }
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        EncounterWaveSmoke.Terrain w=(EncounterWaveSmoke.Terrain)TrialPlacementSmoke.unsafe.allocateInstance(EncounterWaveSmoke.Terrain.class);
        w.blocks=new HashMap<>();w.chunks=new HashMap<>();w.spawned=new ArrayList<>();w.storage=new MapStorage(null);
        w.info=new WorldInfo(new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.DEFAULT),"boss-test");
        BloodthirstSmoke.field(w,World.class,"provider",new WorldProviderSurface());
        BloodthirstSmoke.field(w,World.class,"worldInfo",w.info);
        BloodthirstSmoke.field(w,World.class,"profiler",new net.minecraft.profiler.Profiler());
        BloodthirstSmoke.field(w,World.class,"rand",new Random(4));
        BloodthirstSmoke.field(w,World.class,"playerEntities",new ArrayList<>());
        Boss boss=new Boss(w);
        check(boss.getMaxHealth()==120 && boss.getHealth()==120,"starts with full boss health");
        check(!boss.isNonBoss() && boss.isNoDespawnRequired(),"boss identity and persistence");
        check(boss.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()==6,"base damage");
        check(!boss.enraged(),"first phase");boss.setHealth(60);check(boss.enraged(),"phase threshold");
        boss.setPosition(1,65,1);
        NBTTagCompound save=new NBTTagCompound();boss.writeEntityToNBT(save);
        Boss restored=new Boss(w);restored.readEntityFromNBT(save);
        check(restored.getHealth()==60 && restored.enraged(),"save keeps second phase");
        boss.reward(false);check(boss.reward.isEmpty(),"environment kill does not award scroll");
        boss.reward(true);check(AbilityDiscoveryItem.id(boss.reward).equals("teleport"),"player kill guarantees teleport scroll");
        boss.reward=ItemStack.EMPTY;boss.reward(true);check(AbilityDiscoveryItem.id(boss.reward).equals("teleport"),"repeat victory replaces lost reward");
        check(EntityRiftWalker.inStrike(2.79,0,0,2.8),"inside warning circle");
        check(!EntityRiftWalker.inStrike(2.81,0,0,2.8),"escaping warning avoids strike");
        check(!EntityRiftWalker.inStrike(2,0,2,2.8),"circle excludes bounding square corners");
        check(!EntityRiftWalker.inStrike(0,3,0,2.8),"other floor protected");
        java.lang.reflect.Field charge=EntityRiftWalker.class.getDeclaredField("charge");charge.setAccessible(true);
        charge.setInt(boss,20);check(boss.isAIDisabled(),"warning stops pursuit and melee AI");
        NBTTagCompound chargingSave=new NBTTagCompound();boss.writeEntityToNBT(chargingSave);
        check(!chargingSave.getBoolean("NoAI"),"temporary windup never persists as disabled AI");
        Boss reloadedCharge=new Boss(w);reloadedCharge.readEntityFromNBT(chargingSave);
        check(!reloadedCharge.isAIDisabled(),"saving mid-attack does not freeze boss after load");
        boss.setNoAI(true);boss.writeEntityToNBT(chargingSave);
        check(chargingSave.getBoolean("NoAI"),"explicit administrator NoAI is preserved");boss.setNoAI(false);
        charge.setInt(boss,0);check(!boss.isAIDisabled(),"AI resumes during recovery");
        java.lang.reflect.Field wind=EntityRiftWalker.class.getDeclaredField("WINDUP");wind.setAccessible(true);
        java.lang.reflect.Field pulse=EntityRiftWalker.class.getDeclaredField("PULSE");pulse.setAccessible(true);
        net.minecraft.network.datasync.DataParameter<Integer> windKey=(net.minecraft.network.datasync.DataParameter<Integer>)wind.get(null);
        net.minecraft.network.datasync.DataParameter<Boolean> pulseKey=(net.minecraft.network.datasync.DataParameter<Boolean>)pulse.get(null);
        boss.getDataManager().set(windKey,30);
        check(boss.preparing() && boss.windup(0)==0,"warning starts at first pose");
        boss.getDataManager().set(windKey,15);
        check(Math.abs(boss.windup(0)-0.5F)<0.001F,"synchronized halfway pose");
        boss.getDataManager().set(pulseKey,true);check(boss.preparingPulse(),"wave pose distinct from teleport");
        boss.getDataManager().set(windKey,0);check(!boss.preparingPulse() && boss.windup(0)==0,"no stale warning after cancellation");
        boss.handleStatusUpdate((byte)60);check(boss.impact(0)==1,"successful impact triggers recoil");
        boss.handleStatusUpdate((byte)61);check(boss.meleeSwing(4)>0.99F,"melee event triggers full reach");
        boss.setDead();check(boss.isDead,"safe removal");
        System.out.println("RiftWalkerSmoke: lifecycle, phases, save, rewards and strike boundaries passed; no game launched");
    }
}
