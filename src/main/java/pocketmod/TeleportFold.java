package pocketmod;

/** Fixed presentation envelopes, independent of ability upgrades and teleport distance. */
public final class TeleportFold {
    public static final int TICKS=8, MAX_EVENTS=16, MAX_LENSES=4;
    private TeleportFold(){}
    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    public static float progress(double age){return (float)Math.max(0,Math.min(1,Double.isFinite(age)?age/TICKS:1));}
    public static float radius(float t,boolean arrival){return (float)(.035+.965*(arrival?smooth(t):1-smooth(t)));}
    public static float envelope(float t){return t<=0||t>=1?0:(float)Math.sin(Math.PI*t);}
    public static float warp(float t,boolean arrival){return envelope(t)*(arrival?-.55F:.65F);}
    public static float opacity(float t){return t>=1?0:(float)(.55*(1-smooth(t)));}
    public static boolean valid(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30000000&&Math.abs(z)<=30000000&&Math.abs(y)<=4096;}
    public static double[] distort(double u,double v,double[] lens){
        double x=(u-lens[0])/lens[2],y=(v-lens[1])/lens[3],falloff=Math.pow(Math.max(0,1-x*x-y*y),2);
        return new double[]{u+(u-lens[0])*lens[4]*falloff,v+(v-lens[1])*lens[4]*falloff};
    }
}
