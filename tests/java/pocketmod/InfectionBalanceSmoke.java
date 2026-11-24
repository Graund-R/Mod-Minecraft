package pocketmod;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.*;

public final class InfectionBalanceSmoke {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static class Player extends BloodthirstSmoke.Player {
        Map<Potion,PotionEffect> effects;
        Player(){super();}
        @Override public boolean isPotionActive(Potion potion){return effects.containsKey(potion);}
        @Override public void addPotionEffect(PotionEffect effect){effects.put(effect.getPotion(),effect);}
    }
    public static void main(String[] args)throws Exception{
        TrialPlacementSmoke.main(args);
        for(int stage=1;stage<=4;stage++){
            int[] old=new int[3],now=new int[3],occurrences=new int[3];
            for(int slot=0;slot<600;slot++){
                int kind=ExileEncounters.type(stage,slot);old[kind]++;
                if(ExileEncounters.slotAllowed(kind,occurrences[kind]++))now[kind]++;
            }
            check(now[0]*2==old[0]&&now[1]*2==old[1],"exactly half the Vex/archer slots at stage "+stage);
            check(now[2]==old[2],"wither skeleton rate retained at stage "+stage);
        }
        check(ExileEncounters.typeCap(4,0)==4&&ExileEncounters.typeCap(4,1)==4&&ExileEncounters.typeCap(4,2)==8,"full-stage type caps halved selectively");
        Player p=(Player)TrialPlacementSmoke.unsafe.allocateInstance(Player.class);
        p.data=new NBTTagCompound();p.effects=new HashMap<>();
        BloodthirstSmoke.field(p,Entity.class,"rand",new Random(5));
        for(int i=0;i<120;i++)ExileSymptoms.advance(p,74);
        check(p.effects.isEmpty(),"no early negative episodes");
        for(int i=0;i<44;i++)ExileSymptoms.advance(p,80);
        check(p.effects.isEmpty(),"initial episode grace");
        ExileSymptoms.advance(p,80);
        check(p.effects.containsKey(MobEffects.SLOWNESS)&&!p.effects.containsKey(MobEffects.BLINDNESS),"moderate infection has brief slowing, no blindness");
        check(p.effects.get(MobEffects.SLOWNESS).getDuration()==80,"moderate debuff limited to four seconds");
        ExileSymptoms.reset(ExileMode.state(p));p.effects.clear();
        int blindEpisodes=0,episodes=0;
        for(int i=0;i<3600;i++){
            p.effects.clear();ExileSymptoms.advance(p,100);
            if(!p.effects.isEmpty()){
                episodes++;if(p.effects.containsKey(MobEffects.BLINDNESS))blindEpisodes++;
                for(PotionEffect effect:p.effects.values())check(effect.getDuration()<=120&&effect.getAmplifier()==0,"late episode duration/amplifier bounded");
            }
        }
        check(episodes==120,"one episode per thirty seconds, no effect spam");
        check(blindEpisodes>0&&blindEpisodes<episodes,"blindness occasional, not continuous");
        check(ExileSymptoms.blindnessTicks(0)==40&&ExileSymptoms.blindnessTicks(120)==60,"blindness grows from two to three seconds");
        p.effects.clear();PotionEffect other=new PotionEffect(MobEffects.BLINDNESS,600,2);p.effects.put(MobEffects.BLINDNESS,other);
        ExileSymptoms.apply(p,MobEffects.BLINDNESS,40);
        check(p.effects.get(MobEffects.BLINDNESS)==other,"does not overwrite another source's blindness");
        ExileSymptoms.advance(p,0);
        check(ExileMode.state(p).getInteger("infection_full_time")==0&&ExileMode.state(p).getInteger("infection_symptom_time")==0,"clean region resets escalation");
        ExileConfig.infectionSymptoms=false;p.effects.clear();
        for(int i=0;i<100;i++)ExileSymptoms.advance(p,100);
        check(p.effects.isEmpty(),"symptoms can be disabled independently of visuals");
        ExileConfig.infectionSymptoms=true;
        System.out.println("InfectionBalanceSmoke: "+checks+" checks passed; no game launched");
    }
}
