package pocketmod;
public final class RhinoCoverSmoke {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[]args){
        check(RhinoCoverRules.below(0,-2,0),"directly below must retreat");
        check(RhinoCoverRules.below(3,-1,2),"nearby pit must retreat");
        check(!RhinoCoverRules.below(0,0,0),"normal melee is not a pit");
        check(!RhinoCoverRules.below(6,-2,0),"boss already away from pit");
        double[] a=RhinoCoverRules.retreat(0,0,0,0,0);
        check(Math.hypot(a[0],a[1])==5,"zero-distance hit produces a real retreat");
        a=RhinoCoverRules.retreat(2,0,0,0,0);check(a[0]>2,"retreat increases distance from attacker");
        check(RhinoCoverRules.shallow(65,62),"dig shallow pit");
        check(!RhinoCoverRules.shallow(65,59),"never mine a shaft after deep target");
        check(!RhinoCoverRules.bellyOpen(0,.4F),"standing under boss never gives bonus");
        check(!RhinoCoverRules.bellyOpen(6,.1F),"crouch still armored");
        check(RhinoCoverRules.bellyOpen(6,.45F),"belly exposed at rear apex");
        check(!RhinoCoverRules.bellyOpen(6,.8F),"belly closed after impact");
        check(RhinoCoverRules.bellyHit(2,.5,2.5),"frontal belly hit");
        check(!RhinoCoverRules.bellyHit(-2,0,2),"back armor excluded");
        check(!RhinoCoverRules.bellyHit(2,0,-1),"pit attack excluded from weak spot");
        check(!RhinoCoverRules.bellyHit(2,2,2),"side armor excluded");
        check(RhinoCoverRules.leaveWaterStalemate(true,60,100),"retreat from unreachable water archer");
        check(!RhinoCoverRules.leaveWaterStalemate(true,20,100),"try approaching before giving up");
        check(!RhinoCoverRules.leaveWaterStalemate(true,100,16),"nearby wet target remains attackable");
        check(!RhinoCoverRules.leaveWaterStalemate(false,100,100),"dry barriers use breach logic");
        System.out.println("Rhino cover: mandatory retreat, zero-distance escape, shallow/deep limits, weak-spot time and direction passed.");
    }
}
