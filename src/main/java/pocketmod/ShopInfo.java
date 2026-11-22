package pocketmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
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
                String range = Tr.format("pocketmod.info.teleport.range", n(Abilities.teleportRangeForLevel(lvl)));
                if (lvl != PocketConfig.silenceMinLevel) return range;
                return range + " " + Tr.format("pocketmod.info.teleport.silence", n(PocketConfig.silenceTicks / 20.0));
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
                return Tr.format(k, Thermal.radiusForLevel(lvl), n(Thermal.baseDrainForLevel(lvl)));
            case SHIELD:
                return Tr.format(k, n(PocketConfig.shieldManaPerDamage * (lvl >= 2 ? PocketConfig.shieldL2Factor : 1.0)),
                        n(PocketConfig.shieldUpkeepBase), n(PocketConfig.shieldUpkeepRamp),
                        n(PocketConfig.shieldUpkeepBase * 30 + PocketConfig.shieldUpkeepRamp * 450));
            case FLIGHT:
                return Tr.format(k, Flight.heightForLevel(lvl), n(Flight.speedForLevel(lvl) / 0.05));
            case TOTEM:
                return Tr.format(k, Totems.slotsForLevel(lvl), n(Totems.discountForLevel(lvl) * 100));
            case SOFTFALL:
                return Tr.format(k, n(SoftFall.thresholdForLevel(lvl)), n(SoftFall.baseDrainForLevel(lvl)));
            case MAGNET:
                return Tr.format(k, n(Magnet.radiusForLevel(lvl)));
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

    static List<String> levelChanges(AbilityType ability, int current, EntityPlayer player) {
        double multiplier = Mana.getPriceMultiplier(player);
        if (ability == AbilityType.THERMAL) {
            return thermalChanges(current, multiplier, AbilityStorage.getLevel(player, AbilityType.ORE_SCANNER));
        }
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
        int next = current + 1;
        List<String> lines = new ArrayList<>();
        switch (ability) {
            case FLIGHT:
                addChange(lines, "flight", "height", Flight.heightForLevel(current), Flight.heightForLevel(next));
                addChange(lines, "flight", "speed", Flight.speedForLevel(current) / 0.05,
                        Flight.speedForLevel(next) / 0.05);
                break;
            case TELEPORT:
                addChange(lines, "teleport", "range", Abilities.teleportRangeForLevel(current),
                        Abilities.teleportRangeForLevel(next));
                if (next == PocketConfig.silenceMinLevel) {
                    lines.add(Tr.format("pocketmod.info.teleport.silence", n(PocketConfig.silenceTicks / 20.0)));
                }
                break;
            case INVISIBILITY:
                addChange(lines, "invis", "notice", Abilities.noticeRangeFor(current), Abilities.noticeRangeFor(next));
                addChange(lines, "invis", "drain", Abilities.invisDrainFor(current) * multiplier,
                        Abilities.invisDrainFor(next) * multiplier);
                break;
            case MAGNET:
                addChange(lines, "magnet", "radius", Magnet.radiusForLevel(current), Magnet.radiusForLevel(next));
                break;
            case SOFTFALL:
                addChange(lines, "softfall", "threshold", SoftFall.thresholdForLevel(current), SoftFall.thresholdForLevel(next));
                addChange(lines, "softfall", "drain", SoftFall.baseDrainForLevel(current) * multiplier,
                        SoftFall.baseDrainForLevel(next) * multiplier);
                break;
            case TOTEM:
                addChange(lines, "totem", "slots", Totems.slotsForLevel(current), Totems.slotsForLevel(next));
                addChange(lines, "totem", "discount", Totems.discountForLevel(current) * 100,
                        Totems.discountForLevel(next) * 100);
                break;
            default:
                break;
        }
        return lines;
    }

    private static List<String> thermalChanges(int current, double multiplier, int scannerLevel) {
        int next = current + 1;
        List<String> lines = new ArrayList<>();
        addChange(lines, "thermal", "radius", Thermal.radiusForLevel(current), Thermal.radiusForLevel(next));
        addChange(lines, "thermal", "drain", Thermal.baseDrainForLevel(current) * multiplier,
                Thermal.baseDrainForLevel(next) * multiplier);
        if (next == 3) {
            if (scannerLevel >= 2) {
                lines.add(Tr.format("pocketmod.info.thermal.change.scanner_owned"));
            } else if (scannerLevel == 1) {
                lines.add(Tr.format("pocketmod.info.thermal.change.scanner_radius",
                        OreScanner.radiusForLevel(1), OreScanner.radiusForLevel(2)));
            } else {
                lines.add(Tr.format("pocketmod.info.thermal.change.scanner_unlock", OreScanner.radiusForLevel(2)));
            }
        }
        return lines;
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
