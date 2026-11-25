package pocketmod;
/** Contact checks use transformed foot coordinates, not visual keyframe snapshots. */
public final class RhinoAnimationSmoke {
    private static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    private static void near(double a,double b,String m){check(Math.abs(a-b)<.001,m+": "+a+" != "+b);}
    public static void main(String[]args){
        for(int tick=0;tick<=440;tick++){
            float t=tick/200F;RhinoAnimation.RearPose p=RhinoAnimation.rear(t);
            double c=Math.cos(p.angle),s=Math.sin(p.angle),lc=Math.cos(p.backAngle),ls=Math.sin(p.backAngle);
            double footX=3,footY=-25*p.backScale;
            double lx=-17+footX*lc-footY*ls,ly=25+footX*ls+footY*lc;
            near(p.x+lx*c-ly*s,-14,"hind foot X planted at "+t);
            near(p.y+lx*s+ly*c,0,"hind foot Y planted at "+t);
            check(p.backScale>.7&&p.backScale<=1,"hind legs compress without inverting");
            if(t<.34||t>=1.5){
                lc=Math.cos(p.frontAngle);ls=Math.sin(p.frontAngle);footX=3*p.frontScale;footY=-25*p.frontScale;
                lx=12+footX*lc-footY*ls;ly=25+footX*ls+footY*lc;
                near(p.x+lx*c-ly*s,15,"front foot X during ground contact");
                near(p.y+lx*s+ly*c,0,"front foot Y during ground contact");
            }
        }
        check(RhinoAnimation.rear(.22F).y< -5,"visible crouch before lift");
        check(RhinoAnimation.rear(.7F).angle>0,"body lifts after push");
        check(Math.toDegrees(RhinoAnimation.rear(1F).angle)>80,"near vertical apex");
        near(RhinoAnimation.rear(RhinoAnimation.IMPACT_SECONDS).angle,0,"body level at damage tick");
        near(RhinoAnimation.rear(RhinoAnimation.REAR_SECONDS).y,0,"full recovery");
        check(RhinoAnimation.hornYaw(.28F)<-.7&&RhinoAnimation.hornYaw(.52F)>1,"wide horn sweep");
        System.out.println("Rhino animation: 441 planted hind-foot samples, front contact, crouch, 82-degree apex and impact alignment passed.");
    }
}
