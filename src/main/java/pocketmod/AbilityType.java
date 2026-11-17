package pocketmod;

/** Цены способностей. maxLevel = число цен. */
public enum AbilityType {
    POCKET("pocket", 250, 600),
    TELEPORT("teleport", 150, 400, 900),
    INVISIBILITY("invis", 100, 300, 700),
    SLOW("slow", 200, 500, 1000),
    DECOY("decoy", 250),
    SPECTOR("spector", 300),
    THERMAL("thermal", 180, 450, 800),
    SHIELD("shield", 220, 550),
    FLIGHT("flight", 400, 800, 1500),
    TOTEM("totem", 150, 350, 700),
    SOFTFALL("softfall", 200, 450),
    BLOODSUCKER("bloodsucker", 350, 550, 800),
    MAGNET("magnet", 200, 500),
    WAVE("wave", 280, 450, 700, 1000, 1500),
    ORE_SCANNER("scanner", 250, 500, 900),
    WAREHOUSE("warehouse", 500, 900, 1500);

    public final String id;
    public final int[] levelCosts;
    public final int[] defaultCosts;
    public final int maxLevel;

    AbilityType(String id, int... costs) {
        this.id = id;
        this.maxLevel = costs.length;
        this.levelCosts = costs.clone();
        this.defaultCosts = costs.clone();
    }

    public String titleKey() {
        return "pocketmod.ability." + id;
    }

    /** Цена перехода с currentLevel на следующий. -1 если MAX. */
    public int costForNextLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= levelCosts.length) return -1;
        int c = levelCosts[currentLevel];
        // 0 в конфиге (остаток от старых версий) не должен закрывать существующий уровень
        if (c <= 0) c = defaultCosts[currentLevel];
        return c > 0 ? c : -1;
    }
}
