package pocketmod;

import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
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

    private static final String KEY_BREAK = "pocketmod_invis_break";
    private static final String KEY_SILENT = "pocketmod_silent_until";

    private static final double INVIS_COST_PER_SEC = 0.4;

    public static double invisDrainFor(int lvl) {
        return PocketConfig.invisCostPerSec * (lvl >= 3 ? 0.5 : lvl == 2 ? 0.75 : 1.0);
    }

    public static double noticeRangeFor(int lvl) {
        return PocketConfig.invisNoticeRange * (lvl >= 3 ? 0.34 : lvl == 2 ? 0.67 : 1.0);
    }

    public static double noticeRange(EntityPlayer p) {
        return noticeRangeFor(AbilityStorage.getLevel(p, AbilityType.INVISIBILITY));
    }

    private static double invisDrain(EntityPlayer p) {
        return invisDrainFor(AbilityStorage.getLevel(p, AbilityType.INVISIBILITY));
    }

    private static final String KEY_SILENCE_OFF = "pm_silence_off";

    public static boolean isSilenceEnabled(EntityPlayer p) {
        return !PlayerData.root(p).getBoolean(KEY_SILENCE_OFF);
    }

    public static void setSilenceEnabled(EntityPlayer p, boolean on) {
        PlayerData.root(p).setBoolean(KEY_SILENCE_OFF, !on);
    }

    public static boolean isDecoyEnabled(EntityPlayer p) {
        return !PlayerData.root(p).getBoolean("pocketmod_decoy_off");
    }

    public static void setDecoyEnabled(EntityPlayer p, boolean v) {
        PlayerData.root(p).setBoolean("pocketmod_decoy_off", !v);
    }

    /** Приманка создаётся в момент включения невидимости (разовая плата). */
    private static void spawnDecoy(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.DECOY) || !isDecoyEnabled(p)) return;
        double cost = PocketConfig.decoyCost * Mana.getPriceMultiplier(p);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.decoy_no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        boolean ok = false;
        try {
            EntityDecoy d = new EntityDecoy(p.world);
            d.ownerId = p.getUniqueID();
            d.setLocationAndAngles(p.posX, p.posY, p.posZ, p.rotationYaw, 0f);
            ok = p.world.spawnEntity(d);
        } catch (Throwable t) {
            System.err.println("[PocketMod] decoy spawn failed:");
            t.printStackTrace();
        }
        if (!ok) {
            Mana.add(p, cost);
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.decoy_failed"), true);
        }
    }

    /** Удар игроком сбрасывает невидимость на несколько секунд. */
    public static void breakInvisibility(EntityPlayer p) {
        if (!isInvisible(p) || PocketConfig.invisHitBreakTicks <= 0) return;
        boolean was = p.getEntityData().getInteger(KEY_BREAK) > 0;
        p.getEntityData().setInteger(KEY_BREAK, PocketConfig.invisHitBreakTicks);
        if (!was && p instanceof EntityPlayerMP) {
            ((EntityPlayerMP) p).sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_broken",
                    String.valueOf(Math.round(PocketConfig.invisHitBreakTicks / 20.0))), true);
        }
    }

    public static boolean isInvisBroken(EntityPlayer p) {
        return p.getEntityData().getInteger(KEY_BREAK) > 0;
    }

    public static boolean isSilent(EntityPlayer p) {
        return p.ticksExisted < p.getEntityData().getInteger(KEY_SILENT);
    }

    public static void toggleInvisibility(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.INVISIBILITY)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean now = !isInvisible(player);
        if (now) {
            double cost = invisDrain(player) * Mana.getPriceMultiplier(player);
            if (Mana.get(player) < cost) {
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) cost), true);
                return;
            }
        }
        player.getEntityData().setBoolean(KEY_INVIS, now);
        player.getEntityData().setInteger(KEY_BREAK, 0);
        if (now) {
            player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
            player.setInvisible(true);
            dropTargets(player);
            spawnDecoy(player);
            WorldServer ws = (WorldServer) player.world;
            ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, player.posX, player.posY + 1.0, player.posZ, 20, 0.3, 0.6, 0.3, 0.02);
            ws.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 0.4F, 0.6F);
        } else {
            player.removePotionEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            EntityDecoy.removeDecoys(player);
            ((WorldServer) player.world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL, player.posX, player.posY + 1.0, player.posZ, 12, 0.3, 0.6, 0.3, 0.02);
        }
        player.sendStatusMessage(new TextComponentTranslation(
                now ? "pocketmod.msg.invis_on" : "pocketmod.msg.invis_off"), true);
    }

    public static boolean isInvisible(EntityPlayer player) {
        return player.getEntityData().getBoolean(KEY_INVIS);
    }

    public static void tickInvisibility(EntityPlayerMP player) {
        if (!isInvisible(player)) return;
        int br = player.getEntityData().getInteger(KEY_BREAK);
        if (br > 0) {
            // невидимость сбита ударом: ты виден, мана в это время не тратится
            player.getEntityData().setInteger(KEY_BREAK, br - 1);
            player.removePotionEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            if (br == 1) player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_back"), true);
            return;
        }
        double drain = invisDrain(player) * Mana.getPriceMultiplier(player) / 20.0;
        if (!Mana.spend(player, drain)) {
            player.getEntityData().setBoolean(KEY_INVIS, false);
            player.removePotionEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            EntityDecoy.removeDecoys(player);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.invis_off"), true);
            return;
        }
        if (player.ticksExisted % 10 == 0) dropTargets(player);
        player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 40, 0, false, false));
        player.setInvisible(true);
    }

    /** Мобы, которые уже заметили игрока, теряют цель, если стоят дальше радиуса «вплотную». */
    static void dropTargets(EntityPlayerMP player) {
        double range = noticeRange(player);
        AxisAlignedBB box = player.getEntityBoundingBox().grow(48.0);
        List<EntityLiving> list = player.world.getEntitiesWithinAABB(EntityLiving.class, box);
        for (EntityLiving e : list) {
            if (e instanceof EntityDecoy || e.isDead) continue;
            if (e.getAttackTarget() == player && e.getDistance(player) > range) {
                e.setAttackTarget(null);
                e.setRevengeTarget(null);
                e.getNavigator().clearPath();
            }
        }
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
            d += invisDrain(player) * Mana.getPriceMultiplier(player);
        }
        if (isSlow(player)) {
            d += TimeSlow.drainPerSec(player);
        }
        if (Thermal.isOn(player)) d += Thermal.drainPerSec(player);
        if (Spector.isActive(player)) d += Spector.drainPerSec(player);
        if (Flight.isArmed(player) && player.capabilities.isFlying) d += Flight.drainPerSec(player);
        if (SoftFall.isActive(player)) d += SoftFall.drainPerSec(player);
        d += BodyEffects.totalRentDrain(player);
        return d;
    }

    /** Минимальный множитель времени: 0.1, а на уровне 3 замедление доходит до полного нуля (стоп). */
    public static float minSlowFactor(EntityPlayer player) {
        int l = AbilityStorage.getLevel(player, AbilityType.SLOW);
        return l >= 3 ? 0.0f : l == 2 ? 0.1f : 0.25f;
    }

    public static float getSlowFactor(EntityPlayer player) {
        if (!player.getEntityData().hasKey(KEY_SLOW_FACTOR)) return 0.25f;
        float f = player.getEntityData().getFloat(KEY_SLOW_FACTOR);
        return Math.max(minSlowFactor(player), Math.min(0.75f, f));
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
        if (Spector.isActive(player)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.teleport_spector"), true);
            return;
        }
        World world = player.world;
        double range = teleportRange(player);

        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLook(1.0f);
        Vec3d end = eye.addVector(look.x * range, look.y * range, look.z * range);

        boolean inPocket = world.provider.getDimension() == PocketMod.DIM_ID;
        RayTraceResult hit = inPocket ? null : world.rayTraceBlocks(eye, end, false, true, false);
        BlockPos land = null;
        if (inPocket) {
            // в кармане телепорт идёт только по внутренним клеткам: сквозь стены и потолок нельзя
            land = pocketLanding((WorldServer) world, eye, look, range);
        } else if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            land = hit.getBlockPos().offset(hit.sideHit);
            land = findTopLanding(world, land);
        } else {
            land = new BlockPos(end.x, end.y, end.z);
            land = findTopLanding(world, land);
        }
        if (!inPocket && land != null && !PocketTeleporter.standable(world, land)) {
            double[] alt = PocketTeleporter.findStandable((WorldServer) world, land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
            land = alt == null ? null : new BlockPos(alt[0], alt[1], alt[2]);
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
        if (isSilenceEnabled(player) && AbilityStorage.getLevel(player, AbilityType.TELEPORT) >= PocketConfig.silenceMinLevel) {
            player.getEntityData().setInteger(KEY_SILENT, player.ticksExisted + PocketConfig.silenceTicks);
        }
    }

    /** Последняя свободная внутренняя клетка вдоль взгляда (стены и всё, что внутри ставил игрок, останавливают луч). */
    private static BlockPos pocketLanding(WorldServer w, Vec3d eye, Vec3d look, double range) {
        PocketData d = PocketData.get(w);
        BlockPos last = null;
        for (double t = 0.5; t <= range; t += 0.25) {
            double x = eye.x + look.x * t, y = eye.y + look.y * t, z = eye.z + look.z * t;
            BlockPos head = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
            BlockPos feet = new BlockPos(head.getX(), MathHelper.floor(y - 1.0), head.getZ());
            if (!d.interior.contains(head) || !d.interior.contains(feet)) break;
            if (w.getBlockState(head).getMaterial().blocksMovement()) break;
            if (w.getBlockState(feet).getMaterial().blocksMovement()) break;
            last = feet;
        }
        if (last == null) return null;
        // опускаем на пол, не выходя за внутренние клетки
        for (int i = 0; i < 64; i++) {
            BlockPos below = last.down();
            if (!d.interior.contains(below) || w.getBlockState(below).getMaterial().blocksMovement()) break;
            last = below;
        }
        return last;
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

    /** Волна: Shift+Space — откидывает, слегка бьёт и дезориентирует врагов вокруг. */
    public static void shockwave(EntityPlayerMP p) {
        if (Spector.isActive(p)) return;
        if (!AbilityStorage.isUnlocked(p, AbilityType.WAVE)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        int wl = Math.max(1, Math.min(AbilityType.WAVE.maxLevel, AbilityStorage.getLevel(p, AbilityType.WAVE))) - 1;
        double cost = PocketConfig.waveCost[wl] * Mana.getPriceMultiplier(p);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        double r = PocketConfig.waveRadius[wl];
        float dmg = (float) PocketConfig.waveDamage[wl];
        double knock = PocketConfig.waveKnock[wl];
        int slowTicks = (int) Math.round(PocketConfig.waveSlowSec[wl] * 20.0);
        int slowAmp = (int) Math.round(PocketConfig.waveSlowAmp[wl]);
        int nauseaTicks = (int) Math.round(PocketConfig.waveNauseaSec[wl] * 20.0);
        int weakTicks = (int) Math.round(PocketConfig.waveWeakSec[wl] * 20.0);
        AxisAlignedBB box = p.getEntityBoundingBox().grow(r, r * 0.6, r);
        List<EntityLivingBase> list = p.world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        WorldServer ws = (WorldServer) p.world;
        for (EntityLivingBase e : list) {
            if (e == p || e.isDead) continue;
            if (!(e instanceof net.minecraft.entity.monster.IMob)
                    && !e.isCreatureType(net.minecraft.entity.EnumCreatureType.MONSTER, false)) {
                continue;
            }
            double dx = e.posX - p.posX;
            double dz = e.posZ - p.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.1) dist = 0.1;
            double str = (1.0 - Math.min(1.0, dist / r)) * knock;
            e.motionX += (dx / dist) * str;
            e.motionY += 0.35 * str;
            e.motionZ += (dz / dist) * str;
            e.velocityChanged = true;
            e.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(p), dmg);
            if (slowTicks > 0) {
                e.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.SLOWNESS, slowTicks, slowAmp));
            }
            if (nauseaTicks > 0) {
                e.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NAUSEA, nauseaTicks, 0));
            }
            if (weakTicks > 0) {
                e.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.WEAKNESS, weakTicks, 0));
            }
        }
        ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, p.posX, p.posY + 0.2, p.posZ, 1, 0.0, 0.0, 0.0, 0.0);
        ws.spawnParticle(EnumParticleTypes.CLOUD, p.posX, p.posY + 0.3, p.posZ, 30, 1.5, 0.3, 1.5, 0.05);
        ws.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6F, 1.4F);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.shockwave"), true);
    }

}
