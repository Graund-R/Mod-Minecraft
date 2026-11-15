package pocketmod;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Тексты для карточек магазина: описание, клавиши и что даёт каждый уровень (числа берутся из конфига). */
@SideOnly(Side.CLIENT)
public class ShopInfo {

    private static String n(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return String.valueOf((long) Math.rint(v));
        return String.format("%.2f", v).replaceAll("0+$", "").replaceAll("[.,]$", "");
    }

    public static String desc(AbilityType t) {
        return Tr.format("pocketmod.info." + t.id + ".desc");
    }

    public static String keys(AbilityType t) {
        String k = "pocketmod.info." + t.id + ".key";
        switch (t) {
            case POCKET:
                return Tr.format(k, ClientHandler.keyName("pocket"));
            case TELEPORT:
                return Tr.format(k, ClientHandler.keyName("teleport"));
            case INVISIBILITY:
                return Tr.format(k, ClientHandler.keyName("invis"));
            case SLOW:
                return Tr.format(k, ClientHandler.keyName("slow"));
            case DECOY:
                return Tr.format(k, ClientHandler.keyName("invis"));
            case SPECTOR:
                return Tr.format(k, ClientHandler.keyName("spector"), ClientHandler.keyName("spector"));
            case THERMAL:
                return Tr.format(k, ClientHandler.keyName("thermal"));
            case SHIELD:
                return Tr.format(k, ClientHandler.keyName("shield"));
            case TOTEM:
                return Tr.format(k, ClientHandler.keyName("teleport"), ClientHandler.keyName("teleport"));
            default:
                return Tr.format(k);
        }
    }

    public static String level(AbilityType t, int lvl) {
        String k = "pocketmod.info." + t.id + ".l" + lvl;
        switch (t) {
            case POCKET:
                return lvl == 2 ? Tr.format(k, (int) Math.round(PocketConfig.pocketDiscountL2 * 100)) : Tr.format(k);
            case TELEPORT:
                if (lvl == 1) return Tr.format(k, n(PocketConfig.teleportBaseRange));
                if (lvl == 2) return Tr.format(k, n(PocketConfig.teleportRange2), n(PocketConfig.silenceTicks / 20.0));
                return Tr.format(k, n(PocketConfig.teleportRange3));
            case INVISIBILITY:
                return Tr.format(k, n(Abilities.noticeRangeFor(lvl)), n(Abilities.invisDrainFor(lvl)));
            case SLOW:
                if (lvl == 1) return Tr.format(k, "0.25");
                if (lvl == 2) return Tr.format(k, "0.1");
                return Tr.format(k, n(PocketConfig.slowZeroDrain));
            case DECOY:
                return Tr.format(k, PocketConfig.decoyMaxSec, PocketConfig.decoyCost);
            case THERMAL:
                return lvl == 1
                        ? Tr.format(k, n(PocketConfig.thermalRadius1), n(PocketConfig.thermalDrain1))
                        : Tr.format(k, n(PocketConfig.thermalRadius2), n(PocketConfig.thermalDrain2));
            case SHIELD:
                return Tr.format(k, n(PocketConfig.shieldManaPerDamage * (lvl >= 2 ? PocketConfig.shieldL2Factor : 1.0)));
            case FLIGHT: {
                int i = Math.max(0, Math.min(2, lvl - 1));
                return Tr.format(k, PocketConfig.flightHeight[i], n(PocketConfig.flightSpeed[i] / 0.05));
            }
            case TOTEM:
                if (lvl == 1) return Tr.format(k, PocketConfig.totemSlots1);
                if (lvl == 2) return Tr.format(k, PocketConfig.totemSlots2, (int) Math.round(PocketConfig.totemDiscount2 * 100));
                return Tr.format(k, PocketConfig.totemSlots3, (int) Math.round(PocketConfig.totemDiscount3 * 100));
            case SOFTFALL:
                if (lvl == 1) return Tr.format(k, n(PocketConfig.softFallMinDist), n(PocketConfig.softFallDrain));
                return Tr.format(k, n(PocketConfig.softFallMinDistL2), n(PocketConfig.softFallDrain * PocketConfig.softFallL2Factor));
            case MAGNET:
                return lvl == 1
                        ? Tr.format(k, n(PocketConfig.magnetRadius1))
                        : Tr.format(k, n(PocketConfig.magnetRadius2));
            case BLOODSUCKER:
                return Tr.format(k);
            case WAVE: {
                int i = Math.max(0, Math.min(PocketConfig.waveRadius.length - 1, lvl - 1));
                return Tr.format(k, n(PocketConfig.waveRadius[i]), n(PocketConfig.waveDamage[i]),
                        n(PocketConfig.waveSlowAmp[i] + 1), n(PocketConfig.waveSlowSec[i]), n(PocketConfig.waveCost[i]));
            }
            default:
                return Tr.format(k);
        }
    }
}
