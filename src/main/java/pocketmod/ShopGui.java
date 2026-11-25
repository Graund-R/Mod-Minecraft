package pocketmod;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Экран магазина. */
@SideOnly(Side.CLIENT)
public class ShopGui extends GuiScreen {

    private static final int BTN_CLOSE = 0, BTN_TAKE = 1, BTN_CREDIT = 2;
    private static final int BTN_PREV_TAB = 10, BTN_NEXT_TAB = 11;
    private static final int BTN_DECOY = 150;
    private static final int BTN_SILENCE = 151;
    private static final int BTN_BUYSEL = 160;
    private static final int BTN_OPEN_WAREHOUSE = 161;
    private static final int BTN_PERM = 200;
    private static final int BTN_RENT = 210;
    private static final int BTN_STAKE = 220;    // 220..222
    private static final int BTN_DOOR_MANAGE = 232;
    private static final int BTN_MORPHISM = 233;

    private static ShopCatalog.Tab tab = ShopCatalog.TABS[Math.max(0,Math.min(ShopCatalog.TABS.length-1,ClientViewState.number("shop.tab",0)))];
    private static int selected = Math.max(-1,Math.min(tab.cards.length-1,ClientViewState.number("shop.selected",initialSelection())));
    private final Map<GuiButton, ShopCatalog.Tab> tabButtons = new HashMap<>();
    private final Map<GuiButton, Integer> cardButtons = new HashMap<>();
    private static int tabOffset;
    private static int cardOffset=ClientViewState.number("shop.offset",0);
    private static int detailScroll=ClientViewState.number("shop.detail",0);
    private int detailMaxScroll;
    private boolean alignSelectedTab = true;

    private static int initialSelection() {
        for (int i = 0; i < tab.cards.length; i++) {
            if (!tab.cards[i].group) return i;
        }
        return -1;
    }

    private final EntityPlayer player;
    private boolean creditOpen;
    private int creditAmount = 0;
    private boolean dragging = false;
    private int sliderX, sliderY, sliderW, sliderH;
    private final int top = 66;
    private static final int CARD_STEP = 22;
    private int spinTicks = 0;
    private String shownResult = "";

    public ShopGui(EntityPlayer player) {
        this.player = player;
    }

    private EntityPlayer dataPlayer() { return player; }

    private ShopCatalog.Card selectedCard() {
        return selected >= 0 ? tab.cards[selected] : null;
    }

    private AbilityType selectedAbility() {
        ShopCatalog.Card card = selectedCard();
        return card == null ? null : card.ability;
    }

    private int visibleTabCount() {
        return Math.min(ShopCatalog.TABS.length, Math.max(1, (width - 40) / 92));
    }

    private int contentBottom() {
        return height - (creditOpen ? 76 : 48);
    }

    private int visibleRows(int start) {
        return Math.max(0, (contentBottom() - start) / CARD_STEP);
    }

    @Override
    public void initGui() {
        PocketDoorNetwork.requestState();
        int rows = visibleRows(top);
        if (selected >= 0 && rows > 0) {
            cardOffset = Math.max(0, Math.max(selected - rows + 1, Math.min(selected, cardOffset)));
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonList.clear();
        tabButtons.clear();
        cardButtons.clear();
        int cx = width / 2;
        int count = visibleTabCount();
        int maxTabOffset = ShopCatalog.TABS.length - count;
        if (alignSelectedTab) {
            for (int i = 0; i < ShopCatalog.TABS.length; i++) {
                if (ShopCatalog.TABS[i] == tab) {
                    tabOffset = Math.max(0, i - count + 1);
                    break;
                }
            }
            alignSelectedTab = false;
        }
        tabOffset = Math.min(tabOffset, maxTabOffset);
        int tw = Math.min(108, (width - 40) / count);
        int startX = cx - count * tw / 2;
        for (int i = 0; i < count; i++) {
            ShopCatalog.Tab item = ShopCatalog.TABS[tabOffset + i];
            GuiButton button = new ShopStyle.Button(-1, startX + i * tw, 2, tw - 2, 16,
                    Tr.format("pocketmod.tab." + item.id));
            ((ShopStyle.Button) button).active = item == tab;
            button.enabled = item != tab;
            tabButtons.put(button, item);
            buttonList.add(button);
        }
        if (maxTabOffset > 0) {
            GuiButton previous = new ShopStyle.Button(BTN_PREV_TAB, startX - 17, 2, 16, 16, "<");
            previous.enabled = tabOffset > 0;
            buttonList.add(previous);
            GuiButton next = new ShopStyle.Button(BTN_NEXT_TAB, startX + count * tw, 2, 16, 16, ">");
            next.enabled = tabOffset < maxTabOffset;
            buttonList.add(next);
        }

        int px = panelX();
        int pw = width - px - 8;
        int visibleCards = visibleRows(top);
        cardOffset = Math.min(cardOffset, Math.max(0, tab.cards.length - visibleCards));
        for (int i = cardOffset; i < Math.min(tab.cards.length, cardOffset + visibleCards); i++) {
            ShopCatalog.Card card = tab.cards[i];
            if (card.group) continue;
            GuiButton button = new ShopStyle.Button(-1, 8, top + (i - cardOffset) * CARD_STEP,
                    listWidth(), 20, cardLabel(card, i == selected));
            ((ShopStyle.Button) button).active = i == selected;
            ((ShopStyle.Button) button).icon = card.ability != null ? card.ability.id
                    : card.effect >= 0 ? "body_" + BodyEffects.IDS[card.effect] : "pocket";
            cardButtons.put(button, i);
            buttonList.add(button);
        }

        ShopCatalog.Card card = selectedCard();
        int y = contentBottom() + 3;
        int buyWidth = pw / 2 - 2;
        int actionX = px + pw / 2;
        if (card != null && card.effect >= 0) {
            int effect = card.effect;
            buttonList.add(new ShopStyle.Button(BTN_PERM + effect, px, y, buyWidth, 20, permLabel(effect)));
            buttonList.add(new ShopStyle.Button(BTN_RENT + effect, actionX, y, buyWidth, 20, rentLabel(effect)));
        } else if (card != null && card.ability == AbilityType.MORPHISM) {
            buttonList.add(new ShopStyle.Button(BTN_MORPHISM, px, y, pw, 20, morphismLabel()));
        } else if (card != null && card.opensPocketAccess()) {
            buttonList.add(new ShopStyle.Button(BTN_DOOR_MANAGE, px, y, pw, 20,
                    Tr.format("pocketmod.shop.pocket.manage")));
        } else if (card != null) {
            AbilityType ability = card.ability;
            ShopStyle.Button purchase=new ShopStyle.Button(BTN_BUYSEL,px,y,buyWidth,20,buyLabel(ability));
            if(TimeBranches.child(ability)&&!AbilityStorage.isUnlocked(player,AbilityType.SLOW)){purchase.enabled=false;purchase.displayString=Tr.format("pocketmod.temporal.need_parent");}
            buttonList.add(purchase);
            if (ability == AbilityType.WAREHOUSE) {
                buttonList.add(new ShopStyle.Button(BTN_OPEN_WAREHOUSE, actionX, y, buyWidth, 20, Tr.format("pocketmod.gui.warehouse_open")));
            } else if (ability == AbilityType.INVISIBILITY) {
                buttonList.add(new ShopStyle.Button(BTN_DECOY, actionX, y, buyWidth, 20, decoyLabel()));
            } else if (ability == AbilityType.TELEPORT) {
                buttonList.add(new ShopStyle.Button(BTN_SILENCE, actionX, y, buyWidth, 20, silenceLabel()));
            }
        } else if (tab.loot) {
            for (int i = 0; i < 3; i++) {
                int w = Math.min(90, (width - 20) / 3);
                buttonList.add(new ShopStyle.Button(BTN_STAKE + i, cx - (3 * w + 8) / 2 + i * (w + 4), top, w, 18, stakeLabel(i)));
            }
        }

        int creditRight = width - 8;
        buttonList.add(new ShopStyle.Button(BTN_CREDIT, creditRight - 70, 21, 66, 13, Tr.format("pocketmod.shop.credit")));
        int footerY = height - (creditOpen ? 40 : 23);
        buttonList.add(new ShopStyle.Button(BTN_CLOSE, width - 78, footerY, 70, 18, Tr.format("pocketmod.gui.close")));
        if (creditOpen) {
            sliderW = Math.max(1, Math.min(150, width - 178));
            sliderH = 10;
            sliderX = 12;
            sliderY = height - 28;
            buttonList.add(new ShopStyle.Button(BTN_TAKE, width - 164, footerY, 80, 18, Tr.format("pocketmod.gui.take")));
        }
        for (GuiButton b : buttonList) {
            if (b instanceof ShopStyle.Button) {
                ((ShopStyle.Button) b).primary = b.id == BTN_BUYSEL || b.id == BTN_MORPHISM
                        || b.id >= BTN_PERM && b.id < BTN_PERM + BodyEffects.IDS.length;
            }
        }
        refreshButtonLabels();
        if (creditAmount == 0) {
            creditAmount = PocketConfig.creditMin;
        }
    }

    // ===== подписи =====
    private String morphismLabel() {
        if (!PocketConfig.morphEnabled) return Tr.format("pocketmod.shop.morph.disabled");
        net.minecraft.nbt.NBTTagCompound state = PocketDoorNetwork.getClientState();
        if (!state.getBoolean("morphismAvailable")) return Tr.format("pocketmod.shop.morph.required");
        return state.getBoolean("morphismUnlocked")
                ? Tr.format("pocketmod.shop.morph.catalog") : buyLabel(AbilityType.MORPHISM);
    }

    private String buyLabel(AbilityType t) {
        EntityPlayer dp = dataPlayer();
        int lvl = AbilityStorage.getUpgradeLevel(dp, t);
        if (lvl >= t.maxLevel) return Tr.format("pocketmod.gui.max");
        int cost = t.costForNextLevel(lvl);
        if (cost < 0) return "-";
        double real = Mana.shopPrice(dp, cost);
        String s = Tr.format(lvl == 0 ? "pocketmod.gui.buy" : "pocketmod.gui.upgrade", (int) Math.ceil(real));
        return (Mana.get(dp) < real ? TextFormatting.RED.toString() : "") + s;
    }

    private int listWidth() {
        return Math.min(150, width / 3);
    }

    private int panelX() {
        return 18 + listWidth();
    }

    private String cardLabel(ShopCatalog.Card card, boolean selected) {
        String name = (selected ? TextFormatting.GOLD : TextFormatting.WHITE) + Tr.format(card.titleKey);
        if (card.opensPocketAccess()) return fitLabel(name, listWidth() - 34);
        EntityPlayer dp = dataPlayer();
        int level = card.effect >= 0 ? BodyEffects.perm(dp, card.effect)
                : AbilityStorage.getUpgradeLevel(dp, card.ability);
        int max = card.effect >= 0 ? BodyEffects.maxPerm(card.effect) : card.ability.maxLevel;
        String status = "";
        if (level > 0) {
            status = TextFormatting.GREEN + " · " + level + "/" + max;
        } else if (card.effect >= 0 && BodyEffects.rent(dp, card.effect)) {
            status = TextFormatting.AQUA + " · " + Tr.format("pocketmod.shop.rented");
        }
        return fitLabel(name, listWidth() - 34 - fontRenderer.getStringWidth(status)) + status;
    }

    private String fitLabel(String label, int maxWidth) {
        if (fontRenderer.getStringWidth(label) <= maxWidth) return label;
        return fontRenderer.trimStringToWidth(label, Math.max(0, maxWidth - 6)) + "…";
    }

    private String silenceLabel() {
        return Tr.format(Abilities.isSilenceEnabled(dataPlayer()) ? "pocketmod.gui.silence_on" : "pocketmod.gui.silence_off");
    }


    private static final int COL_PANEL = ShopStyle.PANEL;
    private static final int COL_PANEL_EDGE = ShopStyle.EDGE;
    private static final int COL_ACCENT = ShopStyle.GOLD;
    private static final int COL_ACCENT_DIM = 0xFF88704A;
    private static final int COL_OK = ShopStyle.GREEN;
    private static final int COL_MUTED = ShopStyle.MUTED;

    private void drawFrame(int x1, int y1, int x2, int y2, int fill, int edge) {
        Gui.drawRect(x1, y1, x2, y2, fill);
        Gui.drawRect(x1, y1, x2, y1 + 1, edge);
        Gui.drawRect(x1, y2 - 1, x2, y2, edge);
        Gui.drawRect(x1, y1, x1 + 1, y2, edge);
        Gui.drawRect(x2 - 1, y1, x2, y2, edge);
    }

    private void drawManaChip(EntityPlayer dp, int cx, int y) {
        double mana = Mana.get(dp);
        double mult = Mana.getPriceMultiplier(dp);
        double drain = Abilities.totalDrainPerSec(dp);
        int w = width - 16;
        int x = cx - w / 2;
        drawFrame(x, y, x + w, y + 28, COL_PANEL, COL_PANEL_EDGE);
        fontRenderer.drawStringWithShadow(Tr.format("pocketmod.gui.title"), x + 8, y + 4, COL_ACCENT);
        String manaS = Tr.format("pocketmod.gui.mana", (long) mana, String.format("%.2f", mult));

        String drainLine = drain > 0.001
                ? Tr.format("pocketmod.gui.drain", String.format("%.1f", drain), (long) (mana / drain / 60.0))
                : Tr.format("pocketmod.gui.drain_none");
        int drainWidth = fontRenderer.getStringWidth(drainLine);
        if (fontRenderer.getStringWidth(manaS) + drainWidth + 24 > w) drainLine = "";
        fontRenderer.drawStringWithShadow(manaS, x + 8, y + 15, ShopStyle.MANA);
        fontRenderer.drawStringWithShadow(drainLine, x + w - 8 - fontRenderer.getStringWidth(drainLine), y + 15, COL_MUTED);
    }

    private void drawAbilityPanel(EntityPlayer dp) {
        AbilityType t = selectedAbility();
        int x = panelX();
        int w = Math.max(80, width - x - 10);
        int y0 = top - 4;
        int y1 = contentBottom();
        drawFrame(x - 6, y0, x + w + 4, y1, COL_PANEL, COL_PANEL_EDGE);
        Gui.drawRect(x - 6, y0, x + w + 4, y0 + 2, COL_ACCENT_DIM);

        int y = top;
        fontRenderer.drawStringWithShadow(Tr.format(t.titleKey()), x, y, COL_ACCENT);
        int lvl = AbilityStorage.getUpgradeLevel(dp, t);
        String badge = lvl == 0
                ? TextFormatting.GRAY + Tr.format("pocketmod.gui.locked")
                : TextFormatting.GREEN + Tr.format("pocketmod.gui.level", lvl, t.maxLevel);
        fontRenderer.drawStringWithShadow(badge, x, y + 12, 0xFFFFFF);
        ShopStyle.progress(x + w / 2, y + 15, w / 2, lvl, t.maxLevel);
        y += 26;
        Gui.drawRect(x, y, x + w, y + 1, COL_PANEL_EDGE);
        int textTop = y + 6;
        int textBottom = y1 - 4;
        y = textTop - detailScroll;

        for (Object o : fontRenderer.listFormattedStringToWidth(ShopInfo.desc(t), w)) {
            drawDetailLine((String) o, x, y, textTop, textBottom, 0xC8CDD5);
            y += 10;
        }
        y += 4;
        for (Object o : fontRenderer.listFormattedStringToWidth(
                TextFormatting.AQUA + Tr.format("pocketmod.gui.key_line", ShopInfo.keys(t)), w)) {
            drawDetailLine((String) o, x, y, textTop, textBottom, 0x7EC8E3);
            y += 10;
        }
        y += 8;

        if (lvl > 0 && lvl < t.maxLevel) {
            java.util.List<String> changes = ShopInfo.levelChanges(t, lvl, dp);
            if (!changes.isEmpty()) {
                drawDetailLine(Tr.format("pocketmod.gui.next_level_changes", lvl + 1),
                        x, y, textTop, textBottom, COL_ACCENT);
                y += 12;
                for (String change : changes) {
                    for (String line : fontRenderer.listFormattedStringToWidth(change, w)) {
                        drawDetailLine(line, x, y, textTop, textBottom, 0xFFE8C070);
                        y += 10;
                    }
                }
                y += 6;
            }
        }

        for (int L = 1; L <= t.maxLevel; L++) {
            int cost = t.costForNextLevel(L - 1);
            String text;
            int color;
            if (L <= lvl) {
                text = Tr.format("pocketmod.gui.lvl_done", L, ShopInfo.level(t, L));
                color = COL_OK;
            } else {
                double real = cost > 0 ? Mana.shopPrice(dp, cost) : 0;
                String pr = String.valueOf((int) Math.ceil(real));
                text = Tr.format(L == lvl + 1 ? "pocketmod.gui.lvl_next" : "pocketmod.gui.lvl_later", L, pr, ShopInfo.level(t, L));
                color = L == lvl + 1 ? 0xFFE8C070 : COL_MUTED;
            }
            for (Object o : fontRenderer.listFormattedStringToWidth(text, w)) {
                drawDetailLine((String) o, x, y, textTop, textBottom, color);
                y += 10;
            }
            y += 3;
        }
        if (t == AbilityType.INVISIBILITY) {
            y += 4;
            drawDetailLine(Tr.format(AbilityType.DECOY.titleKey()), x, y, textTop, textBottom, COL_ACCENT);
            y += 12;
            for (String line : fontRenderer.listFormattedStringToWidth(ShopInfo.desc(AbilityType.DECOY), w)) {
                drawDetailLine(line, x, y, textTop, textBottom, 0xC8CDD5);
                y += 10;
            }
        }
        detailMaxScroll = Math.max(0, y + detailScroll - textBottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        drawScrollIndicator(x + w + 1, textTop, textBottom, detailScroll, detailMaxScroll);
    }

    private void drawSpecialPanel(EntityPlayer player, ShopCatalog.Card card) {
        int x = panelX();
        int w = width - x - 10;
        int bottom = contentBottom() - 4;
        int textTop = top + 17;
        drawFrame(x - 6, top - 4, x + w + 4, contentBottom(), COL_PANEL, COL_PANEL_EDGE);
        fontRenderer.drawStringWithShadow(Tr.format(card.titleKey), x, top, COL_ACCENT);

        java.util.List<String> details = new java.util.ArrayList<>();
        int effect = card.effect;
        if (card.opensPocketAccess()) {
            details.add(Tr.format("pocketmod.shop.pocket.access"));
        } else if (effect < 0) {
            details.add(Tr.format("pocketmod.shop.morph.info"));
        } else {
            int level = BodyEffects.perm(player, effect);
            int max = BodyEffects.maxPerm(effect);
            details.add(level > 0 ? Tr.format("pocketmod.gui.level", level, max)
                    : Tr.format("pocketmod.gui.locked"));
            if (effect == BodyEffects.REGENERATION) {
                for (int i = 1; i <= max; i++) {
                    String description = Tr.format("pocketmod.shop.regen." + i, PocketConfig.regenSaturationPercent);
                    int price = (int) Math.ceil(Mana.shopPrice(player, BodyEffects.permCost(effect, i - 1)));
                    details.add(i <= level ? Tr.format("pocketmod.gui.lvl_done", i, description)
                            : Tr.format(i == level + 1 ? "pocketmod.gui.lvl_next" : "pocketmod.gui.lvl_later",
                                    i, price, description));
                }
                if (level == 0) details.add(Tr.format("pocketmod.shop.regen.rent"));
            } else {
                details.add(Tr.format("pocketmod.shop.effect." + BodyEffects.IDS[effect], PocketConfig.heartsRent));
            }
        }

        drawScrolledDetails(details, x, w, textTop, bottom);
    }

    private void drawScrolledDetails(java.util.List<String> details, int x, int w, int top, int bottom) {
        int y = top - detailScroll;
        for (String paragraph : details) {
            for (String line : fontRenderer.listFormattedStringToWidth(paragraph, w)) {
                drawDetailLine(line, x, y, top, bottom, 0xC8CDD5);
                y += 10;
            }
            y += 4;
        }
        detailMaxScroll = Math.max(0, y + detailScroll - bottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        drawScrollIndicator(x + w + 1, top, bottom, detailScroll, detailMaxScroll);
    }

    private void drawDetailLine(String text, int x, int y, int start, int end, int color) {
        if (y >= start && y + fontRenderer.FONT_HEIGHT <= end) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        }
    }

    private void drawScrollIndicator(int x, int start, int end, int offset, int maxOffset) {
        if (maxOffset <= 0 || end <= start + 8) return;
        int y = start + (end - start - 8) * offset / maxOffset;
        Gui.drawRect(x, start, x + 2, end, COL_PANEL_EDGE);
        Gui.drawRect(x, y, x + 2, y + 8, COL_ACCENT);
    }

    private String decoyLabel() {
        if (!AbilityStorage.isUnlocked(dataPlayer(), AbilityType.DECOY)) {
            double price = Mana.shopPrice(dataPlayer(), AbilityType.DECOY.costForNextLevel(0));
            return Tr.format("pocketmod.shop.decoy.buy", (int) Math.ceil(price));
        }
        return Tr.format(Abilities.isDecoyEnabled(dataPlayer()) ? "pocketmod.gui.decoy_on" : "pocketmod.gui.decoy_off");
    }

    private String permLabel(int i) {
        EntityPlayer dp = dataPlayer();
        int have = BodyEffects.perm(dp, i);
        if (have >= BodyEffects.maxPerm(i)) return Tr.format("pocketmod.gui.max");
        double real = Mana.shopPrice(dp, BodyEffects.permCost(i, have));
        String s = Tr.format(have == 0 ? "pocketmod.gui.perm" : "pocketmod.gui.upgrade", (int) Math.ceil(real));
        return (Mana.get(dp) < real ? TextFormatting.RED.toString() : "") + s;
    }

    private String rentLabel(int i) {
        EntityPlayer dp = dataPlayer();
        if (i == BodyEffects.REGENERATION && BodyEffects.perm(dp, i) > 0) {
            return Tr.format("pocketmod.shop.rent.included");
        }
        String c = String.format("%.1f", BodyEffects.rentCost(dp, i));
        return Tr.format(BodyEffects.rent(dp, i) ? "pocketmod.gui.rent_on" : "pocketmod.gui.rent_off", c);
    }

    private String stakeLabel(int i) {
        EntityPlayer dp = dataPlayer();
        double real = PocketConfig.lootStakes[i] * Mana.getPriceMultiplier(dp);
        return (Mana.get(dp) < real ? TextFormatting.RED.toString() : "") + Tr.format("pocketmod.loot.spin", (int) Math.ceil(real));
    }

    private static String formatTime(long ms) {
        long sec = Math.max(0L, ms) / 1000L;
        return String.format("%d:%02d", sec / 60L, sec % 60L);
    }

    private double sliderFraction() {
        int min = PocketConfig.creditMin;
        int max = Math.max(min, PocketConfig.creditMax);
        if (max == min) return 0.0;
        return (creditAmount - min) / (double) (max - min);
    }

    private void updateSlider(int mx) {
        double f = (mx - sliderX) / (double) sliderW;
        f = Math.max(0.0, Math.min(1.0, f));
        int min = PocketConfig.creditMin;
        int max = Math.max(min, PocketConfig.creditMax);
        int v = min + (int) Math.round(f * (max - min));
        if (max - min >= 100) {
            v = (v / 10) * 10;
        }
        creditAmount = Math.max(min, Math.min(max, v));
    }

    private boolean inSlider(int mx, int my) {
        return mx >= sliderX && mx <= sliderX + sliderW && my >= sliderY - 2 && my <= sliderY + sliderH + 2;
    }

    private String resultText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        int k = raw.indexOf('|');
        String id = k < 0 ? raw : raw.substring(0, k);
        String arg = k < 0 ? "" : raw.substring(k + 1);
        if (id.equals("debuff")) {
            StringBuilder sb = new StringBuilder();
            for (String n : arg.split(",")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(Tr.format("effect." + n));
            }
            arg = sb.toString();
        }
        return Tr.format("pocketmod.loot.res." + id, arg);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawGradientRect(0, 0, width, height, 0xDD0C1220, 0xF0141B2B);
        EntityPlayer dp = dataPlayer();
        int cx = width / 2;
        boolean hasCredit = Mana.hasCredit(dp);

        // верхняя плашка с маной
        drawManaChip(dp, cx, 20);

        if (hasCredit) {
            drawCenteredString(fontRenderer,
                    Tr.format("pocketmod.gui.credit_active", (long) Math.ceil(Mana.getCredit(dp)),
                            formatTime(Mana.getCreditLeftMs(dp))),
                    cx, 52, 0xFF8888);
        } else {
            double disc = Mana.getDiscount(dp);
            if (disc > 0.001) {
                drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.discount", (int) Math.round(disc * 100)), cx, 52, COL_OK);
            }
        }

        for (Map.Entry<GuiButton, ShopCatalog.Tab> entry : tabButtons.entrySet()) {
            GuiButton button = entry.getKey();
            if (entry.getValue() == tab) {
                Gui.drawRect(button.x, 18, button.x + button.width, 20, COL_ACCENT);
            }
        }

        int visibleCards = visibleRows(top);
        for (int i = cardOffset; i < Math.min(tab.cards.length, cardOffset + visibleCards); i++) {
            ShopCatalog.Card card = tab.cards[i];
            if (card.group) {
                fontRenderer.drawStringWithShadow(fitLabel(Tr.format(card.titleKey), listWidth() - 4), 10,
                        top + (i - cardOffset) * CARD_STEP + 7, COL_ACCENT);
            }
        }
        drawScrollIndicator(8 + listWidth(), top, contentBottom(),
                cardOffset, tab.cards.length - visibleCards);
        ShopCatalog.Card card = selectedCard();
        if (card != null && (card.ability == null || card.ability == AbilityType.MORPHISM)) {
            drawSpecialPanel(dp, card);
        } else if (card != null) {
            drawAbilityPanel(dp);
        } else if (tab.loot) {
            drawFrame(4, top - 6, width - 4, contentBottom(), COL_PANEL, COL_PANEL_EDGE);
            drawLoot(dp, cx);
        }

        if (creditOpen) drawCredit(hasCredit, dp);
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (Map.Entry<GuiButton, Integer> entry : cardButtons.entrySet()) {
            if (entry.getKey().isMouseOver()) {
                drawHoveringText(Tr.format(tab.cards[entry.getValue()].titleKey), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawCredit(boolean hasCredit, EntityPlayer dp) {
        drawFrame(4, height - 48, width - 4, height - 4, COL_PANEL, COL_PANEL_EDGE);
        if (!hasCredit) {
            int repay = (int) Math.round(creditAmount * (1.0 + PocketConfig.creditInterest));
            fontRenderer.drawStringWithShadow(
                    Tr.format("pocketmod.gui.credit_amount", creditAmount, repay, PocketConfig.creditDurationSec / 60),
                    sliderX, sliderY - 15, 0xC8CDD5);
        } else {
            fontRenderer.drawStringWithShadow(TextFormatting.RED + Tr.format("pocketmod.gui.credit_active",
                    (long) Math.ceil(Mana.getCredit(dp)), formatTime(Mana.getCreditLeftMs(dp))),
                    12, height - 40, 0xFFFFFF);
            return;
        }
        Gui.drawRect(sliderX, sliderY + 3, sliderX + sliderW, sliderY + sliderH - 3, 0xFF2A3038);
        Gui.drawRect(sliderX, sliderY + 3, sliderX + (int) (sliderFraction() * sliderW), sliderY + sliderH - 3, 0xFF6A5030);
        int kx = sliderX + (int) (sliderFraction() * (sliderW - 6));
        Gui.drawRect(kx, sliderY, kx + 6, sliderY + sliderH, COL_ACCENT);
    }

    private void drawLoot(EntityPlayer dp, int cx) {
        int x = Math.max(10, cx - 140);
        java.util.List<String> details = new java.util.ArrayList<>();
        if (spinTicks > 0) {
            Loot.Out[] all = Loot.Out.values();
            int index = Math.floorMod(spinTicks * 7 + (int) (System.nanoTime() / 90000000L), all.length);
            details.add(TextFormatting.YELLOW + Tr.format("pocketmod.loot.out." + all[index].id));
        } else if (!shownResult.isEmpty()) {
            details.add(resultText(shownResult));
        }
        details.add(Tr.format("pocketmod.loot.info", String.format("%.0f", Loot.rtp() * 100.0),
                Math.max(0, PocketConfig.lootPity - Loot.pity(dp))));
        double discount = Mana.getDiscount(dp);
        if (discount > 0) {
            details.add(Tr.format("pocketmod.gui.discount", (int) Math.round(discount * 100.0)));
        }
        for (Loot.Out outcome : Loot.Out.values()) {
            details.add(String.format("%.1f%%  ", outcome.p * 100.0) + Tr.format("pocketmod.loot.out." + outcome.id));
        }
        drawScrolledDetails(details, x, width - x - 12, top + 22, contentBottom() - 4);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (y < top || y >= contentBottom()) return;
        int direction = wheel > 0 ? -1 : 1;
        if (!tab.loot && x < panelX()) {
            cardOffset = Math.max(0, Math.min(cardOffset + direction, tab.cards.length - visibleRows(top)));
            rebuildButtons();
        } else {
            detailScroll = Math.max(0, Math.min(detailScroll + direction * 16, detailMaxScroll));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && creditOpen && inSlider(mouseX, mouseY) && !Mana.hasCredit(dataPlayer())) {
            dragging = true;
            updateSlider(mouseX);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragging) {
            updateSlider(mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragging = false;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_CLOSE) { mc.player.closeScreen(); return; }
        if (button.id == BTN_CREDIT) {
            creditOpen = !creditOpen;
            rebuildButtons();
            return;
        }
        if (button.id == BTN_OPEN_WAREHOUSE) { NetworkHandler.action(NetAction.OPEN_WAREHOUSE, 0, 0, 0, ""); return; }
        if (button.id == BTN_DOOR_MANAGE) {
            mc.displayGuiScreen(new PocketDoorGui(player));
            return;
        }
        if (button.id == BTN_MORPHISM) {
            net.minecraft.nbt.NBTTagCompound state = PocketDoorNetwork.getClientState();
            if (!PocketConfig.morphEnabled || !state.getBoolean("morphismAvailable")) return;
            if (state.getBoolean("morphismUnlocked")) PocketDoorNetwork.requestMorphCatalog();
            else PocketDoorNetwork.upgradeAbility(AbilityType.MORPHISM);
            return;
        }
        if (button.id == BTN_PREV_TAB || button.id == BTN_NEXT_TAB) {
            tabOffset += button.id == BTN_PREV_TAB ? -1 : 1;
            rebuildButtons();
            return;
        }
        ShopCatalog.Tab targetTab = tabButtons.get(button);
        if (targetTab != null) {
            tab = targetTab;
            selected = initialSelection();
            cardOffset = detailScroll = detailMaxScroll = 0;
            rebuildButtons();
            return;
        }
        Integer targetCard = cardButtons.get(button);
        if (targetCard != null) {
            selected = targetCard;
            detailScroll = detailMaxScroll = 0;
            rebuildButtons();
            return;
        }
        if (button.id == BTN_TAKE) {
            if (!Mana.hasCredit(dataPlayer())) NetworkHandler.action(NetAction.TAKE_CREDIT, 0, 0, creditAmount, "");
            return;
        }
        if (button.id == BTN_DECOY) {
            if (AbilityStorage.isUnlocked(dataPlayer(), AbilityType.DECOY)) {
                NetworkHandler.action(NetAction.TOGGLE_DECOY, 0, 0, 0, "");
            } else {
                NetworkHandler.action(NetAction.UPGRADE_ABILITY, AbilityType.DECOY.ordinal(), 0, 0, "");
            }
            return;
        }
        if (button.id == BTN_SILENCE) { NetworkHandler.action(NetAction.TOGGLE_SILENCE, 0, 0, 0, ""); return; }
        if (button.id == BTN_BUYSEL) {
            AbilityType ability = selectedAbility();
            if (ability != null) NetworkHandler.action(NetAction.UPGRADE_ABILITY, ability.ordinal(), 0, 0, "");
            return;
        }
        if (button.id >= BTN_PERM && button.id < BTN_PERM + BodyEffects.IDS.length) {
            NetworkHandler.action(NetAction.BUY_BODY_PERMANENT, button.id - BTN_PERM, 0, 0, ""); return;
        }
        if (button.id >= BTN_RENT && button.id < BTN_RENT + BodyEffects.IDS.length) {
            NetworkHandler.action(NetAction.TOGGLE_BODY_RENT, button.id - BTN_RENT, 0, 0, ""); return;
        }
        if (button.id >= BTN_STAKE && button.id < BTN_STAKE + 3) {
            if (spinTicks > 0) return;
            int tier = button.id - BTN_STAKE;
            double real = PocketConfig.lootStakes[tier] * Mana.getPriceMultiplier(dataPlayer());
            if (Mana.get(dataPlayer()) < real) return;
            spinTicks = 30; shownResult = "";
            NetworkHandler.action(NetAction.SPIN_LOOT, tier, 0, 0, "");
        }
    }

    @Override
    public void updateScreen() {
        EntityPlayer dp = dataPlayer();
        boolean hasCredit = Mana.hasCredit(dp);
        if (spinTicks > 0) {
            spinTicks--;
            if (spinTicks % 3 == 0) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.4F, 1.4F);
            }
            if (spinTicks == 0) {
                shownResult = Loot.last(dp);
            }
        }
        refreshButtonLabels();
    }

    private void refreshButtonLabels() {
        EntityPlayer dp = dataPlayer();
        boolean hasCredit = Mana.hasCredit(dp);
        for (GuiButton b : buttonList) {
            Integer idx = cardButtons.get(b);
            if (idx != null) {
                b.displayString = cardLabel(tab.cards[idx], idx == selected);
            } else if (b.id == BTN_MORPHISM) {
                b.displayString = morphismLabel();
                b.enabled = PocketConfig.morphEnabled && PocketDoorNetwork.getClientState().getBoolean("morphismAvailable");
            } else if (b.id == BTN_BUYSEL) {
                AbilityType ability = selectedAbility();
                int level = AbilityStorage.getUpgradeLevel(dp, ability);
                b.displayString = buyLabel(ability);
                b.enabled = level < ability.maxLevel && ability.costForNextLevel(level) > 0
                        && Mana.get(dp) >= Mana.shopPrice(dp, ability.costForNextLevel(level));
            } else if (b.id == BTN_OPEN_WAREHOUSE) {
                b.enabled = AbilityStorage.isUnlocked(dp, AbilityType.WAREHOUSE);
            } else if (b.id == BTN_SILENCE) {
                b.displayString = silenceLabel();
                b.enabled = AbilityStorage.getLevel(dp, AbilityType.TELEPORT) >= PocketConfig.silenceMinLevel;
            } else if (b.id == BTN_DECOY) {
                b.displayString = decoyLabel();
            } else if (b.id >= BTN_PERM && b.id < BTN_PERM + BodyEffects.IDS.length) {
                int i = b.id - BTN_PERM;
                b.displayString = permLabel(i);
                b.enabled = BodyEffects.perm(dp, i) < BodyEffects.maxPerm(i)
                        && Mana.get(dp) >= Mana.shopPrice(dp, BodyEffects.permCost(i, BodyEffects.perm(dp, i)));
            } else if (b.id >= BTN_RENT && b.id < BTN_RENT + BodyEffects.IDS.length) {
                b.displayString = rentLabel(b.id - BTN_RENT);
            } else if (b.id >= BTN_STAKE && b.id < BTN_STAKE + 3) {
                b.displayString = stakeLabel(b.id - BTN_STAKE);
                b.enabled = spinTicks == 0;
            } else if (b.id == BTN_TAKE) {
                b.enabled = !hasCredit;
            }
        }
    }

    @Override
    public void onGuiClosed(){int index=0;for(int i=0;i<ShopCatalog.TABS.length;i++)if(tab==ShopCatalog.TABS[i])index=i;
        ClientViewState.save("shop.","tab",index,"selected",selected,"offset",cardOffset,"detail",detailScroll);}
    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
