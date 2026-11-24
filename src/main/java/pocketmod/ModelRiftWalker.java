package pocketmod;

import net.minecraft.client.model.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.*;

/** Original segmented sentinel. All animated offsets are rebuilt every frame. */
@SideOnly(Side.CLIENT)
public final class ModelRiftWalker extends ModelBase {
    private final ModelRenderer torso, pelvis, head, mask, crownL, crownR, armL, armR, handL, handR;
    private final ModelRenderer[] skirts = new ModelRenderer[4];
    private final ModelRenderer[] shards = new ModelRenderer[6];
    private final ModelRenderer core, eyes, seam;
    public ModelRiftWalker() {
        textureWidth=128; textureHeight=128;
        torso=part(0,0,-5,-10,-3,10,12,6); torso.setRotationPoint(0,-9,0);
        pelvis=part(0,22,-4,0,-2.5F,8,5,5); pelvis.setRotationPoint(0,-7,0);
        head=part(40,0,-3.5F,-7,-3.5F,7,7,7); head.setRotationPoint(0,-21,0);
        mask=part(40,16,-4,-6.5F,-4,8,6,1); head.addChild(mask);
        crownL=part(74,0,-1,-10,-1,2,10,2); crownL.setRotationPoint(3,-4,0);head.addChild(crownL);
        crownR=part(74,0,-1,-10,-1,2,10,2); crownR.setRotationPoint(-3,-4,0);head.addChild(crownR);
        armL=part(0,40,-1.5F,0,-1.5F,3,15,3); armL.setRotationPoint(8,-18,0);
        armR=part(0,40,-1.5F,0,-1.5F,3,15,3); armR.setRotationPoint(-8,-18,0);
        handL=part(16,40,-2,-1,-2,4,5,4); handL.setRotationPoint(0,15,0);armL.addChild(handL);
        handR=part(16,40,-2,-1,-2,4,5,4); handR.setRotationPoint(0,15,0);armR.addChild(handR);
        for(int i=0;i<4;i++) skirts[i]=part(0,64,-3,0,-1,6,18,2);
        for(int i=0;i<6;i++) shards[i]=part(92,0,-1.5F,-4,-1,3,8,2);
        core=part(64,32,-2,-2,-2,4,4,4);
        eyes=part(96,32,-3,-4.5F,-4.06F,6,1,1);head.addChild(eyes);
        seam=part(96,40,-0.5F,-8,-3.1F,1,7,1);torso.addChild(seam);
    }
    private ModelRenderer part(int u,int v,float x,float y,float z,int w,int h,int d) {
        ModelRenderer p=new ModelRenderer(this,u,v);p.addBox(x,y,z,w,h,d);return p;
    }
    private void pose(EntityRiftWalker e,float limb,float amount,float age,float yaw,float pitch) {
        float partial=MathHelper.clamp(age-e.ticksExisted,0,1);
        float charge=e.windup(partial), impact=e.impact(partial), melee=e.meleeSwing(partial);
        float lift=MathHelper.sin(age*0.075F)*0.6F;
        float spread=e.enraged()?1.5F:0;
        torso.rotationPointY=-9+lift; torso.rotateAngleX=charge*0.18F-impact*0.22F;
        torso.rotateAngleY=MathHelper.sin(age*0.025F)*0.08F;
        pelvis.rotationPointY=-7+lift;
        head.rotationPointY=-21+lift-charge*1.2F;
        head.rotateAngleY=yaw*0.017453292F;head.rotateAngleX=pitch*0.017453292F-charge*0.28F;
        crownL.rotateAngleZ=0.42F+charge*0.25F+spread*0.08F;
        crownR.rotateAngleZ=-crownL.rotateAngleZ;
        float walk=MathHelper.cos(limb*0.5F)*amount*0.35F;
        armL.rotationPointY=armR.rotationPointY=-18+lift;
        armL.rotationPointX=8+spread+charge*2;armR.rotationPointX=-8-spread-charge*2;
        float raise=e.preparingPulse()?charge*2.5F:charge*1.55F;
        armL.rotateAngleX=-raise+walk-impact*0.6F;
        armR.rotateAngleX=-raise-walk-impact*0.6F-melee*1.7F;
        armL.rotateAngleZ=-0.13F-charge*(e.preparingPulse()?0.6F:0.15F)-impact*0.65F;
        armR.rotateAngleZ=-armL.rotateAngleZ;
        armR.rotateAngleY=-melee*0.35F;
        handL.rotateAngleX=handR.rotateAngleX=-charge*0.45F;
        for(int i=0;i<4;i++) {
            float a=(float)(i*Math.PI/2+Math.PI/4);
            ModelRenderer p=skirts[i];p.setRotationPoint(MathHelper.cos(a)*3,-2+lift,MathHelper.sin(a)*3);
            p.rotateAngleY=-a+(float)Math.PI/2;
            p.rotateAngleX=0.10F+MathHelper.sin(age*0.06F+i)*0.08F+charge*0.18F+impact*0.4F;
            p.rotateAngleZ=MathHelper.cos(limb*0.4F+i)*amount*0.07F;
        }
        float radius=9+spread+charge*4+impact*5;
        for(int i=0;i<6;i++) {
            float a=age*(e.enraged()?0.045F:0.025F)+(float)(i*Math.PI/3);
            ModelRenderer p=shards[i];p.setRotationPoint(MathHelper.cos(a)*radius,-15+lift+MathHelper.sin(a*2)*3,MathHelper.sin(a)*radius);
            p.rotateAngleY=-a;p.rotateAngleZ=0.35F+MathHelper.sin(age*0.04F+i)*0.15F+impact;
            p.rotateAngleX=charge*0.4F;
        }
        core.setRotationPoint(0,-14+lift,-4.3F);
        core.rotateAngleX=age*0.04F;core.rotateAngleY=age*0.06F;core.rotateAngleZ=(float)Math.PI/4;
    }
    @Override public void render(Entity entity,float limb,float amount,float age,float yaw,float pitch,float scale) {
        EntityRiftWalker e=(EntityRiftWalker)entity;pose(e,limb,amount,age,yaw,pitch);
        // Emissive features are drawn separately, never tinted by terrain lighting.
        eyes.isHidden=true;seam.isHidden=true;
        torso.render(scale);pelvis.render(scale);head.render(scale);armL.render(scale);armR.render(scale);
        for(ModelRenderer p:skirts)p.render(scale);
        for(ModelRenderer p:shards)p.render(scale);
        eyes.isHidden=false;seam.isHidden=false;
    }
    public void renderGlow(float scale) {
        core.render(scale);
        // Apply the same parent transforms without drawing the solid parent geometry.
        net.minecraft.client.renderer.GlStateManager.pushMatrix();head.postRender(scale);eyes.render(scale);net.minecraft.client.renderer.GlStateManager.popMatrix();
        net.minecraft.client.renderer.GlStateManager.pushMatrix();torso.postRender(scale);seam.render(scale);net.minecraft.client.renderer.GlStateManager.popMatrix();
    }
}
