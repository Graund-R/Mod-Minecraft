package pocketmod;
/** Spatial rules are based on a visible position or the last actual incoming hit. */
public final class RhinoCoverRules {
    private RhinoCoverRules(){}
    public static boolean leaveWaterStalemate(boolean wet,int stalled,double distanceSq){return wet&&stalled>=60&&distanceSq>25;}
    public static boolean below(double dx,double dy,double dz){return dy<-.7&&dx*dx+dz*dz<30.25;}
    public static boolean shallow(double bossY,double knownY){double depth=bossY-knownY;return depth>.7&&depth<=4;}
    public static boolean bellyOpen(int action,float progress){return action==6&&progress*RhinoAnimation.REAR_SECONDS>=.55F&&progress*RhinoAnimation.REAR_SECONDS<1.5F;}
    public static boolean bellyHit(double forward,double lateral,double height){return forward>-.5&&forward<4.5&&Math.abs(lateral)<1.35&&height>.6&&height<5.7;}
    public static double[] retreat(double bossX,double bossZ,double knownX,double knownZ,double yaw){
        double dx=bossX-knownX,dz=bossZ-knownZ,len=Math.sqrt(dx*dx+dz*dz);
        if(len<.1){dx=Math.sin(yaw);dz=-Math.cos(yaw);len=1;}
        return new double[]{bossX+dx/len*5,bossZ+dz/len*5};
    }
}
