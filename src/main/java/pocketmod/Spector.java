package pocketmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/** Спектор: дух вылетает из тела (режим наблюдателя), тело остаётся стоять сущностью-копией. */
public class Spector {

    private static final String ACTIVE = "pm_spec";
    private static final String GM = "pm_spec_gm";
    private static final String BODY = "pm_spec_body";
    private static final String SX = "pm_spec_x", SY = "pm_spec_y", SZ = "pm_spec_z";

    public static boolean isActive(EntityPlayer p) {
        return p.getEntityData().getBoolean(ACTIVE);
    }

    private static EntityDecoy body(EntityPlayer p) {
        Entity e = p.world.getEntityByID(p.getEntityData().getInteger(BODY));
        return e instanceof EntityDecoy && !e.isDead ? (EntityDecoy) e : null;
    }

    public static double drainPerSec(EntityPlayer p) {
        EntityDecoy b = body(p);
        double dist = b == null ? 0.0 : p.getDistance(b);
        return (PocketConfig.spectorBaseDrain + PocketConfig.spectorPerBlock * dist) * Mana.getPriceMultiplier(p);
    }

    public static void start(EntityPlayerMP p) {
        if (isActive(p)) return;
        if (!AbilityStorage.isUnlocked(p, AbilityType.SPECTOR)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        if (Flight.isArmed(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.spector_flight"), true);
            return;
        }
        if (p.isSpectator()) {
            // уже spectator без нашего флага — сброс
            return;
        }
        double need = PocketConfig.spectorBaseDrain * Mana.getPriceMultiplier(p);
        if (Mana.get(p) < need) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(need)), true);
            return;
        }
        EntityDecoy b = new EntityDecoy(p.world);
        b.body = true;
        b.ownerId = p.getUniqueID();
        b.setLocationAndAngles(p.posX, p.posY, p.posZ, p.rotationYaw, 0f);
        boolean spawned = false;
        try {
            spawned = p.world.spawnEntity(b);
        } catch (Throwable t) {
            System.err.println("[PocketMod] spector body spawn failed:");
            t.printStackTrace();
        }
        if (!spawned) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.body_failed"), true);
            return;
        }

        NBTTagCompound d = p.getEntityData();
        d.setBoolean(ACTIVE, true);
        d.setInteger(GM, p.interactionManager.getGameType().getID());
        d.setInteger(BODY, b.getEntityId());
        d.setDouble(SX, p.posX);
        d.setDouble(SY, p.posY);
        d.setDouble(SZ, p.posZ);
        Abilities.clearMobTargets(p, 16.0);
        p.setGameType(GameType.SPECTATOR);
        fx((WorldServer) p.world, p.posX, p.posY + 1.0, p.posZ, false);
    }

    /** Отпустили X: дух возвращается в тело. */
    public static void stop(EntityPlayerMP p) {
        if (isActive(p)) end(p, true);
    }

    public static void forceReturnIfActive(EntityPlayerMP p) {
        if (isActive(p)) end(p, true);
    }

    private static void end(EntityPlayerMP p, boolean toBody) {
        NBTTagCompound d = p.getEntityData();
        EntityDecoy b = body(p);
        double x = d.getDouble(SX), y = d.getDouble(SY), z = d.getDouble(SZ);
        float yaw = p.rotationYaw, pitch = p.rotationPitch;
        if (b != null) {
            x = b.posX;
            y = b.posY;
            z = b.posZ;
            b.setDead();
        }
        GameType prev = GameType.getByID(d.getInteger(GM));
        d.setBoolean(ACTIVE, false);
        fx((WorldServer) p.world, p.posX, p.posY + 1.0, p.posZ, false);
        p.setGameType(prev);
        if (toBody) {
            p.connection.setPlayerLocation(x, y, z, yaw, pitch);
            fx((WorldServer) p.world, x, y + 1.0, z, false);
        }
        p.fallDistance = 0f;
    }

    /** Фазовый шаг: тело появляется в переданных координатах духа (клиентская камера). */
    public static void phaseStep(EntityPlayerMP p) {
        try {
            doPhaseStep(p);
        } catch (Throwable t) {
            System.err.println("[PocketMod] phase step failed:");
            t.printStackTrace();
            p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                    "Фазовый шаг: ошибка " + t.getClass().getSimpleName() + " " + t.getMessage()), true);
        }
    }

    /** Ближайшая свободная для игрока точка (до 3 блоков вверх, ±1 в стороны) или null. */
    private static double[] freeSpot(EntityPlayerMP p, double x, double y, double z) {
        double w = p.width / 2.0, h = p.height;
        int[] off = { 0, 1, -1 };
        for (int dy = 0; dy <= 3; dy++) {
            for (int ix = 0; ix < off.length; ix++) {
                for (int iz = 0; iz < off.length; iz++) {
                    double cx = x + off[ix], cy = y + dy, cz = z + off[iz];
                    net.minecraft.util.math.AxisAlignedBB bb = new net.minecraft.util.math.AxisAlignedBB(
                            cx - w, cy, cz - w, cx + w, cy + h, cz + w);
                    if (p.world.getCollisionBoxes(null, bb).isEmpty()) return new double[] { cx, cy, cz };
                }
            }
        }
        return null;
    }

    private static void doPhaseStep(EntityPlayerMP p) {
        if (!isActive(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.spector_need"), true);
            return;
        }
        // Берём позицию из серверного объекта игрока, а не из пользовательского пакета.
        double x = p.posX, y = p.posY, z = p.posZ;
        float yaw = p.rotationYaw, pitch = p.rotationPitch;
        net.minecraft.util.math.BlockPos target = new net.minecraft.util.math.BlockPos(
                net.minecraft.util.math.MathHelper.floor(x),
                net.minecraft.util.math.MathHelper.floor(y),
                net.minecraft.util.math.MathHelper.floor(z));
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < 0.0 || y > 254.0 || !p.world.getWorldBorder().contains(target)
                || !p.world.isBlockLoaded(target)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.phase_blocked"), true);
            return;
        }
        if (p.world.provider.getDimension() == PocketMod.DIM_ID) {
            PocketData pd = PocketData.get(p.world);
            net.minecraft.util.math.BlockPos f = new net.minecraft.util.math.BlockPos(
                    net.minecraft.util.math.MathHelper.floor(x),
                    net.minecraft.util.math.MathHelper.floor(y),
                    net.minecraft.util.math.MathHelper.floor(z));
            if (!pd.interior.contains(f) && !pd.interior.contains(f.up())) {
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.pocket_limits"), true);
                return;
            }
        }
        // Не появляться внутри блоков: ищем свободную точку рядом с духом.
        double[] spot = freeSpot(p, x, y, z);
        if (spot == null) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.phase_blocked"), true);
            return;
        }
        x = spot[0];
        y = spot[1];
        z = spot[2];
        EntityDecoy b = body(p);
        double dist = 0.0;
        if (b != null) {
            double dx = x - b.posX, dy = y - b.posY, dz = z - b.posZ;
            dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        double cost = (PocketConfig.phaseBase + PocketConfig.phasePerBlock * dist) * Mana.getPriceMultiplier(p);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        // Позиция меняется только после успешного списания маны.
        p.setPositionAndRotation(x, y, z, yaw, pitch);
        NBTTagCompound d = p.getEntityData();
        if (b != null) b.setDead();
        GameType prev = GameType.getByID(d.getInteger(GM));
        if (prev == null || prev == GameType.SPECTATOR || prev == GameType.NOT_SET) prev = GameType.SURVIVAL;
        d.setBoolean(ACTIVE, false);
        d.setInteger(BODY, -1);
        p.setGameType(prev);
        p.connection.setPlayerLocation(x, y, z, yaw, pitch);
        p.setPositionAndUpdate(x, y, z);
        p.fallDistance = 0f;
        fx((WorldServer) p.world, x, y + 1.0, z, true);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.phase_ok"), true);
    }

        /** Телу нанесли урон: мгновенный возврат, урон получает игрок. */
    public static void onBodyHit(EntityDecoy body, DamageSource src, float amount) {
        EntityPlayer o = body.world.getPlayerEntityByUUID(body.ownerId);
        body.setDead();
        if (o instanceof EntityPlayerMP && isActive(o)) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            end(p, true);
            p.attackEntityFrom(src, amount);
        }
    }

    /** Дух платит ману, только пока цело тело (вызывает реестр способностей). */
    public static boolean isPaying(EntityPlayer p) {
        return isActive(p) && body(p) != null;
    }

    /** Мана кончилась (вызывает реестр способностей). */
    public static void onManaExhausted(EntityPlayerMP p) {
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.spector_off"), true);
        end(p, true);
    }

    /** Каждый тик: проверка тела и частицы. Ману списывает реестр до вызова. */
    public static void tick(EntityPlayerMP p) {
        if (!isActive(p)) return;
        if (body(p) == null) {
            end(p, true);
            return;
        }
        if (p.ticksExisted % 10 == 0) {
            ((WorldServer) p.world).spawnParticle(EnumParticleTypes.SPELL_MOB, p.posX, p.posY + 1.0, p.posZ,
                    0, 0.4, 0.7, 1.0, 1.0);
        }
    }

    /** При входе в игру: если вышли в духе — вернуть. */
    public static void recoverOnLogin(EntityPlayerMP p) {
        if (isActive(p)) end(p, true);
    }

    private static void fx(WorldServer ws, double x, double y, double z, boolean strong) {
        int n = strong ? 40 : 14;
        ws.spawnParticle(EnumParticleTypes.PORTAL, x, y, z, n, 0.4, 0.6, 0.4, 0.2);
        if (strong) {
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z, 30, 0.3, 0.5, 0.3, 0.2);
            ws.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.5F);
            ws.playSound(null, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6F, 0.6F);
        } else {
            ws.playSound(null, x, y, z, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.15F, 1.6F);
        }
    }
}
