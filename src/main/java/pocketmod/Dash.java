package pocketmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Рывок: двойное нажатие W/A/S/D. Короткое быстрое перемещение (около 8 блоков за 0,25 с) вдоль выбранного направления.
 *
 * Движение выполняет клиент (в Minecraft игрок двигается клиентом), сервер проверяет условия, списывает ману и заряд,
 * а затем на время рывка: даёт неуязвимость, отбрасывает и бьёт врагов на пути, отражает снаряды в коротком окне.
 * Скорость рывка не передаётся серверу как скорость игрока, поэтому стрелы игрока вылетают строго по взгляду.
 *
 * Доступен игроку в своём теле (на земле и в полёте). Недоступен в облике духа (Спектор), в режиме наблюдателя и во
 * время телепорта (после любого телепорта рывок блокируется на полсекунды, а идущий рывок прерывается).
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class Dash {

    public static final String ID = "pm_dash_id";
    public static final String DX = "pm_dash_dx";
    public static final String DZ = "pm_dash_dz";
    private static final String CHARGES = "pm_dash_c";
    private static final String STAMP = "pm_dash_t";
    private static final String LOCK = "pm_tp_lock";
    private static final String GRACE_UNTIL = "pm_dash_grace";

    private static final class State {
        int age;
        boolean effectsEnded;
        final Set<Integer> hit = new HashSet<Integer>();
        final Set<Integer> reflected = new HashSet<Integer>();
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<UUID, State>();

    private Dash() {}

    /** Любой телепорт (прыжок, тотем, вход и выход из кармана): прерывает рывок и блокирует новый на полсекунды. */
    public static void onTeleport(EntityPlayer p) {
        p.getEntityData().setLong(LOCK, p.world.getTotalWorldTime() + 10L);
        State state=ACTIVE.remove(p.getUniqueID());
        if(state!=null)DashEffects.cancel(p,p.getEntityData().getInteger(ID));
    }

    public static boolean isDashing(EntityPlayer p) {
        return ACTIVE.containsKey(p.getUniqueID());
    }

    /** Можно ли начать рывок сейчас. */
    public static boolean allowed(EntityPlayer p) {
        return AbilityStorage.isUnlocked(p, AbilityType.DASH)
                && p.isEntityAlive()
                && !p.isSpectator()
                && !p.isRiding()
                && !p.isPlayerSleeping()
                && !p.isElytraFlying()
                && !Spector.isActive(p)
                && p.world.getTotalWorldTime() >= p.getEntityData().getLong(LOCK)
                && !isDashing(p);
    }

    // ----- параметры по уровню (1..4); читаются и сервером, и клиентом -----
    public static int level(EntityPlayer p) {
        return Math.max(1, Math.min(AbilityType.DASH.maxLevel, AbilityStorage.getLevel(p, AbilityType.DASH)));
    }

    private static double pick(double[] values, int level) {
        return values[Math.min(values.length, Math.max(1, level)) - 1];
    }

    public static double speed(EntityPlayer p) {
        return speed(level(p));
    }

    static double speed(int level) {
        return Math.max(0.2, pick(PocketConfig.dashSpeed, level));
    }

    public static int ticks(EntityPlayer p) {
        return ticks(level(p));
    }

    static int ticks(int level) {
        return Math.max(1, (int) Math.round(pick(PocketConfig.dashTicks, level)));
    }

    public static double distance(EntityPlayer p) {
        return distance(level(p));
    }

    static double distance(int level) {
        return speed(level) * ticks(level);
    }

    public static double cost(EntityPlayer p) {
        return cost(level(p));
    }

    static double cost(int level) {
        return pick(PocketConfig.dashCost, level);
    }

    public static int chargesMax(EntityPlayer p) {
        return chargesMax(level(p));
    }

    static int chargesMax(int level) {
        return Math.max(1, (int) Math.round(pick(PocketConfig.dashCharges, level)));
    }

    public static double rechargeSec(EntityPlayer p) {
        return pick(PocketConfig.dashRechargeSec, level(p));
    }

    public static double damage(EntityPlayer p) {
        return damage(level(p));
    }

    static double damage(int level) {
        return pick(PocketConfig.dashDamage, level);
    }

    private static int maxCharges(EntityPlayer p) {
        return chargesMax(p);
    }

    private static int rechargeTicks(EntityPlayer p) {
        return rechargeTicks(level(p));
    }

    static int rechargeTicks(int level) {
        return Math.max(1, (int) Math.round(pick(PocketConfig.dashRechargeSec, level) * 20.0));
    }

    static int reflectionTicks(int level) {
        int configured = (int) Math.round(pick(PocketConfig.dashReflectTicks, level));
        return Math.max(0, Math.min(configured, ticks(level) + 3));
    }

    static int graceTicks(int level) {
        return Math.max(0, (int) Math.round(pick(PocketConfig.dashGraceTicks, level)));
    }

    static boolean refundsCharge(int level) {
        return damage(level) > 0.0 && pick(PocketConfig.dashRefund, level) >= 1.0;
    }

    /** Текущее число зарядов с учётом восстановления. */
    private static int charges(EntityPlayer p, long now) {
        NBTTagCompound e = p.getEntityData();
        int max = maxCharges(p);
        if (!e.hasKey(CHARGES)) {
            e.setInteger(CHARGES, max);
            e.setLong(STAMP, now);
        }
        int c = Math.min(max, e.getInteger(CHARGES));
        if (c >= max) return max;
        long stamp = e.getLong(STAMP);
        if (now < stamp) stamp = now;
        long gained = (now - stamp) / rechargeTicks(p);
        if (gained > 0) {
            c = (int) Math.min((long) max, c + gained);
            stamp += gained * rechargeTicks(p);
            e.setInteger(CHARGES, c);
            e.setLong(STAMP, c >= max ? now : stamp);
        }
        return c;
    }

    private static int secondsToNextCharge(EntityPlayer p, long now) {
        long stamp = p.getEntityData().getLong(STAMP);
        long left = rechargeTicks(p) - Math.max(0L, now - stamp);
        return (int) Math.max(1L, (left + 19L) / 20L);
    }

    /** Запрос клиента: dir 0 вперёд, 1 назад, 2 влево, 3 вправо. */
    public static void request(EntityPlayerMP p, int dir) {
        if (dir < 0 || dir > 3) return;
        if (!AbilityStorage.isUnlocked(p, AbilityType.DASH)) return;
        if (!allowed(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.dash_blocked"), true);
            return;
        }
        long now = p.world.getTotalWorldTime();
        int c = charges(p, now);
        if (c <= 0) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.dash_cooldown", secondsToNextCharge(p, now)), true);
            return;
        }
        double cost = cost(p) * Mana.getPriceMultiplier(p);
        if (!Mana.spendAbility(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        NBTTagCompound e = p.getEntityData();
        if (c >= maxCharges(p)) e.setLong(STAMP, now);
        e.setInteger(CHARGES, c - 1);

        double yaw = Math.toRadians(p.rotationYaw);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double dx;
        double dz;
        switch (dir) {
            case 0: dx = fx; dz = fz; break;
            case 1: dx = -fx; dz = -fz; break;
            case 2: dx = lx; dz = lz; break;
            default: dx = -lx; dz = -lz; break;
        }
        e.setFloat(DX, (float) dx);
        e.setFloat(DZ, (float) dz);
        e.setInteger(ID, e.getInteger(ID) + 1);
        p.fallDistance = 0f;
        ACTIVE.put(p.getUniqueID(), new State());
        DashEffects.start(p,e.getInteger(ID),ticks(p));
    }

    private static boolean isEnemy(EntityPlayerMP p, EntityLivingBase t) {
        if (t instanceof EntityPlayer) return false;
        if (t instanceof IMob) return true;
        return t instanceof EntityLiving && ((EntityLiving) t).getAttackTarget() == p;
    }

    private static boolean ownedBy(Entity pr, EntityPlayer p) {
        if (pr instanceof EntityArrow) return ((EntityArrow) pr).shootingEntity == p;
        if (pr instanceof EntityThrowable) return ((EntityThrowable) pr).getThrower() == p;
        if (pr instanceof EntityFireball) return ((EntityFireball) pr).shootingEntity == p;
        return false;
    }

    /** Каждый тик (из реестра способностей): сбивание врагов, отражение снарядов, окончание рывка. */
    public static void tick(EntityPlayerMP p) {
        State st = ACTIVE.get(p.getUniqueID());
        if (st == null) return;
        if (!p.isEntityAlive() || Spector.isActive(p) || p.isSpectator()) {
            if(!st.effectsEnded)DashEffects.end(p,p.getEntityData().getInteger(ID));
            ACTIVE.remove(p.getUniqueID());
            return;
        }
        st.age++;
        NBTTagCompound e = p.getEntityData();
        int dashTicks = ticks(p);
        double dmg = damage(p);
        if(st.age>=dashTicks+1&&!st.effectsEnded){st.effectsEnded=true;DashEffects.end(p,e.getInteger(ID));}

        if (dmg > 0.0 && st.age <= dashTicks + 1) {
            double dx = e.getFloat(DX);
            double dz = e.getFloat(DZ);
            AxisAlignedBB box = p.getEntityBoundingBox().grow(0.8, 0.3, 0.8);
            for (EntityLivingBase t : p.world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
                if (t == p || st.hit.contains(Integer.valueOf(t.getEntityId())) || !isEnemy(p, t)) continue;
                st.hit.add(Integer.valueOf(t.getEntityId()));
                t.attackEntityFrom(DamageSource.causePlayerDamage(p), (float) dmg);
                t.knockBack(p, 0.9F, -dx, -dz);
                // высший уровень: каждый сбитый враг возвращает заряд
                if (refundsCharge(level(p))) {
                    int max = maxCharges(p);
                    long now = p.world.getTotalWorldTime();
                    e.setInteger(CHARGES, Math.min(max, charges(p, now) + 1));
                }
            }
        }

        int reflect = reflectionTicks(level(p));
        if (reflect > 0 && st.age <= reflect) {
            AxisAlignedBB rb = p.getEntityBoundingBox().grow(2.5);
            for (Entity pr : p.world.getEntitiesWithinAABB(Entity.class, rb)) {
                if (!(pr instanceof IProjectile) || ownedBy(pr, p)) continue;
                if (!st.reflected.add(Integer.valueOf(pr.getEntityId()))) continue;
                pr.motionX = -pr.motionX;
                pr.motionY = -pr.motionY;
                pr.motionZ = -pr.motionZ;
                pr.rotationYaw += 180.0F;
                pr.velocityChanged = true;
                if (pr instanceof EntityArrow) {
                    ((EntityArrow) pr).shootingEntity = p;
                } else if (pr instanceof EntityFireball) {
                    EntityFireball fb = (EntityFireball) pr;
                    fb.shootingEntity = p;
                    fb.accelerationX = -fb.accelerationX;
                    fb.accelerationY = -fb.accelerationY;
                    fb.accelerationZ = -fb.accelerationZ;
                }
            }
        }

        if (st.age > dashTicks + 2) {
            ACTIVE.remove(p.getUniqueID());
            // короткая неуязвимость после рывка (с 3 уровня), чтобы выйти из схватки
            int grace = graceTicks(level(p));
            if (grace > 0) e.setLong(GRACE_UNTIL, p.world.getTotalWorldTime() + grace);
        }
    }

    /** Неуязвимость на время рывка (кроме падения в бездну). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        if (event.getSource() == DamageSource.OUT_OF_WORLD) return;
        EntityPlayer pl = (EntityPlayer) event.getEntityLiving();
        if (isDashing(pl) || pl.world.getTotalWorldTime() < pl.getEntityData().getLong(GRACE_UNTIL)) event.setCanceled(true);
    }
}
