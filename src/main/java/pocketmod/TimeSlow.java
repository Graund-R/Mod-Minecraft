package pocketmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Замедление мира (игрок не замедляется): пропуск тиков внутри «пузыря» вокруг каждого игрока с включённой способностью.
 * При факторе f пузырь «живёт» f тиков из каждого одного (накопитель у каждого пузыря свой).
 *  - живые существа (кроме игроков): на пропущенных тиках отменяем LivingUpdateEvent;
 *  - остальные сущности (стрелы, предметы, TNT, блоки...): на пропущенных тиках откатываем
 *    позицию/скорость к значениям на начало тика;
 *  - текущая вода и лава: отложенные обновления жидкостей откладываются.
 * Если пузыри пересекаются, сущностью управляет самый сильный (с меньшим фактором). Игроки, включая чужих, никогда не
 * замедляются. Число одновременно обрабатываемых пузырей ограничено настройкой (нагрузка на сервер).
 * Не замедляются: рост растений, печки, редстоун, погода, горение/яд существ.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class TimeSlow {

    /** Пузырь замедления вокруг одного игрока. */
    private static class Bubble {
        boolean skip;
        double acc;
        double sx, sy, sz;
        float factor;
        final List<Entity> frozen = new ArrayList<Entity>();
        final List<double[]> saved = new ArrayList<double[]>();

        boolean contains(double x, double y, double z) {
            double r = PocketConfig.slowBubbleRadius;
            double dx = x - sx;
            double dy = y - sy;
            double dz = z - sz;
            return dx * dx + dy * dy + dz * dz <= r * r;
        }
    }

    private static class WorldState {
        /** Накопители по игрокам (сохраняются между тиками). */
        final Map<UUID, Bubble> byPlayer = new HashMap<UUID, Bubble>();
        /** Пузыри, активные в этом тике, самые сильные первыми. */
        final List<Bubble> active = new ArrayList<Bubble>();
    }

    private static final Map<Integer, WorldState> states = new HashMap<Integer, WorldState>();

    private static WorldState state(WorldServer world) {
        Integer dim = Integer.valueOf(world.provider.getDimension());
        WorldState st = states.get(dim);
        if (st == null) {
            st = new WorldState();
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

    /** Самый сильный активный пузырь, в который попадает точка (или null). */
    private static Bubble controlling(WorldState st, double x, double y, double z) {
        for (Bubble b : st.active) {
            if (b.contains(x, y, z)) return b;
        }
        return null;
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        try {
            worldTick(event);
        } catch (Throwable t) {
            System.err.println("[PocketMod] TimeSlow world tick error:");
            t.printStackTrace();
            states.clear();
        }
    }

    private static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote) return;
        if (!(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        WorldState st = state(world);

        if (event.phase == TickEvent.Phase.START) {
            st.active.clear();

            // источники: игроки с включённым замедлением, самые сильные первыми, не больше лимита
            List<EntityPlayer> casters = new ArrayList<EntityPlayer>();
            for (EntityPlayer p : world.playerEntities) {
                if (Abilities.isSlow(p) && Abilities.getSlowFactor(p) < 0.99f) casters.add(p);
            }
            Collections.sort(casters, new Comparator<EntityPlayer>() {
                @Override
                public int compare(EntityPlayer a, EntityPlayer b) {
                    return Float.compare(Abilities.getSlowFactor(a), Abilities.getSlowFactor(b));
                }
            });
            int limit = Math.max(1, PocketConfig.slowMaxBubbles);
            java.util.Set<UUID> keep = new java.util.HashSet<UUID>();
            for (int i = 0; i < casters.size() && i < limit; i++) {
                EntityPlayer p = casters.get(i);
                Bubble b = st.byPlayer.get(p.getUniqueID());
                if (b == null) {
                    b = new Bubble();
                    st.byPlayer.put(p.getUniqueID(), b);
                }
                keep.add(p.getUniqueID());
                b.factor = Abilities.getSlowFactor(p);
                b.sx = p.posX;
                b.sy = p.posY;
                b.sz = p.posZ;
                b.frozen.clear();
                b.saved.clear();
                b.acc += b.factor;
                if (b.acc >= 1.0) {
                    b.acc -= 1.0;
                    b.skip = false; // в этом тике пузырь живёт нормально
                } else {
                    b.skip = true;
                }
                st.active.add(b);
            }
            st.byPlayer.keySet().retainAll(keep);
            if (st.active.isEmpty()) return;

            // вода и лава в пузырях, которые пропускают тик
            if (PocketConfig.slowFluids) {
                for (Bubble b : st.active) {
                    if (!b.skip) continue;
                    try {
                        slowFluids(world, b.sx, b.sz);
                    } catch (Throwable t) {
                        System.err.println("[PocketMod] TimeSlow fluid error: " + t);
                    }
                }
            }

            // остальные сущности: каждую замораживает самый сильный пузырь, если он пропускает этот тик
            java.util.Set<Integer> done = new java.util.HashSet<Integer>();
            for (Bubble b : st.active) {
                AxisAlignedBB box = new AxisAlignedBB(b.sx, b.sy, b.sz, b.sx, b.sy, b.sz).grow(PocketConfig.slowBubbleRadius);
                List<Entity> list = world.getEntitiesWithinAABB(Entity.class, box);
                for (Entity e : list) {
                    if (e instanceof EntityPlayer || e instanceof EntityLivingBase || e instanceof EntityHanging) continue;
                    if (!done.add(Integer.valueOf(e.getEntityId()))) continue;
                    if (isPlayerMount(e)) continue;
                    Bubble owner = controlling(st, e.posX, e.posY, e.posZ);
                    if (owner == null || !owner.skip) continue;
                    double fuse = e instanceof EntityTNTPrimed ? ((EntityTNTPrimed) e).getFuse() : 0.0;
                    owner.frozen.add(e);
                    owner.saved.add(new double[] { e.posX, e.posY, e.posZ, e.motionX, e.motionY, e.motionZ,
                            e.rotationYaw, e.rotationPitch, fuse });
                }
            }
        } else if (event.phase == TickEvent.Phase.END) {
            for (Bubble b : st.active) {
                if (!b.skip) continue;
                for (int i = 0; i < b.frozen.size(); i++) {
                    Entity e = b.frozen.get(i);
                    if (e.isDead) continue;
                    double[] s = b.saved.get(i);
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
                b.skip = false;
                b.frozen.clear();
                b.saved.clear();
            }
            st.active.clear();
        }
    }

    /**
     * Течение воды и лавы идёт через отложенные обновления блоков. На пропущенных тиках откладываем обновления
     * жидкостей рядом с игроком ещё на тик; остальные отложенные обновления возвращаем с прежним временем.
     */
    private static void slowFluids(WorldServer world, double sx, double sz) {
        int r = PocketConfig.slowFluidRadius;
        StructureBoundingBox box = new StructureBoundingBox((int) Math.floor(sx) - r, 0, (int) Math.floor(sz) - r,
                (int) Math.floor(sx) + r, 255, (int) Math.floor(sz) + r);
        List<NextTickListEntry> peek = world.getPendingBlockUpdates(box, false);
        if (peek == null || peek.isEmpty()) return;
        boolean any = false;
        for (NextTickListEntry e : peek) {
            if (isFluid(e.getBlock())) {
                any = true;
                break;
            }
        }
        if (!any) return; // жидкостей рядом нет: ничего не трогаем
        List<NextTickListEntry> list = world.getPendingBlockUpdates(box, true);
        if (list == null) return;
        long now = world.getTotalWorldTime();
        for (NextTickListEntry e : list) {
            Block b = e.getBlock();
            long delay = e.scheduledTime - now;
            if (isFluid(b)) delay += 1;
            if (delay < 0) delay = 0;
            world.scheduleBlockUpdate(e.position, b, (int) delay, e.priority);
        }
    }

    private static boolean isFluid(Block b) {
        return b instanceof BlockLiquid || b instanceof BlockFluidBase;
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        try {
            livingUpdate(event);
        } catch (Throwable t) {
            System.err.println("[PocketMod] TimeSlow living update error:");
            t.printStackTrace();
        }
    }

    private static void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e instanceof EntityPlayer) return;
        WorldState st = states.get(Integer.valueOf(e.world.provider.getDimension()));
        if (st == null || st.active.isEmpty()) return;
        Bubble owner = controlling(st, e.posX, e.posY, e.posZ);
        if (owner == null || !owner.skip) return;
        if (isPlayerMount(e)) return;
        event.setCanceled(true);
    }

    public static double drainPerSec(EntityPlayer player) {
        float factor = Abilities.getSlowFactor(player);
        if (factor <= 0.001f) return PocketConfig.slowZeroDrain * Mana.getPriceMultiplier(player);
        return (1.0 / Math.max(0.1, factor)) * PocketConfig.slowDrainBase * Mana.getPriceMultiplier(player);
    }

    public static void tickMana(EntityPlayerMP player) {
        if (!Abilities.isSlow(player)) return;
        if (!Mana.spendAbility(player, drainPerSec(player) / 20.0)) {
            player.getEntityData().setBoolean("pocketmod_slow", false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.slow_off"), true);
        }
    }
}
