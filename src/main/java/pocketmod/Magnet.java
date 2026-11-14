package pocketmod;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Магнит: увеличенный радиус подбора предметов.
 * Уровень 1 — базовый радиус, уровень 2 — больше.
 * Предметы плавно притягиваются к игроку.
 */
public class Magnet {

    public static double radius(EntityPlayerMP p) {
        int lvl = AbilityStorage.getLevel(p, AbilityType.MAGNET);
        if (lvl >= 2) return PocketConfig.magnetRadius2;
        if (lvl >= 1) return PocketConfig.magnetRadius1;
        return 0.0;
    }

    public static void tick(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.MAGNET)) return;
        double r = radius(p);
        if (r <= 0.0) return;

        AxisAlignedBB box = p.getEntityBoundingBox().grow(r, r * 0.6, r);
        List<EntityItem> items = p.world.getEntitiesWithinAABB(EntityItem.class, box);
        for (EntityItem item : items) {
            if (item.isDead || item.cannotPickup()) continue;
            // не трогаем предметы, которые только что выпали у самого игрока (короткий grace)
            if (item.delayBeforeCanPickup > 0) continue;

            double dx = p.posX - item.posX;
            double dy = (p.posY + 0.8) - item.posY;
            double dz = p.posZ - item.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.4) {
                // очень близко — пусть ванильный подбор сработает
                continue;
            }
            double speed = 0.25 + (r - dist) * 0.05;
            if (speed > 0.6) speed = 0.6;
            item.motionX += dx / dist * speed * 0.35;
            item.motionY += dy / dist * speed * 0.25;
            item.motionZ += dz / dist * speed * 0.35;
            item.velocityChanged = true;
        }
    }
}
