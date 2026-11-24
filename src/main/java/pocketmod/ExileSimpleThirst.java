package pocketmod;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;

/** Deterministic test model. Original context model remains in ExileBloodthirst. */
public final class ExileSimpleThirst {
    private ExileSimpleThirst() {}
    public static boolean isSimple(EntityPlayerMP p) {
        int override=ExileDebug.enabled(p) ? ExileMode.state(p).getInteger("debug_thirst_model") : 0;
        return override==1 || (override!=2 && ExileConfig.simpleThirst);
    }
    public static boolean paused(EntityPlayerMP p) {
        return ExileDebug.enabled(p) && ExileMode.state(p).getBoolean("debug_thirst_pause");
    }
    public static int speed(EntityPlayerMP p) {
        if(!ExileDebug.enabled(p)) return 1;
        int value=ExileMode.state(p).getInteger("debug_thirst_speed");
        return value==5 || value==20 || value==60 ? value : 1;
    }
    static boolean night(long time) { long t=Math.floorMod(time,24000L); return t>=13000 && t<23000; }
    public static boolean night(EntityPlayerMP p) {
        int override=ExileDebug.enabled(p) ? ExileMode.state(p).getInteger("debug_thirst_night") : 0;
        return override==2 || (override!=1 && p.dimension==0 && night(p.world.getWorldTime()));
    }
    static int points(NBTTagCompound s) {
        return Math.max(0,Math.min(ExileConfig.simpleThirstLimit,s.getInteger("simple_points")));
    }
    static int level(NBTTagCompound s) {
        return Math.max(0,Math.min(ExileConfig.simpleAccelMax,s.getInteger("simple_accel")));
    }
    static double finite(double value) { return Double.isFinite(value) && value>=0 ? value : 0; }
    static double rate(NBTTagCompound s, boolean night) {
        return (1+level(s)*(double)ExileConfig.simpleAccelStep)*(night?ExileConfig.simpleNightMultiplier:1);
    }
    static void canonical(NBTTagCompound s) {
        s.setInteger("simple_points",points(s));
        s.setInteger("thirst",(int)Math.round(points(s)*(double)ExileConfig.thirstSeconds/Math.max(1,ExileConfig.simpleThirstLimit)));
    }
    public static void ensureModel(EntityPlayerMP p) {
        NBTTagCompound s=ExileMode.state(p);
        boolean simple=isSimple(p);
        if(!s.getBoolean("thirst_model_initialized") || simple!=s.getBoolean("thirst_simple_active")) {
            boolean wasSimple=s.getBoolean("thirst_simple_active");
            if(!s.getBoolean("thirst_model_initialized") || !wasSimple)
                s.setInteger("legacy_thirst_saved",s.getInteger("thirst"));
            if(simple) {
                if(!s.getBoolean("simple_initialized")) {
                    s.setInteger("simple_points",(int)Math.round(ExileBloodthirst.fraction(s.getInteger("thirst"),ExileConfig.thirstSeconds)*ExileConfig.simpleThirstLimit));
                    s.setBoolean("simple_initialized",true);
                }
                canonical(s);
            } else if(wasSimple) s.setInteger("thirst",Math.max(0,Math.min(ExileConfig.thirstSeconds,s.getInteger("legacy_thirst_saved"))));
            s.setBoolean("thirst_simple_active",simple);
            s.setBoolean("thirst_model_initialized",true);
            s.setInteger("thirst_damage_seconds",0);
        }
        if(simple) canonical(s);
    }
    /** One active second, with an optional bounded test time multiplier. */
    static void tick(NBTTagCompound s, boolean night, int speed) {
        for(int i=0;i<Math.max(1,Math.min(60,speed));i++) tickOne(s,night);
    }
    private static void tickOne(NBTTagCompound s, boolean night) {
        int speed=1;
        double age=finite(s.getDouble("simple_accel_clock"))+speed;
        int level=level(s), max=ExileConfig.simpleAccelMax;
        while(age>=Math.max(1,ExileConfig.simpleAccelSeconds) && level<max) {
            age-=Math.max(1,ExileConfig.simpleAccelSeconds); level++;
        }
        if(level>=max) age=0;
        s.setDouble("simple_accel_clock",age); s.setInteger("simple_accel",level);
        double work=finite(s.getDouble("simple_clock"))+rate(s,night)*speed;
        int value=points(s);
        while(work>=Math.max(1,ExileConfig.simpleThirstSeconds) && value<ExileConfig.simpleThirstLimit) {
            work-=Math.max(1,ExileConfig.simpleThirstSeconds); value++;
        }
        if(value>=ExileConfig.simpleThirstLimit) work=0;
        s.setDouble("simple_clock",work); s.setInteger("simple_points",value);
        canonical(s);
    }
    public static void advance(EntityPlayerMP p) { tick(ExileMode.state(p),night(p),speed(p)); }
    static String kill(NBTTagCompound s) {
        String result;
        if(level(s)>0) { s.setInteger("simple_accel",level(s)-1); result="accel"; }
        else if(points(s)>0) { s.setInteger("simple_points",points(s)-1); result="point"; }
        else result="empty";
        s.setDouble("simple_accel_clock",0);
        s.setString("simple_last_event",result);
        canonical(s);
        return result;
    }
    public static void kill(EntityPlayerMP p) {
        ensureModel(p);
        String result=kill(ExileMode.state(p));
        ExileBloodthirst.refreshModifiers(p);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.simple.kill_"+result),true);
        NetworkHandler.sync(p);
    }
    public static void debug(EntityPlayerMP p,int action,int value) {
        ensureModel(p); NBTTagCompound s=ExileMode.state(p); boolean simple=isSimple(p);
        if(action==15) {
            s.setInteger("debug_thirst_model",simple?2:1);
            ensureModel(p);
        } else if(action==14) s.setBoolean("debug_thirst_pause",!s.getBoolean("debug_thirst_pause"));
        else if(action==10 && (value==-1 || value==1)) {
            if(simple) s.setInteger("simple_points",Math.max(0,Math.min(ExileConfig.simpleThirstLimit,points(s)+value)));
            else s.setInteger("thirst",Math.max(0,Math.min(ExileConfig.thirstSeconds,s.getInteger("thirst")+value*Math.max(1,ExileConfig.thirstSeconds/ExileConfig.simpleThirstLimit))));
        } else if(action==11 && (value==0 || value==1)) {
            if(simple) { s.setInteger("simple_points",value==0?0:ExileConfig.simpleThirstLimit); s.setDouble("simple_clock",0); }
            else s.setInteger("thirst",value==0?0:ExileConfig.thirstSeconds);
            s.setInteger("thirst_damage_seconds",0);
        } else if(action==12 && simple && (value==-1 || value==1)) {
            s.setInteger("simple_accel",Math.max(0,Math.min(ExileConfig.simpleAccelMax,level(s)+value)));
            s.setDouble("simple_accel_clock",0);
        } else if(action==13 && simple) {
            int old=speed(p); s.setInteger("debug_thirst_speed",old==1?5:old==5?20:old==20?60:1);
        } else if(action==16 && simple) kill(s);
        else if(action==17 && simple) s.setInteger("debug_thirst_night",(s.getInteger("debug_thirst_night")+1)%3);
        else if(action==18 && simple) { s.setInteger("simple_accel",0); s.setDouble("simple_accel_clock",0); }
        else if(action==19) {
            if(simple) {
                s.setInteger("simple_points",0); s.setInteger("simple_accel",0); s.setDouble("simple_clock",0);
                s.setDouble("simple_accel_clock",0);
            }
            s.setInteger("thirst",0); s.setInteger("thirst_damage_seconds",0);
        }
        if(isSimple(p)) canonical(s);
        ExileBloodthirst.refreshModifiers(p);
    }
    public static void snapshot(EntityPlayerMP p) {
        ensureModel(p); NBTTagCompound s=ExileMode.state(p);
        s.setInteger("simple_limit",ExileConfig.simpleThirstLimit);
        s.setInteger("simple_accel_max",ExileConfig.simpleAccelMax);
        s.setInteger("simple_speed",speed(p));
        boolean night=night(p); s.setBoolean("simple_night",night);
        double rate=rate(s,night);
        s.setDouble("simple_rate",rate);
        s.setInteger("simple_eta",points(s)>=ExileConfig.simpleThirstLimit?0:(int)Math.ceil(
                Math.max(0,ExileConfig.simpleThirstSeconds-finite(s.getDouble("simple_clock")))/(rate*speed(p))));
        s.setInteger("simple_accel_eta",level(s)>=ExileConfig.simpleAccelMax?0:(int)Math.ceil(
                Math.max(0,ExileConfig.simpleAccelSeconds-finite(s.getDouble("simple_accel_clock")))/speed(p)));
        String reason=s.getBoolean("cured")?"cured":p.isCreative()?"creative"
                :p.isSpectator()&&!Spector.isActive(p)?"spectator":PocketDimensionManager.isPocketDimension(p.dimension)?"shelter"
                :paused(p)?"paused":"active";
        s.setString("simple_status",reason);
    }
}
