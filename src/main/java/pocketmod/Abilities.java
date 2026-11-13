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

    private static final double INVIS_COST_PER_SEC = 0.4;

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
        player.getEntityData().setBoolean(KEY_INVIS, now);
        if (now) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
            player.setInvisible(true);
        } else {
            player.removePotionEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
        }
        player.sendStatusMessage(new TextComponentTranslation(
                now ? "pocketmod.msg.invis_on" : "pocketmod.msg.invis_off"), true);
    }

    public static boolean isInvisible(EntityPlayer player) {
        return player.getEntityData().getBoolean(KEY_INVIS);
    }

    public static void tickInvisibility(EntityPlayerMP player) {
        if (!isInvisible(player)) return;
        double drain = INVIS_COST_PER_SEC * Mana.getPriceMultiplier(player) / 20.0;
        if (!Mana.spend(player, drain)) {
            player.getEntityData().setBoolean(KEY_INVIS, false);
            player.removePotionEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_off"), true);
            return;
        }
        player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
        player.setInvisible(true);
    }

    public static void toggleSlow(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.SLOW)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean now = !isSlow(player);
        if (now) {
            double cost = TimeSlow.drainPerSec(player);
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

    public static boolean isSlow(EntityPlayer player) {
        return player.getEntityData().getBoolean(KEY_SLOW);
    }

    public static void tickSlow(EntityPlayerMP player) {
        TimeSlow.tickMana(player);
    }

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

    public static float getSlowFactor(EntityPlayer player) {
        float f = player.getEntityData().getFloat(KEY_SLOW_FACTOR);
        return f > 0.05f ? f : 0.25f;
    }

    public static void setSlowFactor(EntityPlayer player, float factor) {
        player.getEntityData().setFloat(KEY_SLOW_FACTOR, factor);
    }

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
            land = hit.getBlockPos().offset(hit.sideHit);
            land = findTopLanding(world, land);
        } else {
            land = new BlockPos(end.x, end.y, end.z);
            land = findTopLanding(world, land);
        }
        if (land == null) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.teleport_unsafe"), true);
            return;
        }

        double dist = Math.sqrt(player.getDistanceSq(land.getX() + 0.5, land.getY(), land.getZ() + 0.5));
        double cost = dist * PocketConfig.teleportCostPerBlock * Mana.getPriceMultiplier(player);
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

    static void teleportFx(WorldServer ws, Vec3d from, Vec3d to) {
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
                if (world.isAirBlock(top) || !world.getBlockState(top).getMaterial().isSolid()) {
                    return top;
                }
                return above;
            }
            top = above;
        }
        return null;
    }

    public static void clearMobTargets(EntityPlayer player, double radius) {
        if (!(player.world instanceof WorldServer)) return;
        AxisAlignedBB box = player.getEntityBoundingBox().grow(radius);
        List<EntityLiving> list = player.world.getEntitiesWithinAABB(EntityLiving.class, box);
        for (EntityLiving e : list) {
            if (e.getAttackTarget() == player) {
                e.setAttackTarget(null);
            }
        }
    }
}
