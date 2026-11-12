package pocketmod;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class Abilities {

    private static final String KEY_INVIS = "pocketmod_invis";
    private static final String KEY_SLOW = "pocketmod_slow";
    private static final String KEY_SLOW_FACTOR = "pocketmod_slow_factor";

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
        boolean now = !isInvisible(player);
        if (now) {
            double cost = 2.0 * Mana.getPriceMultiplier(player);
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
        double cost = 2.0 * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, cost / 20.0)) {
            setInvisible(player, false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_off"), true);
            return;
        }
        if (!player.isPotionActive(MobEffects.INVISIBILITY)) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
        }
    }

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

    public static void teleportLook(EntityPlayerMP player) {
        World world = player.world;
        double range = PocketConfig.teleportBaseRange;

        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLook(1.0f);
        Vec3d end = eye.addVector(look.x * range, look.y * range, look.z * range);

        RayTraceResult hit = world.rayTraceBlocks(eye, end, false, true, false);
        BlockPos land = null;

        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            // Always try to stand ON TOP of the hit block (ceiling -> roof, underside of island -> top)
            BlockPos hitBlock = hit.getBlockPos();
            land = findTopLanding(world, hitBlock);
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
        double cost = (dist / range) * PocketConfig.teleportCostFull * Mana.getPriceMultiplier(player);
        cost = Math.max(1.0, cost);

        if (!Mana.spend(player, cost)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }

        clearMobTargets(player, 16.0);
        player.dismountRidingEntity();
        player.fallDistance = 0f;

        player.connection.setPlayerLocation(land.getX() + 0.5, land.getY(), land.getZ() + 0.5,
                player.rotationYaw, player.rotationPitch);
    }

    /** Climb solid column up to 16, then stand on top if safe. */
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

    private static boolean isSafe(World world, BlockPos feet) {
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

    private static void clearMobTargets(EntityPlayer player, double radius) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(radius);
        List<Entity> list = player.world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (Entity e : list) {
            if (e instanceof EntityMob) {
                EntityMob mob = (EntityMob) e;
                if (mob.getAttackTarget() == player) {
                    mob.setAttackTarget(null);
                }
            }
        }
    }
}
