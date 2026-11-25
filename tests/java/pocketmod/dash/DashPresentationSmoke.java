package pocketmod;

import io.netty.buffer.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.model.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real model and packet checks without a world, graphics context or Minecraft client launch. */
public final class DashPresentationSmoke {
    public static void main(String[] args)throws Exception{
        DashEffects.Message sent=new DashEffects.Message();sent.dimension=-1;sent.entityId=1234;sent.sequence=17;sent.duration=5;sent.end=false;
        ByteBuf bytes=Unpooled.buffer();
        try{
            sent.toBytes(bytes);DashEffects.Message read=new DashEffects.Message();read.fromBytes(bytes);
            if(read.dimension!=-1||read.entityId!=1234||read.sequence!=17||read.duration!=5||read.end||bytes.isReadable())throw new AssertionError("dash start packet");
            bytes.clear();sent.end=true;sent.duration=0;sent.toBytes(bytes);read.fromBytes(bytes);if(!read.end||read.duration!=0)throw new AssertionError("dash stop packet");
            bytes.clear();sent.duration=-1;sent.toBytes(bytes);read.fromBytes(bytes);if(!read.end||read.duration!=-1)throw new AssertionError("quiet teleport cancellation was lost");
        }finally{bytes.release();}
        Method capture=DashVisuals.class.getDeclaredMethod("pose",ModelBiped.class),apply=DashVisuals.class.getDeclaredMethod("apply",ModelBiped.class,float[].class);
        capture.setAccessible(true);apply.setAccessible(true);
        for(boolean slim:new boolean[]{false,true}){
            ModelPlayer model=new ModelPlayer(0,slim);model.bipedRightArm.rotateAngleX=.73F;model.bipedBodyWear.rotateAngleY=.19F;model.bipedLeftLeg.rotationPointZ=.51F;
            float[] recorded=(float[])capture.invoke(null,model);
            model.bipedRightArm.rotateAngleX=-1;model.bipedBodyWear.rotateAngleY=-1;model.bipedLeftLeg.rotationPointZ=-1;
            apply.invoke(null,model,recorded);
            if(model.bipedRightArm.rotateAngleX!=.73F||model.bipedBodyWear.rotateAngleY!=.19F||model.bipedLeftLeg.rotationPointZ!=.51F)throw new AssertionError("ghost pose follows live model instead of snapshot");
            ModelBiped armor=new ModelBiped(1);apply.invoke(null,armor,recorded);
            if(armor.bipedRightArm.rotateAngleX!=.73F)throw new AssertionError("armor pose differs from skin");
        }
        for(Class<?> type:new Class<?>[]{DashVisuals.class,DashTrailPath.class,DashEffects.class,DashSounds.class}){
            ClassNode c=new ClassNode();new ClassReader(type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")).accept(c,0);
            for(MethodNode method:c.methods)for(AbstractInsnNode n:method.instructions.toArray()){
                if(n instanceof MethodInsnNode){String owner=((MethodInsnNode)n).owner;if(owner.equals("pocketmod/AbilityStorage")||owner.equals("pocketmod/Dash"))throw new AssertionError("visual style reads ability progression: "+type.getSimpleName());}
            }
        }
        System.out.println("Dash presentation: packet roundtrip, frozen wide/slim/armor poses and level independence passed");
    }
}
