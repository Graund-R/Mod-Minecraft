package pocketmod;

/** Типизированные действия клиент-серверного протокола. Числовые ID сохранены для совместимости пакетов. */
public enum NetAction {
    TAKE_CREDIT(1),
    TOGGLE_DECOY(2),
    TOGGLE_SILENCE(3),
    UPGRADE_BLOODSUCKER(4),
    UPGRADE_ABILITY(5),
    BUY_BODY_PERMANENT(6),
    TOGGLE_BODY_RENT(7),
    SPIN_LOOT(8),
    CREATE_TOTEM(9),
    MOVE_TOTEM(10),
    DELETE_TOTEM(11),
    TELEPORT_TOTEM(12),
    TELEPORT_LAST_TOTEM(13),
    TELEPORT_LOOK(14),
    TOGGLE_INVISIBILITY(15),
    TOGGLE_SLOW(16),
    TOGGLE_SPECTOR(17),
    PHASE_STEP(18),
    SHOCKWAVE(19),
    TOGGLE_THERMAL(20),
    TOGGLE_SHIELD(21),
    START_FLIGHT(22),
    TOGGLE_POCKET(23),
    SET_SLOW_FACTOR(24),
    OPEN_WAREHOUSE(25),
    TOGGLE_ORE_SCANNER(26),
    TOGGLE_SCAN_TARGET(27),
    WAREHOUSE_CLICK(28),
    DEATH_TOTEM_CHOICE(29),
    DASH(30);

    public final int id;
    NetAction(int id) { this.id = id; }

    public static NetAction fromId(int id) {
        for (NetAction action : values()) {
            if (action.id == id) return action;
        }
        return null;
    }
}
