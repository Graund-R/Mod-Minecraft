package pocketmod;

public enum AbilityType {
    TELEPORT("teleport", "Телепорт", 150, 400, 900, 3),
    INVISIBILITY("invis", "Невидимость", 120, 350, 800, 3),
    SLOW("slow", "Замедление времени", 200, 500, 1100, 3),
    DECOY("decoy", "Приманка (клон)", 250, 0, 0, 1),
    SPECTOR("spector", "Спектор", 300, 0, 0, 1),
    THERMAL("thermal", "Тепловизор", 180, 450, 0, 2),
    SHIELD("shield", "Щит", 220, 550, 0, 2),
    FLIGHT("flight", "Полёт", 400, 0, 0, 1),
    TOTEM("totem", "Тотемы", 200, 500, 1000, 3);

    public final String id;
    public final String title;
    public final int[] levelCosts;
    public final int maxLevel;

    AbilityType(String id, String title, int c1, int c2, int c3, int maxLevel) {
        this.id = id;
        this.title = title;
        this.maxLevel = maxLevel;
        this.levelCosts = new int[] { c1, c2, c3 };
    }

    public int costForNextLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= maxLevel) return -1;
        if (currentLevel >= levelCosts.length) return -1;
        int c = levelCosts[currentLevel];
        return c > 0 ? c : -1;
    }
}
