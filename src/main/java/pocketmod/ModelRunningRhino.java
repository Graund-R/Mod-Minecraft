package pocketmod;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.*;

/** Exact editable preview geometry; all transforms are reset each frame. */
@SideOnly(Side.CLIENT)
public final class ModelRunningRhino extends ModelBase {
    private final JsonArray parts;
    private final JsonObject pivots;
    private static final float DEG=57.29578F;
    public ModelRunningRhino(){
        textureWidth=160;textureHeight=16;
        try(InputStream in=Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(PocketMod.MODID,"models/entity/running_rhino.json")).getInputStream()){
            JsonObject model=new JsonParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            parts=model.getAsJsonArray("parts");pivots=model.getAsJsonObject("groups");
        }catch(IOException e){throw new IllegalStateException("Missing running rhino geometry",e);}
    }
    private static float n(JsonArray a,int i){return a.get(i).getAsFloat();}
    @Override public void render(Entity entity,float limb,float amount,float age,float yaw,float pitch,float scale){
        EntityRunningRhino rhino=(EntityRunningRhino)entity;int action=rhino.action();float t=rhino.progress(age-entity.ticksExisted);
        RhinoAnimation.RearPose rear=action==EntityRunningRhino.REAR?RhinoAnimation.rear(t*RhinoAnimation.REAR_SECONDS):null;
        boolean charge=action==EntityRunningRhino.CHARGE;
        float cycle=charge?age*.95F:limb*.95F;
        float strength=charge?1.1F:Math.min(.72F,amount*2.1F);
        GlStateManager.pushMatrix();GlStateManager.translate(0,24*scale,0);GlStateManager.scale(scale,-scale,scale);GlStateManager.rotate(90,0,1,0);
        if(rear!=null){GlStateManager.translate(rear.x,rear.y,0);GlStateManager.rotate(rear.angle*DEG,0,0,1);}
        else if(charge){GlStateManager.translate(0,1+(float)Math.abs(Math.sin(cycle))*2.7F,0);GlStateManager.rotate(-5+(float)Math.sin(cycle)*3,0,0,1);}
        else if(action==EntityRunningRhino.ROAM)GlStateManager.translate(0,(float)Math.sin(age*.115F)*.6F,0);
        for(JsonElement element:parts){JsonObject p=element.getAsJsonObject();String group=p.get("group").getAsString();JsonArray pos=p.getAsJsonArray("pos"),size=p.getAsJsonArray("size"),rot=p.getAsJsonArray("rot");
            GlStateManager.pushMatrix();
            if(pivots.has(group)){
                JsonArray pivot=pivots.getAsJsonArray(group);float gx=n(pivot,0),gy=n(pivot,1),gz=n(pivot,2);GlStateManager.translate(gx,gy,gz);
                if(group.startsWith("front")||group.startsWith("back")){
                    float offset=(group.equals("frontL")||group.equals("backR"))?0:3.14159F;
                    float angle=(float)Math.sin(cycle+offset)*strength;
                    if(action!=EntityRunningRhino.ROAM&&!charge)angle=0;
                    float sx=1,sy=1;
                    if(rear!=null){
                        if(group.startsWith("back")){angle=rear.backAngle;sy=rear.backScale;}
                        else{angle=rear.frontAngle;sx=sy=rear.frontScale;}
                    }
                    GlStateManager.rotate(angle*DEG,0,0,1);GlStateManager.scale(sx,sy,1);
                }else if(group.equals("head")){
                    float angle=charge?-.26F:action==EntityRunningRhino.WINDUP?-.28F*t:0;
                    if(rear!=null)angle=rear.head;
                    if(action==EntityRunningRhino.HORN){GlStateManager.rotate(RhinoAnimation.hornYaw(t*.9F)*DEG,0,1,0);angle+=(float)Math.sin(t*Math.PI)*.42F;}
                    if(action==EntityRunningRhino.STUCK)angle=-.32F+(float)Math.sin(age*.65F)*.06F;
                    if(action==EntityRunningRhino.OVERHEAT)angle=-.24F+(float)Math.sin(age*.4F)*.07F;
                    GlStateManager.rotate(angle*DEG,0,0,1);
                }else if(group.equals("tail"))GlStateManager.rotate((float)Math.sin(age*.2F)*19,0,1,0);
                GlStateManager.translate(-gx,-gy,-gz);
            }
            GlStateManager.translate(n(pos,0),n(pos,1),n(pos,2));
            GlStateManager.rotate(n(rot,2),0,0,1);GlStateManager.rotate(n(rot,1),0,1,0);GlStateManager.rotate(n(rot,0),1,0,0);
            cube(n(size,0),n(size,1),n(size,2),p.get("mat").getAsInt());GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
    private void cube(float w,float h,float d,int mat){
        double x=w/2,y=h/2,z=d/2;double[][] v={{-x,-y,-z},{x,-y,-z},{x,y,-z},{-x,y,-z},{-x,-y,z},{x,-y,z},{x,y,z},{-x,y,z}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,1,5,4},{3,7,6,2},{0,4,7,3},{1,2,6,5}};float[][] normals={{0,0,-1},{0,0,1},{0,-1,0},{0,1,0},{-1,0,0},{1,0,0}};
        double u0=(mat*16+.5)/160,u1=(mat*16+15.5)/160,v0=.5/16,v1=15.5/16;
        Tessellator tess=Tessellator.getInstance();BufferBuilder b=tess.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_NORMAL);
        for(int i=0;i<6;i++)for(int j=0;j<4;j++){double[] p=v[faces[i][j]];b.pos(p[0],p[1],p[2]).tex(j==0||j==3?u0:u1,j<2?v0:v1).normal(normals[i][0],normals[i][1],normals[i][2]).endVertex();}tess.draw();
    }
}
