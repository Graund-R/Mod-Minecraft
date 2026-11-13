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

    public static void toggleInvisibility(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.INVISIBILITY)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean on = isInvis(player);
        setInvis(player, !on);
        player.sendStatusMessage(new TextComponentTranslation(on ? "pocketmod.msg.invis_off" : "pocketmod.msg.invis_on"), true);
    }

    public static boolean isInvis(EntityPlayer player) {
        return PlayerData.root(player).getBoolean(KEY_INVIS);
    }

    public static void setInvis(EntityPlayer player, boolean on) {
        PlayerData.root(player).setBoolean(KEY_INVIS, on);
        if (on) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 20 * 60 * 60, 0, false, false));
            player.setInvisible(true);
        } else {
            player.removePotionEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
        }
    }

    public static void tickInvisibility(EntityPlayerMP player) {
        if (!isInvis(player)) return;
        int lvl = AbilityStorage.getLevel(player, AbilityType.INVISIBILITY);
        double drain = lvl >= 3 ? 0.15 : (lvl >= 2 ? 0.25 : 0.4);
        if (!Mana.spend(player, drain / 20.0)) {
            setInvis(player, false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_off"), true);
        } else {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
            player.setInvisible(true);
        }
    }

    public static void toggleSlow(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.SLOW)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean on = isSlow(player);
        setSlow(player, !on);
        player.sendStatusMessage(new TextComponentTranslation(on ? "pocketmod.msg.slow_off" : "pocketmod.msg.slow_on"), true);
    }

    public static boolean isSlow(EntityPlayer player) {
        return PlayerData.root(player).getBoolean(KEY_SLOW);
    }

    public static void setSlow(EntityPlayer player, boolean on) {
        PlayerData.root(player).setBoolean(KEY_SLOW, on);
        if (!on) {
            TimeSlow.clear(player);
        }
    }

    public static float getSlowFactor(EntityPlayer player) {
        float f = PlayerData.root(player).getFloat(KEY_SLOW_FACTOR);
        return f > 0.05f ? f : 0.5f;
    }

    public static void setSlowFactor(EntityPlayer player, float factor) {
        PlayerData.root(player).setFloat(KEY_SLOW_FACTOR, factor);
    }

    public static void tickSlow(EntityPlayerMP player) {
        if (!isSlow(player)) return;
        double drain = 0.8 / 20.0;
        if (!Mana.spend(player, drain)) {
            setSlow(player, false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.slow_off"), true);
            return;
        }
        TimeSlow.apply(player, getSlowFactor(player));
    }

    public static double totalDrainPerSec(EntityPlayer player) {
        double d = 0;
        if (isInvis(player)) {
            int lvl = AbilityStorage.getLevel(player, AbilityType.INVISIBILITY);
            d += lvl >= 3 ? 0.15 : (lvl >= 2 ? 0.25 : 0.4);
        }
        if (isSlow(player)) d += 0.8;
        return d;
    }

    public static void teleportLook(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.TELEPORT)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        int range = PocketConfig.teleportBaseRange;
        int lvl = AbilityStorage.getLevel(player, AbilityType.TELEPORT);
        if (lvl >= 3) range = PocketConfig.teleportRange3;
        else if (lvl >= 2) range = PocketConfig.teleportRange2;

        World world = player.world;
        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLook(1.0f);
        Vec3d end = eye.add(look.x * range, look.y * range, look.z * range);
        RayTraceResult hit = world.rayTraceBlocks(eye, end, false, true, false);

        BlockPos land;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            land = hit.getBlockPos().offset(hit.sideHit);
            land = findTopLanding(world, land);
        } else {
            land = new BlockPos(end);
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

    /** След частиц по пути и звук. */
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
