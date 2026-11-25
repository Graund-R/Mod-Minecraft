package pocketmod;

import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Render-only pose snapshots: no clone entities, AI, hit boxes, names or gameplay effects. */
@SideOnly(Side.CLIENT)
public final class DashVisuals {
    private static final Map<Integer,Trail> TRAILS=new LinkedHashMap<Integer,Trail>();
    private static final ResourceLocation RHINO=new ResourceLocation(PocketMod.MODID,"textures/entity/running_rhino.png");
    private static DashVisuals instance;
    public DashVisuals(){instance=this;}
    private static World world;
    private static int clock;
    private final ModelPlayer wide=new ModelPlayer(0,false),slim=new ModelPlayer(0,true);
    private final ModelBiped armor=new ModelBiped(1),leggings=new ModelBiped(.5F);
    private ModelRunningRhino rhino;
    private static final EntityEquipmentSlot[] ARMOR={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};

    private static final class Trail {
        EntityLivingBase actor;int sequence,until;boolean active=true;
        double start,end=-100,startX,startY,startZ,endX,endY,endZ;boolean finalSample;
        final DashTrailPath path=new DashTrailPath();
        final Deque<Echo> echoes=new ArrayDeque<Echo>();
        Trail(EntityLivingBase actor,int sequence,int duration){this.actor=actor;this.sequence=sequence;until=clock+duration+2;start=clock;startX=actor.posX;startY=actor.posY;startZ=actor.posZ;}
    }
    static final class Echo {
        double x,y,z,born;float yaw,phase,age,limb,amount,lean,roll;boolean small,angry,rhino,sneak;
        ResourceLocation skin;
        float[] pose;boolean[] visible;
        final ResourceLocation[] equipment=new ResourceLocation[4],overlays=new ResourceLocation[4];
        final int[] dyes={0xFFFFFF,0xFFFFFF,0xFFFFFF,0xFFFFFF};
    }
    public static void receive(DashEffects.Message m){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null||mc.world.provider.getDimension()!=m.dimension)return;
        resetWorld(mc.world);
        if(m.end){Trail t=TRAILS.get(m.entityId);if(t!=null&&t.sequence==m.sequence){if(m.duration<0)TRAILS.remove(m.entityId);else finish(t);}return;}
        Entity actor=mc.world.getEntityByID(m.entityId);
        if(!(actor instanceof AbstractClientPlayer)&&!(actor instanceof EntityRunningRhino))return;
        if(!PocketConfig.dashVisuals)return;
        Trail old=TRAILS.get(m.entityId);
        if(old!=null&&old.sequence==m.sequence&&old.active)return;
        if(old==null&&TRAILS.size()>=DashTrailPath.MAX_ACTORS)TRAILS.remove(TRAILS.keySet().iterator().next());
        Trail next=new Trail((EntityLivingBase)actor,m.sequence,Math.max(1,Math.min(100,m.duration)));
        if(old!=null&&old.actor==actor)next.echoes.addAll(old.echoes);
        for(DashTrailPath.Point origin:next.path.move(actor.posX,actor.posY,actor.posZ))if(instance!=null){next.echoes.addLast(instance.capture(next,origin,0));while(next.echoes.size()>DashTrailPath.COPIES)next.echoes.removeFirst();}
        TRAILS.put(m.entityId,next);
    }
    private static void resetWorld(World next){if(world!=next){TRAILS.clear();world=next;clock=0;}}
    private static void finish(Trail t){
        if(!t.active)return;t.active=false;t.end=clock;t.finalSample=true;t.endX=t.actor.posX;t.endY=t.actor.posY;t.endZ=t.actor.posZ;
        EntityLivingBase actor=t.actor;
        if(actor.isInvisible()||!actor.isEntityAlive())return;
        Minecraft mc=Minecraft.getMinecraft();
        BlockPos below=new BlockPos(actor.posX,actor.getEntityBoundingBox().minY-.1,actor.posZ);
        net.minecraft.block.state.IBlockState ground=actor.world.getBlockState(below);
        if(actor.onGround && ground.getMaterial().isSolid())for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;
            mc.world.spawnParticle(EnumParticleTypes.BLOCK_DUST,actor.posX+Math.cos(angle)*.3,actor.posY+.05,actor.posZ+Math.sin(angle)*.3,
                    Math.cos(angle)*.08,.04,Math.sin(angle)*.08,net.minecraft.block.Block.getStateId(ground));
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();resetWorld(mc.world);
        if(mc.world==null||mc.isGamePaused())return;clock++;
        if(!PocketConfig.dashVisuals){TRAILS.clear();return;}
        Iterator<Trail> it=TRAILS.values().iterator();
        while(it.hasNext()){
            Trail t=it.next();EntityLivingBase actor=t.actor;
            if(actor.world!=mc.world||mc.world.getEntityByID(actor.getEntityId())!=actor||!actor.isEntityAlive()||actor.isInvisible()
                    ||actor instanceof EntityPlayer&&((EntityPlayer)actor).isSpectator()){it.remove();continue;}
            if(t.active&&(clock>=t.until || actor instanceof EntityRunningRhino&&((EntityRunningRhino)actor).action()!=EntityRunningRhino.CHARGE))finish(t);
            while(!t.echoes.isEmpty()&&clock-t.echoes.peekFirst().born>=DashTrailPath.FADE_TICKS)t.echoes.removeFirst();
            if(!t.active&&t.echoes.isEmpty()&&clock-t.end>4)it.remove();
        }
    }
    private static ModelRenderer[] parts(ModelBiped m){
        if(m instanceof ModelPlayer){ModelPlayer p=(ModelPlayer)m;return new ModelRenderer[]{m.bipedHead,m.bipedHeadwear,m.bipedBody,m.bipedRightArm,m.bipedLeftArm,m.bipedRightLeg,m.bipedLeftLeg,p.bipedBodyWear,p.bipedRightArmwear,p.bipedLeftArmwear,p.bipedRightLegwear,p.bipedLeftLegwear};}
        return new ModelRenderer[]{m.bipedHead,m.bipedHeadwear,m.bipedBody,m.bipedRightArm,m.bipedLeftArm,m.bipedRightLeg,m.bipedLeftLeg};
    }
    private static float[] pose(ModelBiped model){
        ModelRenderer[] parts=parts(model);float[] out=new float[parts.length*6];int i=0;
        for(ModelRenderer p:parts){out[i++]=p.rotationPointX;out[i++]=p.rotationPointY;out[i++]=p.rotationPointZ;out[i++]=p.rotateAngleX;out[i++]=p.rotateAngleY;out[i++]=p.rotateAngleZ;}
        return out;
    }
    private static void apply(ModelBiped model,float[] values){
        ModelRenderer[] parts=parts(model);int i=0;
        for(ModelRenderer p:parts){p.rotationPointX=values[i++];p.rotationPointY=values[i++];p.rotationPointZ=values[i++];p.rotateAngleX=values[i++];p.rotateAngleY=values[i++];p.rotateAngleZ=values[i++];}
    }
    private static float angle(float before,float now,float partial){return before+MathHelper.wrapDegrees(now-before)*partial;}
    private Echo capture(Trail trail,DashTrailPath.Point position,float partial){return capturePose(trail.actor,position,partial,true);}
    private Echo capturePose(EntityLivingBase actor,DashTrailPath.Point position,float partial,boolean running){Echo e=new Echo();e.x=position.x;e.y=position.y;e.z=position.z;e.born=clock+partial;
        e.yaw=angle(actor.prevRenderYawOffset,actor.renderYawOffset,partial);e.age=actor.ticksExisted+partial;e.limb=actor.limbSwing;
        e.amount=actor.prevLimbSwingAmount+(actor.limbSwingAmount-actor.prevLimbSwingAmount)*partial;
        if(actor instanceof EntityRunningRhino){EntityRunningRhino r=(EntityRunningRhino)actor;e.rhino=true;e.phase=r.progress(partial);e.angry=r.angry();e.skin=RHINO;return e;}
        AbstractClientPlayer player=(AbstractClientPlayer)actor;e.small="slim".equals(player.getSkinType());e.skin=player.getLocationSkin();e.sneak=player.isSneaking();
        ModelPlayer model=e.small?slim:wide;model.setVisible(true);model.isSneak=e.sneak;model.isRiding=false;model.isChild=false;
        model.swingProgress=player.getSwingProgress(partial);
        boolean right=player.getPrimaryHand()==EnumHandSide.RIGHT;
        model.leftArmPose=(right?player.getHeldItemOffhand():player.getHeldItemMainhand()).isEmpty()?ModelBiped.ArmPose.EMPTY:ModelBiped.ArmPose.ITEM;
        model.rightArmPose=(right?player.getHeldItemMainhand():player.getHeldItemOffhand()).isEmpty()?ModelBiped.ArmPose.EMPTY:ModelBiped.ArmPose.ITEM;
        model.setLivingAnimations(player,e.limb,e.amount,partial);
        model.setRotationAngles(e.limb,running?Math.max(.55F,e.amount):e.amount,e.age,angle(player.prevRotationYawHead,player.rotationYawHead,partial)-e.yaw,
                player.prevRotationPitch+(player.rotationPitch-player.prevRotationPitch)*partial,.0625F,player);
        e.pose=pose(model);
        double dx=player.posX-player.lastTickPosX,dz=player.posZ-player.lastTickPosZ,length=Math.hypot(dx,dz),yaw=Math.toRadians(e.yaw);
        if(running&&length>.001){e.lean=(float)((-Math.sin(yaw)*dx+Math.cos(yaw)*dz)/length*9);e.roll=(float)((Math.cos(yaw)*dx+Math.sin(yaw)*dz)/length*9);}
        model.bipedHeadwear.showModel=player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.HAT);
        model.bipedBodyWear.showModel=player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.JACKET);
        model.bipedLeftArmwear.showModel=player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.LEFT_SLEEVE);
        model.bipedRightArmwear.showModel=player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.RIGHT_SLEEVE);
        model.bipedLeftLegwear.showModel=player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.LEFT_PANTS_LEG);
        model.bipedRightLegwear.showModel=player.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.RIGHT_PANTS_LEG);
        ModelRenderer[] pieces=parts(model);e.visible=new boolean[pieces.length];for(int i=0;i<pieces.length;i++)e.visible[i]=pieces[i].showModel;
        for(int i=0;i<ARMOR.length;i++){
            ItemStack stack=player.getItemStackFromSlot(ARMOR[i]);if(stack.isEmpty()||!(stack.getItem() instanceof ItemArmor))continue;
            ItemArmor item=(ItemArmor)stack.getItem();String name=item.getArmorMaterial().getName(),domain="minecraft";
            if(name.contains(":")){domain=name.substring(0,name.indexOf(':'));name=name.substring(name.indexOf(':')+1);}
            String base=domain+":textures/models/armor/"+name+"_layer_"+(ARMOR[i]==EntityEquipmentSlot.LEGS?2:1);
            e.equipment[i]=new ResourceLocation(ForgeHooksClient.getArmorTexture(player,stack,base+".png",ARMOR[i],null));
            if(item.hasColor(stack)){e.dyes[i]=item.getColor(stack);e.overlays[i]=new ResourceLocation(ForgeHooksClient.getArmorTexture(player,stack,base+"_overlay.png",ARMOR[i],"overlay"));}
        }
        return e;
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();if(!PocketConfig.dashVisuals||TRAILS.isEmpty()||mc.world==null||mc.player==null||mc.isGamePaused())return;
        resetWorld(mc.world);
        Entity camera=mc.getRenderViewEntity();if(camera==null)return;
        float partial=event.getPartialTicks();double now=clock+partial;
        double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,
                cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,
                cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        State state=new State();
        try{
            state.begin();
            for(Trail t:TRAILS.values()){
                EntityLivingBase actor=t.actor;if(actor.isInvisible()||actor.getDistanceSq(camera)>96*96)continue;
                if(t.active||t.finalSample){
                    double x=actor.lastTickPosX+(actor.posX-actor.lastTickPosX)*partial,y=actor.lastTickPosY+(actor.posY-actor.lastTickPosY)*partial,z=actor.lastTickPosZ+(actor.posZ-actor.lastTickPosZ)*partial;
                    for(DashTrailPath.Point point:t.path.move(x,y,z)){
                        t.echoes.addLast(capture(t,point,partial));while(t.echoes.size()>DashTrailPath.COPIES)t.echoes.removeFirst();
                    }
                    t.finalSample=false;
                }
                for(Echo echo:t.echoes){
                    float alpha=DashTrailPath.alpha(now-echo.born);if(alpha<=.005F)continue;
                    // A nearby local copy must not fill the first-person camera with the inside of a head.
                    if(actor==mc.player&&mc.gameSettings.thirdPersonView==0&&mc.player.getDistanceSq(echo.x,echo.y,echo.z)<.8*.8)continue;
                    draw(echo,alpha,cx,cy,cz);
                }
                lightning(t,now,cx,cy,cz);bursts(t,now,cx,cy,cz);
            }
        }finally{state.restore();}
    }
    private void draw(Echo e,float alpha,double cx,double cy,double cz){
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(e.x-cx,e.y-cy-(e.sneak?.125:0),e.z-cz);GlStateManager.rotate(180-e.yaw,0,1,0);
            GlStateManager.scale(-1,-1,1);GlStateManager.translate(0,-1.501,0);
            Minecraft.getMinecraft().getTextureManager().bindTexture(e.skin);GlStateManager.color(.64F,.82F,1,alpha);
            if(e.rhino){if(rhino==null)rhino=new ModelRunningRhino();rhino.renderPose(EntityRunningRhino.CHARGE,e.phase,e.age,e.limb,e.amount,e.angry,.0625F);return;}
            GlStateManager.scale(.9375F,.9375F,.9375F);GlStateManager.rotate(e.lean,1,0,0);GlStateManager.rotate(e.roll,0,0,1);
            ModelPlayer model=e.small?slim:wide;apply(model,e.pose);
            ModelRenderer[] pieces=parts(model);for(int i=0;i<pieces.length;i++){pieces[i].showModel=e.visible[i];pieces[i].render(.0625F);}
            for(int i=0;i<ARMOR.length;i++)if(e.equipment[i]!=null){
                ModelBiped suit=i==2?leggings:armor;apply(suit,e.pose);suit.setVisible(false);
                switch(ARMOR[i]){
                    case HEAD:suit.bipedHead.showModel=true;suit.bipedHeadwear.showModel=true;break;
                    case CHEST:suit.bipedBody.showModel=true;suit.bipedRightArm.showModel=true;suit.bipedLeftArm.showModel=true;break;
                    case LEGS:suit.bipedBody.showModel=true;suit.bipedRightLeg.showModel=true;suit.bipedLeftLeg.showModel=true;break;
                    case FEET:suit.bipedRightLeg.showModel=true;suit.bipedLeftLeg.showModel=true;break;
                    default:break;
                }
                Minecraft.getMinecraft().getTextureManager().bindTexture(e.equipment[i]);int colour=e.dyes[i];
                GlStateManager.color(((colour>>16)&255)/255F*.64F,((colour>>8)&255)/255F*.82F,(colour&255)/255F,alpha);
                for(ModelRenderer part:parts(suit))part.render(.0625F);
                if(e.overlays[i]!=null){Minecraft.getMinecraft().getTextureManager().bindTexture(e.overlays[i]);GlStateManager.color(.64F,.82F,1,alpha);for(ModelRenderer part:parts(suit))part.render(.0625F);}
            }
        }finally{GlStateManager.popMatrix();}
    }
    private static void vertex(BufferBuilder b,double x,double y,double z,float red,float green,float blue,float a){b.pos(x,y,z).color(red,green,blue,a).endVertex();}
    private static void lightning(Trail t,double now,double cx,double cy,double cz){
        if(t.echoes.size()<2)return;
        GlStateManager.disableTexture2D();GlStateManager.color(1,1,1,1);GL11.glLineWidth(1.4F);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
        Echo before=null;int link=0;
        for(Echo current:t.echoes){
            if(before!=null){
                float a=Math.min(DashTrailPath.alpha(now-before.born),DashTrailPath.alpha(now-current.born))*.85F;
                double dx=current.x-before.x,dz=current.z-before.z,length=Math.sqrt(dx*dx+dz*dz);
                if(length>.01&&length<DashTrailPath.SPACING*1.6){
                    double px=-dz/length,pz=dx/length,height=t.actor.height*.55;
                    for(int strand=0;strand<2;strand++){
                        double ox=before.x-cx+px*(strand==0?.18:-.18),oy=before.y-cy+height,oz=before.z-cz+pz*(strand==0?.18:-.18);
                        for(int step=1;step<=5;step++){
                            double f=step/5.0,j=step==5?0:DashTrailPath.jitter(t.sequence+link*17+strand*43,step)*.11;
                            double nx=before.x-cx+dx*f+px*(j+(strand==0?.18:-.18)),ny=before.y-cy+(current.y-before.y)*f+height+j,nz=before.z-cz+dz*f+pz*(j+(strand==0?.18:-.18));
                            vertex(b,ox,oy,oz,strand==0?.67F:.83F,.78F,1,a);vertex(b,nx,ny,nz,strand==0?.67F:.83F,.78F,1,a);ox=nx;oy=ny;oz=nz;
                        }
                    }
                }
            }
            before=current;link++;
        }
        Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
    }
    private static void bursts(Trail t,double now,double cx,double cy,double cz){
        double age=t.active?now-t.start:now-t.end;if(age<0||age>4)return;
        double x=t.active?t.startX:t.endX,y=t.active?t.startY:t.endY,z=t.active?t.startZ:t.endZ;float alpha=(float)(1-age/4)*.65F;
        double radius=.2+age*.2,height=t.actor.height;
        GlStateManager.disableTexture2D();GL11.glLineWidth(1.4F);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
        for(int i=0;i<24;i++){
            double a=i*Math.PI/12,next=(i+1)*Math.PI/12;
            vertex(b,x-cx+Math.cos(a)*radius,y-cy+.04,z-cz+Math.sin(a)*radius,.7F,.85F,1,alpha);
            vertex(b,x-cx+Math.cos(next)*radius,y-cy+.04,z-cz+Math.sin(next)*radius,.7F,.85F,1,alpha);
        }
        for(int i=0;i<5;i++){
            double a=i*Math.PI*2/5;
            vertex(b,x-cx+Math.cos(a)*.3,y-cy+height*.2,z-cz+Math.sin(a)*.3,.8F,.85F,1,alpha);
            vertex(b,x-cx+Math.cos(a)*.45,y-cy+height*.85,z-cz+Math.sin(a)*.45,.8F,.85F,1,0);
        }
        Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        Minecraft mc=Minecraft.getMinecraft();if(event.getType()!=RenderGameOverlayEvent.ElementType.ALL||!PocketConfig.dashVisuals||!PocketConfig.dashFirstPerson||mc.player==null||mc.gameSettings.thirdPersonView!=0||mc.isGamePaused())return;
        Trail t=TRAILS.get(mc.player.getEntityId());if(t==null||mc.player.isInvisible())return;
        double age=clock+mc.getRenderPartialTicks()-t.start;
        float alpha=t.active?.28F:(float)Math.max(0,1-(clock-t.end)/3.0)*.2F;if(alpha<=0)return;
        int width=event.getResolution().getScaledWidth(),height=event.getResolution().getScaledHeight();State state=new State();
        try{
            state.begin();GlStateManager.disableDepth();GlStateManager.disableTexture2D();GL11.glLineWidth(1.3F);
            BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
            for(int i=0;i<16;i++){
                double a=i*Math.PI/8+.06*Math.sin(age*.7),inner=.78+.04*DashTrailPath.jitter(47,i),outer=1.14;
                vertex(b,width*.5+Math.cos(a)*width*.5*inner,height*.5+Math.sin(a)*height*.5*inner,0,.7F,.83F,1,0);
                vertex(b,width*.5+Math.cos(a)*width*.5*outer,height*.5+Math.sin(a)*height*.5*outer,0,.7F,.83F,1,alpha);
            }
            Tessellator.getInstance().draw();
        }finally{state.restore();}
    }
    @SubscribeEvent public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event){
        if(PocketConfig.dashSounds||event.getSound()==null)return;
        ResourceLocation id=event.getSound().getSoundLocation();if(PocketMod.MODID.equals(id.getResourceDomain())&&id.getResourcePath().startsWith("dash_"))event.setResultSound(null);
    }
    static void clearActor(int actor){TRAILS.remove(actor);}
    static Echo foldSnapshot(EntityLivingBase actor,double x,double feetY,double z){return instance==null||!(actor instanceof AbstractClientPlayer)||actor.isInvisible()?null:instance.capturePose(actor,new DashTrailPath.Point(x,feetY,z),0,false);}
    static void drawFold(Echo echo,float alpha,float width,float stretch,double cx,double cy,double cz){
        if(instance==null||echo==null)return;
        GlStateManager.pushMatrix();
        try{GlStateManager.translate(echo.x-cx,echo.y-cy+.9,echo.z-cz);GlStateManager.scale(width,stretch,width);GlStateManager.translate(-(echo.x-cx),-(echo.y-cy+.9),-(echo.z-cz));instance.draw(echo,alpha,cx,cy,cz);}
        finally{GlStateManager.popMatrix();}
    }
    /** Restore both actual GL state and GlStateManager caches; never leave alpha/depth altered for other renders. */
    static final class State {
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY,width=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        // LWJGL 2 requires room for 16 values for every buffered glGetFloat, including GL_CURRENT_COLOR.
        final FloatBuffer colour=colourBuffer();
        static FloatBuffer colourBuffer(){return BufferUtils.createFloatBuffer(16);}
        int texture;boolean textured;
        State(){GL11.glGetFloat(GL11.GL_CURRENT_COLOR,colour);}
        void begin(){
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);textured=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.disableLighting();GlStateManager.disableAlpha();GlStateManager.enableDepth();GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        }
        void restore(){
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.bindTexture(texture);if(textured)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();GlStateManager.depthMask(mask);GL11.glLineWidth(width);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lightX,lightY);GlStateManager.color(colour.get(0),colour.get(1),colour.get(2),colour.get(3));GlStateManager.setActiveTexture(active);
        }
    }
}
