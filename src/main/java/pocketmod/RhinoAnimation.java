package pocketmod;

/** Pure pose mathematics, in the approved model's pixel coordinates. */
public final class RhinoAnimation {
    public static final float REAR_SECONDS=2.2F, IMPACT_SECONDS=1.5F;
    private RhinoAnimation(){}
    public static final class RearPose {
        public float angle, x, y, frontAngle, frontScale=1, backAngle, backScale=1, head;
    }
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static float smooth(float x){x=clamp(x);return x*x*(3-2*x);}
    public static RearPose rear(float q){
        RearPose p=new RearPose();float squat=0;
        if(q<.24F)squat=-5.5F*smooth(q/.24F);
        else if(q<.44F){float k=smooth((q-.24F)/.2F);squat=-5.5F*(1-k);p.angle=.3F*k;}
        else if(q<.9F)p.angle=.3F+1.13F*smooth((q-.44F)/.46F);
        else if(q<1.12F)p.angle=1.43F;
        else if(q<1.5F)p.angle=1.43F*(1-(float)Math.pow(clamp((q-1.12F)/.38F),2));
        else if(q<1.66F)squat=-3.8F*(float)Math.sin((q-1.5F)/.16F*Math.PI/2);
        else squat=-3.8F*(1-smooth((q-1.66F)/.54F));
        float c=(float)Math.cos(p.angle),s=(float)Math.sin(p.angle);
        p.x=-17-(-17*c-25*s);p.y=25-(-17*s+25*c)+squat;
        p.backAngle=-p.angle;p.backScale=(25+squat)/25;
        if(q<.34F||q>=1.5F){
            float tx=15-p.x,ty=-p.y,dx=tx*c+ty*s-12,dy=-tx*s+ty*c-25;
            p.frontAngle=(float)(Math.atan2(dy,dx)-Math.atan2(-25,3));
            p.frontScale=(float)(Math.hypot(dx,dy)/Math.hypot(3,25));
        }else p.frontAngle=.95F*smooth((q-.34F)/.2F)*(q<1.12F?1:1-smooth((q-1.12F)/.38F));
        p.head=q<.9F?-.12F*smooth(q/.9F):q<1.12F?-.18F:0;
        return p;
    }
    public static float hornYaw(float q){
        return q<.3F?-.8F*smooth(q/.3F):q<.52F?-.8F+1.85F*smooth((q-.3F)/.22F):1.05F*(1-smooth((q-.52F)/.35F));
    }
}
