package pocketmod;

import net.minecraft.client.audio.ISound;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * При активном замедлении новые звуки играют с пониженным pitch.
 * Уже играющие не меняются — только новые.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = PocketMod.MODID, value = Side.CLIENT)
public class SlowSound {

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        // флаг замедления хранится на серверном игроке, у клиентского его нет
        net.minecraft.entity.player.EntityPlayerMP sp = ClientHandler.sp();
        if (sp == null || !Abilities.isSlow(sp)) return;

        ISound original = event.getSound();
        if (original == null) return;
        // зацикленные/тикающие звуки (вагонетка, элитры) не трогаем — обёртка их сломала бы
        if (original instanceof net.minecraft.client.audio.ITickableSound) return;

        SoundCategory cat = original.getCategory();
        if (cat == SoundCategory.MUSIC) return;

        float factor = Abilities.getSlowFactor(sp);
        float pitchMul = Math.max(0.3f, 0.25f + factor * 0.9f);
        final float newPitch = original.getPitch() * pitchMul;

        ISound wrapped = new ISound() {
            @Override
            public net.minecraft.util.ResourceLocation getSoundLocation() {
                return original.getSoundLocation();
            }
            @Override
            public net.minecraft.client.audio.SoundEventAccessor createAccessor(net.minecraft.client.audio.SoundHandler handler) {
                return original.createAccessor(handler);
            }
            @Override
            public net.minecraft.client.audio.Sound getSound() {
                return original.getSound();
            }
            @Override
            public SoundCategory getCategory() {
                return original.getCategory();
            }
            @Override
            public boolean canRepeat() {
                return original.canRepeat();
            }
            @Override
            public int getRepeatDelay() {
                return original.getRepeatDelay();
            }
            @Override
            public float getVolume() {
                return original.getVolume();
            }
            @Override
            public float getPitch() {
                return newPitch;
            }
            @Override
            public float getXPosF() {
                return original.getXPosF();
            }
            @Override
            public float getYPosF() {
                return original.getYPosF();
            }
            @Override
            public float getZPosF() {
                return original.getZPosF();
            }
            @Override
            public AttenuationType getAttenuationType() {
                return original.getAttenuationType();
            }
        };
        event.setResultSound(wrapped);
    }
}
