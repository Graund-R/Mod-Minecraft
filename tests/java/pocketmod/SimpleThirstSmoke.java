package pocketmod;

import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import sun.misc.Unsafe;

/** Deterministic timelines and model preservation, without launching Minecraft. */
public final class SimpleThirstSmoke {
    static int checks;
    static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    static void near(double a,double b,String message) {check(Math.abs(a-b)<0.00001,message+": "+a+" / "+b);}
    static void seconds(NBTTagCompound s,boolean night,int seconds) {
        for(int i=0;i<seconds;i++)ExileSimpleThirst.tick(s,night,1);
    }
    public static void main(String[] args)throws Exception {
        NBTTagCompound day=new NBTTagCompound();
        seconds(day,false,59);check(day.getInteger("simple_points")==0,"no point before a full minute");
        seconds(day,false,1);check(day.getInteger("simple_points")==1,"exactly one point in the first day minute");
        NBTTagCompound night=new NBTTagCompound();
        seconds(night,true,29);check(night.getInteger("simple_points")==0,"night point not early");
        seconds(night,true,1);check(night.getInteger("simple_points")==1,"night multiplier gives a point in 30 seconds");
        seconds(day,false,59);check(day.getInteger("simple_accel")==0,"acceleration waits two minutes");
        seconds(day,false,1);check(day.getInteger("simple_accel")==1,"one acceleration step after two minutes");
        near(ExileSimpleThirst.rate(day,false),1.25,"visible day rate");
        near(ExileSimpleThirst.rate(day,true),2.5,"night multiplies the current rate");
        day.setInteger("simple_points",5);day.setInteger("simple_accel",2);
        check(ExileSimpleThirst.kill(day).equals("accel"),"kill lowers acceleration first");
        check(day.getInteger("simple_points")==5&&day.getInteger("simple_accel")==1,"no hidden point relief at accelerated rate");
        ExileSimpleThirst.kill(day);check(day.getInteger("simple_accel")==0&&day.getInteger("simple_points")==5,"second kill reaches calm");
        ExileSimpleThirst.kill(day);check(day.getInteger("simple_points")==4,"calm kill lowers one point");
        check(day.getDouble("simple_accel_clock")==0,"kill restarts acceleration clock");
        day.setInteger("simple_points",0);ExileSimpleThirst.kill(day);check(day.getInteger("simple_points")==0,"no negative points");
        NBTTagCompound fast=new NBTTagCompound(),slow=new NBTTagCompound();
        for(int i=0;i<8;i++)ExileSimpleThirst.tick(fast,false,60);
        seconds(slow,false,480);
        check(fast.equals(slow),"60x fast test matches the identical one-second timeline");
        check(fast.getInteger("simple_accel")==4,"acceleration is bounded");
        near(ExileSimpleThirst.rate(fast,true),4,"maximum default night rate is 4x");
        seconds(fast,true,1000);
        check(fast.getInteger("simple_points")==10,"points capped at 10");
        check(fast.getInteger("thirst")==ExileConfig.thirstSeconds,"full point scale maps to existing symptoms");
        check(!ExileSimpleThirst.night(12999)&&ExileSimpleThirst.night(13000),"predictable night start");
        check(ExileSimpleThirst.night(22999)&&!ExileSimpleThirst.night(23000),"predictable night end");
        check(ExileSimpleThirst.night(13000+24000),"night repeats with the day cycle");
        NBTTagCompound corrupted=new NBTTagCompound();
        corrupted.setDouble("simple_clock",Double.NaN);corrupted.setDouble("simple_accel_clock",Double.POSITIVE_INFINITY);
        ExileSimpleThirst.tick(corrupted,false,1);
        check(Double.isFinite(corrupted.getDouble("simple_clock")),"corrupt clock is sanitized");
        check(Double.isFinite(corrupted.getDouble("simple_accel_clock")),"corrupt acceleration clock is sanitized");
        Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        BloodthirstSmoke.Player player=(BloodthirstSmoke.Player)((Unsafe)field.get(null)).allocateInstance(BloodthirstSmoke.Player.class);
        player.data=new NBTTagCompound();
        NBTTagCompound state=ExileMode.state(player);state.setInteger("thirst",240);
        ExileSimpleThirst.ensureModel(player);
        check(state.getInteger("simple_points")==5,"old progress initially transfers as a scale fraction");
        state.setInteger("simple_points",7);ExileSimpleThirst.ensureModel(player);
        state.setBoolean("debug_enabled",true);state.setInteger("debug_thirst_model",2);ExileSimpleThirst.ensureModel(player);
        check(state.getInteger("thirst")==240,"original model state is retained");
        state.setInteger("thirst",250);state.setInteger("debug_thirst_model",1);ExileSimpleThirst.ensureModel(player);
        check(state.getInteger("simple_points")==7,"switching back restores independent simple progress");
        state.setInteger("debug_thirst_model",2);ExileSimpleThirst.ensureModel(player);
        check(state.getInteger("thirst")==250,"legacy progress retained across repeated switching");
        state.setBoolean("debug_thirst_pause",true);check(ExileSimpleThirst.paused(player),"pause is enabled explicitly");
        state.setInteger("debug_thirst_model",1); ExileSimpleThirst.ensureModel(player);
        double before=state.getDouble("simple_clock");
        int beforePoints=state.getInteger("simple_points");
        ExileBloodthirst.advance(player);
        near(state.getDouble("simple_clock"),before,"pause stops the actual accumulation entry point");
        check(state.getInteger("simple_points")==beforePoints,"pause leaves points unchanged");
        state.setBoolean("debug_enabled",false);check(!ExileSimpleThirst.paused(player),"hidden tools cannot leave a debug pause active");
        state.setBoolean("debug_enabled",true);state.setInteger("debug_thirst_speed",Integer.MAX_VALUE);
        check(ExileSimpleThirst.speed(player)==1,"invalid test speed is ignored");
        state.setInteger("debug_thirst_speed",60);check(ExileSimpleThirst.speed(player)==60,"bounded fast test speed");
        System.out.println("SimpleThirstSmoke: "+checks+" checks passed; no game launched");
    }
}
