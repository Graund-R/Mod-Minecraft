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
    public static String wallBlock = "minecraft:concrete";

    public static int teleportBaseRange = 50;
    public static int teleportRange2 = 100;
    public static int teleportRange3 = 200;
    public static float teleportCostPerBlock = 0.4f;

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        cfg.load();

        valueIron = cfg.getInt("valueIron", "mana", 10, 0, 1000000, "Mana for one iron ingot");
        valueGold = cfg.getInt("valueGold", "mana", 30, 0, 1000000, "Mana for one gold ingot");
        valueDiamond = cfg.getInt("valueDiamond", "mana", 100, 0, 1000000, "Mana for one diamond");
        valueEmerald = cfg.getInt("valueEmerald", "mana", 80, 0, 1000000, "Mana for one emerald");
        manaPerIngot = cfg.getInt("manaPerIngot", "mana", 10, 0, 1000000, "Fallback for other ingots");
        deathManaLoss = cfg.getFloat("deathManaLoss", "mana", 0.30f, 0f, 1f, "Mana lost on death");
        hudBarScale = cfg.getInt("hudBarScale", "mana", 100, 1, 1000000, "Minimum mana bar length (grows with the most expensive unlocked ability)");

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
        creditDurationSec = cfg.getInt("creditDurationSec", "credit", 1800, 60, 86400, "Duration seconds (game time, pause not counted)");
        creditPenaltyFactor = cfg.getFloat("creditPenaltyFactor", "credit", 0.25f, 0f, 10f, "Penalty factor");
        creditWarnings = cfg.getBoolean("creditWarnings", "credit", true, "Warn 5 and 1 minute before the credit is due");

        entryCost = cfg.getInt("entryCost", "pocket", 10, 0, 1000000, "Base entry cost");
        entryRingStep = cfg.getFloat("entryRingStep", "pocket", 0.2f, 0f, 100f, "Entry cost growth per expanded ring");
        pocketDiscountL2 = cfg.getFloat("pocketDiscountL2", "pocket", 0.25f, 0f, 1f, "Entry discount at pocket level 2");
        mobResetRadius = cfg.getInt("mobResetRadius", "pocket", 40, 0, 128, "Mobs within this radius lose their target on enter/exit");
        expandBaseCost = cfg.getInt("expandBaseCost", "pocket", 4, 1, 100000, "Base expand cost in iron units");
        expandRingStep = cfg.getFloat("expandRingStep", "pocket", 0.2f, 0f, 100f, "Ring cost growth");
        wallBlock = cfg.getString("wallBlock", "pocket", "minecraft:concrete", "Wall block (white concrete = marble look)");

        teleportBaseRange = cfg.getInt("teleportBaseRange", "abilities", 50, 5, 500, "Teleport range, level 1");
        teleportRange2 = cfg.getInt("teleportRange2", "abilities", 100, 5, 1000, "Teleport range, level 2");
        teleportRange3 = cfg.getInt("teleportRange3", "abilities", 200, 5, 2000, "Teleport range, level 3");
        teleportCostPerBlock = cfg.getFloat("teleportCostPerBlock", "abilities", 0.4f, 0f, 1000f, "Mana per block of teleport distance");

        if (cfg.hasChanged()) {
            cfg.save();
        }
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
