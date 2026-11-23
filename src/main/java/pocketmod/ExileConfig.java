package pocketmod;

import net.minecraftforge.common.config.Configuration;

/** Начальные параметры теста. Времена указаны в игровых секундах. */
public final class ExileConfig {
    public static int fullSeconds = 1200;
    public static int firstSeconds = 300;
    public static int fleeSeconds = 600;
    public static int deathSeconds = 900;
    public static int witherSeconds = 1020;
    public static int thirstSeconds = 480;
    public static int combatRelief = 240;
    public static int spawnInterval = 25;
    public static int recoveryDelay = 1800;
    public static float recoveryRate = 0.05f;
    public static int pocketSeconds = 900;
    public static int pocketCooldown = 1200;
    public static boolean visuals = true;
    public static int initialSlots = 18;
    public static int basePrice = 100;
    public static int compassPrice = 50;
    public static float discoveryChance = 0.08f;

    private ExileConfig() {}

    public static void load(Configuration cfg) {
        String c = "изгнанник";
        initialSlots =
                cfg.getInt("initialSlots", c, 18, 9, 36, "Initially accessible inventory slots");
        basePrice =
                cfg.getInt(
                        "basePrice",
                        c,
                        100,
                        1,
                        100000,
                        "Base cost of removing initial restrictions");
        compassPrice = cfg.getInt("compassPrice", c, 50, 1, 100000, "Trial compass price");
        discoveryChance =
                cfg.getFloat(
                        "discoveryChance",
                        c,
                        0.08f,
                        0,
                        1,
                        "Discovery chance from a threatening enemy");
        fullSeconds = cfg.getInt("fullSeconds", c, 1200, 60, 86400, "Time to full contamination");
        firstSeconds =
                cfg.getInt("firstSeconds", c, 300, 1, fullSeconds, "First symptoms and pursuers");
        fleeSeconds =
                cfg.getInt(
                        "fleeSeconds",
                        c,
                        600,
                        firstSeconds,
                        fullSeconds,
                        "Ordinary mobs try to escape");
        deathSeconds =
                cfg.getInt(
                        "deathSeconds",
                        c,
                        900,
                        fleeSeconds,
                        fullSeconds,
                        "Ordinary mobs die in contaminated chunks");
        witherSeconds =
                cfg.getInt(
                        "witherSeconds",
                        c,
                        1020,
                        deathSeconds,
                        fullSeconds,
                        "Wither skeleton stage");
        thirstSeconds =
                cfg.getInt(
                        "thirstSeconds",
                        c,
                        480,
                        60,
                        86400,
                        "Time without combat to severe bloodthirst");
        combatRelief =
                cfg.getInt(
                        "combatRelief",
                        c,
                        240,
                        1,
                        thirstSeconds,
                        "Maximum relief from a threatening fight");
        spawnInterval =
                cfg.getInt(
                        "spawnInterval",
                        c,
                        25,
                        5,
                        600,
                        "Seconds between attempts to spawn a pursuer");
        recoveryDelay =
                cfg.getInt(
                        "recoveryDelay",
                        c,
                        1800,
                        60,
                        86400,
                        "Absence before partial recovery starts");
        recoveryRate =
                cfg.getFloat(
                        "recoveryRate", c, 0.05f, 0, 1, "Exposure recovered per second of absence");
        pocketSeconds =
                cfg.getInt("pocketSeconds", c, 900, 30, 86400, "Pocket shelter time per player");
        pocketCooldown =
                cfg.getInt(
                        "pocketCooldown", c, 1200, 1, 86400, "Recovery time outside all pockets");
        visuals = cfg.getBoolean("visuals", c, true, "Enable contamination fog and tint");
    }
}
