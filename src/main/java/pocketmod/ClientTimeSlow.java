package pocketmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Замедление времени на клиенте. Сервер замедляет движение и ИИ мобов, но их анимации крутятся на клиенте
 * по собственным часам (возраст существа, взмах руки, красная вспышка урона, анимация смерти), поэтому у части мобов
 * они шли в обычном темпе. На тиках, которые сервер пропускает, возвращаем эти часы к значениям начала тика.
 */
@SideOnly(Side.CLIENT)
public class ClientTimeSlow {

    private static final double RADIUS = 96.0;

    private double acc;
    private boolean skip;
    private final List<EntityLivingBase> frozen = new ArrayList<EntityLivingBase>();
    private final List<float[]> saved = new ArrayList<float[]>();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        try {
            if (event.phase == TickEvent.Phase.START) {
                begin(Minecraft.getMinecraft());
            } else {
                end();
            }
        } catch (Throwable t) {
            skip = false;
            frozen.clear();
            saved.clear();
        }
    }

    private void begin(Minecraft mc) {
        skip = false;
        frozen.clear();
        saved.clear();
        if (mc.world == null || mc.player == null || mc.isGamePaused() || !Abilities.isSlow(mc.player)) {
            acc = 0.0;
            return;
        }
        float factor = Abilities.getSlowFactor(mc.player);
        if (factor >= 0.99f) {
            acc = 0.0;
            return;
        }
        acc += factor;
        if (acc >= 1.0) {
            acc -= 1.0;
            return; // этот тик мир живёт нормально
        }
        skip = true;
        double r2 = RADIUS * RADIUS;
        for (Entity e : mc.world.loadedEntityList) {
            if (!(e instanceof EntityLivingBase) || e instanceof EntityPlayer) continue;
            if (e.getDistanceSq(mc.player) > r2) continue;
            if (e.getPassengers().contains(mc.player)) continue;
            EntityLivingBase l = (EntityLivingBase) e;
            frozen.add(l);
            saved.add(new float[] { l.ticksExisted, l.swingProgress, l.prevSwingProgress, l.swingProgressInt,
                    l.isSwingInProgress ? 1f : 0f, l.hurtTime, l.deathTime });
        }
    }

    private void end() {
        if (!skip) return;
        for (int i = 0; i < frozen.size(); i++) {
            EntityLivingBase l = frozen.get(i);
            float[] s = saved.get(i);
            l.ticksExisted = (int) s[0];
            l.swingProgress = s[1];
            l.prevSwingProgress = s[2];
            l.swingProgressInt = (int) s[3];
            l.isSwingInProgress = s[4] > 0.5f;
            l.hurtTime = (int) s[5];
            l.deathTime = (int) s[6];
        }
        skip = false;
        frozen.clear();
        saved.clear();
    }
}
