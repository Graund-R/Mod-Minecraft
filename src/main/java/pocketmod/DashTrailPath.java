package pocketmod;

import java.util.ArrayList;
import java.util.List;

/** Visual constants and distance sampling. No ability levels, mana or combat state. */
public final class DashTrailPath {
    public static final int COPIES=6, FADE_TICKS=8, MAX_ACTORS=8;
    public static final double SPACING=.85, MAX_SEGMENT=12;
    private double x,y,z,remainder;
    private boolean initialized;
    public static final class Point {
        public final double x,y,z;
        Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    public void reset(){initialized=false;remainder=0;}
    public List<Point> move(double nx,double ny,double nz) {
        List<Point> out=new ArrayList<Point>();
        if(!Double.isFinite(nx)||!Double.isFinite(ny)||!Double.isFinite(nz)){reset();return out;}
        if(!initialized){initialized=true;x=nx;y=ny;z=nz;out.add(new Point(x,y,z));return out;}
        double dx=nx-x,dy=ny-y,dz=nz-z,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length>MAX_SEGMENT){reset();initialized=true;x=nx;y=ny;z=nz;return out;}
        if(length>1e-6){
            for(double distance=SPACING-remainder;distance<=length+1e-9;distance+=SPACING) {
                double t=Math.min(1,distance/length);out.add(new Point(x+dx*t,y+dy*t,z+dz*t));
            }
            remainder=(remainder+length)%SPACING;
            if(remainder<1e-8||remainder>SPACING-1e-8)remainder=0;
        }
        x=nx;y=ny;z=nz;return out;
    }
    public static float alpha(double age) {
        if(!Double.isFinite(age)||age<0||age>=FADE_TICKS)return 0;
        float left=(float)(1-age/FADE_TICKS);return .48F*left*left;
    }
    /** Lightning jitter is fixed by a seed, so it cannot flicker with the render frame rate. */
    public static double jitter(int seed,int segment) {
        int h=seed*73428767+segment*912931;h^=h>>>13;h*=1274126177;
        return ((h>>>8)&65535)/65535.0*2-1;
    }
}
