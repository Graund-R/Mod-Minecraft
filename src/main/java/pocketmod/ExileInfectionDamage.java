package pocketmod;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;

/** Environmental magic damage, separate from bloodthirst and short symptom episodes. */
final class ExileInfectionDamage {
    private static final DamageSource SOURCE=new DamageSource("pocketmod.infection").setDamageBypassesArmor().setMagicDamage();
    private ExileInfectionDamage() {}
    static boolean pulse(NBTTagCompound state,boolean dangerous) {
        if(!dangerous||ExileConfig.infectionDamage<=0) {state.removeTag("infection_damage_time");return false;}
        int elapsed=state.getInteger("infection_damage_time")+1;
        if(elapsed<ExileConfig.infectionDamageInterval) {state.setInteger("infection_damage_time",elapsed);return false;}
        state.setInteger("infection_damage_time",0);return true;
    }
    static void advance(EntityPlayerMP player,boolean dangerous) {
        NBTTagCompound state=ExileMode.state(player);
        if(dangerous&&!state.getBoolean("infection_damage_warning"))ExileMode.message(player,"infection_damage");
        state.setBoolean("infection_damage_warning",dangerous);
        if(pulse(state,dangerous))player.attackEntityFrom(SOURCE,ExileConfig.infectionDamage);
    }
}
