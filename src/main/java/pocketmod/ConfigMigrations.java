package pocketmod;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Миграции формата config/pocketmod.cfg.
 *
 * Версия формата хранится в служебной категории. При загрузке выполняются все шаги от сохранённой версии до
 * {@link #CURRENT}; перед первым шагом делается копия файла (pocketmod.cfg.bak-vN).
 *
 * Как добавить правку: увеличь CURRENT, добавь шаг в {@link #run} («if (from < N) stepN(cfg);») и опиши его
 * в CHANGELOG.md. Менять умолчания в коде недостаточно: значение, уже записанное в файл, перебьёт новое умолчание.
 */
public class ConfigMigrations {

    /** Текущая версия формата конфига. */
    public static final int CURRENT = 2;

    /** Служебная категория (скрыта в экране настроек). */
    public static final String META = "служебное";

    /** {старая категория, старый ключ, новая категория, новый ключ, старое умолчание ("*" = переносить всегда)}. */
    private static final String[][] LEGACY = {
            { "abilities", "silenceMinLevel", "способности", "silenceMinLevel", "2" },
            { "abilities", "silenceTicks", "способности", "silenceTicks", "50" },
            { "abilities", "slowSound", "способности", "slowSound", "false" },
            { "abilities", "teleportBaseRange", "способности", "teleportBaseRange", "50" },
            { "abilities", "teleportCostPerBlock", "способности", "teleportCostPerBlock", "0.4" },
            { "abilities", "teleportRange2", "способности", "teleportRange2", "100" },
            { "abilities", "teleportRange3", "способности", "teleportRange3", "200" },
            { "body", "heartsMaxBuys", "тело", "heartsMaxBuys", "5" },
            { "body", "heartsRent", "тело", "heartsRent", "2" },
            { "body", "permCosts", "тело", "permCosts", "300,500,600,150,150" },
            { "body", "rentPerSec", "тело", "rentPerSec", "0.8,1.0,1.2,0.3,0.3" },
            { "controls", "holdMs", "управление", "holdMs", "400" },
            { "credit", "creditDurationSec", "кредит", "creditDurationSec", "1800" },
            { "credit", "creditInterest", "кредит", "creditInterest", "0.20" },
            { "credit", "creditMax", "кредит", "creditMax", "2000" },
            { "credit", "creditMin", "кредит", "creditMin", "100" },
            { "credit", "creditPenaltyFactor", "кредит", "creditPenaltyFactor", "0.25" },
            { "credit", "creditWarnings", "кредит", "creditWarnings", "true" },
            { "flight", "flightDrain", "полёт", "flightDrain", "1.5" },
            { "flight", "flightGraceSec", "полёт", "flightGraceSec", "5" },
            { "flight", "flightHeightK", "полёт", "flightHeightK", "0.04" },
            { "flight", "flightSprintMult", "полёт", "flightSprintMult", "4.0" },
            { "flight", "heights", "полёт", "heights", "32,64,128" },
            { "flight", "speeds", "полёт", "speeds", "0.04,0.055,0.075" },
            { "hud", "hidePotionIcons", "интерфейс", "hidePotionIcons", "true" },
            { "invisibility", "decoyCost", "невидимость", "decoyCost", "40" },
            { "invisibility", "decoyMaxSec", "невидимость", "decoyMaxSec", "30" },
            { "invisibility", "invisCostPerSec", "невидимость", "invisCostPerSec", "0.4" },
            { "invisibility", "invisHitBreakTicks", "невидимость", "invisHitBreakTicks", "100" },
            { "invisibility", "invisNoticeRange", "невидимость", "invisNoticeRange", "3.0" },
            { "loot", "lootPity", "удача", "lootPity", "6" },
            { "loot", "stakes", "удача", "stakes", "50,150,400" },
            { "magnet", "magnetRadius1", "магнит", "magnetRadius1", "6.0" },
            { "magnet", "magnetRadius2", "магнит", "magnetRadius2", "12.0" },
            { "mana", "deathManaLoss", "мана", "deathManaLoss", "0.30" },
            { "mana", "hudBarScale", "мана", "hudBarScale", "100" },
            { "mana", "ingotValues", "мана", "ingotValues", "*" },
            { "mana", "manaPerIngot", "мана", "manaPerIngot", "10" },
            { "mana", "valueDiamond", "мана", "valueDiamond", "100" },
            { "mana", "valueEmerald", "мана", "valueEmerald", "80" },
            { "mana", "valueGold", "мана", "valueGold", "30" },
            { "mana", "valueIron", "мана", "valueIron", "10" },
            { "pocket", "entryCost", "карман", "entryCost", "10" },
            { "pocket", "entryRingStep", "карман", "entryRingStep", "0.2" },
            { "pocket", "expandBaseCost", "карман", "expandBaseCost", "4" },
            { "pocket", "expandRingStep", "карман", "expandRingStep", "0.2" },
            { "pocket", "mobResetRadius", "карман", "mobResetRadius", "40" },
            { "pocket", "pocketDiscountL2", "карман", "pocketDiscountL2", "0.25" },
            { "pocket", "wallBlock", "карман", "wallBlock", "minecraft:quartz_block" },
            { "shield", "shieldL2Factor", "щит", "shieldL2Factor", "0.7" },
            { "shield", "shieldManaPerDamage", "щит", "shieldManaPerDamage", "15" },
            { "slow", "slowDesaturate", "замедление", "slowDesaturate", "true" },
            { "slow", "slowDrainBase", "замедление", "slowDrainBase", "2.0" },
            { "slow", "slowVisual", "замедление", "slowVisual", "false" },
            { "slow", "slowZeroDrain", "замедление", "slowZeroDrain", "40.0" },
            { "softfall", "softFallDrain", "мягкое_падение", "softFallDrain", "3.0" },
            { "softfall", "softFallL2Factor", "мягкое_падение", "softFallL2Factor", "0.5" },
            { "softfall", "softFallMinDist", "мягкое_падение", "softFallMinDist", "4.0" },
            { "softfall", "softFallMinDistL2", "мягкое_падение", "softFallMinDistL2", "2.0" },
            { "softfall", "softFallSpeed", "мягкое_падение", "softFallSpeed", "0.45" },
            { "spector", "phaseBase", "спектор", "phaseBase", "20" },
            { "spector", "phasePerBlock", "спектор", "phasePerBlock", "0.5" },
            { "spector", "spectorBaseDrain", "спектор", "spectorBaseDrain", "0.6" },
            { "spector", "spectorPerBlock", "спектор", "spectorPerBlock", "0.03" },
            { "thermal", "thermalDrain1", "тепловизор", "thermalDrain1", "1.2" },
            { "thermal", "thermalDrain2", "тепловизор", "thermalDrain2", "0.8" },
            { "thermal", "thermalRadius1", "тепловизор", "radiusL1", "24" },
            { "thermal", "thermalRadius2", "тепловизор", "thermalRadius2", "40" },
            { "totems", "totemColors", "тотемы", "totemColors", "*" },
            { "totems", "totemCreateCost", "тотемы", "totemCreateCost", "30" },
            { "totems", "totemDeleteCost", "тотемы", "totemDeleteCost", "10" },
            { "totems", "totemDimensionCost", "тотемы", "totemDimensionCost", "100" },
            { "totems", "totemDiscount2", "тотемы", "totemDiscount2", "0.25" },
            { "totems", "totemDiscount3", "тотемы", "totemDiscount3", "0.5" },
            { "totems", "totemMarkerRange", "тотемы", "totemMarkerRange", "48" },
            { "totems", "totemSlots1", "тотемы", "totemSlots1", "4" },
            { "totems", "totemSlots2", "тотемы", "totemSlots2", "8" },
            { "totems", "totemSlots3", "тотемы", "totemSlots3", "16" },
            { "totems", "totemTeleportBase", "тотемы", "totemTeleportBase", "20" },
            { "totems", "totemTeleportPerBlock", "тотемы", "totemTeleportPerBlock", "0.02" },
            { "wave", "radius", "волна", "radius", "6,7,8,9,10" },
            { "wave", "damage", "волна", "damage", "2,3,4,6,8" },
            { "wave", "knockback", "волна", "knockback", "1.4,1.6,1.8,2.0,2.2" },
            { "wave", "slowSeconds", "волна", "slowSeconds", "3,5,7,9,6" },
            { "wave", "slowAmplifier", "волна", "slowAmplifier", "1,2,3,4,6" },
            { "wave", "nauseaSeconds", "волна", "nauseaSeconds", "3,4,5,6,6" },
            { "wave", "weaknessSeconds", "волна", "weaknessSeconds", "0,0,0,6,8" },
            { "wave", "manaCost", "волна", "manaCost", "25,30,35,45,60" },
    };

    public static void run(Configuration cfg) {
        Property pv = cfg.get(META, "configVersion", 0, "Служебное: версия формата конфига. Не менять.");
        int from = pv.getInt(0);
        if (from >= CURRENT) return;
        backup(cfg, from);
        if (from < 1) step1LegacyCategories(cfg);
        if (from < 2) step2ZeroPrices(cfg);
        pv.set(CURRENT);
        cfg.save();
        System.out.println("[PocketMod] config migrated: v" + from + " -> v" + CURRENT);
    }

    private static void backup(Configuration cfg, int from) {
        try {
            File f = cfg.getConfigFile();
            if (f != null && f.isFile()) {
                File bak = new File(f.getParentFile(), f.getName() + ".bak-v" + from);
                Files.copy(f.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            System.err.println("[PocketMod] config backup failed: " + t);
        }
    }

    /**
     * Шаг 1: до версии 1.9 категории назывались по-английски (mana, flight, wave...). Версия 1.9 перешла на русские
     * имена и потеряла настроенные значения, а старые категории остались в файле и мешали в экране настроек.
     * Переносим только то, что игрок менял (значение отличается от старого умолчания), и удаляем старые категории.
     */
    private static void step1LegacyCategories(Configuration cfg) {
        Set<String> oldCats = new LinkedHashSet<String>();
        for (String[] r : LEGACY) {
            oldCats.add(r[0]);
            if (!cfg.hasCategory(r[0])) continue;
            ConfigCategory oc = cfg.getCategory(r[0]);
            if (!oc.containsKey(r[1])) continue;
            Property old = oc.get(r[1]);
            if (isOldDefault(old, r[4])) continue;
            ConfigCategory nc = cfg.getCategory(r[2]);
            if (nc.containsKey(r[3])) continue;
            Property np = old.isList()
                    ? new Property(r[3], old.getStringList(), old.getType())
                    : new Property(r[3], old.getString(), old.getType());
            nc.put(r[3], np);
        }
        for (String c : oldCats) {
            if (cfg.hasCategory(c)) cfg.removeCategory(cfg.getCategory(c));
        }
    }

    /** Шаг 2: нули в ценах уровней, которых раньше не было (waveLevel2=0 и т.п.), заменяются ценой по умолчанию. */
    private static void step2ZeroPrices(Configuration cfg) {
        if (!cfg.hasCategory("prices")) return;
        ConfigCategory pc = cfg.getCategory("prices");
        for (AbilityType t : AbilityType.values()) {
            for (int i = 0; i < t.defaultCosts.length; i++) {
                String key = t.id + "Level" + (i + 1);
                if (!pc.containsKey(key)) continue;
                Property p = pc.get(key);
                if (p.isIntValue() && p.getInt() <= 0 && t.defaultCosts[i] > 0) {
                    p.set(t.defaultCosts[i]);
                }
            }
        }
    }

    private static boolean isOldDefault(Property old, String def) {
        if ("*".equals(def)) return false;
        String[] a = old.isList() ? old.getStringList() : new String[] { old.getString() };
        String[] b = def.split(",");
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!same(a[i], b[i])) return false;
        }
        return true;
    }

    private static boolean same(String x, String y) {
        x = x.trim();
        y = y.trim();
        try {
            return Math.abs(Double.parseDouble(x) - Double.parseDouble(y)) < 1e-9;
        } catch (NumberFormatException e) {
            return x.equalsIgnoreCase(y);
        }
    }
}
