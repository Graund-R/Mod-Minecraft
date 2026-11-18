package pocketmod;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;

/** Тепловизор: серверная часть (переключатель, расход, подсветка сущностей). */
public class Thermal {

    private static final String KEY = "pm_thermal";

    public static boolean isOn(EntityPlayer p) {
        return p.getEntityData().getBoolean(KEY);
    }

    public static int radius(EntityPlayer p) {
        return AbilityStorage.getLevel(p, AbilityType.THERMAL) >= 2
                ? PocketConfig.thermalRadius2
                : PocketConfig.thermalRadius1;
    }

    public static double drainPerSec(EntityPlayer p) {
        double d = AbilityStorage.getLevel(p, AbilityType.THERMAL) >= 2
                ? PocketConfig.thermalDrain2
                : PocketConfig.thermalDrain1;
        return d * Mana.getPriceMultiplier(p);
    }

    public static void toggle(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.THERMAL)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean now = !isOn(p);
        if (now && Mana.get(p) < drainPerSec(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(drainPerSec(p))), true);
            return;
        }
        p.getEntityData().setBoolean(KEY, now);
        if (!now) clearGlow(p);
        p.sendStatusMessage(new TextComponentTranslation(now ? "pocketmod.msg.thermal_on" : "pocketmod.msg.thermal_switched_off"), true);
    }

    /** Мана кончилась (вызывает реестр способностей). */
    public static void onManaExhausted(EntityPlayerMP p) {
        p.getEntityData().setBoolean(KEY, false);
        clearGlow(p);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.thermal_off"), true);
    }

    /** Подсветка существ. Ману списывает реестр способностей до вызова. */
    public static void tick(EntityPlayerMP p) {
        if (!isOn(p)) return;
        // Подсветка не каждый тик: раз в 5 тиков (~4 раза/сек) — сильно дешевле AABB-скан
        if (p.ticksExisted % 5 != 0) return;

        int r = radius(p);
        AxisAlignedBB box = p.getEntityBoundingBox().grow(r, r, r);
        List<EntityLivingBase> list = p.world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        double r2 = (double) r * (double) r;
        for (EntityLivingBase e : list) {
            if (e == p || e.isDead) continue;
            if (e.getDistanceSq(p) > r2) continue;
            if (!e.isGlowing()) e.setGlowing(true);
            e.getEntityData().setBoolean("pm_thermal_glow", true);
        }
        // Снятие glow ещё реже
        if (p.ticksExisted % 20 == 0) {
            List<EntityLivingBase> wide = p.world.getEntitiesWithinAABB(EntityLivingBase.class, box.grow(8.0));
            for (EntityLivingBase e : wide) {
                if (e == p) continue;
                if (e.getEntityData().getBoolean("pm_thermal_glow") && e.getDistanceSq(p) > r2 + 16.0) {
                    e.setGlowing(false);
                    e.getEntityData().setBoolean("pm_thermal_glow", false);
                }
            }
        }
    }

    private static void clearGlow(EntityPlayerMP p) {
        int r = Math.max(radius(p), 64) + 16;
        AxisAlignedBB box = p.getEntityBoundingBox().grow(r, r, r);
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (e.getEntityData().getBoolean("pm_thermal_glow")) {
                e.setGlowing(false);
                e.getEntityData().setBoolean("pm_thermal_glow", false);
            }
        }
    }
}
