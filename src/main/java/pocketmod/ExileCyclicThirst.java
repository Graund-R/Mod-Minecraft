package pocketmod;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Continuous 0..100 bloodthirst with independently persisted rate, relief and calm randomness. */
public final class ExileCyclicThirst {
    private ExileCyclicThirst() {}
    public static boolean active(EntityPlayerMP player) {
        int override=ExileDebug.enabled(player)?ExileMode.state(player).getInteger("debug_thirst_model"):0;
        return override==3||(override==0&&ExileConfig.cyclicThirst);
    }
    static double bounded(double value,double low,double high) {
        return Double.isFinite(value)?Math.max(low,Math.min(high,value)):low;
    }
    static double percent(NBTTagCompound state) {return bounded(state.getDouble("cycle_thirst"),0,100);}
    static double habit(NBTTagCompound state) {
        return bounded(state.getDouble("cycle_habit_seconds")/Math.max(1,ExileConfig.cyclicHabitSeconds),0,ExileConfig.cyclicHabitMax);
    }
    static double nominalRate(NBTTagCompound state) {
        double bossBonus=Math.min(ExileConfig.cyclicBossCap,state.getCompoundTag("cycle_bosses").getKeySet().size()*ExileConfig.cyclicBossStep);
        return ExileConfig.cyclicBaseRate*(1+bossBonus)*(1+ExileConfig.cyclicHabitRate*habit(state));
    }
    static double ordinaryRelief(NBTTagCompound state) {return 100/(ExileConfig.cyclicKillBase+habit(state));}
    private static double random(NBTTagCompound state,String stream) {
        String key="cycle_rng_"+stream;
        long value=state.getLong(key)+0x9e3779b97f4a7c15L;state.setLong(key,value);
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        value^=value>>>31;
        return (value>>>11)*0x1.0p-53;
    }
    private static int duration(NBTTagCompound state,String stream,int low,int high) {
        return low+(int)(random(state,stream)*(Math.max(low,high)-low+1));
    }
    static void ensure(NBTTagCompound state,long seed) {
        if(state.getBoolean("cycle_initialized"))return;
        state.setBoolean("cycle_initialized",true);
        state.setDouble("cycle_thirst",ExileBloodthirst.fraction(state.getInteger("thirst"),ExileConfig.thirstSeconds)*100);
        state.setLong("cycle_rng_rate",seed);state.setLong("cycle_rng_relief",seed^0x38f71a92cd1L);state.setLong("cycle_rng_calm",seed^0xb28e13712a3L);
        NBTTagCompound bosses=state.getCompoundTag("cycle_bosses");
        for(String key:state.getKeySet())if(key.startsWith("done_boss_")&&state.getBoolean(key))bosses.setBoolean(key.substring(10),true);
        state.setTag("cycle_bosses",bosses);
        state.setDouble("cycle_rate_from",1);
        state.setDouble("cycle_rate_to",1-ExileConfig.cyclicRateRandom+2*ExileConfig.cyclicRateRandom*random(state,"rate"));
        state.setInteger("cycle_rate_left",duration(state,"rate",ExileConfig.cyclicRandomMin,ExileConfig.cyclicRandomMax));
        state.setBoolean("cycle_calm_armed",percent(state)>=ExileConfig.cyclicCalmHigh);
        canonical(state);
    }
    static void ensure(EntityPlayerMP player) {
        NBTTagCompound state=ExileMode.state(player);
        if(state.getBoolean("cycle_initialized"))return;
        long seed=player.getRNG()!=null?player.getRNG().nextLong():0x2156ba739L;
        if(player.getUniqueID()!=null)seed^=player.getUniqueID().getMostSignificantBits()^player.getUniqueID().getLeastSignificantBits();
        ensure(state,seed);
        if(player.world!=null&&!player.world.isRemote&&!state.getCompoundTag("cycle_bosses").getKeySet().isEmpty()) {
            ExileWorldData world=ExileWorldData.get(player.world);
            for(String id:state.getCompoundTag("cycle_bosses").getKeySet())world.bossDefeats.setBoolean(id,true);
            world.markDirty();
        }
    }
    static void canonical(NBTTagCompound state) {
        double value=percent(state);state.setDouble("cycle_thirst",value);
        int limit=Math.max(1,ExileConfig.thirstSeconds);
        state.setInteger("thirst",value>=100?limit:Math.min(limit-1,(int)Math.floor(value*limit/100)));
    }
    static double randomRate(NBTTagCompound state) {
        double from=bounded(state.getDouble("cycle_rate_from"),1-ExileConfig.cyclicRateRandom,1+ExileConfig.cyclicRateRandom);
        double to=bounded(state.getDouble("cycle_rate_to"),1-ExileConfig.cyclicRateRandom,1+ExileConfig.cyclicRateRandom);
        return from+(to-from)*bounded(state.getInteger("cycle_rate_elapsed")/(double)Math.max(1,ExileConfig.cyclicRandomBlend),0,1);
    }
    static void tick(NBTTagCompound state,int speed) {
        for(int i=0;i<Math.max(1,Math.min(60,speed));i++) {
            state.setDouble("cycle_habit_seconds",Math.min(ExileConfig.cyclicHabitMax*ExileConfig.cyclicHabitSeconds,
                    bounded(state.getDouble("cycle_habit_seconds"),0,ExileConfig.cyclicHabitMax*ExileConfig.cyclicHabitSeconds)+1));
            int left=state.getInteger("cycle_rate_left")-1;
            if(left<=0) {
                state.setDouble("cycle_rate_from",randomRate(state));
                state.setDouble("cycle_rate_to",1-ExileConfig.cyclicRateRandom+2*ExileConfig.cyclicRateRandom*random(state,"rate"));
                state.setInteger("cycle_rate_elapsed",0);
                left=duration(state,"rate",ExileConfig.cyclicRandomMin,ExileConfig.cyclicRandomMax);
            }
            state.setInteger("cycle_rate_left",left);
            state.setInteger("cycle_rate_elapsed",Math.min(ExileConfig.cyclicRandomBlend,Math.max(0,state.getInteger("cycle_rate_elapsed"))+1));
            int calm=Math.max(0,state.getInteger("cycle_calm_remaining"));
            double rate=nominalRate(state)*randomRate(state)*(calm>0?ExileConfig.cyclicCalmRate:1);
            double value=Math.min(100,percent(state)+rate/60);
            state.setDouble("cycle_thirst",value);
            if(value>=ExileConfig.cyclicCalmHigh)state.setBoolean("cycle_calm_armed",true);
            state.setInteger("cycle_calm_remaining",Math.max(0,calm-1));
        }
        canonical(state);
    }
    static double weight(double health,double attack,double armor) {
        health=bounded(health,1,1000000);attack=bounded(attack,0,1000000);armor=bounded(armor,0,1000);
        return bounded(Math.sqrt(health/20)*Math.pow(attack/4,.7)*(1+armor/40),.25,4);
    }
    static double kill(NBTTagCompound state,double danger) {
        double before=percent(state);
        if(before>=ExileConfig.cyclicCalmHigh)state.setBoolean("cycle_calm_armed",true);
        double relief=ordinaryRelief(state)*bounded(danger,.25,4)
                *(1-ExileConfig.cyclicReliefRandom+2*ExileConfig.cyclicReliefRandom*random(state,"relief"));
        state.setInteger("cycle_kill_grace",10);
        state.setInteger("thirst_damage_seconds",0);
        if(before>=100) {
            double pending=state.getDouble("cycle_pending_relief")+relief;
            if(pending<25) {state.setDouble("cycle_pending_relief",pending);canonical(state);return 0;}
            state.setDouble("cycle_pending_relief",0);
            state.setDouble("cycle_thirst",Math.max(0,before-pending));
        } else {
            state.setDouble("cycle_pending_relief",0);
            state.setDouble("cycle_thirst",Math.max(0,before-relief));
        }
        if(percent(state)<ExileConfig.cyclicCalmLow&&state.getBoolean("cycle_calm_armed")) {
            state.setInteger("cycle_calm_remaining",Math.max(state.getInteger("cycle_calm_remaining"),duration(state,"calm",ExileConfig.cyclicCalmMin,ExileConfig.cyclicCalmMax)));
            state.setBoolean("cycle_calm_armed",false);
        }
        canonical(state);
        if(percent(state)<100)state.setInteger("thirst_damage_seconds",0);
        return before-percent(state);
    }
    static boolean recordBoss(NBTTagCompound state,String id) {
        if(id==null||id.isEmpty())return false;
        NBTTagCompound bosses=state.getCompoundTag("cycle_bosses");
        if(bosses.getBoolean(id))return false;
        bosses.setBoolean(id,true);state.setTag("cycle_bosses",bosses);return true;
    }
    static boolean victory(NBTTagCompound state,String id) {
        if(!recordBoss(state,id))return false;
        state.setDouble("cycle_thirst",0);state.setDouble("cycle_habit_seconds",0);state.setDouble("cycle_pending_relief",0);state.setInteger("cycle_kill_grace",0);
        state.setInteger("cycle_calm_remaining",ExileConfig.cyclicBossCalm);
        state.setBoolean("cycle_calm_armed",false);state.setInteger("thirst_damage_seconds",0);
        canonical(state);return true;
    }
    static boolean paused(EntityPlayerMP player) {
        return !ExileBloodthirst.eligible(player)||player.isPlayerSleeping()
                ||PocketDimensionManager.isPocketDimension(player.dimension)
                ||ExileMode.state(player).getBoolean("cycle_arena_paused")||ExileSimpleThirst.paused(player);
    }
    static void advance(EntityPlayerMP player) {
        if(!paused(player))tick(ExileMode.state(player),ExileSimpleThirst.speed(player));
    }
    static void debug(EntityPlayerMP player,int action,int value) {
        NBTTagCompound state=ExileMode.state(player);
        if(action==10&&(value==-1||value==1)) {
            double next=bounded(percent(state)+value*10,0,100);state.setDouble("cycle_thirst",next);
            if(next>=ExileConfig.cyclicCalmHigh)state.setBoolean("cycle_calm_armed",true);
        } else if(action==11&&(value==0||value==1)) {
            state.setDouble("cycle_thirst",value*100);state.setBoolean("cycle_calm_armed",value==1);
        } else if(action==12&&(value==-1||value==1)) {
            state.setDouble("cycle_habit_seconds",bounded(state.getDouble("cycle_habit_seconds")+value*ExileConfig.cyclicHabitSeconds,
                    0,ExileConfig.cyclicHabitMax*ExileConfig.cyclicHabitSeconds));
        } else if(action==13) {
            int old=ExileSimpleThirst.speed(player);state.setInteger("debug_thirst_speed",old==1?5:old==5?20:old==20?60:1);
        } else if(action==16)kill(state,1);
        else if(action==18)state.setDouble("cycle_habit_seconds",0);
        else if(action==19) {
            state.setDouble("cycle_thirst",0);state.setDouble("cycle_habit_seconds",0);state.setDouble("cycle_pending_relief",0);state.setInteger("cycle_kill_grace",0);
            state.setInteger("cycle_calm_remaining",0);state.setBoolean("cycle_calm_armed",false);
        }
        if(action!=16){state.setDouble("cycle_pending_relief",0);state.setInteger("cycle_kill_grace",0);}
        canonical(state);state.setInteger("thirst_damage_seconds",0);
    }
    static void snapshot(EntityPlayerMP player) {
        NBTTagCompound state=ExileMode.state(player);
        state.setDouble("cycle_habit",habit(state));state.setDouble("cycle_nominal_rate",nominalRate(state));
        state.setDouble("cycle_rate",paused(player)?0:nominalRate(state)*randomRate(state)*(state.getInteger("cycle_calm_remaining")>0?ExileConfig.cyclicCalmRate:1));
        state.setDouble("cycle_ordinary_relief",ordinaryRelief(state));
        state.setInteger("cycle_boss_count",state.getCompoundTag("cycle_bosses").getKeySet().size());
    }
}
