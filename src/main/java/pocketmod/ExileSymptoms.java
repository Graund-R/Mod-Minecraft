package pocketmod;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/** Brief, separated local-infection episodes; never removes another source's effects. */
public final class ExileSymptoms {
    private ExileSymptoms() {}
    static int interval(int percent) {return percent<75?0:percent<90?45:30;}
    static float blindnessChance(int percent,int fullSeconds) {
        return percent<95?0:0.15f+0.20f*Math.min(1,Math.max(0,fullSeconds)/120f);
    }
    static int blindnessTicks(int fullSeconds) {return 40+(int)(20*Math.min(1,Math.max(0,fullSeconds)/120f));}
    static void reset(NBTTagCompound state) {
        state.setInteger("infection_symptom_time",0);state.setInteger("infection_full_time",0);
    }
    static void apply(EntityPlayerMP player,Potion potion,int ticks) {
        if(!player.isPotionActive(potion))player.addPotionEffect(new PotionEffect(potion,ticks,0,true,false));
    }
    static void advance(EntityPlayerMP player,int percent) {
        NBTTagCompound state=ExileMode.state(player);
        if(!ExileConfig.infectionSymptoms||percent<75) {reset(state);return;}
        int full=percent>=100?Math.min(120,state.getInteger("infection_full_time")+1):0;
        state.setInteger("infection_full_time",full);
        int elapsed=state.getInteger("infection_symptom_time")+1;
        if(elapsed<interval(percent)) {state.setInteger("infection_symptom_time",elapsed);return;}
        state.setInteger("infection_symptom_time",0);
        state.setInteger("infection_episode",state.getInteger("infection_episode")+1);
        boolean blind=player.getRNG().nextFloat()<blindnessChance(percent,full);
        if(blind)apply(player,MobEffects.BLINDNESS,blindnessTicks(full));
        else apply(player,(state.getInteger("infection_episode")&1)==0?MobEffects.WEAKNESS:MobEffects.SLOWNESS,percent>=100?120:80);
    }
}
