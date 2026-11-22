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

    private static final int BTN_CLOSE = 0, BTN_TAKE = 1;
    private static final int BTN_PREV_TAB = 10, BTN_NEXT_TAB = 11;
    private static final int BTN_BODY_EFFECTS = 12;
    private static final int BTN_DECOY = 150;
    private static final int BTN_SILENCE = 151;
    private static final int BTN_BUYSEL = 160;
    private static final int BTN_OPEN_WAREHOUSE = 161;
    private static final int BTN_PERM = 200;     // 200..204
    private static final int BTN_RENT = 210;     // 210..214
    private static final int BTN_STAKE = 220;    // 220..222
    private static final int BTN_DOOR_MANAGE = 232;
    private static final int BTN_MORPHISM = 233;

    private static ShopCatalog.Tab tab = ShopCatalog.TABS[0];
    private static int selected = initialSelection();
    private final Map<GuiButton, ShopCatalog.Tab> tabButtons = new HashMap<>();
    private final Map<GuiButton, Integer> cardButtons = new HashMap<>();
    private int tabOffset;
    private int cardOffset;
    private int detailScroll;
    private int detailMaxScroll;
    private int bodyOffset;
    private boolean alignSelectedTab = true;

    private static int initialSelection() {
        return tab.page == ShopCatalog.Page.BODY_EFFECTS ? -1 : 0;
    }

    private final EntityPlayer player;
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

    private AbilityType[] list() {
        return tab.abilities;
    }

    private AbilityType selectedAbility() {
        return selected >= 0 && selected < list().length ? list()[selected] : null;
    }

    private boolean showsBodyEffects() {
        return tab.page == ShopCatalog.Page.BODY_EFFECTS && selected < 0;
    }

    private int visibleTabCount() {
        return Math.min(ShopCatalog.TABS.length, Math.max(1, (width - 40) / 55));
    }

    private int cardsTop() {
        return tab.page == ShopCatalog.Page.BODY_EFFECTS ? top + CARD_STEP : top;
    }

    private int contentBottom() {
        return height - 76;
    }

    private int visibleRows(int start) {
        return Math.max(0, (contentBottom() - start) / CARD_STEP);
    }

    @Override
    public void initGui() {
        PocketDoorNetwork.requestState();
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
        int tw = Math.min(84, (width - 40) / count);
        int startX = cx - count * tw / 2;
        for (int i = 0; i < count; i++) {
            ShopCatalog.Tab item = ShopCatalog.TABS[tabOffset + i];
            GuiButton button = new GuiButton(-1, startX + i * tw, 2, tw - 2, 16,
                    Tr.format("pocketmod.tab." + item.id));
            button.enabled = item != tab;
            tabButtons.put(button, item);
            buttonList.add(button);
        }
        if (maxTabOffset > 0) {
            GuiButton previous = new GuiButton(BTN_PREV_TAB, startX - 17, 2, 16, 16, "<");
            previous.enabled = tabOffset > 0;
            buttonList.add(previous);
            GuiButton next = new GuiButton(BTN_NEXT_TAB, startX + count * tw, 2, 16, 16, ">");
            next.enabled = tabOffset < maxTabOffset;
            buttonList.add(next);
        }

        int px = panelX();
        int pw = width - px - 8;
        if (tab.page == ShopCatalog.Page.BODY_EFFECTS) {
            GuiButton effects = new GuiButton(BTN_BODY_EFFECTS, 8, top, listWidth(), 20,
                    Tr.format("pocketmod.gui.body_effects"));
            effects.enabled = !showsBodyEffects();
            buttonList.add(effects);
        }
        int visibleCards = visibleRows(cardsTop());
        cardOffset = Math.min(cardOffset, Math.max(0, list().length - visibleCards));
        for (int i = cardOffset; i < Math.min(list().length, cardOffset + visibleCards); i++) {
            GuiButton button = new GuiButton(-1, 8, cardsTop() + (i - cardOffset) * CARD_STEP,
                    listWidth(), 20, cardLabel(list()[i], i == selected));
            cardButtons.put(button, i);
            buttonList.add(button);
        }

        AbilityType ability = selectedAbility();
        if (ability != null) {
            int buyWidth = pw / 2 - 2;
            int actionX = px + pw / 2;
            int y = contentBottom() + 3;
            buttonList.add(new GuiButton(BTN_BUYSEL, px, y, buyWidth, 20, buyLabel(ability)));
            if (ability == AbilityType.WAREHOUSE) {
                buttonList.add(new GuiButton(BTN_OPEN_WAREHOUSE, actionX, y, buyWidth, 20, Tr.format("pocketmod.gui.warehouse_open")));
            } else if (ability == AbilityType.POCKET) {
                buttonList.add(new GuiButton(BTN_DOOR_MANAGE, actionX, y, buyWidth, 20,
                        Tr.format("pocketmod.gui.pocket_doors")));
            } else if (ability == AbilityType.DECOY) {
                buttonList.add(new GuiButton(BTN_DECOY, actionX, y, buyWidth, 20, decoyLabel()));
            } else if (ability == AbilityType.TELEPORT) {
                buttonList.add(new GuiButton(BTN_SILENCE, actionX, y, buyWidth, 20, silenceLabel()));
            }
        } else if (showsBodyEffects()) {
            int controlWidth = pw / 3 - 2;
            int visible = visibleRows(top + CARD_STEP);
            bodyOffset = Math.min(bodyOffset, Math.max(0, BodyEffects.IDS.length - visible));
            buttonList.add(new GuiButton(BTN_MORPHISM, px, top, pw, 20, morphismLabel()));
            for (int i = bodyOffset; i < Math.min(BodyEffects.IDS.length, bodyOffset + visible); i++) {
                int y = top + (i - bodyOffset + 1) * CARD_STEP;
                buttonList.add(new GuiButton(BTN_PERM + i, px + pw / 3, y, controlWidth, 20, permLabel(i)));
                buttonList.add(new GuiButton(BTN_RENT + i, px + 2 * pw / 3, y, controlWidth, 20, rentLabel(i)));
            }
        } else if (tab.page == ShopCatalog.Page.LOOT) {
            for (int i = 0; i < 3; i++) {
                int w = 90;
                buttonList.add(new GuiButton(BTN_STAKE + i, cx - 140 + i * (w + 4), top, w, 18, stakeLabel(i)));
            }
        }

        sliderW = 150;
        sliderH = 10;
        sliderX = cx - 160;
        sliderY = height - 34;
        buttonList.add(new GuiButton(BTN_TAKE, cx, height - 40, 80, 18, Tr.format("pocketmod.gui.take")));
        buttonList.add(new GuiButton(BTN_CLOSE, cx + 90, height - 40, 70, 18, Tr.format("pocketmod.gui.close")));
        if (creditAmount == 0) {
            creditAmount = PocketConfig.creditMin;
        }
    }

    // ===== подписи =====
    private String morphismLabel() {
        if (!PocketConfig.morphEnabled) return "Морфизм: отключён";
        net.minecraft.nbt.NBTTagCompound state = PocketDoorNetwork.getClientState();
        if (!state.getBoolean("morphismAvailable")) return "Требуется Metamorph";
        return state.getBoolean("morphismUnlocked") ? "Каталог образов" : "Купить морфизм: 500";
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

    private String cardLabel(AbilityType t, boolean sel) {
        EntityPlayer dp = dataPlayer();
        int lvl = AbilityStorage.getUpgradeLevel(dp, t);
        String st = lvl == 0 ? TextFormatting.DARK_GRAY + "· " + Tr.format("pocketmod.gui.locked")
                : TextFormatting.GREEN + "· L" + lvl + "/" + t.maxLevel;
        String name = (sel ? TextFormatting.GOLD : TextFormatting.WHITE) + Tr.format(t.titleKey());
        return name + " " + st;
    }

    private String silenceLabel() {
        return Tr.format(Abilities.isSilenceEnabled(dataPlayer()) ? "pocketmod.gui.silence_on" : "pocketmod.gui.silence_off");
    }


    private static final int COL_PANEL = 0xCC101418;
    private static final int COL_PANEL_EDGE = 0xFF2A3340;
    private static final int COL_ACCENT = 0xFFE0A040;
    private static final int COL_ACCENT_DIM = 0xFF8A6030;
    private static final int COL_OK = 0xFF6BCB77;
    private static final int COL_MUTED = 0xFF8A93A0;

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
        int w = Math.min(280, width - 40);
        int x = cx - w / 2;
        drawFrame(x, y, x + w, y + 28, COL_PANEL, COL_PANEL_EDGE);
        fontRenderer.drawStringWithShadow(Tr.format("pocketmod.gui.title"), x + 8, y + 4, COL_ACCENT);
        String manaS = Tr.format("pocketmod.gui.mana", (long) mana, String.format("%.2f", mult));
        fontRenderer.drawStringWithShadow(manaS, x + 8, y + 15, 0xAACCFF);
        String drainLine = drain > 0.001
                ? Tr.format("pocketmod.gui.drain", String.format("%.1f", drain), (long) (mana / drain / 60.0))
                : Tr.format("pocketmod.gui.drain_none");
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
        fontRenderer.drawStringWithShadow(badge, x + w - fontRenderer.getStringWidth(badge), y, 0xFFFFFF);
        y += 14;
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
        detailMaxScroll = Math.max(0, y + detailScroll - textBottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        drawScrollIndicator(x + w + 1, textTop, textBottom, detailScroll, detailMaxScroll);
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
        return Tr.format(Abilities.isDecoyEnabled(dataPlayer()) ? "pocketmod.gui.decoy_on" : "pocketmod.gui.decoy_off");
    }

    private String permLabel(int i) {
        EntityPlayer dp = dataPlayer();
        int have = BodyEffects.perm(dp, i);
        if (have >= BodyEffects.maxPerm(i)) return Tr.format("pocketmod.gui.max");
        double real = Mana.shopPrice(dp, PocketConfig.bodyPermCost[i]);
        String s = Tr.format("pocketmod.gui.perm", (int) Math.ceil(real));
        return (Mana.get(dp) < real ? TextFormatting.RED.toString() : "") + s;
    }

    private String rentLabel(int i) {
        EntityPlayer dp = dataPlayer();
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
            double disc = 1.0 - Mana.getPriceMultiplier(dp);
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

        drawScrollIndicator(8 + listWidth(), cardsTop(), contentBottom(),
                cardOffset, list().length - visibleRows(cardsTop()));
        if (selectedAbility() != null) {
            drawAbilityPanel(dp);
        } else if (showsBodyEffects()) {
            int px = panelX();
            drawFrame(px - 4, top - 4, width - 4, contentBottom(), COL_PANEL, COL_PANEL_EDGE);
            int visible = visibleRows(top + CARD_STEP);
            for (int i = bodyOffset; i < Math.min(BodyEffects.IDS.length, bodyOffset + visible); i++) {
                int y = top + (i - bodyOffset + 1) * CARD_STEP;
                int have = BodyEffects.perm(dp, i);
                String status = have > 0 ? TextFormatting.GREEN + " x" + have : "";
                fontRenderer.drawStringWithShadow(Tr.format("pocketmod.body." + BodyEffects.IDS[i]) + status,
                        px, y + 6, 0xE8ECF0);
            }
            drawScrollIndicator(width - 7, top + CARD_STEP, contentBottom(),
                    bodyOffset, BodyEffects.IDS.length - visible);
        } else if (tab.page == ShopCatalog.Page.LOOT) {
            drawFrame(4, top - 6, width - 4, height - 72, COL_PANEL, COL_PANEL_EDGE);
            drawLoot(dp, cx);
        }

        // нижняя панель кредита
        drawFrame(4, height - 48, width - 4, height - 4, COL_PANEL, COL_PANEL_EDGE);
        if (!hasCredit) {
            int repay = (int) Math.round(creditAmount * (1.0 + PocketConfig.creditInterest));
            fontRenderer.drawStringWithShadow(
                    Tr.format("pocketmod.gui.credit_amount", creditAmount, repay, PocketConfig.creditDurationSec / 60),
                    sliderX, sliderY - 12, 0xC8CDD5);
        } else {
            fontRenderer.drawStringWithShadow(TextFormatting.RED + Tr.format("pocketmod.gui.credit_active",
                    (long) Math.ceil(Mana.getCredit(dp)), formatTime(Mana.getCreditLeftMs(dp))),
                    12, height - 40, 0xFFFFFF);
        }
        Gui.drawRect(sliderX, sliderY + 3, sliderX + sliderW, sliderY + sliderH - 3, 0xFF2A3038);
        Gui.drawRect(sliderX, sliderY + 3, sliderX + (int) (sliderFraction() * sliderW), sliderY + sliderH - 3, 0xFF6A5030);
        int kx = sliderX + (int) (sliderFraction() * (sliderW - 6));
        Gui.drawRect(kx, sliderY, kx + 6, sliderY + sliderH, hasCredit ? 0xFF666666 : COL_ACCENT);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawLoot(EntityPlayer dp, int cx) {
        int x = Math.max(4, cx - 140);
        int y = top + 22;
        String res;
        if (spinTicks > 0) {
            Loot.Out[] all = Loot.Out.values();
            res = TextFormatting.YELLOW + Tr.format("pocketmod.loot.out." + all[(spinTicks * 7 + (int) (System.nanoTime() / 90000000L)) % all.length].id);
        } else {
            res = resultText(shownResult);
        }
        fontRenderer.drawStringWithShadow(res, x, y, 0xFFFFFF);
        y += 12;
        fontRenderer.drawStringWithShadow(Tr.format("pocketmod.loot.info", String.format("%.0f", Loot.rtp() * 100.0),
                Math.max(0, PocketConfig.lootPity - Loot.pity(dp))), x, y, 0xAAAAAA);
        y += 12;
        for (Loot.Out o : Loot.Out.values()) {
            fontRenderer.drawStringWithShadow(
                    String.format("%4.1f%%  ", o.p * 100.0) + Tr.format("pocketmod.loot.out." + o.id), x, y, 0xBBBBBB);
            y += 9;
        }
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
        if (x < panelX()) {
            cardOffset = Math.max(0, Math.min(cardOffset + direction,
                    list().length - visibleRows(cardsTop())));
            rebuildButtons();
        } else if (showsBodyEffects()) {
            bodyOffset = Math.max(0, Math.min(bodyOffset + direction,
                    BodyEffects.IDS.length - visibleRows(top + CARD_STEP)));
            rebuildButtons();
        } else {
            detailScroll = Math.max(0, Math.min(detailScroll + direction * 16, detailMaxScroll));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && inSlider(mouseX, mouseY) && !Mana.hasCredit(dataPlayer())) {
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
            cardOffset = bodyOffset = detailScroll = 0;
            rebuildButtons();
            return;
        }
        Integer targetCard = cardButtons.get(button);
        if (targetCard != null || button.id == BTN_BODY_EFFECTS) {
            selected = targetCard != null ? targetCard : -1;
            detailScroll = 0;
            rebuildButtons();
            return;
        }
        if (button.id == BTN_TAKE) {
            if (!Mana.hasCredit(dataPlayer())) NetworkHandler.action(NetAction.TAKE_CREDIT, 0, 0, creditAmount, "");
            return;
        }
        if (button.id == BTN_DECOY) { NetworkHandler.action(NetAction.TOGGLE_DECOY, 0, 0, 0, ""); return; }
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
        for (GuiButton b : buttonList) {
            Integer idx = cardButtons.get(b);
            if (idx != null) {
                b.displayString = cardLabel(list()[idx], idx == selected);
            } else if (b.id == BTN_MORPHISM) {
                b.displayString = morphismLabel();
                b.enabled = PocketConfig.morphEnabled && PocketDoorNetwork.getClientState().getBoolean("morphismAvailable");
            } else if (b.id == BTN_BUYSEL) {
                AbilityType[] lst = list();
                if (lst.length == 0) {
                    b.enabled = false;
                    b.displayString = "-";
                } else {
                    AbilityType t = lst[Math.min(selected, lst.length - 1)];
                    int lvl = AbilityStorage.getUpgradeLevel(dp, t);
                    int cost = t.costForNextLevel(lvl);
                    b.displayString = buyLabel(t);
                    // активна пока не MAX; нехватка маны не блокирует клик (сообщение при покупке)
                    b.enabled = lvl < t.maxLevel && cost > 0;
                }
            } else if (b.id == BTN_OPEN_WAREHOUSE) {
                b.enabled = AbilityStorage.isUnlocked(dp, AbilityType.WAREHOUSE);
            } else if (b.id == BTN_SILENCE) {
                b.displayString = silenceLabel();
                b.enabled = AbilityStorage.getLevel(dp, AbilityType.TELEPORT) >= PocketConfig.silenceMinLevel;
            } else if (b.id == BTN_DECOY) {
                b.displayString = decoyLabel();
                b.enabled = AbilityStorage.isUnlocked(dp, AbilityType.DECOY);
            } else if (b.id >= BTN_PERM && b.id < BTN_PERM + BodyEffects.IDS.length) {
                int i = b.id - BTN_PERM;
                b.displayString = permLabel(i);
                b.enabled = BodyEffects.perm(dp, i) < BodyEffects.maxPerm(i);
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
    public boolean doesGuiPauseGame() {
        return true;
    }
}
