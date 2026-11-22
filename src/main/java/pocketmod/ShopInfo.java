package pocketmod;

import java.util.ArrayList;
import java.util.List;

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
        if (t == AbilityType.ORE_SCANNER) {
            return Tr.format("pocketmod.info.scanner.desc", n(PocketConfig.oreScannerPeriodTicks / 20.0),
                    PocketConfig.oreScannerVertical);
        }
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
            case ORE_SCANNER:
                return Tr.format(k, ClientHandler.keyName("scanner"));
            case WAREHOUSE:
                return Tr.format(k);
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
                if (lvl >= 3) {
                    return Tr.format(k, OreScanner.radiusForLevel(2));
                }
                return lvl == 1
                        ? Tr.format(k, n(PocketConfig.thermalRadius1), n(PocketConfig.thermalDrain1))
                        : Tr.format(k, n(PocketConfig.thermalRadius2), n(PocketConfig.thermalDrain2));
            case SHIELD:
                return Tr.format(k, n(PocketConfig.shieldManaPerDamage * (lvl >= 2 ? PocketConfig.shieldL2Factor : 1.0)),
                        n(PocketConfig.shieldUpkeepBase), n(PocketConfig.shieldUpkeepRamp),
                        n(PocketConfig.shieldUpkeepBase * 30 + PocketConfig.shieldUpkeepRamp * 450));
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
            case ORE_SCANNER:
                return Tr.format(k, OreScanner.radiusForLevel(lvl), n(PocketConfig.oreScannerDrain));
            case WAREHOUSE:
                return Tr.format(k, lvl);
            case DASH:
                return Tr.format("pocketmod.info.dash.stats", n(Dash.distance(lvl)), n(Dash.ticks(lvl) / 20.0),
                        n(Dash.cost(lvl)), Dash.chargesMax(lvl), n(Dash.rechargeTicks(lvl) / 20.0),
                        n(Dash.damage(lvl)), n(Dash.reflectionTicks(lvl) / 20.0),
                        n(Dash.graceTicks(lvl) / 20.0), refundLabel(Dash.refundsCharge(lvl)));
            case WAVE: {
                WaveLevel wave = new WaveLevel(lvl);
                return Tr.format("pocketmod.info.wave.stats", n(wave.radius), n(wave.damage),
                        n(wave.knockback), n(wave.manaCost), n(wave.cooldownTicks / 20.0),
                        wave.slowTicks > 0 ? wave.slowAmplifier + 1 : 0, n(wave.slowTicks / 20.0),
                        n(wave.nauseaTicks / 20.0), n(wave.weaknessTicks / 20.0));
            }
            default:
                return Tr.format(k);
        }
    }

    static List<String> levelChanges(AbilityType ability, int current, double multiplier) {
        if (ability == AbilityType.DASH) return dashChanges(current, multiplier);
        if (ability == AbilityType.WAVE) return waveChanges(current, multiplier);
        if (ability == AbilityType.ORE_SCANNER) {
            int before = OreScanner.radiusForLevel(current);
            int after = OreScanner.radiusForLevel(current + 1);
            if (before != after) {
                return java.util.Collections.singletonList(
                        Tr.format("pocketmod.info.scanner.change.radius", before, after));
            }
        }
        return java.util.Collections.emptyList();
    }

    private static List<String> waveChanges(int current, double multiplier) {
        WaveLevel before = new WaveLevel(current);
        WaveLevel after = new WaveLevel(current + 1);
        List<String> lines = new ArrayList<>();
        addChange(lines, "wave", "radius", before.radius, after.radius);
        addChange(lines, "wave", "damage", before.damage, after.damage);
        addChange(lines, "wave", "knockback", before.knockback, after.knockback);
        addChange(lines, "wave", "cost", before.manaCost * multiplier, after.manaCost * multiplier);
        addChange(lines, "wave", "cooldown", before.cooldownTicks / 20.0, after.cooldownTicks / 20.0);
        addChange(lines, "wave", "slow_level", before.slowTicks > 0 ? before.slowAmplifier + 1 : 0,
                after.slowTicks > 0 ? after.slowAmplifier + 1 : 0);
        addChange(lines, "wave", "slow_time", before.slowTicks / 20.0, after.slowTicks / 20.0);
        addChange(lines, "wave", "nausea", before.nauseaTicks / 20.0, after.nauseaTicks / 20.0);
        addChange(lines, "wave", "weakness", before.weaknessTicks / 20.0, after.weaknessTicks / 20.0);
        return lines;
    }

    private static List<String> dashChanges(int current, double multiplier) {
        List<String> lines = new ArrayList<>();
        int next = current + 1;
        addChange(lines, "dash", "distance", Dash.distance(current), Dash.distance(next));
        addChange(lines, "dash", "duration", Dash.ticks(current) / 20.0, Dash.ticks(next) / 20.0);
        addChange(lines, "dash", "cost", Dash.cost(current) * multiplier, Dash.cost(next) * multiplier);
        addChange(lines, "dash", "charges", Dash.chargesMax(current), Dash.chargesMax(next));
        addChange(lines, "dash", "recharge", Dash.rechargeTicks(current) / 20.0, Dash.rechargeTicks(next) / 20.0);
        addChange(lines, "dash", "damage", Dash.damage(current), Dash.damage(next));
        addChange(lines, "dash", "reflect", Dash.reflectionTicks(current) / 20.0, Dash.reflectionTicks(next) / 20.0);
        addChange(lines, "dash", "grace", Dash.graceTicks(current) / 20.0, Dash.graceTicks(next) / 20.0);
        if (Dash.refundsCharge(current) != Dash.refundsCharge(next)) {
            lines.add(Tr.format("pocketmod.info.dash.change.refund",
                    refundLabel(Dash.refundsCharge(current)), refundLabel(Dash.refundsCharge(next))));
        }
        return lines;
    }

    private static void addChange(List<String> lines, String ability, String key, double current, double next) {
        String before = n(current);
        String after = n(next);
        if (!before.equals(after)) lines.add(Tr.format("pocketmod.info." + ability + ".change." + key, before, after));
    }

    private static String refundLabel(boolean refund) {
        return Tr.format(refund ? "pocketmod.info.dash.refund_on" : "pocketmod.info.dash.refund_off");
    }
}
