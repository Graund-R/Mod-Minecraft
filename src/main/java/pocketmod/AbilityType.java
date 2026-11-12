package pocketmod;

/** Цены по ТЗ: карман 250/600, телепорт 150/400/900, невидимость 100/300/700,
 *  замедление 200/500/1000, тотемы 150/350/700. Остальные — временные. */
public enum AbilityType {
    POCKET("pocket", 250, 600, 0, 2),
    TELEPORT("teleport", 150, 400, 900, 3),
    INVISIBILITY("invis", 100, 300, 700, 3),
    SLOW("slow", 200, 500, 1000, 3),
    DECOY("decoy", 250, 0, 0, 1),
    SPECTOR("spector", 300, 0, 0, 1),
    THERMAL("thermal", 180, 450, 0, 2),
    SHIELD("shield", 220, 550, 0, 2),
    FLIGHT("flight", 400, 0, 0, 1),
    TOTEM("totem", 150, 350, 700, 3);

    public final String id;
    public final int[] levelCosts;
    public final int maxLevel;

    AbilityType(String id, int c1, int c2, int c3, int maxLevel) {
        this.id = id;
        this.maxLevel = maxLevel;
        this.levelCosts = new int[] { c1, c2, c3 };
    }

    public String titleKey() {
        return "pocketmod.ability." + id;
    }

    public int costForNextLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= maxLevel) return -1;
        if (currentLevel >= levelCosts.length) return -1;
        int c = levelCosts[currentLevel];
        return c > 0 ? c : -1;
    }
}
