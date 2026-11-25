package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Полёт (двойной пробел): как в креативе, но за ману, с потолком высоты и скорости. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class Flight {

    private static final String ARMED = "pm_flight";
    private static final String GRACE = "pm_flight_grace";
    private static final String NOFALL = "pm_flight_nofall";
    private static final String Y0 = "pm_flight_y0";

    public static boolean isArmed(EntityPlayer p) {
        return p.getEntityData().getBoolean(ARMED);
    }

    private static int level(EntityPlayer p) {
        return Math.max(1, Math.min(3, AbilityStorage.getLevel(p, AbilityType.FLIGHT)));
    }

    static int heightForLevel(int level) {
        return level <= 0 ? 0 : PocketConfig.flightHeight[Math.min(level, PocketConfig.flightHeight.length) - 1];
    }

    static float speedForLevel(int level) {
        return level <= 0 ? 0 : PocketConfig.flightSpeed[Math.min(level, PocketConfig.flightSpeed.length) - 1];
    }

    /** Базовый расход (висение у точки взлёта). */
    public static double baseDrain(EntityPlayer p) {
        return PocketConfig.flightDrain * Mana.getPriceMultiplier(p);
    }

    /** Текущий расход: база x ускорение (Ctrl) x (1 + k * высота над точкой взлёта). */
    public static double drainPerSec(EntityPlayer p) {
        double d = baseDrain(p);
        if (isArmed(p) && p.capabilities.isFlying) {
            if (p.isSprinting()) d *= PocketConfig.flightSprintMult;
            double up = Math.max(0.0, p.posY - p.getEntityData().getDouble(Y0));
            d *= 1.0 + PocketConfig.flightHeightK * up;
        }
        return d;
    }

    public static void start(EntityPlayerMP p) {
        if (isArmed(p) || p.isCreative() || p.isSpectator()) return;
        if (!AbilityStorage.isUnlocked(p, AbilityType.FLIGHT)) return; // двойной пробел без покупки молчит
        if (Spector.isActive(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.spector_flight"), true);
            return;
        }
        if (Mana.get(p) < baseDrain(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(baseDrain(p))), true);
            return;
        }
        p.getEntityData().setBoolean(ARMED, true);
        p.getEntityData().setDouble(Y0, p.posY);
        p.getEntityData().setInteger(GRACE, 0);
        p.capabilities.allowFlying = true;
        p.capabilities.isFlying = true;
        p.capabilities.setFlySpeed(speedForLevel(level(p)));
        p.sendPlayerAbilities();
    }

    public static void stop(EntityPlayerMP p) {
        if (!isArmed(p)) return;
        disarm(p);
    }

    private static void disarm(EntityPlayerMP p) {
        p.getEntityData().setBoolean(ARMED, false);
        p.getEntityData().setInteger(GRACE, 0);
        if (!p.isCreative() && !p.isSpectator()) {
            p.capabilities.allowFlying = false;
            p.capabilities.isFlying = false;
            p.capabilities.setFlySpeed(0.05F);
            p.sendPlayerAbilities();
        }
    }

    /** Полёт платит ману только в полёте, не в креативе и не во время мягкого парения (вызывает реестр способностей). */
    public static boolean paying(EntityPlayer p) {
        return isArmed(p) && !p.isCreative() && !p.isSpectator()
                && p.getEntityData().getInteger(GRACE) <= 0 && p.capabilities.isFlying;
    }

    /** Мана кончилась: мягкое парение без расхода (вызывает реестр способностей). */
    public static void onManaExhausted(EntityPlayerMP p) {
        p.getEntityData().setInteger(GRACE, Math.max(1, PocketConfig.flightGraceSec * 20));
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.flight_empty"), true);
    }

    /** Каждый тик: парение, потолок высоты, частицы. Ману списывает реестр до вызова. */
    public static void tick(EntityPlayerMP p) {
        if (p.getEntityData().getBoolean(NOFALL) && p.onGround) {
            p.getEntityData().setBoolean(NOFALL, false);
        }
        if (!isArmed(p)) return;
        if (p.isCreative() || p.isSpectator()) {
            p.getEntityData().setBoolean(ARMED, false);
            return;
        }
        int grace = p.getEntityData().getInteger(GRACE);
        if (grace > 0) {
            // мягкое парение без расхода
            p.fallDistance = 0f;
            p.getEntityData().setInteger(GRACE, grace - 1);
            if (grace - 1 == 0) {
                p.getEntityData().setBoolean(NOFALL, true);
                disarm(p);
            }
            return;
        }
        if (!p.capabilities.isFlying) {
            if (p.onGround) disarm(p);
            return;
        }
        p.fallDistance = 0f;
        int ground = p.world.getHeight(new BlockPos(p.posX, p.posY, p.posZ)).getY();
        double cap = ground + heightForLevel(level(p));
        if (p.posY > cap) {
            p.motionY = Math.min(0.0, p.motionY);
            p.connection.setPlayerLocation(p.posX, cap, p.posZ, p.rotationYaw, p.rotationPitch);
        }
        WorldServer ws = (WorldServer) p.world;
        if (p.ticksExisted % 4 == 0) {
            ws.spawnParticle(EnumParticleTypes.CLOUD, p.posX, p.posY + 0.1, p.posZ, 2, 0.25, 0.02, 0.25, 0.01);
        }
        if (p.ticksExisted % 60 == 0) {
            ws.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.15F, 1.2F);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntityLiving() instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
            if (p.getEntityData().getBoolean(NOFALL)) {
                e.setCanceled(true);
            }
        }
    }
}
