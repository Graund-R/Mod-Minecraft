package pocketmod;

/** Порядок вкладок и карточек в магазине. */
final class ShopCatalog {
    enum Page { ABILITIES, BODY_EFFECTS, LOOT }

    static final Tab[] TABS = {
        new Tab("move", Page.ABILITIES, AbilityType.TELEPORT, AbilityType.FLIGHT,
                AbilityType.SOFTFALL, AbilityType.MAGNET, AbilityType.WAVE, AbilityType.DASH),
        new Tab("space", Page.ABILITIES, AbilityType.POCKET, AbilityType.TOTEM,
                AbilityType.SLOW, AbilityType.WAREHOUSE),
        new Tab("sense", Page.ABILITIES, AbilityType.SPECTOR, AbilityType.THERMAL),
        new Tab("guard", Page.ABILITIES, AbilityType.INVISIBILITY, AbilityType.DECOY, AbilityType.SHIELD),
        new Tab("body", Page.BODY_EFFECTS, AbilityType.BLOODSUCKER),
        new Tab("luck", Page.LOOT)
    };

    static final class Tab {
        final String id;
        final Page page;
        final AbilityType[] abilities;

        Tab(String id, Page page, AbilityType... abilities) {
            this.id = id;
            this.page = page;
            this.abilities = abilities;
        }
    }

    private ShopCatalog() {}
}
