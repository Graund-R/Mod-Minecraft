package pocketmod;

import static pocketmod.AbilityType.*;

/** Порядок вкладок, групп и карточек магазина. */
final class ShopCatalog {
    static final Tab[] TABS = {
        new Tab("move", false,
                group("local"), card(DASH), card(TELEPORT),
                group("air"), card(FLIGHT),
                group("return"), card(TOTEM)),
        new Tab("guard", false,
                group("health"), effect(BodyEffects.HEARTS),
                group("recovery"), effect(BodyEffects.REGENERATION), card(BLOODSUCKER),
                group("damage"), card(SHIELD), card(SOFTFALL), effect(BodyEffects.FIRE),
                group("control"), card(WAVE), card(SLOW),
                group("stealth"), card(INVISIBILITY)),
        new Tab("sense", false,
                group("survey"), card(SPECTOR), card(THERMAL),
                group("resources"), card(ORE_SCANNER), card(MAGNET)),
        new Tab("space", false,
                group("entry"), card(POCKET),
                group("storage"), card(WAREHOUSE),
                group("access"), access()),
        new Tab("body", false,
                group("attributes"), effect(BodyEffects.SPEED), effect(BodyEffects.STRENGTH),
                group("adaptation"), effect(BodyEffects.BREATH),
                group("morph"), card(MORPHISM)),
        new Tab("luck", true)
    };

    static final class Tab {
        final String id;
        final boolean loot;
        final Card[] cards;

        Tab(String id, boolean loot, Card... cards) {
            this.id = id;
            this.loot = loot;
            this.cards = cards;
        }
    }

    static final class Card {
        final String titleKey;
        final AbilityType ability;
        final int effect;
        final boolean group;

        private Card(String titleKey, AbilityType ability, int effect, boolean group) {
            this.titleKey = titleKey;
            this.ability = ability;
            this.effect = effect;
            this.group = group;
        }

        boolean opensPocketAccess() {
            return !group && ability == null && effect < 0;
        }
    }

    private static Card group(String id) {
        return new Card("pocketmod.shop.group." + id, null, -1, true);
    }

    private static Card card(AbilityType ability) {
        return new Card(ability.titleKey(), ability, -1, false);
    }

    private static Card effect(int effect) {
        return new Card("pocketmod.body." + BodyEffects.IDS[effect], null, effect, false);
    }

    private static Card access() {
        return new Card("pocketmod.shop.pocket.doors", null, -1, false);
    }

    private ShopCatalog() {}
}
