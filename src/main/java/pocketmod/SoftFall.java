package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Мягкое падение (пассивная способность): после ~4 блоков падения включается само,
 * замедляет падение (клиент) и гасит урон при приземлении. Каждую секунду падения списывается мана.
 * Мана кончилась в воздухе — защита пропадает, дальше обычное падение.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class SoftFall {

    private static final String ACTIVE = "pm_softfall";

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

    public static void tick(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.SOFTFALL)) {
            if (isActive(p)) setActive(p, false);
            return;
        }
        boolean act = isActive(p);
        if (!falling(p)) {
            if (act) setActive(p, false);
            return;
        }
        float minDist = AbilityStorage.getLevel(p, AbilityType.SOFTFALL) >= 2
                ? PocketConfig.softFallMinDistL2 : PocketConfig.softFallMinDist;
        if (!act && p.fallDistance < minDist) return;
        if (!Mana.spend(p, drainPerSec(p) / 20.0)) {
            if (act) {
                setActive(p, false);
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.softfall_empty"), true);
            }
            return;
        }
        if (!act) {
            setActive(p, true);
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.softfall_on"), true);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (isActive(p)) {
            e.setCanceled(true);
            setActive(p, false);
        }
    }
}
