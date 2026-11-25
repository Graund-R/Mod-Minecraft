package pocketmod;

import java.nio.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.*;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/** Endpoint folds are world geometry; refraction is composed once with the existing infection haze. */
@SideOnly(Side.CLIENT)
public final class TeleportVisuals {
    private static final Deque<Fold> FOLDS=new ArrayDeque<Fold>();
    private static World world;private static int clock;
    private static final class Fold {
        double x,y,z;float width,height;int actor,born;boolean arrival,local;
        DashVisuals.Echo ghost;
    }
    public TeleportVisuals(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager)
            ((net.minecraft.client.resources.IReloadableResourceManager)mc.getResourceManager()).registerReloadListener(manager -> ExileConvectionClient.reset());
    }
    private static void world(World next){if(world!=next){world=next;FOLDS.clear();clock=0;}}
    public static void receive(TeleportEffects.Message m){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null||mc.player==null||mc.world.provider.getDimension()!=m.dimension||!TeleportFold.valid(m.x,m.y,m.z)
                ||!Float.isFinite(m.width)||!Float.isFinite(m.height)||m.width<=0||m.height<=0)return;
        world(mc.world);DashVisuals.clearActor(m.actorId);
        boolean local=mc.player.getEntityId()==m.actorId;
        if(PocketConfig.teleportSounds){
            SoundEvent sound=m.arrival?TeleportSounds.UNFOLD:TeleportSounds.FOLD;
            PositionedSoundRecord record=new PositionedSoundRecord(sound.getSoundName(),m.hostile?SoundCategory.HOSTILE:SoundCategory.PLAYERS,
                    m.arrival?.6F:.5F,1,false,0,local?ISound.AttenuationType.NONE:ISound.AttenuationType.LINEAR,(float)m.x,(float)m.y,(float)m.z);
            if(local&&m.arrival)mc.getSoundHandler().playDelayedSound(record,2);else mc.getSoundHandler().playSound(record);
        }
        if(!PocketConfig.teleportVisuals)return;
        Fold f=new Fold();f.x=m.x;f.y=m.y;f.z=m.z;f.width=Math.max(.3F,Math.min(4,m.width));f.height=Math.max(1,Math.min(5,m.height));f.actor=m.actorId;f.born=clock;f.arrival=m.arrival;f.local=local;
        Entity actor=mc.world.getEntityByID(m.actorId);
        if(!f.arrival&&actor instanceof EntityLivingBase)f.ghost=DashVisuals.foldSnapshot((EntityLivingBase)actor,f.x,f.y-f.height*.5,f.z);
        while(FOLDS.size()>=TeleportFold.MAX_EVENTS)FOLDS.removeFirst();FOLDS.addLast(f);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();world(mc.world);
        if(mc.world==null||mc.isGamePaused())return;clock++;
        if(!PocketConfig.teleportVisuals){FOLDS.clear();return;}
        while(!FOLDS.isEmpty()&&clock-FOLDS.peekFirst().born>=TeleportFold.TICKS)FOLDS.removeFirst();
    }
    private static double x(Entity camera,float p){return camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*p;}
    private static double y(Entity camera,float p){return camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*p;}
    private static double z(Entity camera,float p){return camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*p;}
    @SubscribeEvent public void geometry(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();world(mc.world);
        if(!PocketConfig.teleportVisuals||FOLDS.isEmpty()||mc.player==null)return;
        Entity camera=mc.getRenderViewEntity();if(camera==null)return;
        float p=event.getPartialTicks();double cx=x(camera,p),cy=y(camera,p),cz=z(camera,p);DashVisuals.State state=new DashVisuals.State();
        try{
            state.begin();
            for(Fold f:FOLDS){
                if(camera.getDistanceSq(f.x,f.y,f.z)>96*96 || (f.local && mc.gameSettings.thirdPersonView==0))continue;
                float t=TeleportFold.progress(clock+p-f.born),opacity=TeleportFold.opacity(t),radius=TeleportFold.radius(t,f.arrival);
                if(opacity<=0)continue;
                if(f.ghost!=null&&(!f.local||mc.gameSettings.thirdPersonView!=0))
                    DashVisuals.drawFold(f.ghost,opacity,radius,1+TeleportFold.envelope(t)*.12F,cx,cy,cz);
                GlStateManager.disableTexture2D();GlStateManager.color(1,1,1,1);GL11.glLineWidth(1.6F);
                BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
                double r=(f.width*.65+.5)*radius,h=f.height*.6;
                // Broken horizontal contours curve into a narrow vertical seam rather than forming a portal disc.
                for(int ring=0;ring<3;ring++)for(int i=0;i<32;i++){
                    if(i%16>=12)continue;
                    double a=i*Math.PI/16+t*.9+ring*.4,next=a+Math.PI/16,dy=(ring-1)*h*.55;
                    line(b,f.x-cx+Math.cos(a)*r,f.y-cy+dy+Math.sin(a*2)*.08,f.z-cz+Math.sin(a)*r,
                            f.x-cx+Math.cos(next)*r,f.y-cy+dy+Math.sin(next*2)*.08,f.z-cz+Math.sin(next)*r,opacity*.8F);
                }
                for(int plane=0;plane<2;plane++)for(int i=0;i<40;i++){
                    if(i%20>=17)continue;
                    double a=i*Math.PI/20,next=a+Math.PI/20;
                    double ax=Math.cos(a)*r,bx=Math.cos(next)*r,ay=Math.sin(a)*h,by=Math.sin(next)*h;
                    line(b,f.x-cx+(plane==0?ax:0),f.y-cy+ay,f.z-cz+(plane==1?ax:0),
                            f.x-cx+(plane==0?bx:0),f.y-cy+by,f.z-cz+(plane==1?bx:0),opacity);
                }
                line(b,f.x-cx,f.y-cy-h,f.z-cz,f.x-cx,f.y-cy+h,f.z-cz,opacity*(1-radius));
                Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
            }
        }finally{state.restore();}
    }
    private static void line(BufferBuilder b,double x,double y,double z,double nx,double ny,double nz,float alpha){
        b.pos(x,y,z).color(.70F,.69F,1,alpha).endVertex();b.pos(nx,ny,nz).color(.66F,.85F,1,alpha*.75F).endVertex();
    }
    /** Eight floats per lens: UV/radii, then warp/depth/reserved/reserved. No HUD coordinates are sampled. */
    static float[] lenses(float partial){
        Minecraft mc=Minecraft.getMinecraft();world(mc.world);
        if(!PocketConfig.teleportVisuals||!PocketConfig.teleportRefraction||FOLDS.isEmpty()||mc.player==null)return new float[0];
        Entity camera=mc.getRenderViewEntity();if(camera==null)return new float[0];
        FloatBuffer view=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),out=BufferUtils.createFloatBuffer(3);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,view);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        if(viewport.get(2)<=0||viewport.get(3)<=0)return new float[0];
        double cx=x(camera,partial),cy=y(camera,partial),cz=z(camera,partial);
        List<float[]> lenses=new ArrayList<float[]>();
        for(Fold f:FOLDS){
            float t=TeleportFold.progress(clock+partial-f.born),warp=TeleportFold.warp(t,f.arrival);if(Math.abs(warp)<.001F)continue;
            if(f.local&&f.arrival&&mc.gameSettings.thirdPersonView==0){lenses.add(new float[]{.5F,.5F,.65F,.85F,warp*.32F,-1,0,0});continue;}
            if(!GLU.gluProject((float)(f.x-cx),(float)(f.y-cy),(float)(f.z-cz),view,projection,viewport,out))continue;
            float u=(out.get(0)-viewport.get(0))/viewport.get(2),v=(out.get(1)-viewport.get(1))/viewport.get(3),depth=out.get(2);
            if(depth<=0||depth>=1||u<-.15||u>1.15||v<-.2||v>1.2)continue;
            float scale=TeleportFold.radius(t,f.arrival),rx=(f.width*.65F+.5F)*scale*1.8F,ry=f.height*.7F;
            if(!GLU.gluProject((float)(f.x-cx)+view.get(0)*rx,(float)(f.y-cy)+view.get(4)*rx,(float)(f.z-cz)+view.get(8)*rx,view,projection,viewport,out))continue;
            rx=Math.abs((out.get(0)-viewport.get(0))/viewport.get(2)-u);
            if(!GLU.gluProject((float)(f.x-cx)+view.get(1)*ry,(float)(f.y-cy)+view.get(5)*ry,(float)(f.z-cz)+view.get(9)*ry,view,projection,viewport,out))continue;
            ry=Math.abs((out.get(1)-viewport.get(1))/viewport.get(3)-v);
            if(rx>.001F&&ry>.001F)lenses.add(new float[]{u,v,Math.min(.45F,rx),Math.min(.65F,ry),warp,depth,0,0});
        }
        lenses.sort((a,b)->Float.compare(score(b),score(a)));
        int count=Math.min(TeleportFold.MAX_LENSES,lenses.size());float[] packed=new float[count*8];for(int i=0;i<count;i++)System.arraycopy(lenses.get(i),0,packed,i*8,8);return packed;
    }
    private static float score(float[] lens){return lens[5]<0?1000:lens[2]*lens[3]*Math.abs(lens[4]);}
    @SubscribeEvent public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){
        if(PocketConfig.teleportSounds||e.getSound()==null)return;ResourceLocation id=e.getSound().getSoundLocation();
        if(PocketMod.MODID.equals(id.getResourceDomain())&&id.getResourcePath().startsWith("teleport_"))e.setResultSound(null);
    }
}
