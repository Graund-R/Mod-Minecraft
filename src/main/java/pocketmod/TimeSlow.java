package pocketmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Замедление мира (игрок не замедляется): пропуск тиков.
 * При факторе f мир «живёт» f тиков из каждого одного (накопитель).
 *  - живые существа (кроме игроков): на пропущенных тиках отменяем LivingUpdateEvent;
 *  - остальные сущности (стрелы, предметы, TNT, блоки...): на пропущенных тиках откатываем
 *    позицию/скорость к значениям на начало тика.
 * Не замедляются: рост растений, печки, редстоун, погода, горение/яд существ.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class TimeSlow {

    private static final double RADIUS = 96.0;

    private static class State {
        boolean skip;
        double acc;
        double sx, sy, sz;
        final List<Entity> frozen = new ArrayList<Entity>();
        final List<double[]> saved = new ArrayList<double[]>();
    }

    private static final Map<Integer, State> states = new HashMap<Integer, State>();

    private static State state(WorldServer world) {
        Integer dim = Integer.valueOf(world.provider.getDimension());
        State st = states.get(dim);
        if (st == null) {
            st = new State();
            states.put(dim, st);
        }
        return st;
    }

    private static boolean isPlayerMount(Entity e) {
        for (Entity p : e.getPassengers()) {
            if (p instanceof EntityPlayer) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote) return;
        if (!(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        State st = state(world);

        if (event.phase == TickEvent.Phase.START) {
            st.skip = false;
            st.frozen.clear();
            st.saved.clear();

            float strongest = 1.0f;
            EntityPlayer source = null;
            for (EntityPlayer p : world.playerEntities) {
                if (Abilities.isSlow(p)) {
                    float f = Abilities.getSlowFactor(p);
                    if (f < strongest) {
                        strongest = f;
                        source = p;
                    }
                }
            }
            if (source == null || strongest >= 0.99f) {
                st.acc = 0.0;
                return;
            }

            st.acc += strongest;
            if (st.acc >= 1.0) {
                st.acc -= 1.0;
                return; // этот тик мир живёт нормально
            }

            st.skip = true;
            st.sx = source.posX;
            st.sy = source.posY;
            st.sz = source.posZ;

            AxisAlignedBB box = source.getEntityBoundingBox().grow(RADIUS);
            List<Entity> list = world.getEntitiesWithinAABB(Entity.class, box);
            for (Entity e : list) {
                if (e instanceof EntityPlayer || e instanceof EntityLivingBase) continue;
                if (isPlayerMount(e)) continue;
                double fuse = e instanceof EntityTNTPrimed ? ((EntityTNTPrimed) e).getFuse() : 0.0;
                st.frozen.add(e);
                st.saved.add(new double[] { e.posX, e.posY, e.posZ, e.motionX, e.motionY, e.motionZ,
                        e.rotationYaw, e.rotationPitch, fuse });
            }
        } else if (event.phase == TickEvent.Phase.END) {
            if (!st.skip) return;
            for (int i = 0; i < st.frozen.size(); i++) {
                Entity e = st.frozen.get(i);
                if (e.isDead) continue;
                double[] s = st.saved.get(i);
                e.setPosition(s[0], s[1], s[2]);
                e.prevPosX = s[0];
                e.prevPosY = s[1];
                e.prevPosZ = s[2];
                e.lastTickPosX = s[0];
                e.lastTickPosY = s[1];
                e.lastTickPosZ = s[2];
                e.motionX = s[3];
                e.motionY = s[4];
                e.motionZ = s[5];
                e.rotationYaw = (float) s[6];
                e.rotationPitch = (float) s[7];
                if (e instanceof EntityTNTPrimed) {
                    ((EntityTNTPrimed) e).setFuse((int) s[8]);
                }
            }
            st.skip = false;
            st.frozen.clear();
            st.saved.clear();
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e instanceof EntityPlayer) return;
        State st = states.get(Integer.valueOf(e.world.provider.getDimension()));
        if (st == null || !st.skip) return;

        double dx = e.posX - st.sx;
        double dy = e.posY - st.sy;
        double dz = e.posZ - st.sz;
        if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) return;
        if (isPlayerMount(e)) return;

        event.setCanceled(true);
    }

    public static double drainPerSec(EntityPlayer player) {
        float factor = Abilities.getSlowFactor(player);
        return (1.0 / Math.max(0.1, factor)) * 2.0 * Mana.getPriceMultiplier(player);
    }

    public static void tickMana(EntityPlayerMP player) {
        if (!Abilities.isSlow(player)) return;
        if (!Mana.spend(player, drainPerSec(player) / 20.0)) {
            player.getEntityData().setBoolean("pocketmod_slow", false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.slow_off"), true);
        }
    }
}
