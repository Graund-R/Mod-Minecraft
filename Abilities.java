package pocketmod;

import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class Abilities {

    private static final String KEY_INVIS = "pocketmod_invis";
    private static final String KEY_SLOW = "pocketmod_slow";
    private static final String KEY_SLOW_FACTOR = "pocketmod_slow_factor";

    /** Расход невидимости, маны/сек (до множителя цен). */
    private static final double INVIS_COST_PER_SEC = 2.0;

    // ===== Невидимость =====
    public static boolean isInvisible(EntityPlayer player) {
        return player.getEntityData().getBoolean(KEY_INVIS);
    }

    public static void setInvisible(EntityPlayer player, boolean v) {
        player.getEntityData().setBoolean(KEY_INVIS, v);
        if (v) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
        } else {
            player.removePotionEffect(MobEffects.INVISIBILITY);
        }
    }

    public static void toggleInvisibility(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.INVISIBILITY)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean now = !isInvisible(player);
        if (now) {
            double cost = INVIS_COST_PER_SEC * Mana.getPriceMultiplier(player);
            if (Mana.get(player) < cost) {
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) cost), true);
                return;
            }
        }
        setInvisible(player, now);
        player.sendStatusMessage(new TextComponentTranslation(
                now ? "pocketmod.msg.invis_on" : "pocketmod.msg.invis_off"), true);
    }

    public static void tickInvisibility(EntityPlayerMP player) {
        if (!isInvisible(player)) return;
        double cost = INVIS_COST_PER_SEC * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, cost / 20.0)) {
            setInvisible(player, false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_off"), true);
            return;
        }
        if (!player.isPotionActive(MobEffects.INVISIBILITY)) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
        }
    }

    // ===== Замедление =====
    public static boolean isSlow(EntityPlayer player) {
        return player.getEntityData().getBoolean(KEY_SLOW);
    }

    public static float getSlowFactor(EntityPlayer player) {
        float f = player.getEntityData().getFloat(KEY_SLOW_FACTOR);
        if (f < 0.05f || f > 1.0f) f = 0.25f;
        return f;
    }

    public static void setSlowFactor(EntityPlayer player, float f) {
        f = MathHelper.clamp(f, 0.1f, 0.75f);
        player.getEntityData().setFloat(KEY_SLOW_FACTOR, f);
    }

    public static void toggleSlow(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.SLOW)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean now = !isSlow(player);
        if (now) {
            double cost = 3.0 * Mana.getPriceMultiplier(player);
            if (Mana.get(player) < cost) {
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) cost), true);
                return;
            }
            if (!player.getEntityData().hasKey(KEY_SLOW_FACTOR)) {
                setSlowFactor(player, 0.25f);
            }
        }
        player.getEntityData().setBoolean(KEY_SLOW, now);
        player.sendStatusMessage(new TextComponentTranslation(
                now ? "pocketmod.msg.slow_on" : "pocketmod.msg.slow_off"), true);
    }

    public static void tickSlow(EntityPlayerMP player) {
        TimeSlow.tickMana(player);
    }

    /** Суммарный расход маны в секунду (для шапки магазина). */
    public static double totalDrainPerSec(EntityPlayer player) {
        double d = 0.0;
        if (isInvisible(player)) {
            d += INVIS_COST_PER_SEC * Mana.getPriceMultiplier(player);
        }
        if (isSlow(player)) {
            d += TimeSlow.drainPerSec(player);
        }
        return d;
    }

    // ===== Телепорт =====
    public static double teleportRange(EntityPlayer player) {
        int lvl = AbilityStorage.getLevel(player, AbilityType.TELEPORT);
        if (lvl >= 3) return PocketConfig.teleportRange3;
        if (lvl == 2) return PocketConfig.teleportRange2;
        return PocketConfig.teleportBaseRange;
    }

    public static void teleportLook(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.TELEPORT)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        World world = player.world;
        double range = teleportRange(player);

        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLook(1.0f);
        Vec3d end = eye.addVector(look.x * range, look.y * range, look.z * range);

        RayTraceResult hit = world.rayTraceBlocks(eye, end, false, true, false);
        BlockPos land = null;

        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            // Луч попал в блок (в бок, снизу или сверху): сначала пробуем встать НА его верх,
            // и только если там небезопасно — остановиться перед стеной
            land = findTopLanding(world, hit.getBlockPos());
            if (land == null) {
                Vec3d before = hit.hitVec.subtract(look.scale(0.3));
                land = findSafePos(world, new BlockPos(before));
            }
        } else {
            land = findSafePos(world, new BlockPos(end));
        }

        if (land == null) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.teleport_unsafe"), true);
            return;
        }

        double dist = player.getDistance(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
        double cost = dist * PocketConfig.teleportCostPerBlock * Mana.getPriceMultiplier(player);
        cost = Math.max(1.0, cost);

        if (!Mana.spend(player, cost)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }

        Vec3d from = new Vec3d(player.posX, player.posY + 1.0, player.posZ);
        Vec3d to = new Vec3d(land.getX() + 0.5, land.getY() + 1.0, land.getZ() + 0.5);

        clearMobTargets(player, 16.0);
        player.dismountRidingEntity();
        player.fallDistance = 0f;

        player.connection.setPlayerLocation(land.getX() + 0.5, land.getY(), land.getZ() + 0.5,
                player.rotationYaw, player.rotationPitch);

        teleportFx((WorldServer) world, from, to);
    }

    /** След частиц по пути и звук. */
    private static void teleportFx(WorldServer ws, Vec3d from, Vec3d to) {
        double len = from.distanceTo(to);
        int steps = (int) Math.min(40.0, Math.max(2.0, len / 1.5));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            ws.spawnParticle(EnumParticleTypes.PORTAL,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    3, 0.2, 0.2, 0.2, 0.05);
        }
        ws.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 1.2F);
    }

    private static BlockPos findTopLanding(World world, BlockPos hitBlock) {
        BlockPos top = hitBlock;
        for (int i = 0; i < 16; i++) {
            BlockPos above = top.up();
            if (world.isAirBlock(above) || !world.getBlockState(above).getMaterial().isSolid()) {
                break;
            }
            top = above;
        }
        BlockPos feet = top.up();
        if (isSafe(world, feet)) {
            return feet;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos p = feet.add(dx, 0, dz);
                if (isSafe(world, p)) return p;
            }
        }
        return null;
    }

    private static BlockPos findSafePos(World world, BlockPos near) {
        for (int dy = 0; dy >= -3; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = near.add(dx, dy, dz);
                    if (isSafe(world, p)) return p;
                }
            }
        }
        for (int dy = 1; dy <= 5; dy++) {
            BlockPos p = near.up(dy);
            if (isSafe(world, p)) return p;
        }
        return null;
    }

    static boolean isSafe(World world, BlockPos feet) {
        if (feet.getY() < 1 || feet.getY() > 254) return false;
        if (world.provider.getDimension() == -1 && feet.getY() >= 127) return false;

        BlockPos head = feet.up();
        BlockPos ground = feet.down();

        if (!world.isAirBlock(feet) || !world.isAirBlock(head)) return false;
        if (world.isAirBlock(ground) && !world.getBlockState(ground).getMaterial().isSolid()) {
            if (world.isAirBlock(ground.down()) && world.isAirBlock(ground.down(2))) return false;
        }

        String name = world.getBlockState(feet).getBlock().getRegistryName().toString();
        String name2 = world.getBlockState(head).getBlock().getRegistryName().toString();
        String nameG = world.getBlockState(ground).getBlock().getRegistryName().toString();
        if (name.contains("lava") || name.contains("fire") || name.contains("cactus")) return false;
        if (name2.contains("lava") || name2.contains("fire")) return false;
        if (nameG.contains("lava") || nameG.contains("fire") || nameG.contains("cactus")) return false;

        return true;
    }

    /** Мобы в радиусе теряют цель на игрока. */
    public static void clearMobTargets(EntityPlayer player, double radius) {
        if (radius <= 0) return;
        AxisAlignedBB box = player.getEntityBoundingBox().grow(radius);
        List<EntityLiving> list = player.world.getEntitiesWithinAABB(EntityLiving.class, box);
        for (EntityLiving e : list) {
            if (e.getAttackTarget() == player) {
                e.setAttackTarget(null);
            }
        }
    }
}
