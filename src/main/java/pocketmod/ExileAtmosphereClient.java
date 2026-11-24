package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Local ambience never leaks from a remote infected chunk or into the pocket. */
@SideOnly(Side.CLIENT)
public final class ExileAtmosphereClient {
    private Object lastWorld;
    private int ambienceTicks,lastStage,lastEpisode;
    static float strength;
    private final java.util.List<ISound> playing=new java.util.ArrayList<>();
    private void stop(Minecraft mc) {
        for(ISound sound:playing)mc.getSoundHandler().stopSound(sound);
        playing.clear();
    }
    private static final class FadingSound extends net.minecraft.client.audio.MovingSound {
        private final float base;
        private final int duration;
        private int age;
        FadingSound(SoundEvent event,float base,float pitch,int duration) {
            super(event,SoundCategory.AMBIENT);this.base=base;this.pitch=pitch;this.duration=duration;
            attenuationType=ISound.AttenuationType.NONE;volume=base*Math.max(.01f,strength);
        }
        @Override public void update() {
            volume=base*strength;
            if(++age>=duration||strength<=0.0001f)donePlaying=true;
        }
    }
    private void sound(Minecraft mc,SoundEvent sound,float volume,float pitch) {
        if(!ExileConfig.infectionSounds)return;
        int duration=sound==ExileSounds.INFECTION_WIND?245:sound==ExileSounds.INFECTION_WHISPER?85:65;
        ISound record=new FadingSound(sound,volume,pitch,duration);
        mc.getSoundHandler().playSound(record);playing.add(record);
    }
    private static boolean active(Minecraft mc) {
        return mc.player!=null&&mc.world!=null&&ExileProgression.inMode(mc.player)
                &&!ExileMode.state(mc.player).getBoolean("cured")&&!PocketDimensionManager.isPocketDimension(mc.player.dimension);
    }
    @SubscribeEvent public void water(net.minecraftforge.client.event.RenderBlockOverlayEvent event) {
        if (event.getOverlayType() == net.minecraftforge.client.event.RenderBlockOverlayEvent.OverlayType.WATER
                && ExileConfig.visuals && active(Minecraft.getMinecraft()) && strength > 0.01f)
            event.setCanceled(true);
    }
    @SubscribeEvent public void fogColor(EntityViewRenderEvent.FogColors event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ExileConfig.visuals || !active(mc) || strength <= 0 ||
                !mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)) return;
        float light = event.getRed() * .3f + event.getGreen() * .59f + event.getBlue() * .11f;
        float amount = Math.min(1, strength * 2);
        event.setRed(event.getRed() + (light - event.getRed()) * amount);
        event.setGreen(event.getGreen() + (light - event.getGreen()) * amount);
        event.setBlue(event.getBlue() + (light - event.getBlue()) * amount);
    }
    @SubscribeEvent public void convection(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (ExileConfig.visuals && active(mc) && strength > .01f)
            ExileConvectionClient.render(strength, (mc.player.ticksExisted + event.getPartialTicks()) / 20f);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(lastWorld!=mc.world) {stop(mc);lastWorld=mc.world;ambienceTicks=lastStage=0;lastEpisode=mc.player==null?0:ExileMode.state(mc.player).getInteger("infection_episode");strength=0;}
        if(mc.player==null||mc.world==null)return;
        if(mc.isGamePaused())return;
        playing.removeIf(sound->sound instanceof FadingSound&&((FadingSound)sound).isDonePlaying());
        if(!ExileConfig.infectionSounds)stop(mc);
        NBTTagCompound state=ExileMode.state(mc.player);
        if (mc.currentScreen == null && state.getBoolean("resurrection_blast_waiting")) {
            NetworkHandler.action(NetAction.RESURRECTION_READY, 0, 0, 0, "");
            state.setBoolean("resurrection_blast_waiting", false);
        }
        int percent=state.getInteger("contamination"),stage=state.getInteger("stage");
        boolean eligible=active(mc)&&stage>0;
        strength=ExileAtmosphereEnvelope.step(strength,eligible?percent/100f:0);
        if(!eligible) {
            if(strength<=0)stop(mc);
            ambienceTicks=0;lastEpisode=state.getInteger("infection_episode");
            if(strength<=0)lastStage=0;
            return;
        }
        if(stage>lastStage) {
            sound(mc,ExileSounds.INFECTION_WHISPER,0.12f+strength*0.16f,0.95f);
            if (lastStage == 0) sound(mc,ExileSounds.INFECTION_WIND,.22f,.95f);
        }
        lastStage=stage;
        if(++ambienceTicks >= (int)(310-strength*30)) {
            ambienceTicks=0;sound(mc,ExileSounds.INFECTION_WIND,0.12f+strength*0.20f,0.9f+mc.world.rand.nextFloat()*0.15f);
        }
        int episode=state.getInteger("infection_episode");
        if(episode!=lastEpisode) {lastEpisode=episode;sound(mc,ExileSounds.INFECTION_SURGE,0.18f+strength*0.12f,0.9f);}
        if(!ExileConfig.visuals||mc.player.ticksExisted%10!=0)return;
        int count=1+(int)(strength*3);
        for(int i=0;i<count;i++) {
            double x=mc.player.posX+(mc.world.rand.nextDouble()-0.5)*12;
            double y=mc.player.posY+mc.world.rand.nextDouble()*3;
            double z=mc.player.posZ+(mc.world.rand.nextDouble()-0.5)*12;
            mc.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,x,y,z,0,0.015,0);
        }
    }
}
