package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
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
    private static final KeyBinding REWIND=new KeyBinding("pocketmod.temporal.key_rewind",Keyboard.KEY_U,"key.categories.pocketmod");
    private static final KeyBinding FUTURE=new KeyBinding("pocketmod.temporal.key_preview",Keyboard.KEY_O,"key.categories.pocketmod");
    private static final KeyBinding MENU=new KeyBinding("pocketmod.temporal.key_menu",Keyboard.KEY_I,"key.categories.pocketmod");
    private boolean rewindDown,futureDown,menuDown,wasFrozen;
    private net.minecraft.client.shader.ShaderGroup ownShader;
    public TemporalClient(){ClientRegistry.registerKeyBinding(REWIND);ClientRegistry.registerKeyBinding(FUTURE);ClientRegistry.registerKeyBinding(MENU);}
    public static String rewindKey(){return REWIND.getDisplayName();}
    public static String futureKey(){return FUTURE.getDisplayName();}
    public static String menuKey(){return MENU.getDisplayName();}
    private static boolean down(KeyBinding key){return key.getKeyCode()>0&&Keyboard.isKeyDown(key.getKeyCode())&&key.getKeyModifier().isActive(net.minecraftforge.client.settings.KeyConflictContext.IN_GAME);}
    static NBTTagCompound state(){EntityPlayer p=Minecraft.getMinecraft().player;return p==null?new NBTTagCompound():PlayerData.root(p).getCompoundTag("temporal");}
    static void action(int kind,int value){NetworkHandler.action(NetAction.TEMPORAL,kind,value,0,"");}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null||mc.player==null){TemporalSystem.clientFrozen=false;TemporalSystem.clientCanMove=true;wasFrozen=false;ownShader=null;rewindDown=futureDown=menuDown=false;return;}
        if(TemporalSystem.clientFrozen!=wasFrozen){if(TemporalSystem.clientFrozen)mc.getSoundHandler().pauseSounds();else mc.getSoundHandler().resumeSounds();wasFrozen=TemporalSystem.clientFrozen;}
        if(TemporalSystem.clientFrozen&&ownShader==null&&mc.entityRenderer.getShaderGroup()==null){
            try{mc.entityRenderer.loadShader(new net.minecraft.util.ResourceLocation("minecraft","shaders/post/desaturate.json"));ownShader=mc.entityRenderer.getShaderGroup();}catch(Exception ignored){}
        }else if(!TemporalSystem.clientFrozen&&ownShader!=null){if(mc.entityRenderer.getShaderGroup()==ownShader)mc.entityRenderer.stopUseShader();ownShader=null;}
        if(TemporalSystem.clientFrozen){mc.world.setWorldTime(TemporalSystem.clientDay);for(net.minecraft.entity.Entity entity:mc.world.loadedEntityList)TemporalHooks.skipEntity(entity);}
        if(TemporalSystem.reversing(state().getInteger("mode"))){
            net.minecraft.nbt.NBTTagList poses=state().getTagList("poses",10);
            for(int i=0;i<poses.tagCount();i++){NBTTagCompound pose=poses.getCompoundTagAt(i);net.minecraft.entity.Entity entity=mc.world.getEntityByID(pose.getInteger("entity"));if(entity!=null)TemporalPose.apply(entity,pose);}
        }
        if(mc.currentScreen!=null){rewindDown=futureDown=menuDown=false;return;}
        boolean rewind=down(REWIND),future=down(FUTURE),menu=down(MENU);
        if(rewind&&!rewindDown)action(3,0);
        if(future&&!futureDown)action(1,0);
        if(menu&&!menuDown)mc.displayGuiScreen(new TemporalGui());
        rewindDown=rewind;futureDown=future;menuDown=menu;
    }
    @SubscribeEvent public void mouse(MouseEvent event){
        if(Minecraft.getMinecraft().currentScreen!=null)return;
        if(!TemporalSystem.clientCanMove){event.setCanceled(true);return;}
        if(event.getButton()==0&&event.isButtonstate()&&state().getInteger("mode")==TemporalSystem.STASIS)action(4,0);
    }
    @SubscribeEvent public void input(InputUpdateEvent event){
        if(TemporalSystem.clientCanMove)return;
        event.getMovementInput().moveForward=event.getMovementInput().moveStrafe=0;event.getMovementInput().jump=event.getMovementInput().sneak=false;
    }
    @SubscribeEvent public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event){
        if(!TemporalSystem.clientFrozen||event.getSound()==null)return;
        net.minecraft.util.SoundCategory c=event.getSound().getCategory();
        if(c==net.minecraft.util.SoundCategory.MUSIC||c==net.minecraft.util.SoundCategory.AMBIENT||c==net.minecraft.util.SoundCategory.WEATHER)event.setResultSound(null);
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.ALL)return;
        Minecraft mc=Minecraft.getMinecraft();NBTTagCompound s=state();int mode=s.getInteger("mode");if(mode==0)return;
        int w=event.getResolution().getScaledWidth(),h=event.getResolution().getScaledHeight();
        int color=mode==TemporalSystem.FUTURE?0xFFC58AFF:0xFF9FEAFF;
        Gui.drawRect(0,0,w,3,color);Gui.drawRect(0,h-3,w,h,color);
        String time=String.format(java.util.Locale.ROOT,"%.2f",s.getInteger("left")/20.0);
        String title=net.minecraft.client.resources.I18n.format("pocketmod.temporal.hud."+mode,time);
        mc.fontRenderer.drawStringWithShadow(title,(w-mc.fontRenderer.getStringWidth(title))/2,12,color);
        if(s.getBoolean("owner")){
            String hint=mode==TemporalSystem.FUTURE?net.minecraft.client.resources.I18n.format("pocketmod.temporal.hint_record",futureKey()):
                mode==TemporalSystem.RETURN?net.minecraft.client.resources.I18n.format("pocketmod.temporal.hint_return",futureKey()):
                mode==TemporalSystem.REWIND?net.minecraft.client.resources.I18n.format("pocketmod.temporal.hint_rewind",rewindKey()):
                net.minecraft.client.resources.I18n.format("pocketmod.temporal.hint_stop",ClientHandler.keyName("slow"));
            mc.fontRenderer.drawStringWithShadow(hint,(w-mc.fontRenderer.getStringWidth(hint))/2,25,0xEEEEEE);
            if(TemporalSystem.reversing(mode)){
                int total=Math.max(1,s.getInteger("past")+s.getInteger("future"));
                Gui.drawRect(w/2-90,39,w/2+90,43,0xAA202A38);
                Gui.drawRect(w/2-90,39,w/2-90+180*s.getInteger("past")/total,43,color);
            }
        }
    }
}
