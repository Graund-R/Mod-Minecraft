package pocketmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class ExileDebugClient {
    public static Boolean borders;
    private final KeyBinding key = new KeyBinding("key.pocketmod.exile_debug", Keyboard.KEY_F8, "key.categories.pocketmod");
    public ExileDebugClient() { net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(key); }
    @SubscribeEvent public void key(net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!key.isPressed() || mc.player == null || mc.currentScreen != null || !ExileProgression.inMode(mc.player)) return;
        if (!ExileMode.state(mc.player).getBoolean("debug_available")) return;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            if(ExileMode.state(mc.player).getBoolean("debug_enabled")) NetworkHandler.action(NetAction.EXILE_DEBUG,0,0,0,"");
            return;
        }
        if (!ExileMode.state(mc.player).getBoolean("debug_enabled")) NetworkHandler.action(NetAction.EXILE_DEBUG, 0,0,0,"");
        mc.displayGuiScreen(new ExileDebugGui());
    }
    static String[] lines(NBTTagCompound s, NBTTagCompound d) {
        List<String> out = new ArrayList<>();
        out.add(Tr.format("pocketmod.debug.current", s.getInteger("stage"), s.getInteger("contamination"), d.getInteger("full"), s.getBoolean("debug_pause") ? Tr.format("pocketmod.debug.paused") : ""));
        out.add(Tr.format("pocketmod.debug.gray",Tr.format("pocketmod.debug.gray_"+ExileGrayClient.status),Tr.format("pocketmod.debug.reason_"+d.getString("reason"))));
        out.add(Tr.format("pocketmod.debug.schedule", d.getInteger("interval"), d.getInteger("batch"), d.getInteger("cap")));
        out.add(Tr.format("pocketmod.debug.terrain_age",d.getInteger("full_minutes"),d.getInteger("black"),String.format(java.util.Locale.ROOT,"%.2f",d.getDouble("spread"))));
        out.add(Tr.format("pocketmod.debug.infection_symptoms",s.getInteger("infection_full_time"),
                Math.max(0,ExileSymptoms.interval(s.getInteger("contamination"))-s.getInteger("infection_symptom_time"))));
        NBTTagCompound r = s.getCompoundTag("spawn_report");
        out.add(Tr.format("pocketmod.debug.respawns", d.getInteger("respawn_delay"), d.getInteger("respawn_count"), d.getInteger("respawn_next")));
        out.add(Tr.format("pocketmod.debug.auto_state", Tr.format(s.getBoolean("debug_waves_pause") ? "pocketmod.debug.stopped" : "pocketmod.debug.running")));
        out.add(Tr.format("pocketmod.debug.pending",d.getInteger("pending_seconds"),d.getInteger("pending_stage")));
        out.add(Tr.format("pocketmod.debug.wave_types",r.getInteger("wave_0"),r.getInteger("planned_0"),r.getInteger("wave_1"),r.getInteger("planned_1"),r.getInteger("wave_2"),r.getInteger("planned_2")));
        out.add(Tr.format("pocketmod.debug.result", Tr.format("pocketmod.debug.result_"+r.getString("result")), r.getInteger("wave"), r.getInteger("total")));
        out.add(Tr.format("pocketmod.debug.reject", r.getInteger("floor"), r.getInteger("space"), r.getInteger("visible"), r.getInteger("path"), r.getInteger("unloaded"), r.getInteger("event")));
        NBTTagList mobs = d.getTagList("mobs",10);
        StringBuilder counts = new StringBuilder();
        for (int i=0; i<mobs.tagCount(); i++) { NBTTagCompound c=mobs.getCompoundTagAt(i); if(i>0) counts.append(" | "); counts.append(c.getString("id").replace("minecraft:vex",Tr.format("pocketmod.debug.vex")).replace("minecraft:wither_skeleton",Tr.format("pocketmod.debug.wither")).replace("minecraft:skeleton",Tr.format("pocketmod.debug.skeleton"))).append(": ").append(c.getInteger("count")); }
        out.add(Tr.format("pocketmod.debug.live", counts.length()==0 ? "0" : counts.toString()));
        NBTTagList chunks = d.getTagList("chunks",10);
        for(int row=0;row<3;row++) {
            StringBuilder line = new StringBuilder();
            for(int col=0;col<3;col++) { NBTTagCompound c=chunks.getCompoundTagAt(row*3+col); line.append(Tr.format("pocketmod.debug.chunk",c.getInteger("x"),c.getInteger("z"),c.getInteger("stage"),c.getInteger("percent"))).append("   "); }
            out.add(line.toString());
        }
        if (!d.getBoolean("edit")) out.add(Tr.format("pocketmod.debug.readonly"));
        out.add(Tr.format("pocketmod.debug.hint"));
        return out.toArray(new String[0]);
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e) {
        Minecraft mc=Minecraft.getMinecraft();
        if(e.getType()!=RenderGameOverlayEvent.ElementType.ALL || mc.player==null || mc.gameSettings.hideGUI || mc.currentScreen instanceof ExileDebugGui) return;
        NBTTagCompound s=ExileMode.state(mc.player);
        if(!ExileConfig.debugHud || !s.getBoolean("debug_enabled") || !s.getBoolean("debug_available")) return;
        String text=Tr.format("pocketmod.debug.hud",s.getInteger("stage"),s.getInteger("contamination"));
        int y=e.getResolution().getScaledHeight()-58;
        Gui.drawRect(7,y-2,12+mc.fontRenderer.getStringWidth(text),y+11,0xB0000000);
        mc.fontRenderer.drawStringWithShadow(text,10,y,0xFFFFA0);
    }
    private static void line(BufferBuilder b, double x,double y,double z,double x2,double y2,double z2,float r,float g,float blue) {
        b.pos(x,y,z).color(r,g,blue,0.8f).endVertex(); b.pos(x2,y2,z2).color(r,g,blue,0.8f).endVertex();
    }
    @SubscribeEvent public void boundaries(RenderWorldLastEvent e) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null || mc.world==null || !(borders == null ? ExileConfig.debugBorders : borders) || !ExileMode.state(mc.player).getBoolean("debug_enabled")
                || !ExileMode.state(mc.player).getBoolean("debug_available") || mc.player.dimension!=0) return;
        double px=mc.getRenderManager().viewerPosX, py=mc.getRenderManager().viewerPosY, pz=mc.getRenderManager().viewerPosZ;
        int cx=MathHelper.floor(mc.player.posX)>>4, cz=MathHelper.floor(mc.player.posZ)>>4;
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.pushMatrix(); GlStateManager.translate(-px,-py,-pz);
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0); GlStateManager.depthMask(false); GL11.glLineWidth(1.5f);
        BufferBuilder b=Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
        NBTTagList chunks=ExileMode.state(mc.player).getCompoundTag("debug_snapshot").getTagList("chunks",10);
        for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++) {
            int x=(cx+dx)*16,z=(cz+dz)*16,stage=0;
            for(int i=0;i<chunks.tagCount();i++){NBTTagCompound c=chunks.getCompoundTagAt(i); if(c.getInteger("x")==cx+dx && c.getInteger("z")==cz+dz)stage=c.getInteger("stage");}
            float r=stage==0?0.1f:1f,g=stage==0?1f:Math.max(0.1f,1-stage*0.22f);
            for(int sx:new int[]{0,16})for(int sz:new int[]{0,16})line(b,x+sx,0,z+sz,x+sx,256,z+sz,r,g,0.2f);
            int middle=MathHelper.floor(mc.player.posY/16)*16;
            for(int y=middle-16;y<=middle+32;y+=16){
                line(b,x,y,z,x+16,y,z,r,g,0.2f);line(b,x+16,y,z,x+16,y,z+16,r,g,0.2f);
                line(b,x+16,y,z+16,x,y,z+16,r,g,0.2f);line(b,x,y,z+16,x,y,z,r,g,0.2f);
            }
        }
        Tessellator.getInstance().draw(); GL11.glLineWidth(1f); GlStateManager.depthMask(true);
        GlStateManager.disableBlend(); GlStateManager.enableTexture2D();
        if(depth) GlStateManager.enableDepth();
        if(lighting) GlStateManager.enableLighting();
        GlStateManager.color(1,1,1,1); GlStateManager.popMatrix();
    }
}
