package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class TemporalClient {
    private final KeyBinding rewind=new KeyBinding("pocketmod.temporal.key_rewind",Keyboard.KEY_U,"key.categories.pocketmod");
    private final KeyBinding forward=new KeyBinding("pocketmod.temporal.key_forward",Keyboard.KEY_I,"key.categories.pocketmod");
    private final KeyBinding preview=new KeyBinding("pocketmod.temporal.key_preview",Keyboard.KEY_O,"key.categories.pocketmod");
    private int ticks,held;
    private boolean previewDown,rewindDown;
    private final KeyBinding activate=new KeyBinding("pocketmod.temporal.key_activate",Keyboard.KEY_P,"key.categories.pocketmod");
    private boolean activateDown;
    private static boolean down(KeyBinding key){return key.getKeyCode()>0 && Keyboard.isKeyDown(key.getKeyCode()) && key.getKeyModifier().isActive(net.minecraftforge.client.settings.KeyConflictContext.IN_GAME);}
    private boolean wheel,wasFrozen;
    private net.minecraft.client.shader.ShaderGroup ownShader;
    public TemporalClient(){ClientRegistry.registerKeyBinding(rewind);ClientRegistry.registerKeyBinding(forward);ClientRegistry.registerKeyBinding(preview);ClientRegistry.registerKeyBinding(activate);}
    private static NBTTagCompound state() {EntityPlayer p=Minecraft.getMinecraft().player;return p==null?new NBTTagCompound():PlayerData.root(p).getCompoundTag("temporal");}
    static void action(int kind,int value) {NetworkHandler.action(NetAction.TEMPORAL,kind,value,0,"");}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();ticks++;
        if(mc.world==null || mc.player==null){TemporalSystem.clientFrozen=false;TemporalSystem.clientCanMove=true;wheel=false;held=0;wasFrozen=false;ownShader=null;return;}
        if(TemporalSystem.clientFrozen!=wasFrozen){
            if(TemporalSystem.clientFrozen)mc.getSoundHandler().pauseSounds();else mc.getSoundHandler().resumeSounds();
            wasFrozen=TemporalSystem.clientFrozen;
        }
        if(TemporalSystem.clientFrozen && ownShader==null && mc.entityRenderer.getShaderGroup()==null){
            try{mc.entityRenderer.loadShader(new net.minecraft.util.ResourceLocation("minecraft","shaders/post/desaturate.json"));ownShader=mc.entityRenderer.getShaderGroup();}catch(Exception ignored){}
        }else if(!TemporalSystem.clientFrozen && ownShader!=null){
            if(mc.entityRenderer.getShaderGroup()==ownShader)mc.entityRenderer.stopUseShader();ownShader=null;
        }
        if(TemporalSystem.clientFrozen){
            mc.world.setWorldTime(TemporalSystem.clientDay);
            for(net.minecraft.entity.Entity entity:mc.world.loadedEntityList)if(TemporalHooks.skipEntity(entity))TemporalHooks.settle(entity);
        }
        NBTTagCompound state=state();
        if(mc.currentScreen!=null){held=0;previewDown=rewindDown=activateDown=false;return;}
        boolean previewNow=down(preview),rewindNow=down(rewind),activateNow=down(activate);
        if(previewNow&&!previewDown)action(1,0);
        if(activateNow&&!activateDown)action(10,0);
        boolean rewindPressed=rewindNow&&!rewindDown;
        previewDown=previewNow;rewindDown=rewindNow;activateDown=activateNow;
        if(state.getInteger("mode")==3 && state.getBoolean("owner")) {
            if(rewindPressed)action(3,5);
            return;
        }
        if(state.getInteger("mode")!=1 || !state.getBoolean("owner")){wheel=false;held=0;return;}
        if(rewindNow) {
            held++;
            if(held>=6 && ticks%2==0){wheel=false;action(0,-1);}
        }else if(held>0){if(held<6)wheel=!wheel;held=0;}
        if(down(forward) && ticks%2==0)action(0,1);
        // Preview of a redirected projectile, refreshed while looking at it.
        if(ticks%4==0 && TemporalSystem.clientFrozen) {
            net.minecraft.util.math.Vec3d eye=mc.player.getPositionEyes(1),look=mc.player.getLookVec();
            for(net.minecraft.entity.Entity entity:mc.world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class,mc.player.getEntityBoundingBox().grow(6))) {
                if(!(entity instanceof net.minecraft.entity.IProjectile) && !(entity instanceof net.minecraft.entity.projectile.EntityFireball))continue;
                if(entity.getEntityBoundingBox().grow(.4).calculateIntercept(eye,eye.add(look.scale(6)))==null)continue;
                for(int i=1;i<9;i++)mc.world.spawnParticle(net.minecraft.util.EnumParticleTypes.END_ROD,entity.posX+look.x*i*.4,entity.posY+look.y*i*.4,entity.posZ+look.z*i*.4,0,0,0);
                break;
            }
        }
    }
    @SubscribeEvent public void mouse(MouseEvent e) {
        Minecraft mc=Minecraft.getMinecraft();if(mc.currentScreen!=null)return;
        if(!TemporalSystem.clientCanMove){e.setCanceled(true);return;}
        if(wheel && e.getDwheel()!=0){action(0,e.getDwheel()<0?-1:1);e.setCanceled(true);}
        if(e.getButton()==0 && e.isButtonstate() && TemporalSystem.clientFrozen)action(4,0);
    }
    @SubscribeEvent public void input(InputUpdateEvent e) {
        if(TemporalSystem.clientCanMove)return;
        e.getMovementInput().moveForward=0;e.getMovementInput().moveStrafe=0;
        e.getMovementInput().jump=false;e.getMovementInput().sneak=false;
    }
    @SubscribeEvent public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e) {
        if(!TemporalSystem.clientFrozen || e.getSound()==null)return;
        net.minecraft.util.SoundCategory category=e.getSound().getCategory();
        if(category==net.minecraft.util.SoundCategory.MUSIC || category==net.minecraft.util.SoundCategory.AMBIENT
                || category==net.minecraft.util.SoundCategory.WEATHER)e.setResultSound(null);
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e) {
        if(e.getType()!=RenderGameOverlayEvent.ElementType.ALL)return;
        Minecraft mc=Minecraft.getMinecraft();NBTTagCompound s=state();if(s.getInteger("mode")==0)return;
        ScaledResolution r=e.getResolution();int w=r.getScaledWidth(),h=r.getScaledHeight();
        int colour=s.getInteger("mode")==2?0x18A070FF:0x1870BFFF;
        Gui.drawRect(0,0,w,3,colour|0xB0000000);Gui.drawRect(0,h-3,w,h,colour|0xB0000000);
        String title=net.minecraft.client.resources.I18n.format("pocketmod.temporal.hud."+s.getInteger("mode"),String.format(java.util.Locale.ROOT,"%.1f",s.getInteger("left")/20.0));
        mc.fontRenderer.drawStringWithShadow(title,(w-mc.fontRenderer.getStringWidth(title))/2,12,0xBDEAFF);
        String detail=net.minecraft.client.resources.I18n.format("pocketmod.temporal.history",s.getInteger("past")/20.0,s.getInteger("future")/20.0,s.getInteger("rewind")/20.0);
        if(s.getBoolean("owner"))mc.fontRenderer.drawStringWithShadow(detail,(w-mc.fontRenderer.getStringWidth(detail))/2,24,0xDDDDDD);
        if(s.getBoolean("owner"))mc.fontRenderer.drawStringWithShadow(
            activate.getDisplayName()+": "+net.minecraft.client.resources.I18n.format("pocketmod.temporal.key_activate")+"  "+rewind.getDisplayName()+": −  "+forward.getDisplayName()+": +  "+preview.getDisplayName()+": "+net.minecraft.client.resources.I18n.format("pocketmod.temporal.key_preview"),8,h-62,0xBDEAFF);
        if(wheel)mc.fontRenderer.drawStringWithShadow(net.minecraft.client.resources.I18n.format("pocketmod.temporal.wheel"),8,h-50,0xBDEAFF);
    }
}
