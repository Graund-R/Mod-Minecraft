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
    public static String wallBlock = "minecraft:concrete";

    public static int teleportBaseRange = 50;
    public static int teleportRange2 = 100;
    public static int teleportRange3 = 200;
    public static float teleportCostPerBlock = 0.4f;
    public static int silenceTicks = 50;
    public static int silenceMinLevel = 2;

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

    public static float softFallDrain = 3.0f;
    public static float softFallL2Factor = 0.5f;
    public static float softFallMinDist = 4.0f;
    public static float softFallMinDistL2 = 2.0f;
    public static float softFallSpeed = 0.45f;

    public static float magnetRadius1 = 6.0f;
    public static float magnetRadius2 = 12.0f;

    public static float slowZeroDrain = 40.0f;
    public static boolean slowDesaturate = true;
    public static boolean slowVisual = false;
    public static boolean hidePotionIcons = true;
    public static boolean slowSound = false;

    public static float invisCostPerSec = 0.4f;
    public static int invisHitBreakTicks = 100;
    public static float decoyCost = 25f;
    public static int decoyMaxSec = 30;

    public static float thermalRadius1 = 28f;
    public static float thermalRadius2 = 40f;
    public static float thermalDrain1 = 1.5f;
    public static float thermalDrain2 = 1.0f;

    public static float shieldManaPerDamage = 15f;
    public static float shieldL2Factor = 0.7f;

    public static int[] flightHeight = { 128, 192, 256 };
    public static float[] flightSpeed = { 0.05f, 0.08f, 0.12f };
    public static float flightDrain = 2.0f;

    public static float phaseBase = 30f;
    public static float phasePerBlock = 0.3f;

    public static int heartsRent = 2;
    public static int[] lootStakes = { 50, 150, 400 };
    public static int lootPity = 6;

    public static Configuration config;

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        config = cfg;
        cfg.load();

        valueIron = cfg.getInt("valueIron", "mana", 10, 0, 1000000, "Mana for one iron ingot");
        valueGold = cfg.getInt("valueGold", "mana", 30, 0, 1000000, "Mana for one gold ingot");
        valueDiamond = cfg.getInt("valueDiamond", "mana", 100, 0, 1000000, "Mana for one diamond");
        valueEmerald = cfg.getInt("valueEmerald", "mana", 80, 0, 1000000, "Mana for one emerald");
        manaPerIngot = cfg.getInt("manaPerIngot", "mana", 10, 0, 1000000, "Fallback for other ingots");
        deathManaLoss = cfg.getFloat("deathManaLoss", "mana", 0.30f, 0f, 1f, "Mana lost on death");
        hudBarScale = cfg.getInt("hudBarScale", "mana", 100, 1, 1000000, "Minimum mana bar length");

        String[] overrides = cfg.getStringList("ingotValues", "mana",
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

        holdMs = cfg.getInt("holdMs", "controls", 400, 100, 2000, "Milliseconds for hold");

        creditMin = cfg.getInt("creditMin", "credit", 100, 1, 1000000, "Min credit");
        creditMax = cfg.getInt("creditMax", "credit", 2000, 1, 1000000, "Max credit");
        creditInterest = cfg.getFloat("creditInterest", "credit", 0.20f, 0f, 10f, "Interest");
        creditDurationSec = cfg.getInt("creditDurationSec", "credit", 1800, 60, 86400, "Duration seconds");
        creditPenaltyFactor = cfg.getFloat("creditPenaltyFactor", "credit", 0.25f, 0f, 10f, "Penalty factor");
        creditWarnings = cfg.getBoolean("creditWarnings", "credit", true, "Warn 5 and 1 minute before due");

        entryCost = cfg.getInt("entryCost", "pocket", 10, 0, 1000000, "Base entry cost");
        entryRingStep = cfg.getFloat("entryRingStep", "pocket", 0.2f, 0f, 100f, "Entry cost growth per ring");
        pocketDiscountL2 = cfg.getFloat("pocketDiscountL2", "pocket", 0.25f, 0f, 1f, "Entry discount at level 2");
        mobResetRadius = cfg.getInt("mobResetRadius", "pocket", 40, 0, 128, "Mob target reset radius");
        expandBaseCost = cfg.getInt("expandBaseCost", "pocket", 4, 1, 100000, "Base expand cost in iron units");
        expandRingStep = cfg.getFloat("expandRingStep", "pocket", 0.2f, 0f, 100f, "Ring cost growth");
        wallBlock = cfg.getString("wallBlock", "pocket", "minecraft:concrete", "Wall block");

        teleportBaseRange = cfg.getInt("teleportBaseRange", "abilities", 50, 5, 500, "Teleport range L1");
        teleportRange2 = cfg.getInt("teleportRange2", "abilities", 100, 5, 1000, "Teleport range L2");
        teleportRange3 = cfg.getInt("teleportRange3", "abilities", 200, 5, 2000, "Teleport range L3");
        teleportCostPerBlock = cfg.getFloat("teleportCostPerBlock", "abilities", 0.4f, 0f, 1000f, "Mana per block");
        silenceTicks = cfg.getInt("silenceTicks", "abilities", 50, 0, 600, "Silence after teleport (ticks)");
        silenceMinLevel = cfg.getInt("silenceMinLevel", "abilities", 2, 1, 3, "Min teleport level for silence");

        totemCreateCost = cfg.getInt("totemCreateCost", "totems", 30, 0, 1000000, "Create cost");
        totemDeleteCost = cfg.getInt("totemDeleteCost", "totems", 10, 0, 1000000, "Delete cost");
        totemTeleportBase = cfg.getInt("totemTeleportBase", "totems", 20, 0, 1000000, "Base teleport cost");
        totemTeleportPerBlock = cfg.getFloat("totemTeleportPerBlock", "totems", 0.02f, 0f, 1000f, "Per block");
        totemDimensionCost = cfg.getInt("totemDimensionCost", "totems", 100, 0, 1000000, "Dimension change cost");
        totemSlots1 = cfg.getInt("totemSlots1", "totems", 4, 1, 64, "Slots L1");
        totemSlots2 = cfg.getInt("totemSlots2", "totems", 8, 1, 64, "Slots L2");
        totemSlots3 = cfg.getInt("totemSlots3", "totems", 16, 1, 64, "Slots L3");
        totemDiscount2 = cfg.getFloat("totemDiscount2", "totems", 0.25f, 0f, 1f, "Discount L2");
        totemDiscount3 = cfg.getFloat("totemDiscount3", "totems", 0.5f, 0f, 1f, "Discount L3");
        totemMarkerRange = cfg.getInt("totemMarkerRange", "totems", 48, 8, 128, "Marker range");

        softFallDrain = cfg.getFloat("softFallDrain", "softfall", 3.0f, 0f, 1000f, "Mana/sec while active");
        softFallL2Factor = cfg.getFloat("softFallL2Factor", "softfall", 0.5f, 0.01f, 1f, "Cost factor L2");
        softFallMinDist = cfg.getFloat("softFallMinDist", "softfall", 4.0f, 1f, 100f, "Starts after this many blocks (L1)");
        softFallMinDistL2 = cfg.getFloat("softFallMinDistL2", "softfall", 2.0f, 0.5f, 100f, "Starts after this many blocks (L2)");
        softFallSpeed = cfg.getFloat("softFallSpeed", "softfall", 0.45f, 0.1f, 3f, "Max falling speed");

        magnetRadius1 = cfg.getFloat("magnetRadius1", "magnet", 6.0f, 1f, 64f, "Radius L1");
        magnetRadius2 = cfg.getFloat("magnetRadius2", "magnet", 12.0f, 1f, 64f, "Radius L2");

        slowZeroDrain = cfg.getFloat("slowZeroDrain", "slow", 40.0f, 0f, 100000f, "Mana/sec at full stop");
        slowVisual = cfg.getBoolean("slowVisual", "slow", false, "Vignette/desaturation");
        slowDesaturate = cfg.getBoolean("slowDesaturate", "slow", true, "Desaturate");
        hidePotionIcons = cfg.getBoolean("hidePotionIcons", "hud", true, "Hide potion icons");
        slowSound = cfg.getBoolean("slowSound", "abilities", false, "Lower sound pitch");

        invisCostPerSec = cfg.getFloat("invisCostPerSec", "invisibility", 0.4f, 0f, 10000f, "Mana/sec L1");
        invisHitBreakTicks = cfg.getInt("invisHitBreakTicks", "invisibility", 100, 0, 6000, "Break duration after hit");
        decoyCost = cfg.getFloat("decoyCost", "invisibility", 25f, 0f, 10000f, "Decoy create cost");
        decoyMaxSec = cfg.getInt("decoyMaxSec", "invisibility", 30, 1, 300, "Decoy max seconds");

        thermalRadius1 = cfg.getFloat("thermalRadius1", "thermal", 28f, 8f, 128f, "Radius L1");
        thermalRadius2 = cfg.getFloat("thermalRadius2", "thermal", 40f, 8f, 128f, "Radius L2");
        thermalDrain1 = cfg.getFloat("thermalDrain1", "thermal", 1.5f, 0f, 100f, "Drain L1");
        thermalDrain2 = cfg.getFloat("thermalDrain2", "thermal", 1.0f, 0f, 100f, "Drain L2");

        shieldManaPerDamage = cfg.getFloat("shieldManaPerDamage", "shield", 15f, 0.1f, 1000f, "Mana per damage");
        shieldL2Factor = cfg.getFloat("shieldL2Factor", "shield", 0.7f, 0.01f, 1f, "Cost factor L2");

        phaseBase = cfg.getFloat("phaseBase", "spector", 30f, 0f, 10000f, "Phase step base cost");
        phasePerBlock = cfg.getFloat("phasePerBlock", "spector", 0.3f, 0f, 100f, "Phase step per block");

        heartsRent = cfg.getInt("heartsRent", "body", 2, 1, 50, "Extra hearts while renting");
        int[] ls = cfg.get("loot", "stakes", new int[] { 50, 150, 400 }, "Spin prices").getIntList();
        if (ls.length >= 3) lootStakes = ls;
        lootPity = cfg.getInt("lootPity", "loot", 6, 1, 100, "Guaranteed win after N bad");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static int totemColor(int index) {
        return totemPalette[Math.abs(index) % totemPalette.length];
    }

    public static IBlockState wallState() {
        Block b = Block.getBlockFromName(wallBlock);
        if (b == null || b == Blocks.AIR) {
            b = Blocks.CONCRETE;
        }
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
