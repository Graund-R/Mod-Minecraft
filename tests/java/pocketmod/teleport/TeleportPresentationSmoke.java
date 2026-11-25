package pocketmod;

import io.netty.buffer.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class TeleportPresentationSmoke {
    public static void main(String[] args)throws Exception{
        float lastOut=2,lastIn=0;
        for(int i=0;i<=80;i++){
            float t=TeleportFold.progress(i*.1),out=TeleportFold.radius(t,false),in=TeleportFold.radius(t,true);
            if(out>lastOut+1e-5||in<lastIn-1e-5||out<=0||in<=0)throw new AssertionError("fold envelope does not close/open monotonically");
            if(TeleportFold.warp(t,false)<0||TeleportFold.warp(t,true)>0)throw new AssertionError("fold and release have the same direction");
            lastOut=out;lastIn=in;
        }
        if(TeleportFold.progress(Double.NaN)!=1||TeleportFold.opacity(1)!=0||TeleportFold.envelope(1)!=0)throw new AssertionError("expired effect persists");
        if(TeleportFold.valid(Double.POSITIVE_INFINITY,64,0)||TeleportFold.valid(0,0,Double.NaN)||TeleportFold.valid(30000001,64,0))throw new AssertionError("invalid coordinates admitted");
        double[] a=TeleportFold.distort(.58,.5,new double[]{.5,.5,.2,.4,.65});
        double[] b=TeleportFold.distort(.58,.5,new double[]{.5,.5,.2,.4,-.55});
        if(!(a[0]>.58&&b[0]<.58))throw new AssertionError("sample field does not compress/release");
        TeleportEffects.Message sent=new TeleportEffects.Message();sent.dimension=-1;sent.actorId=27;sent.x=12.25;sent.y=65.1;sent.z=-173;sent.width=.6F;sent.height=1.8F;sent.arrival=true;sent.hostile=false;
        ByteBuf bytes=Unpooled.buffer();
        try{sent.toBytes(bytes);TeleportEffects.Message read=new TeleportEffects.Message();read.fromBytes(bytes);
            if(bytes.isReadable()||read.dimension!=-1||read.actorId!=27||read.x!=12.25||read.y!=65.1||read.z!=-173||read.width!=.6F||read.height!=1.8F||!read.arrival||read.hostile)throw new AssertionError("endpoint packet changed");
        }finally{bytes.release();}
        for(Class<?> type:new Class<?>[]{TeleportEffects.class,TeleportVisuals.class,TeleportFold.class}){
            ClassNode c=new ClassNode();new ClassReader(type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")).accept(c,0);
            for(MethodNode method:c.methods)for(AbstractInsnNode n:method.instructions.toArray())if(n instanceof MethodInsnNode){
                MethodInsnNode call=(MethodInsnNode)n;
                if(call.owner.equals("pocketmod/AbilityStorage")||call.owner.equals("pocketmod/Mana")||call.name.equals("setPlayerLocation")||call.name.equals("changeDimension")||call.name.equals("loadShader"))throw new AssertionError("presentation changes progression, movement or active shader: "+call.name);
            }
        }
        System.out.println("Teleport presentation: envelopes, bounds, endpoint packet and independent non-gameplay rendering passed");
    }
}
