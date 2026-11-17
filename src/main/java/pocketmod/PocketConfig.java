package pocketmod;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.config.Configuration;

public class PocketConfig {

    public static int valueIron = 10;
    public static int valueGold = 30;
    public static int valueDiamond = 100;
    public static int valueEmerald = 80;
    public static int manaPerIngot = 10;
    public static final Map<String, Integer> ingotValues = new HashMap<String, Integer>();
    public static float deathManaLoss = 0.30f;
    /** Минимальная длина шкалы маны (пока ничего не открыто). */
    public static int hudBarScale = 100;

    public static int holdMs = 400;

    public static int creditMin = 100;
    public static int creditMax = 2000;
    public static float creditInterest = 0.20f;
    public static int creditDurationSec = 1800;
    public static float creditPenaltyFactor = 0.25f;
    public static boolean creditWarnings = true;

    public static int entryCost = 10;
    public static float entryRingStep = 0.2f;
    public static float pocketDiscountL2 = 0.25f;
    public static int mobResetRadius = 40;
    public static int expandBaseCost = 4;
    public static float expandRingStep = 0.2f;
    public static String wallBlock = "minecraft:quartz_block";

    public static int teleportBaseRange = 50;
    public static int teleportRange2 = 100;
    public static int teleportRange3 = 200;
    public static float teleportCostPerBlock = 0.4f;

    // Тотемы
    public static int totemCreateCost = 30;
    public static int totemDeleteCost = 10;
    public static int totemTeleportBase = 20;
    public static float totemTeleportPerBlock = 0.02f;
    public static int totemDimensionCost = 100;
    public static int totemSlots1 = 4;
    public static int totemSlots2 = 8;
    public static int totemSlots3 = 16;
    public static float totemDiscount2 = 0.25f;
    public static float totemDiscount3 = 0.5f;
    public static int totemMarkerRange = 48;
    public static int[] totemPalette = { 0xFF6B6B, 0xFFD93D, 0x6BCB77, 0x4D96FF, 0xC77DFF, 0xFF9F45 };


    // Невидимость / приманка / тишина
    public static int decoyCost = 40;
    public static int decoyMaxSec = 30;
    public static int invisHitBreakTicks = 100;
    public static double invisNoticeRange = 3.0;
    public static int silenceMinLevel = 2;
    public static int silenceTicks = 50;

    // Спектор
    public static float spectorBaseDrain = 0.6f;
    public static float spectorPerBlock = 0.03f;
    public static float phaseBase = 20f;
    public static float phasePerBlock = 0.5f;

    // Тепловизор
    public static int thermalRadius1 = 64;
    public static int thermalRadius2 = 128;
    public static float thermalDrain1 = 1.2f;
    public static float thermalDrain2 = 0.8f;

    // Щит
    public static float shieldManaPerDamage = 15f;
    public static float shieldL2Factor = 0.7f;
    /** Расход щита (мана/сек) сразу после включения, даже если урона нет. */
    public static float shieldUpkeepBase = 3.0f;
    /** Прирост расхода щита за каждую секунду, пока он включён. */
    public static float shieldUpkeepRamp = 0.4f;
    /** Остывание щита: сколько секунд «нагрева» уходит за секунду, пока щит выключен. */
    public static float shieldHeatDecay = 0.5f;

    // Полёт
    public static float flightDrain = 1.5f;
    public static int[] flightHeight = { 32, 64, 128 };
    public static float[] flightSpeed = { 0.04f, 0.055f, 0.075f };
    public static int flightGraceSec = 5;
    /** Множитель расхода при ускорении (Ctrl). */
    public static float flightSprintMult = 4.0f;
    /** Прибавка к расходу за каждый блок высоты над точкой взлёта (0.04 = +4% за блок). */
    public static float flightHeightK = 0.04f;

    // Волна: параметры по уровням 1..5
    public static double[] waveRadius = { 6, 7, 8, 9, 10 };
    public static double[] waveDamage = { 2, 3, 4, 6, 8 };
    public static double[] waveKnock = { 1.4, 1.6, 1.8, 2.0, 2.2 };
    public static double[] waveSlowSec = { 3, 5, 7, 9, 6 };
    /** Амплитуда замедления (1 = Slowness II ... 6 = Slowness VII, полная остановка). */
    public static double[] waveSlowAmp = { 1, 2, 3, 4, 6 };
    public static double[] waveNauseaSec = { 3, 4, 5, 6, 6 };
    public static double[] waveWeakSec = { 0, 0, 0, 6, 8 };
    public static double[] waveCost = { 25, 30, 35, 45, 60 };
    /** Перезарядка волны по уровням, секунды (дольше, чем длится замедление). */
    public static double[] waveCooldown = { 10, 11, 12, 13, 15 };

    // Тело: сердца, скорость, сила, огнестойкость, дыхание
    public static int[] bodyPermCost = { 300, 500, 600, 150, 150, 1200 };
    public static double[] bodyRentPerSec = { 0.8, 1.0, 1.2, 0.3, 0.3, 2.5 };
    public static int heartsMaxBuys = 5;
    public static int heartsRent = 2;
    public static int regenMaxBuys = 3;
    public static int warehouseDeathCost = 100;
    public static float oreScannerDrain = 3.0f;
    public static int[] oreScannerRadius = { 16, 24, 32 };
    public static int oreScannerVertical = 12;
    public static String[] oreScannerTargets = { "oreDiamond", "oreEmerald", "oreGold", "oreIron", "oreRedstone", "oreLapis", "oreCoal", "oreQuartz" };
    public static float pocketTickCost = 0.0f;

    // Лутка
    public static int[] lootStakes = { 50, 150, 400 };
    public static int lootPity = 6;

    // Мягкое падение
    public static float softFallDrain = 3.0f;
    public static float softFallL2Factor = 0.5f;
    public static float softFallMinDist = 4.0f;
    public static float softFallMinDistL2 = 2.0f;
    public static float softFallSpeed = 0.45f;

    // Магнит
    public static float magnetRadius1 = 6.0f;
    public static float magnetRadius2 = 12.0f;

    // Замедление: полный стоп (ур. 3) и визуал
    public static float slowZeroDrain = 40.0f;
    public static boolean slowDesaturate = true;
    public static boolean slowVisual = false;

    public static boolean hidePotionIcons = true;

    /** Понижать тон звуков при замедлении (выключено, пока не выяснили причину вылета). */
    public static boolean slowSound = false;

    /** Живой объект конфига: его же редактирует игровой экран настроек. */
    public static Configuration config;

    public static float invisCostPerSec = 0.4f;
    public static float slowDrainBase = 2.0f;

    /** Перечитать значения после правки в игровом экране настроек. */
    public static void reload() {
        if (config != null) load(null);
    }

    public static void load(File file) {
        if (config == null) {
            config = new Configuration(file);
            config.load();
            ConfigMigrations.run(config);
        }
        Configuration cfg = config;

        valueIron = cfg.getInt("valueIron", "мана", 10, 0, 1000000, "Mana for one iron ingot");
        valueGold = cfg.getInt("valueGold", "мана", 30, 0, 1000000, "Mana for one gold ingot");
        valueDiamond = cfg.getInt("valueDiamond", "мана", 100, 0, 1000000, "Mana for one diamond");
        valueEmerald = cfg.getInt("valueEmerald", "мана", 80, 0, 1000000, "Mana for one emerald");
        manaPerIngot = cfg.getInt("manaPerIngot", "мана", 10, 0, 1000000, "Fallback for other ingots");
        deathManaLoss = cfg.getFloat("deathManaLoss", "мана", 0.30f, 0f, 1f, "Mana lost on death");
        hudBarScale = cfg.getInt("hudBarScale", "мана", 100, 1, 1000000, "Minimum mana bar length (grows with the most expensive unlocked ability)");

        String[] overrides = cfg.getStringList("ingotValues", "мана",
                new String[] { "ingotIron=10", "ingotGold=30" },
                "oreDictName=mana overrides");
        ingotValues.clear();
        for (String s : overrides) {
            String[] kv = s.split("=");
            if (kv.length == 2) {
                try {
                    ingotValues.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        holdMs = cfg.getInt("holdMs", "управление", 400, 100, 2000, "Milliseconds for hold");

        creditMin = cfg.getInt("creditMin", "кредит", 100, 1, 1000000, "Min credit");
        creditMax = cfg.getInt("creditMax", "кредит", 2000, 1, 1000000, "Max credit");
        creditInterest = cfg.getFloat("creditInterest", "кредит", 0.20f, 0f, 10f, "Interest");
        creditDurationSec = cfg.getInt("creditDurationSec", "кредит", 1800, 60, 86400, "Duration seconds (game time, pause not counted)");
        creditPenaltyFactor = cfg.getFloat("creditPenaltyFactor", "кредит", 0.25f, 0f, 10f, "Penalty factor");
        creditWarnings = cfg.getBoolean("creditWarnings", "кредит", true, "Warn 5 and 1 minute before the credit is due");

        entryCost = cfg.getInt("entryCost", "карман", 10, 0, 1000000, "Base entry cost");
        entryRingStep = cfg.getFloat("entryRingStep", "карман", 0.2f, 0f, 100f, "Entry cost growth per expanded ring");
        pocketDiscountL2 = cfg.getFloat("pocketDiscountL2", "карман", 0.25f, 0f, 1f, "Entry discount at pocket level 2");
        mobResetRadius = cfg.getInt("mobResetRadius", "карман", 40, 0, 128, "Mobs within this radius lose their target on enter/exit");
        expandBaseCost = cfg.getInt("expandBaseCost", "карман", 4, 1, 100000, "Base expand cost in iron units");
        expandRingStep = cfg.getFloat("expandRingStep", "карман", 0.2f, 0f, 100f, "Ring cost growth");
        wallBlock = cfg.getString("wallBlock", "карман", "minecraft:quartz_block", "Блок стен кармана (кварц; измерение всегда яркое)");
        pocketTickCost = cfg.getFloat("pocketTickCost", "карман", 0.0f, 0f, 100000f, "Мана в секунду за нахождение в кармане; 0 = бесплатно.");

        teleportBaseRange = cfg.getInt("teleportBaseRange", "способности", 50, 5, 500, "Teleport range, level 1");
        teleportRange2 = cfg.getInt("teleportRange2", "способности", 100, 5, 1000, "Teleport range, level 2");
        teleportRange3 = cfg.getInt("teleportRange3", "способности", 200, 5, 2000, "Teleport range, level 3");
        teleportCostPerBlock = cfg.getFloat("teleportCostPerBlock", "способности", 0.4f, 0f, 1000f, "Mana per block of teleport distance");

        totemCreateCost = cfg.getInt("totemCreateCost", "тотемы", 30, 0, 1000000, "Mana to create a totem");
        totemDeleteCost = cfg.getInt("totemDeleteCost", "тотемы", 10, 0, 1000000, "Mana to delete a totem");
        totemTeleportBase = cfg.getInt("totemTeleportBase", "тотемы", 20, 0, 1000000, "Base mana for a totem teleport");
        totemTeleportPerBlock = cfg.getFloat("totemTeleportPerBlock", "тотемы", 0.02f, 0f, 1000f, "Mana per block of distance (same dimension)");
        totemDimensionCost = cfg.getInt("totemDimensionCost", "тотемы", 100, 0, 1000000, "Extra mana when changing dimension");
        totemSlots1 = cfg.getInt("totemSlots1", "тотемы", 4, 1, 64, "Slots at totem level 1");
        totemSlots2 = cfg.getInt("totemSlots2", "тотемы", 8, 1, 64, "Slots at totem level 2");
        totemSlots3 = cfg.getInt("totemSlots3", "тотемы", 16, 1, 64, "Slots at totem level 3");
        totemDiscount2 = cfg.getFloat("totemDiscount2", "тотемы", 0.25f, 0f, 1f, "Teleport price discount at level 2");
        totemDiscount3 = cfg.getFloat("totemDiscount3", "тотемы", 0.5f, 0f, 1f, "Teleport price discount at level 3");
        totemMarkerRange = cfg.getInt("totemMarkerRange", "тотемы", 48, 8, 128, "Marker draw distance");
        String[] cols = cfg.getStringList("totemColors", "тотемы",
                new String[] { "FF6B6B", "FFD93D", "6BCB77", "4D96FF", "C77DFF", "FF9F45" },
                "Marker colors (RRGGBB), used in list order");
        int[] parsed = new int[cols.length];
        int n = 0;
        for (String c : cols) {
            try {
                parsed[n] = Integer.parseInt(c.trim().replace("#", ""), 16) & 0xFFFFFF;
                n++;
            } catch (NumberFormatException ignored) {
            }
        }
        if (n > 0) {
            totemPalette = java.util.Arrays.copyOf(parsed, n);
        }


        decoyCost = cfg.getInt("decoyCost", "невидимость", 40, 0, 1000000, "Mana to create the decoy clone");
        decoyMaxSec = cfg.getInt("decoyMaxSec", "невидимость", 30, 1, 600, "Decoy lifetime, seconds");
        invisHitBreakTicks = cfg.getInt("invisHitBreakTicks", "невидимость", 100, 0, 6000, "Invisibility is broken this many ticks after you hit something");
        invisNoticeRange = cfg.getFloat("invisNoticeRange", "невидимость", 3.0f, 0f, 32f, "Mobs still notice an invisible player this close");
        silenceMinLevel = cfg.getInt("silenceMinLevel", "способности", 2, 1, 3, "Teleport level that gives Silence");
        silenceTicks = cfg.getInt("silenceTicks", "способности", 50, 0, 1200, "Ticks mobs ignore you after teleport (Silence)");

        spectorBaseDrain = cfg.getFloat("spectorBaseDrain", "спектор", 0.6f, 0f, 1000f, "Mana/sec in spirit form");
        spectorPerBlock = cfg.getFloat("spectorPerBlock", "спектор", 0.03f, 0f, 1000f, "Extra mana/sec per block from body");
        phaseBase = cfg.getFloat("phaseBase", "спектор", 20f, 0f, 100000f, "Phase step base cost");
        phasePerBlock = cfg.getFloat("phasePerBlock", "спектор", 0.5f, 0f, 1000f, "Phase step cost per block");

        thermalRadius1 = cfg.getInt("radiusL1", "тепловизор", 64, 8, 256, "Радиус тепловизора, уровень 1");
        thermalRadius2 = cfg.getInt("thermalRadius2", "тепловизор", 128, 8, 256, "Радиус тепловизора, уровень 2");
        thermalDrain1 = cfg.getFloat("thermalDrain1", "тепловизор", 1.2f, 0f, 1000f, "Мана/сек уровень 1");
        thermalDrain2 = cfg.getFloat("thermalDrain2", "тепловизор", 0.8f, 0f, 1000f, "Мана/сек уровень 2");

        shieldManaPerDamage = cfg.getFloat("shieldManaPerDamage", "щит", 15f, 0f, 10000f, "Mana per 1 damage");
        shieldL2Factor = cfg.getFloat("shieldL2Factor", "щит", 0.7f, 0.01f, 1f, "Cost factor at shield level 2");
        shieldUpkeepBase = cfg.getFloat("shieldUpkeepBase", "щит", 3.0f, 0f, 10000f, "Mana/sec the active shield costs right after switching on, even without damage");
        shieldUpkeepRamp = cfg.getFloat("shieldUpkeepRamp", "щит", 0.4f, 0f, 10000f, "Extra mana/sec for every second the shield stays on");
        shieldHeatDecay = cfg.getFloat("shieldHeatDecay", "щит", 0.5f, 0f, 1000f, "Seconds of shield heat that cool off per second while it is off");

        flightDrain = cfg.getFloat("flightDrain", "полёт", 1.5f, 0f, 1000f, "Mana/sec while flying");
        flightGraceSec = cfg.getInt("flightGraceSec", "полёт", 5, 0, 60, "Soft hover seconds at zero mana");
        flightSprintMult = cfg.getFloat("flightSprintMult", "полёт", 4.0f, 1f, 100f, "Drain multiplier while sprint-flying (Ctrl)");
        flightHeightK = cfg.getFloat("flightHeightK", "полёт", 0.04f, 0f, 10f, "Extra drain per block above the take-off point (0.04 = +4% per block)");
        int[] fh = cfg.get("полёт", "heights", new int[] { 32, 64, 128 }, "Max height above ground by level").getIntList();
        if (fh.length >= 3) flightHeight = fh;
        double[] fs = cfg.get("полёт", "speeds", new double[] { 0.04, 0.055, 0.075 }, "Fly speed by level (vanilla creative 0.05)").getDoubleList();
        if (fs.length >= 3) flightSpeed = new float[] { (float) fs[0], (float) fs[1], (float) fs[2] };

        waveRadius = arr(cfg, "radius", "волна", waveRadius, "Радиус волны по уровням 1..5");
        waveDamage = arr(cfg, "damage", "волна", waveDamage, "Урон волны по уровням");
        waveKnock = arr(cfg, "knockback", "волна", waveKnock, "Откид волны по уровням");
        waveSlowSec = arr(cfg, "slowSeconds", "волна", waveSlowSec, "Длительность замедления (сек) по уровням");
        waveSlowAmp = arr(cfg, "slowAmplifier", "волна", waveSlowAmp, "Сила замедления по уровням (1=II, 6=полная остановка)");
        waveNauseaSec = arr(cfg, "nauseaSeconds", "волна", waveNauseaSec, "Длительность тошноты (сек), 0 = нет");
        waveWeakSec = arr(cfg, "weaknessSeconds", "волна", waveWeakSec, "Длительность слабости (сек), 0 = нет");
        waveCost = arr(cfg, "manaCost", "волна", waveCost, "Мана за волну по уровням");
        waveCooldown = arr(cfg, "cooldownSeconds", "волна", waveCooldown, "Wave cooldown in seconds by level 1..5");

        int[] bp = cfg.get("тело", "permCosts", new int[] { 300, 500, 600, 150, 150, 1200 }, "Permanent price: hearts, speed, strength, fire resistance, water breathing, regeneration").getIntList();
        if (bp.length >= 6) bodyPermCost = bp;
        else if (bp.length >= 5) { bodyPermCost = java.util.Arrays.copyOf(bp, 6); bodyPermCost[5] = 1200; cfg.get("тело", "permCosts", bodyPermCost).set(bodyPermCost); }
        double[] br = cfg.get("тело", "rentPerSec", new double[] { 0.8, 1.0, 1.2, 0.3, 0.3, 2.5 }, "Rent mana/sec in the same order").getDoubleList();
        if (br.length >= 6) bodyRentPerSec = br;
        else if (br.length >= 5) { bodyRentPerSec = java.util.Arrays.copyOf(br, 6); bodyRentPerSec[5] = 2.5; cfg.get("тело", "rentPerSec", bodyRentPerSec).set(bodyRentPerSec); }
        heartsMaxBuys = cfg.getInt("heartsMaxBuys", "тело", 5, 1, 50, "Max permanent heart purchases (+1 heart each)");
        heartsRent = cfg.getInt("heartsRent", "тело", 2, 1, 50, "Extra hearts while renting");
        regenMaxBuys = cfg.getInt("regenMaxBuys", "тело", 3, 1, 3, "Maximum permanent regeneration levels");
        warehouseDeathCost = cfg.getInt("warehouseDeathCost", "склад", 100, 0, 1000000, "Mana per occupied warehouse slot on death.");
        oreScannerDrain = cfg.getFloat("oreScannerDrain", "сканер", 3.0f, 0f, 10000f, "Mana per second while scanning.");
        oreScannerVertical = cfg.getInt("oreScannerVertical", "сканер", 12, 4, 64, "Vertical scan distance.");
        int[] osr = cfg.get("сканер", "radii", new int[] { 16, 24, 32 }, "Ore scan radius by level.").getIntList();
        if (osr.length >= 3) oreScannerRadius = osr;
        oreScannerTargets = cfg.getStringList("targets", "сканер",
                new String[] { "oreDiamond", "oreEmerald", "oreGold", "oreIron", "oreRedstone", "oreLapis", "oreCoal", "oreQuartz" },
                "OreDictionary names (oreDiamond) or registry IDs (minecraft:diamond_ore) to highlight.");

        int[] ls = cfg.get("удача", "stakes", new int[] { 50, 150, 400 }, "Spin prices (3 tiers)").getIntList();
        if (ls.length >= 3) lootStakes = ls;
        lootPity = cfg.getInt("lootPity", "удача", 6, 1, 100, "Guaranteed win after this many bad results");

        softFallDrain = cfg.getFloat("softFallDrain", "мягкое_падение", 3.0f, 0f, 1000f, "Mana/sec while the soft fall is active");
        softFallL2Factor = cfg.getFloat("softFallL2Factor", "мягкое_падение", 0.5f, 0.01f, 1f, "Cost factor at soft fall level 2");
        softFallMinDist = cfg.getFloat("softFallMinDist", "мягкое_падение", 4.0f, 1f, 100f, "Soft fall starts after falling this many blocks (level 1)");
        softFallMinDistL2 = cfg.getFloat("softFallMinDistL2", "мягкое_падение", 2.0f, 0.5f, 100f, "Soft fall starts after falling this many blocks (level 2)");
        softFallSpeed = cfg.getFloat("softFallSpeed", "мягкое_падение", 0.45f, 0.1f, 3f, "Max falling speed (blocks/tick) while soft fall is active");

        magnetRadius1 = cfg.getFloat("magnetRadius1", "магнит", 6.0f, 1f, 64f, "Item magnet radius at level 1");
        magnetRadius2 = cfg.getFloat("magnetRadius2", "магнит", 12.0f, 1f, 64f, "Item magnet radius at level 2");

        slowZeroDrain = cfg.getFloat("slowZeroDrain", "замедление", 40.0f, 0f, 100000f, "Mana/sec at full stop (x0, slow level 3)");
        slowVisual = cfg.getBoolean("slowVisual", "замедление", false, "Draw the vignette/desaturation effect while time is slowed (off by default)");
        slowDesaturate = cfg.getBoolean("slowDesaturate", "замедление", true, "Desaturate the picture while time is slowed (vanilla post shader)");

        hidePotionIcons = cfg.getBoolean("hidePotionIcons", "интерфейс", true, "Hide vanilla potion icons (only the mana bar on screen)");

        slowSound = cfg.getBoolean("slowSound", "способности", false, "Lower the pitch of sounds while time is slowed");

        invisCostPerSec = cfg.getFloat("invisCostPerSec", "невидимость", 0.4f, 0f, 10000f, "Mana per second while invisible (level 1; level 2 x0.75, level 3 x0.5)");
        slowDrainBase = cfg.getFloat("slowDrainBase", "замедление", 2.0f, 0f, 10000f, "Slow time cost: mana/sec = base / factor (factor 0.25 -> base*4)");

        for (AbilityType t : AbilityType.values()) {
            for (int i = 0; i < t.levelCosts.length; i++) {
                String key = t.id + "Level" + (i + 1);
                int def = t.defaultCosts[i];
                int v = cfg.getInt(key, "prices", def, 0, 100000000,
                        "Цена " + t.id + ", уровень " + (i + 1));
                // Старые конфиги писали 0 в несуществующие слоты (waveLevel2=0).
                // 0 при ненулевом дефолте — не «нет уровня», а мусор: вернуть цену.
                if (v <= 0 && def > 0) {
                    v = def;
                    cfg.get("prices", key, def).set(def);
                }
                t.levelCosts[i] = v;
            }
        }

        localize(cfg);

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    /** Русские названия/описания для экрана настроек (ключи перевода + комментарии). */
    private static void localize(Configuration cfg) {
        for (String cat : cfg.getCategoryNames()) {
            net.minecraftforge.common.config.ConfigCategory cc = cfg.getCategory(cat);
            String ck = ConfigLang.catLangKey(cat);
            if (ck != null) cc.setLanguageKey(ck);
            for (net.minecraftforge.common.config.Property p : cc.getOrderedValues()) {
                String[] row = ConfigLang.row(cat, p.getName());
                if (row == null) continue;
                String lk = ConfigLang.langKey(cat, p.getName());
                if (lk != null) p.setLanguageKey(lk);
                String old = p.getComment();
                int i = old == null ? -1 : old.lastIndexOf(" [range");
                p.setComment(row[1] + (i >= 0 ? old.substring(i) : ""));
            }
        }
    }

    private static double[] arr(Configuration cfg, String name, String cat, double[] def, String comment) {
        double[] v = cfg.get(cat, name, def, comment).getDoubleList();
        return v.length >= def.length ? v : def;
    }

    public static int totemColor(int index) {
        return totemPalette[Math.abs(index) % totemPalette.length];
    }

    public static IBlockState wallState() {
        Block b = Block.getBlockFromName(wallBlock);
        if (b == null || b == Blocks.AIR) {
            b = Blocks.QUARTZ_BLOCK;
        }
        // white concrete meta 0 if someone still sets concrete
        if (b == Blocks.CONCRETE) {
            return b.getStateFromMeta(0);
        }
        return b.getDefaultState();
    }

    public static int expandCost(int ring) {
        double c = expandBaseCost * (1.0 + expandRingStep * (Math.max(1, ring) - 1));
        return Math.max(1, (int) Math.round(c));
    }
}
