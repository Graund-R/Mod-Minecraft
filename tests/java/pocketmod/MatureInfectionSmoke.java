package pocketmod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;

public final class MatureInfectionSmoke {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static class Player extends BloodthirstSmoke.Player {
        float received;
        DamageSource source;
        @Override public boolean attackEntityFrom(DamageSource cause,float amount) {
            source=cause;received+=amount;return true;
        }
    }
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        ExileConfig.infectionDamageAfter=1200;ExileConfig.infectionDamageInterval=2;ExileConfig.infectionDamage=1;
        ExileWorldData data=new ExileWorldData("hazard");data.setExposure(0,-1,2,600);
        data.seconds=59;check(data.blackPercent(0,-1,2)==19,"darkness interpolates within a minute");
        data.seconds=600;check(!data.dangerous(0,-1,2),"no damage through exactly twenty minutes");
        data.seconds=601;check(data.dangerous(0,-1,2),"mature patch becomes dangerous after twenty minutes");
        check(!data.dangerous(0,0,2)&&!data.dangerous(1,-1,2),"hazard is local to infected dimension and column");
        ExileWorldData restored=new ExileWorldData("reload");restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        check(restored.dangerous(0,-1,2),"save/reload cannot restart safe grace");
        data.seconds+=100;check(data.dangerous(0,-1,2),"leaving and returning does not reset danger");
        data.setExposure(0,-1,2,0);check(!data.dangerous(0,-1,2),"actual cleaning removes hazard");
        NBTTagCompound state=new NBTTagCompound();
        check(!ExileInfectionDamage.pulse(state,true),"first hazardous second is a warning grace");
        check(ExileInfectionDamage.pulse(state,true),"half-heart pulse every two seconds");
        check(!ExileInfectionDamage.pulse(state,true),"no damage every tick");
        check(!ExileInfectionDamage.pulse(state,false),"safe terrain ends damage immediately");
        check(!ExileInfectionDamage.pulse(state,true),"exposure resumes at pulse boundary");
        NBTTagCompound savedPulse=state.copy();
        check(ExileInfectionDamage.pulse(savedPulse,true),"stored damage interval survives state serialization");
        ExileConfig.infectionDamage=0;check(!ExileInfectionDamage.pulse(state,true),"zero configured damage disables hazard");ExileConfig.infectionDamage=1;
        Player player=(Player)TrialPlacementSmoke.unsafe.allocateInstance(Player.class);player.data=new NBTTagCompound();
        ExileConfig.infectionSymptoms=false;
        for(int y:new int[]{65,10}) {
            player.posY=y;ExileInfectionDamage.advance(player,true);ExileInfectionDamage.advance(player,true);
            check(player.received==(y==65?1:2),"identical actual damage call at surface and cave depth");
        }
        check(player.source.isUnblockable()&&player.source.isMagicDamage(),"infection bypasses armor and uses magic protection rules");
        check(player.source.getDamageType().equals("pocketmod.infection"),"death messages distinguish infection from bloodthirst");
        NBTTagCompound stranded=new NBTTagCompound();
        check(!ExileEncounters.strandedExpired(stranded,true,0),"unreachable mob is not removed immediately");
        check(!ExileEncounters.strandedExpired(stranded,true,599),"thirty-second persistence before replacement");
        check(ExileEncounters.strandedExpired(stranded,true,600),"stranded population slot eventually freed");
        check(!ExileEncounters.strandedExpired(stranded,false,700)&&!stranded.hasKey("pm_exile_stranded"),"restored path clears stranded clock");
        check(!ExileEncounters.strandedExpired(stranded,true,1000),"a later blockage starts a fresh clock");
        check(!ExileEncounters.strandedExpired(stranded,true,1599),"old blockage cannot prematurely retire a mob");
        System.out.println("MatureInfectionSmoke: "+checks+" maturity, damage, cave and save checks passed; no game launched");
    }
}
