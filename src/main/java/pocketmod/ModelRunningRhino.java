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
        EntityRunningRhino rhino=(EntityRunningRhino)entity;int action=rhino.action();float t=rhino.progress(age-entity.ticksExisted),rear=0;
        if(action==EntityRunningRhino.REAR){rear=t<.6F?(float)Math.sin(Math.min(1,t/.35F)*Math.PI/2):Math.max(0,1-(t-.6F)/.12F);}
        boolean charge=action==EntityRunningRhino.CHARGE;
        float cycle=charge?age*.7F:limb*.65F;
        float strength=charge?.72F:Math.min(.45F,amount*1.5F);
        GlStateManager.pushMatrix();GlStateManager.translate(0,24*scale,0);GlStateManager.scale(scale,-scale,scale);GlStateManager.rotate(90,0,1,0);
        GlStateManager.translate(-18,8,0);GlStateManager.rotate(rear*57,0,0,1);GlStateManager.translate(18,-8,0);
        if(charge)GlStateManager.translate(0,1+(float)Math.abs(Math.sin(cycle))*1.5F,0);
        for(JsonElement element:parts){JsonObject p=element.getAsJsonObject();String group=p.get("group").getAsString();JsonArray pos=p.getAsJsonArray("pos"),size=p.getAsJsonArray("size"),rot=p.getAsJsonArray("rot");
            GlStateManager.pushMatrix();
            if(pivots.has(group)){
                JsonArray pivot=pivots.getAsJsonArray(group);float gx=n(pivot,0),gy=n(pivot,1),gz=n(pivot,2);GlStateManager.translate(gx,gy,gz);
                if(group.startsWith("front")||group.startsWith("back")){
                    float offset=(group.equals("frontL")||group.equals("backR"))?0:3.14159F;
                    float angle=(float)Math.sin(cycle+offset)*strength;
                    if(action!=EntityRunningRhino.ROAM&&!charge)angle=0;
                    if(group.startsWith("front"))angle+=rear*.9F;
                    GlStateManager.rotate(angle*DEG,0,0,1);
                }else if(group.equals("head")){
                    float angle=charge?-.18F:action==EntityRunningRhino.WINDUP?-.12F*t:0;
                    if(action==EntityRunningRhino.HORN){GlStateManager.rotate((float)Math.sin(t*Math.PI*2)*32,0,1,0);angle+=(float)Math.sin(t*Math.PI)*.28F;}
                    if(action==EntityRunningRhino.STUCK)angle=-.22F+(float)Math.sin(age*.4F)*.025F;
                    if(action==EntityRunningRhino.OVERHEAT)angle=-.16F+(float)Math.sin(age*.25F)*.045F;
                    GlStateManager.rotate(angle*DEG,0,0,1);
                }else if(group.equals("tail"))GlStateManager.rotate((float)Math.sin(age*.13F)*10,0,1,0);
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
