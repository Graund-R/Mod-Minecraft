package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Мягкое падение (пассивная): после N блоков падения включается само,
 * на сервере гасит fallDistance и ограничивает скорость, при приземлении урон = 0.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class SoftFall {

    private static final String ACTIVE = "pm_softfall";
    /** Режим текущего падения: 0 — само, 1 — игрок выключил (пробел), 2 — игрок включил обратно. Сбрасывается на земле. */
    private static final String MODE = "pm_softfall_mode";
    /** Сколько блоков игрок на самом деле пролетел под мягким падением (чтобы после выключения урон был честным). */
    private static final String ACC = "pm_softfall_acc";
    private static final String LASTY = "pm_softfall_y";

    public static boolean isActive(EntityPlayer p) {
        return p.getEntityData().getBoolean(ACTIVE);
    }

    private static void setActive(EntityPlayer p, boolean v) {
        p.getEntityData().setBoolean(ACTIVE, v);
    }

    public static double drainPerSec(EntityPlayer p) {
        double d = PocketConfig.softFallDrain;
        if (AbilityStorage.getLevel(p, AbilityType.SOFTFALL) >= 2) d *= PocketConfig.softFallL2Factor;
        return d * Mana.getPriceMultiplier(p);
    }

    private static boolean falling(EntityPlayerMP p) {
        return !p.onGround
                && !p.capabilities.isFlying
                && !p.capabilities.disableDamage
                && !p.isSpectator()
                && !p.isInWater()
                && !p.isInLava()
                && !p.isElytraFlying()
                && !p.isRiding()
                && !p.isOnLadder();
    }

    /**
     * Пробел в воздухе (пакет от клиента): пока мягкое падение работает, выключает его; повторное нажатие включает
     * обратно. Быстрое двойное нажатие по-прежнему запускает полёт (клиент шлёт его отдельным пакетом).
     */
    public static void toggleByPlayer(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.SOFTFALL) || !falling(p)) return;
        NBTTagCompound e = p.getEntityData();
        int mode = e.getInteger(MODE);
        if (mode == 1) {
            e.setInteger(MODE, 2);
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.softfall_resumed"), true);
        } else if (isActive(p)) {
            e.setInteger(MODE, 1);
            setActive(p, false);
            // выключили в воздухе: дальше падение обычное, и уже пролетенное расстояние засчитывается
            p.fallDistance = Math.max(p.fallDistance, e.getFloat(ACC));
            e.setFloat(ACC, 0f);
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.softfall_off"), true);
        }
    }

    public static void tick(EntityPlayerMP p) {
        NBTTagCompound e = p.getEntityData();
        if (!AbilityStorage.isUnlocked(p, AbilityType.SOFTFALL)) {
            if (isActive(p)) setActive(p, false);
            if (e.getInteger(MODE) != 0) e.setInteger(MODE, 0);
            return;
        }
        boolean act = isActive(p);
        if (!falling(p)) {
            if (act) setActive(p, false);
            if (e.getInteger(MODE) != 0) e.setInteger(MODE, 0);
            if (e.getFloat(ACC) != 0f) e.setFloat(ACC, 0f);
            return;
        }
        int mode = e.getInteger(MODE);
        if (mode == 1) {
            // игрок выключил мягкое падение: падаем как обычно, ману не тратим
            if (act) setActive(p, false);
            return;
        }
        float minDist = AbilityStorage.getLevel(p, AbilityType.SOFTFALL) >= 2
                ? PocketConfig.softFallMinDistL2 : PocketConfig.softFallMinDist;
        if (!act && mode != 2 && p.fallDistance < minDist) return;
        if (!Mana.spend(p, drainPerSec(p) / 20.0)) {
            if (act) {
                setActive(p, false);
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.softfall_empty"), true);
            }
            return;
        }
        if (!act) {
            setActive(p, true);
            e.setFloat(ACC, p.fallDistance);
            e.setDouble(LASTY, p.posY);
            if (mode == 0) p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.softfall_on"), true);
        } else {
            double ly = e.getDouble(LASTY);
            if (ly > p.posY) e.setFloat(ACC, e.getFloat(ACC) + (float) (ly - p.posY));
            e.setDouble(LASTY, p.posY);
        }
        // Главный фикс: на сервере не накапливаем урон и режем скорость падения
        p.fallDistance = 0f;
        if (p.motionY < -PocketConfig.softFallSpeed) {
            p.motionY = -PocketConfig.softFallSpeed;
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (isActive(p) || AbilityStorage.isUnlocked(p, AbilityType.SOFTFALL) && p.getEntityData().getBoolean(ACTIVE)) {
            e.setCanceled(true);
            e.setDistance(0f);
            setActive(p, false);
            p.fallDistance = 0f;
        }
    }
}
