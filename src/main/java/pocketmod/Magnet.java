package pocketmod;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Магнит: увеличенный радиус подбора предметов.
 * Скан предметов — раз в 2 тика, чтобы не грузить мир.
 */
public class Magnet {

    public static double radius(EntityPlayerMP p) {
        return radiusForLevel(AbilityStorage.getLevel(p, AbilityType.MAGNET));
    }

    static double radiusForLevel(int lvl) {
        if (lvl >= 2) return PocketConfig.magnetRadius2;
        if (lvl >= 1) return PocketConfig.magnetRadius1;
        return 0.0;
    }

    public static void tick(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.MAGNET)) return;
        // раз в 2 тика достаточно для плавного притяжения
        if ((p.ticksExisted & 1) != 0) return;
        double r = radius(p);
        if (r <= 0.0) return;

        AxisAlignedBB box = p.getEntityBoundingBox().grow(r, r * 0.6, r);
        List<EntityItem> items = p.world.getEntitiesWithinAABB(EntityItem.class, box);
        if (items.isEmpty()) return;
        double r2 = r * r;
        for (EntityItem item : items) {
            if (item.isDead || item.cannotPickup()) continue;
            double dx = p.posX - item.posX;
            double dy = (p.posY + 0.8) - item.posY;
            double dz = p.posZ - item.posZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > r2 || distSq < 0.16) continue;
            double dist = Math.sqrt(distSq);
            double speed = 0.25 + (r - dist) * 0.05;
            if (speed > 0.6) speed = 0.6;
            double inv = (speed * 0.35) / dist;
            item.motionX += dx * inv;
            item.motionY += dy * (speed * 0.25) / dist;
            item.motionZ += dz * inv;
            item.velocityChanged = true;
        }
    }
}
