package pocketmod;

import net.minecraft.nbt.*;
import java.io.*;

/** Exact formula, persistent randomness and independent model migration, without Minecraft. */
public final class CyclicThirstSmoke {
    static int checks;
    static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    static void near(double a,double b,String text){check(Math.abs(a-b)<.00001,text+": "+a+" / "+b);}
    static NBTTagCompound fresh(long seed,double value,double habit) {
        NBTTagCompound s=new NBTTagCompound();ExileCyclicThirst.ensure(s,seed);
        s.setDouble("cycle_thirst",value);s.setDouble("cycle_habit_seconds",habit*1800);ExileCyclicThirst.canonical(s);return s;
    }
    static void seconds(NBTTagCompound s,int count){for(int i=0;i<count;i++)ExileCyclicThirst.tick(s,1);}
    public static void main(String[] args)throws Exception {
        check(ExileConfig.cyclicThirst,"cyclic model is the actual default");
        near(ExileConfig.cyclicBaseRate,10,"ten points per minute");
        check(ExileConfig.cyclicHabitSeconds==1800&&ExileConfig.cyclicBossCalm==180,"agreed habit and boss timings");
        ExileConfig.cyclicRateRandom=0;ExileConfig.cyclicReliefRandom=0;
        NBTTagCompound s=fresh(7,0,0);seconds(s,60);
        near(ExileCyclicThirst.percent(s),10+1.5*61/3600.0,"integrated fill includes continuously rising habit");
        near(ExileCyclicThirst.habit(s),1/30.0,"first minute of continuous habit");
        seconds(s,1740);near(ExileCyclicThirst.habit(s),1,"one habit level per thirty minutes");
        seconds(s,10000);near(ExileCyclicThirst.habit(s),6,"habit has a hard upper bound");
        near(ExileCyclicThirst.nominalRate(s),19,"maximum pre-boss fill rate");
        s=fresh(8,60,0);near(ExileCyclicThirst.kill(s,1),100/3.0,"ordinary initial relief");
        check(s.getInteger("cycle_calm_remaining")==0,"partial relief above fifteen gives no calm");
        ExileCyclicThirst.kill(s,1);near(ExileCyclicThirst.percent(s),0,"hunting can fully sate thirst");
        int calm=s.getInteger("cycle_calm_remaining");check(calm>=45&&calm<=75,"ordinary calm duration bounded");
        ExileCyclicThirst.kill(s,1);check(s.getInteger("cycle_calm_remaining")==calm,"killing at zero cannot renew calm");
        s=fresh(9,20,0);ExileCyclicThirst.kill(s,1);check(s.getInteger("cycle_calm_remaining")==0,"calm requires a prior high wave");
        s=fresh(10,0,0);s.setInteger("cycle_calm_remaining",60);ExileCyclicThirst.tick(s,1);
        near(ExileCyclicThirst.percent(s),(10*(1+.15/1800))/240,"calm fills four times slower");
        check(s.getInteger("cycle_calm_remaining")==59,"calm uses active seconds");
        s=fresh(11,100,6);near(ExileCyclicThirst.kill(s,1),100/9.0,"late ordinary relief");
        near(ExileCyclicThirst.habit(s),6,"ordinary kills do not erase accumulated habit");
        near(ExileCyclicThirst.weight(20,4,0),1,"ordinary danger normalization");
        check(ExileCyclicThirst.weight(20,12,0)>ExileCyclicThirst.weight(20,4,0),"attack increases relief");
        check(ExileCyclicThirst.weight(40,4,0)>1&&ExileCyclicThirst.weight(20,4,20)>1,"health and armor contribute");
        near(ExileCyclicThirst.weight(1000000,1000000,1000),4,"boss-sized weights remain capped");
        near(ExileCyclicThirst.weight(1,0,0),.25,"weak dangerous creatures have a bounded minimum");
        s=fresh(12,95,6);check(ExileCyclicThirst.victory(s,"boss:a"),"new boss is a first victory");
        check(ExileCyclicThirst.percent(s)==0&&ExileCyclicThirst.habit(s)==0&&s.getInteger("cycle_calm_remaining")==180,"new boss resets thirst and habit and grants three-minute calm");
        near(ExileCyclicThirst.nominalRate(s),10.5,"new cycle base increases five percent");
        s.setDouble("cycle_thirst",95);s.setDouble("cycle_habit_seconds",3600);s.setInteger("cycle_calm_remaining",0);
        check(!ExileCyclicThirst.victory(s,"boss:a"),"same boss type cannot give another first-victory reset");
        check(ExileCyclicThirst.percent(s)==95&&ExileCyclicThirst.habit(s)==2,"repeat boss preserves habit");
        ExileCyclicThirst.kill(s,4);near(ExileCyclicThirst.percent(s),15,"repeat boss still gives strong ordinary relief");
        for(int i=0;i<20;i++)ExileCyclicThirst.victory(s,"boss:"+i);
        near(ExileCyclicThirst.nominalRate(s),15,"permanent boss bonus stops at fifty percent");
        s=fresh(3,99.99,0);check(s.getInteger("thirst")<ExileConfig.thirstSeconds,"rounding cannot activate frenzy before a full bar");
        s.setDouble("cycle_thirst",100);ExileCyclicThirst.canonical(s);check(s.getInteger("thirst")==ExileConfig.thirstSeconds,"exactly full maps to existing frenzy and damage");
        ExileConfig.cyclicRateRandom=.15f;ExileConfig.cyclicReliefRandom=.1f;
        NBTTagCompound a=fresh(123,60,0),b=a.copy();
        seconds(a,117);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(a,bytes);
        NBTTagCompound restored=CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        for(int i=0;i<2000;i++) {
            ExileCyclicThirst.tick(a,1);ExileCyclicThirst.tick(restored,1);
            if(i%33==0){ExileCyclicThirst.kill(a,1.5);ExileCyclicThirst.kill(restored,1.5);}
        }
        check(a.equals(restored),"NBT reload preserves every rate, relief and calm random outcome");
        a=fresh(321,0,0);b=a.copy();
        for(int i=0;i<600;i++){ExileCyclicThirst.tick(a,1);ExileCyclicThirst.tick(b,1);if(i%17==0)ExileCyclicThirst.kill(a,1);}
        check(a.getLong("cycle_rng_rate")==b.getLong("cycle_rng_rate"),"hunting cannot reroll fill-rate randomness");
        near(ExileCyclicThirst.randomRate(a),ExileCyclicThirst.randomRate(b),"rate stream is independent of kill count");
        a=fresh(34,0,0);b=a.copy();ExileCyclicThirst.tick(a,60);seconds(b,60);check(a.equals(b),"fast test time matches identical one-second simulation");
        for(int seed=1;seed<=20;seed++) {
            NBTTagCompound run=fresh(seed,60,0);double last=ExileCyclicThirst.randomRate(run);
            for(int i=0;i<2000;i++) {
                ExileCyclicThirst.tick(run,1);double rate=ExileCyclicThirst.randomRate(run);
                if(rate<.84999||rate>1.15001||Math.abs(rate-last)>.03001)throw new AssertionError("randomness bounds/smoothing");
                last=rate;if(i%73==0)ExileCyclicThirst.kill(run,1);
                if(ExileCyclicThirst.percent(run)<0||ExileCyclicThirst.percent(run)>100)throw new AssertionError("bar bounds");
            }
        }
        check(true,"forty thousand production ticks keep bounded smooth randomness and bar limits");
        s=fresh(5,50,0);s.setDouble("cycle_habit_seconds",Double.NaN);s.setDouble("cycle_rate_from",Double.NaN);s.setDouble("cycle_rate_to",Double.POSITIVE_INFINITY);s.setInteger("cycle_rate_elapsed",Integer.MIN_VALUE);ExileCyclicThirst.tick(s,1);
        check(Double.isFinite(ExileCyclicThirst.percent(s))&&Double.isFinite(ExileCyclicThirst.randomRate(s)),"corrupt numeric state is sanitized");
        s=new NBTTagCompound();s.setInteger("thirst",240);s.setBoolean("done_boss_minecraft:wither",true);ExileCyclicThirst.ensure(s,1);
        near(ExileCyclicThirst.percent(s),50,"migration keeps existing thirst fraction");
        check(!ExileCyclicThirst.victory(s,"minecraft:wither"),"old boss achievement cannot become a new reset");
        near(ExileCyclicThirst.nominalRate(s),10.5,"old boss progress migrates to the bounded baseline");
        System.out.println("CyclicThirstSmoke: "+checks+" formula, migration and persistent-randomness checks passed; no game launched");
    }
}
