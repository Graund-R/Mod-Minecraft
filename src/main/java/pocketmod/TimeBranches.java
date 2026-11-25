package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/** One ability family; branch unlocks and purchases remain independent. */
public final class TimeBranches {
    static final String PAID="time_mana_paid";
    private TimeBranches(){}
    static boolean child(AbilityType branch){return branch==AbilityType.TIME_REWIND||branch==AbilityType.TIME_FUTURE;}
    public static boolean unlocked(EntityPlayer player,AbilityType branch){
        return AbilityStorage.isUnlocked(player,AbilityType.SLOW)&&(!child(branch)||AbilityStorage.isUnlocked(player,branch));
    }
    static int level(EntityPlayer player,AbilityType branch){return unlocked(player,branch)?AbilityStorage.getLevel(player,branch):0;}
    static double at(double[] values,int level){double v=level<=0?0:values[Math.min(level,values.length)-1];return Double.isFinite(v)?Math.max(0,v):0;}
    static int rewindTicks(int level){return (int)Math.max(0,Math.min(400,at(PocketConfig.timeRewindSeconds,level)*20));}
    static int futureTicks(int level){return (int)Math.max(0,Math.min(400,at(PocketConfig.timeFutureSeconds,level)*20));}
    static double rewindRate(int level){return at(PocketConfig.timeRewindMana,level);}
    static double futureRate(int level){return at(PocketConfig.timeFutureMana,level);}
    static double futureEntry(int level){return at(PocketConfig.timeFutureEntry,level);}
    static double slowDiscount(int level){return level>=3?.5:level==2?.75:1;}
    static boolean pay(EntityPlayer player,double amount){
        return payExact(player,Math.max(0,amount)*Mana.getPriceMultiplier(player));
    }
    static boolean payExact(EntityPlayer player,double amount){
        if(!Double.isFinite(amount)||amount<0)return false;
        if(!Mana.spendAbility(player,amount))return false;
        NBTTagCompound root=PlayerData.root(player);root.setDouble(PAID,root.getDouble(PAID)+amount);return true;
    }
    /** The ability cannot refund its own payment by rewinding to before that payment. */
    static NBTTagCompound restoreCost(NBTTagCompound recorded,NBTTagCompound current){
        NBTTagCompound result=recorded.copy();
        double paid=Math.max(recorded.getDouble(PAID),current.getDouble(PAID));
        double debt=Math.max(0,paid-recorded.getDouble(PAID));
        result.setDouble("pocketmod_mana",Math.max(0,recorded.getDouble("pocketmod_mana")-debt));
        result.setDouble(PAID,paid);return result;
    }
}
