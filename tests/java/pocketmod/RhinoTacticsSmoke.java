package pocketmod;
public final class RhinoTacticsSmoke {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        RhinoTactics t=new RhinoTactics();
        for(int i=0;i<200;i++)t.tick(true,true);
        check(!t.shouldCollapse(true),"must attempt horns before collapse");
        t.attemptedHorn();check(!t.shouldCollapse(true),"one failed probe is not enough");
        t.attemptedHorn();check(t.shouldCollapse(true),"patience expires after varied attempts");
        check(!t.shouldCollapse(false),"ground opponent never triggers anti-air collapse");
        t.collapsed();check(t.frustration()==0,"collapse resets frustration");
        t.attemptedHorn();t.attemptedHorn();
        for(int i=0;i<200;i++)t.tick(true,true);
        check(!t.shouldCollapse(true),"remaining cooldown blocks repeated collapse");
        for(int i=0;i<60;i++)t.tick(true,true);
        check(t.shouldCollapse(true),"new attempts and expired cooldown allow collapse");
        t.collapsed();for(int i=0;i<300;i++)t.tick(true,false);
        check(!t.shouldCollapse(true),"attack animations do not build frustration");
        t.restore(9999,-5);check(t.frustration()==300&&t.cooldown()==0,"save values bounded");
        check(!t.shouldCollapse(true),"reload requires new failed attempts");
        t.attemptedHorn();t.attemptedHorn();for(int i=0;i<110;i++)t.tick(false,true);
        check(!t.shouldCollapse(true)&&t.frustration()==0,"landing clears irritation and probes");
        check(RhinoTactics.inHornArc(3,1,1),"horn hits its forward arc");
        check(!RhinoTactics.inHornArc(-2,0,0),"horn cannot hit behind");
        check(!RhinoTactics.inHornArc(2,0,4),"failed upward horn cannot damage unreachable target");
        System.out.println("Rhino tactics: patience, failed probes, cooldown, landing, save bounds and horn arc passed.");
    }
}
