package pocketmod;

import java.util.*;
public final class DashPathSmoke {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static List<DashTrailPath.Point> straight(int frames){
        DashTrailPath path=new DashTrailPath();List<DashTrailPath.Point> points=new ArrayList<DashTrailPath.Point>();
        for(int i=0;i<=frames;i++)points.addAll(path.move(9.3*i/frames,4,-2));return points;
    }
    public static void main(String[] args){
        List<DashTrailPath.Point> sparse=straight(1),smooth=straight(240);
        check(sparse.size()==smooth.size(),"copy spacing depends on frame rate");
        for(int i=0;i<sparse.size();i++){
            check(Math.abs(sparse.get(i).x-smooth.get(i).x)<1e-7,"different path at high FPS");
            if(i>0)check(Math.abs(smooth.get(i).x-smooth.get(i-1).x-DashTrailPath.SPACING)<1e-7,"spacing or duplicate frame-boundary copy");
        }
        DashTrailPath path=new DashTrailPath();path.move(0,0,0);
        check(path.move(0,0,0).isEmpty(),"standing against a wall emits a stack of bodies");
        check(path.move(80,0,0).isEmpty(),"teleport draws a long bridge of ghosts");
        check(path.move(80.1,0,0).isEmpty(),"teleport reset retains old distance");
        path.move(Double.NaN,0,0);check(path.move(1,2,3).size()==1,"non-finite coordinates poison future samples");
        float last=1;for(double age=0;age<=DashTrailPath.FADE_TICKS;age+=.05){float alpha=DashTrailPath.alpha(age);check(alpha>=0&&alpha<=last+1e-6,"fade is not monotonic");last=alpha;}
        check(DashTrailPath.alpha(-1)==0&&DashTrailPath.alpha(100)==0&&DashTrailPath.alpha(Double.NaN)==0,"expired/invalid ghosts remain visible");
        for(int i=0;i<100;i++)check(Math.abs(DashTrailPath.jitter(7,i))<=1,"unbounded lightning jitter");
        System.out.println("Dash trails: FPS-independent spacing, collision, teleport, fade and jitter passed");
    }
}
