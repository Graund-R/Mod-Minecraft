package pocketmod;

import net.minecraftforge.common.config.Configuration;

/** Начальные параметры теста. Времена указаны в игровых секундах. */
public final class ExileConfig {
    public static int fullSeconds = 600;
    public static int firstSeconds = 120;
    public static int fleeSeconds = 300;
    public static int deathSeconds = 450;
    public static int witherSeconds = 480;
    public static int thirstSeconds = 480;
    public static int combatRelief = 240;
    public static float frenzySpeed = 0.20f;
    public static float frenzyStrength = 0.25f;
    public static float thirstExhaustion = 0.10f;
    public static boolean heartbeat = true;
    public static int spawnInterval = 8;
    public static int recoveryDelay = 1800;
    public static float recoveryRate = 0.05f;
    public static int pocketSeconds = 900;
    public static int pocketCooldown = 1200;
    public static boolean visuals = true;
    public static int blackeningMinutes=5;
    public static int infectionDamageAfter=1200;
    public static int infectionDamageInterval=2;
    public static float infectionDamage=1;
    public static int bedMaxContamination=50,bedMaxThirst=75;
    public static float bedHealing=4;
    public static boolean infectionSounds=true;
    public static boolean infectionSymptoms=true;
    public static int initialSlots = 18;
    public static int basePrice = 100;
    public static int compassPrice = 50;
    public static float discoveryChance = 0.08f;

    public static boolean cyclicThirst=true;
    public static float cyclicBaseRate=10,cyclicHabitMax=6,cyclicHabitRate=.15f,cyclicKillBase=3;
    public static int cyclicHabitSeconds=1800,cyclicBossCalm=180;
    public static float cyclicBossStep=.05f,cyclicBossCap=.5f,cyclicCalmRate=.25f;
    public static int cyclicCalmMin=45,cyclicCalmMax=75,cyclicCalmLow=15,cyclicCalmHigh=40;
    public static float cyclicRateRandom=.15f,cyclicReliefRandom=.1f;
    public static int cyclicRandomMin=60,cyclicRandomMax=120,cyclicRandomBlend=10;
    public static boolean simpleThirst = true;
    public static int simpleThirstLimit = 10;
    public static int simpleThirstSeconds = 60;
    public static int simpleAccelSeconds = 120;
    public static float simpleAccelStep = 0.25f;
    public static int simpleAccelMax = 4;
    public static float simpleNightMultiplier = 2f;

    public static boolean debugTools = true;
    public static boolean debugHud = true, debugBorders = true;
    public static int debugAreaRadius = 1;
    public static int respawnStage1 = 15, respawnStage2 = 10, respawnLate = 5;
    public static int spawnBatch = 3;
    public static int pursuerCap = 24;
    public static boolean trialGeneration = true;
    public static int firstTrialMin = 1000, firstTrialMax = 1500;
    public static int nextTrialMin = 2000, nextTrialMax = 3000;
    public static String[] trialGroundBlocks = new String[0];
    public static String[] trialStructureData = {"Village", "Temple", "Mansion", "Monument", "Stronghold", "Mineshaft"};

    private ExileConfig() {}

    public static void load(Configuration cfg) {
        String c = "изгнанник";
        cyclicThirst=cfg.getBoolean("cyclicThirst",c,true,"Use cyclic bloodthirst; legacy test models remain available in F8");
        cyclicBaseRate=cfg.getFloat("cyclicBaseRate",c,10f,0.1f,100f,"Base thirst points per active minute");
        cyclicHabitSeconds=cfg.getInt("cyclicHabitSeconds",c,1800,60,86400,"Active seconds per habit level");
        cyclicHabitMax=cfg.getFloat("cyclicHabitMax",c,6f,0f,50f,"Maximum habit level");
        cyclicHabitRate=cfg.getFloat("cyclicHabitRate",c,0.15f,0f,2f,"Additional rate per habit level");
        cyclicKillBase=cfg.getFloat("cyclicKillBase",c,3f,1f,100f,"Ordinary relief is 100 divided by this value plus habit");
        cyclicBossStep=cfg.getFloat("cyclicBossStep",c,0.05f,0f,1f,"Base-rate bonus per distinct boss victory");
        cyclicBossCap=cfg.getFloat("cyclicBossCap",c,0.5f,0f,5f,"Maximum bonus from distinct bosses");
        cyclicBossCalm=cfg.getInt("cyclicBossCalm",c,180,0,3600,"Calm after a first boss victory, active seconds");
        cyclicCalmMin=cfg.getInt("cyclicCalmMin",c,45,0,600,"Minimum ordinary calm duration, active seconds");
        cyclicCalmMax=cfg.getInt("cyclicCalmMax",c,75,0,600,"Maximum ordinary calm duration, active seconds");
        cyclicCalmRate=cfg.getFloat("cyclicCalmRate",c,0.25f,0f,1f,"Fill rate during calm");
        cyclicCalmLow=cfg.getInt("cyclicCalmLow",c,15,0,100,"Relief must lower thirst below this value to start calm");
        cyclicCalmHigh=cfg.getInt("cyclicCalmHigh",c,40,1,100,"Thirst must reach this value before ordinary calm can trigger");
        cyclicRateRandom=cfg.getFloat("cyclicRateRandom",c,0.15f,0f,0.5f,"Bounded fill-rate randomness around one");
        cyclicReliefRandom=cfg.getFloat("cyclicReliefRandom",c,0.1f,0f,0.5f,"Bounded relief randomness around one");
        cyclicRandomMin=cfg.getInt("cyclicRandomMin",c,60,10,3600,"Minimum seconds between fill-rate targets");
        cyclicRandomMax=cfg.getInt("cyclicRandomMax",c,120,10,3600,"Maximum seconds between fill-rate targets");
        cyclicRandomBlend=cfg.getInt("cyclicRandomBlend",c,10,1,120,"Seconds to blend between fill-rate targets");
        cyclicCalmMax=Math.max(cyclicCalmMin,cyclicCalmMax);
        cyclicCalmLow=Math.min(99,cyclicCalmLow);
        cyclicCalmHigh=Math.max(cyclicCalmLow+1,cyclicCalmHigh);
        cyclicRandomMax=Math.max(cyclicRandomMin,cyclicRandomMax);
        simpleThirst = cfg.getBoolean("simpleThirst", c, true, "Use predictable test thirst; false preserves the original context model");
        simpleThirstLimit = cfg.getInt("simpleThirstLimit", c, 10, 2, 100, "Simple thirst points to maximum");
        simpleThirstSeconds = cfg.getInt("simpleThirstSeconds", c, 60, 5, 3600, "Base active seconds per point");
        simpleAccelSeconds = cfg.getInt("simpleAccelSeconds", c, 120, 10, 3600, "Active seconds without a kill per acceleration step");
        simpleAccelStep = cfg.getFloat("simpleAccelStep", c, 0.25f, 0, 2, "Extra rate per acceleration step");
        simpleAccelMax = cfg.getInt("simpleAccelMax", c, 4, 0, 20, "Maximum acceleration steps");
        simpleNightMultiplier = cfg.getFloat("simpleNightMultiplier", c, 2f, 1, 10, "Additional nighttime rate multiplier");
        debugTools = cfg.getBoolean("debugTools", c, true, "Enable temporary Exile testing tools");
        debugHud = cfg.getBoolean("debugHud", c, true, "Show the test HUD while the tool is enabled");
        debugBorders = cfg.getBoolean("debugBorders", c, true, "Default visibility of infected chunk boundaries");
        debugAreaRadius = cfg.getInt("debugAreaRadius", c, 1, 0, 4, "Default edit radius in chunks: 0=1x1, 1=3x3, 4=9x9");
        respawnStage1 = cfg.getInt("respawnStage1", c, 15, 1, 600, "Replacement delay after death at infection stage I, seconds");
        respawnStage2 = cfg.getInt("respawnStage2", c, 10, 1, 600, "Replacement delay after death at infection stage II, seconds");
        respawnLate = cfg.getInt("respawnLate", c, 5, 1, 600, "Replacement delay after death at infection stages III-IV, seconds");
        spawnBatch = cfg.getInt("spawnBatch", c, 3, 1, 8, "Pursuers added per reinforcement wave");
        pursuerCap = cfg.getInt("pursuerCap", c, 24, 4, 64, "Maximum nearby pursuers at full contamination");
        trialGeneration = cfg.getBoolean("trialGeneration", c, true, "Place trials in loaded populated chunks");
        firstTrialMin = cfg.getInt("firstTrialMin", c, 1000, 128, 100000, "Minimum distance of first trial from world spawn");
        firstTrialMax = cfg.getInt("firstTrialMax", c, 1500, firstTrialMin + 64, 200000, "Maximum first trial distance");
        nextTrialMin = cfg.getInt("nextTrialMin", c, 2000, 128, 100000, "Minimum distance between consecutive trials");
        nextTrialMax = cfg.getInt("nextTrialMax", c, 3000, nextTrialMin + 64, 200000, "Maximum distance between consecutive trials");
        trialGroundBlocks = cfg.getStringList("trialGroundBlocks", c, new String[0], "Additional natural ground registry names for modded terrain");
        trialStructureData = cfg.getStringList("trialStructureData", c, trialStructureData, "Saved structure data names excluded from trial placement");
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
        fullSeconds = cfg.getInt("fullSeconds", c, 600, 60, 86400, "Time to full contamination");
        firstSeconds =
                cfg.getInt("firstSeconds", c, 120, 1, fullSeconds, "First symptoms and pursuers");
        fleeSeconds =
                cfg.getInt(
                        "fleeSeconds",
                        c,
                        300,
                        firstSeconds,
                        fullSeconds,
                        "Ordinary mobs try to escape");
        deathSeconds =
                cfg.getInt(
                        "deathSeconds",
                        c,
                        450,
                        fleeSeconds,
                        fullSeconds,
                        "Ordinary mobs die in contaminated chunks");
        witherSeconds =
                cfg.getInt(
                        "witherSeconds",
                        c,
                        480,
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
                        8,
                        2,
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
        frenzySpeed = cfg.getFloat("frenzySpeed", c, 0.20f, 0, 2, "Movement speed bonus at maximum bloodthirst");
        frenzyStrength = cfg.getFloat("frenzyStrength", c, 0.25f, 0, 3, "Attack damage bonus at maximum bloodthirst");
        thirstExhaustion = cfg.getFloat("thirstExhaustion", c, 0.10f, 0, 4, "Additional exhaustion per second at maximum bloodthirst");
        heartbeat = cfg.getBoolean("heartbeat", c, true, "Enable local bloodthirst heartbeat");
        bedMaxContamination=cfg.getInt("bedMaxContamination",c,50,1,100,"Sleep requires local contamination below this percent");
        bedMaxThirst=cfg.getInt("bedMaxThirst",c,75,1,100,"Sleep requires bloodthirst below this percent");
        bedHealing=cfg.getFloat("bedHealing",c,4,0,20,"Maximum health restored by completed sleep; one food point per health point, keeping six food points in reserve");
        infectionDamageAfter=cfg.getInt("infectionDamageAfter",c,1200,60,86400,"Local infection age before terrain damage, seconds since initial exposure; independent of depth");
        infectionDamageInterval=cfg.getInt("infectionDamageInterval",c,2,1,60,"Seconds between mature infection damage pulses");
        infectionDamage=cfg.getFloat("infectionDamage",c,1,0,20,"Damage per mature infection pulse; 1 means half a heart, 0 disables damage");
        blackeningMinutes=cfg.getInt("blackeningMinutes",c,5,1,30,"Minutes after full infection to black terrain, continuous visual darkening; also controls spread acceleration");
        infectionSounds=cfg.getBoolean("infectionSounds",c,true,"Enable local infection wind, whispers and episode sounds");
        infectionSymptoms=cfg.getBoolean("infectionSymptoms",c,true,"Brief infection debuffs above 75 percent; rare 2-3 second blindness above 95 percent");
        visuals = cfg.getBoolean("visuals", c, true, "Enable contamination and bloodthirst screen effects");
    }
}
