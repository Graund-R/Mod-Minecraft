package pocketmod;

import net.minecraft.nbt.*;

/** Border and transient synchronization checks without starting Minecraft. */
public final class AtmosphereTransitionSmoke {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        float value=0;
        for(int tick=0;tick<40;tick++) {
            float next=ExileAtmosphereEnvelope.step(value,1);
            check(next>=value&&next-value<=.02501f,"gradual fade in");value=next;
        }
        check(Math.abs(value-1)<.00001,"full ambience in two seconds");
        value=ExileAtmosphereEnvelope.step(value,0);
        check(value>.97,"border never instantly stops sound/fog");
        for(int tick=0;tick<10;tick++)value=ExileAtmosphereEnvelope.step(value,0);
        check(value>.7,"short clean crossing retains a fading atmosphere");
        float turned=ExileAtmosphereEnvelope.step(value,1);
        check(turned>value&&turned-value<=.02501,"return reverses fade continuously");
        for(int tick=0;tick<50;tick++)value=ExileAtmosphereEnvelope.step(value,0);
        check(value==0,"clean region eventually becomes silent");
        NBTTagCompound missing=new NBTTagCompound();
        check(!ExileGrayClient.validSnapshot(missing,0),"temporary missing spectator data cannot erase terrain colors");
        NBTTagCompound valid=new NBTTagCompound();valid.setInteger("dimension",0);valid.setTag("chunks",new NBTTagList());
        check(ExileGrayClient.validSnapshot(valid,0),"explicit clean snapshot is still authoritative");
        check(!ExileGrayClient.validSnapshot(valid,1),"different dimension never reuses colors");
        valid.setBoolean("spector_active",true);
        check(ExileGrayClient.validSnapshot(valid,0),"spectator activation preserves terrain snapshot");
        System.out.println("AtmosphereTransitionSmoke: "+checks+" checks passed; no game launched");
    }
}
