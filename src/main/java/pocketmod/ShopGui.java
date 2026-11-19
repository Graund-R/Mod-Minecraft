package pocketmod;

import java.io.IOException;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Магазин с вкладками: Движение / Время / Восприятие / Защита / Тело / Удача. */
@SideOnly(Side.CLIENT)
public class ShopGui extends GuiScreen {

    private static final int BTN_CLOSE = 0, BTN_TAKE = 1;
    private static final int BTN_TAB = 10;       // 10..15
    private static final int BTN_BUY = 100;      // 100..
    private static final int BTN_DECOY = 150;
    private static final int BTN_SILENCE = 151;
    private static final int BTN_BUYSEL = 160;
    private static final int BTN_BLOOD = 165;   // кровосися на вкладке «Тело»
    private static final int BTN_PERM = 200;     // 200..204
    private static final int BTN_RENT = 210;     // 210..214
    private static final int BTN_STAKE = 220;    // 220..222
    private static final int BTN_DOOR_MANAGE = 232;
    private static final int BTN_MORPHISM = 233;

    private static final String[] TAB_KEYS = { "move", "space", "sense", "guard", "body", "luck" };
    private static final AbilityType[][] TAB_ABIL = {
            { AbilityType.TELEPORT, AbilityType.FLIGHT, AbilityType.SOFTFALL, AbilityType.MAGNET, AbilityType.WAVE, AbilityType.BLOODSUCKER },
            { AbilityType.POCKET, AbilityType.TOTEM, AbilityType.SLOW },
            { AbilityType.SPECTOR, AbilityType.THERMAL },
            { AbilityType.INVISIBILITY, AbilityType.DECOY, AbilityType.SHIELD },
            {}, {} };

    private static int tab = 0;
    private static int selected = 0;

    private final EntityPlayer player;
    private int creditAmount = 0;
    private boolean dragging = false;
    private int sliderX, sliderY, sliderW, sliderH;
    private final int top = 66;
    private int rowH = 20;
    private int spinTicks = 0;
    private String shownResult = "";

    public ShopGui(EntityPlayer player) {
        this.player = player;
    }

    private EntityPlayer dataPlayer() {
        return player;
    }

    private net.minecraft.nbt.NBTTagCompound shopState() {
        return PocketDoorNetwork.getClientState();
    }

    private int abilityLevel(AbilityType type) {
        int[] levels = shopState().getIntArray("abilityLevels");
        return levels.length > type.ordinal() ? levels[type.ordinal()] : 0;
    }

    private int bodyLevel(int index) {
        int[] levels = shopState().getIntArray("bodyLevels");
        return levels.length > index ? levels[index] : 0;
    }

    private boolean bodyRent(int index) {
        int[] rents = shopState().getIntArray("bodyRent");
        return rents.length > index && rents[index] != 0;
    }

    private double manaValue() {
        return shopState().hasKey("mana") ? shopState().getDouble("mana") : 0.0D;
    }

    private double priceMultiplier() {
        return shopState().hasKey("priceMultiplier") ? shopState().getDouble("priceMultiplier") : 1.0D;
    }

    private double discount() {
        return shopState().hasKey("discount") ? shopState().getDouble("discount") : 0.0D;
    }

    private double shopPrice(double base) {
        return base * priceMultiplier() * (1.0D - discount());
    }

    private boolean hasCredit() {
        return shopState().getBoolean("hasCredit");
    }

    private double creditValue() {
        return shopState().getDouble("credit");
    }

    private long creditLeftMs() {
        return shopState().getLong("creditLeftMs");
    }

    private boolean decoyEnabled() {
        return shopState().getBoolean("decoyEnabled");
    }

    private boolean silenceEnabled() {
        return shopState().getBoolean("silenceEnabled");
    }

    private double bodyRentCost(int index) {
        return PocketConfig.bodyRentPerSec[index] * priceMultiplier();
    }

    private String lastLoot() {
        return shopState().getString("lastLoot");
    }

    private double totalDrain() {
        return shopState().getDouble("totalDrain");
    }

    private AbilityType[] list() {
        return TAB_ABIL[tab];
    }

    @Override
    public void initGui() {
        PocketDoorNetwork.requestState();
        buttonList.clear();
        int cx = width / 2;

        int tw = Math.min(72, (width - 8) / 6);
        for (int i = 0; i < 6; i++) {
            GuiButton b = new GuiButton(BTN_TAB + i, cx - tw * 3 + i * tw, 2, tw - 2, 16, Tr.format("pocketmod.tab." + TAB_KEYS[i]));
            b.enabled = i != tab;
            buttonList.add(b);
        }

        rowH = Math.max(14, Math.min(20, (height - 80 - top) / 7));
        int bx = cx + 10;
        if (tab <= 3) {
            AbilityType[] l = list();
            if (selected >= l.length) selected = 0;
            for (int i = 0; i < l.length; i++) {
                buttonList.add(new GuiButton(BTN_BUY + i, 8, top + i * 22, LIST_W, 20, cardLabel(l[i], i == selected)));
            }
            int px = panelX();
            GuiButton buy = new GuiButton(BTN_BUYSEL, px, height - 78, 160, 20, buyLabel(l[selected]));
            buy.enabled = true;
            buttonList.add(buy);
            if (tab == 1) {
                buttonList.add(new GuiButton(BTN_DOOR_MANAGE, px, top - 22, 256, 18,
                        Tr.format("pocketmod.gui.door_manage")));
            }
            if (l[selected] == AbilityType.DECOY) {
                buttonList.add(new GuiButton(BTN_DECOY, px + 156, height - 66, 120, 18, decoyLabel()));
            } else if (l[selected] == AbilityType.TELEPORT) {
                buttonList.add(new GuiButton(BTN_SILENCE, px + 156, height - 66, 120, 18, silenceLabel()));
            }
        } else if (tab == 4) {
            buttonList.add(new GuiButton(BTN_MORPHISM, bx, top - 22, 196, 18, morphismLabel()));
            // только эффекты тела — без наложений
            for (int i = 0; i < BodyEffects.IDS.length; i++) {
                int ry = top + i * rowH;
                buttonList.add(new GuiButton(BTN_PERM + i, bx, ry, 90, rowH - 2, permLabel(i)));
                buttonList.add(new GuiButton(BTN_RENT + i, bx + 94, ry, 96, rowH - 2, rentLabel(i)));
            }
        } else {
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
        if (!shopState().getBoolean("morphismAvailable")) return "Требуется Metamorph";
        return shopState().getBoolean("morphismUnlocked")
                ? "Каталог образов" : "Купить морфизм: 500";
    }

    private String buyLabel(AbilityType t) {
        EntityPlayer dp = dataPlayer();
        int lvl = abilityLevel(t);
        if (lvl >= t.maxLevel) return Tr.format("pocketmod.gui.max");
        int cost = t.costForNextLevel(lvl);
        if (cost < 0) return "-";
        double real = shopPrice(cost);
        String s = Tr.format(lvl == 0 ? "pocketmod.gui.buy" : "pocketmod.gui.upgrade", (int) Math.ceil(real));
        return (manaValue() < real ? TextFormatting.RED.toString() : "") + s;
    }

    private static final int LIST_W = 150;

    private int panelX() {
        return 8 + LIST_W + 10;
    }

    private String cardLabel(AbilityType t, boolean sel) {
        EntityPlayer dp = dataPlayer();
        int lvl = abilityLevel(t);
        String st = lvl == 0 ? TextFormatting.DARK_GRAY + "· " + Tr.format("pocketmod.gui.locked")
                : TextFormatting.GREEN + "· L" + lvl + "/" + t.maxLevel;
        String name = (sel ? TextFormatting.GOLD : TextFormatting.WHITE) + Tr.format(t.titleKey());
        return name + " " + st;
    }

    private String silenceLabel() {
        return Tr.format(silenceEnabled() ? "pocketmod.gui.silence_on" : "pocketmod.gui.silence_off");
    }


    private static final int COL_PANEL = 0xCC101418;
    private static final int COL_PANEL_EDGE = 0xFF2A3340;
    private static final int COL_ACCENT = 0xFFE0A040;
    private static final int COL_ACCENT_DIM = 0xFF8A6030;
    private static final int COL_ROW = 0xAA151A22;
    private static final int COL_ROW_SEL = 0xCC243044;
    private static final int COL_OK = 0xFF6BCB77;
    private static final int COL_MUTED = 0xFF8A93A0;

    private void drawFrame(int x1, int y1, int x2, int y2, int fill, int edge) {
        Gui.drawRect(x1, y1, x2, y2, fill);
        Gui.drawRect(x1, y1, x2, y1 + 1, edge);
        Gui.drawRect(x1, y2 - 1, x2, y2, edge);
        Gui.drawRect(x1, y1, x1 + 1, y2, edge);
        Gui.drawRect(x2 - 1, y1, x2, y2, edge);
    }

    private void drawAccentBar(int x, int y, int h) {
        Gui.drawRect(x, y, x + 3, y + h, COL_ACCENT);
    }

    private void drawManaChip(EntityPlayer dp, int cx, int y) {
        double mana = manaValue();
        double mult = priceMultiplier();
        double drain = totalDrain();
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
        AbilityType t = list()[Math.min(selected, list().length - 1)];
        int x = panelX();
        int w = Math.max(80, width - x - 10);
        int y0 = top - 4;
        int y1 = height - 72;
        drawFrame(x - 6, y0, x + w + 4, y1, COL_PANEL, COL_PANEL_EDGE);
        Gui.drawRect(x - 6, y0, x + w + 4, y0 + 2, COL_ACCENT_DIM);

        int y = top;
        fontRenderer.drawStringWithShadow(Tr.format(t.titleKey()), x, y, COL_ACCENT);
        int lvl = abilityLevel(t);
        String badge = lvl == 0
                ? TextFormatting.GRAY + Tr.format("pocketmod.gui.locked")
                : TextFormatting.GREEN + Tr.format("pocketmod.gui.level", lvl, t.maxLevel);
        fontRenderer.drawStringWithShadow(badge, x + w - fontRenderer.getStringWidth(badge), y, 0xFFFFFF);
        y += 14;
        Gui.drawRect(x, y, x + w, y + 1, COL_PANEL_EDGE);
        y += 6;

        for (Object o : fontRenderer.listFormattedStringToWidth(ShopInfo.desc(t), w)) {
            fontRenderer.drawStringWithShadow((String) o, x, y, 0xC8CDD5);
            y += 10;
        }
        y += 4;
        for (Object o : fontRenderer.listFormattedStringToWidth(
                TextFormatting.AQUA + Tr.format("pocketmod.gui.key_line", ShopInfo.keys(t)), w)) {
            fontRenderer.drawStringWithShadow((String) o, x, y, 0x7EC8E3);
            y += 10;
        }
        y += 8;
        fontRenderer.drawStringWithShadow(TextFormatting.YELLOW + "——", x, y, COL_MUTED);
        y += 12;

        for (int L = 1; L <= t.maxLevel; L++) {
            int cost = t.costForNextLevel(L - 1);
            String text;
            int color;
            int rowY = y - 1;
            if (L <= lvl) {
                Gui.drawRect(x - 2, rowY, x + w, rowY + 11, 0x33204030);
                text = Tr.format("pocketmod.gui.lvl_done", L, ShopInfo.level(t, L));
                color = COL_OK;
            } else {
                double real = cost > 0 ? shopPrice(cost) : 0;
                String pr = String.valueOf((int) Math.ceil(real));
                text = Tr.format(L == lvl + 1 ? "pocketmod.gui.lvl_next" : "pocketmod.gui.lvl_later", L, pr, ShopInfo.level(t, L));
                color = L == lvl + 1 ? 0xFFE8C070 : COL_MUTED;
                if (L == lvl + 1) {
                    Gui.drawRect(x - 2, rowY, x + w, rowY + 11, 0x33403020);
                }
            }
            for (Object o : fontRenderer.listFormattedStringToWidth(text, w)) {
                fontRenderer.drawStringWithShadow((String) o, x, y, color);
                y += 10;
            }
            y += 3;
        }
    }

    private String decoyLabel() {
        return Tr.format(decoyEnabled() ? "pocketmod.gui.decoy_on" : "pocketmod.gui.decoy_off");
    }

    private String permLabel(int i) {
        EntityPlayer dp = dataPlayer();
        int have = bodyLevel(i);
        if (have >= BodyEffects.maxPerm(i)) return Tr.format("pocketmod.gui.max");
        double real = shopPrice(PocketConfig.bodyPermCost[i]);
        String s = Tr.format("pocketmod.gui.perm", (int) Math.ceil(real));
        return (manaValue() < real ? TextFormatting.RED.toString() : "") + s;
    }

    private String rentLabel(int i) {
        EntityPlayer dp = dataPlayer();
        String c = String.format("%.1f", bodyRentCost(i));
        return Tr.format(bodyRent(i) ? "pocketmod.gui.rent_on" : "pocketmod.gui.rent_off", c);
    }

    private String stakeLabel(int i) {
        EntityPlayer dp = dataPlayer();
        double real = PocketConfig.lootStakes[i] * priceMultiplier();
        return (manaValue() < real ? TextFormatting.RED.toString() : "") + Tr.format("pocketmod.loot.spin", (int) Math.ceil(real));
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
        boolean hasCredit = hasCredit();

        // верхняя плашка с маной
        drawManaChip(dp, cx, 20);

        if (hasCredit) {
            drawCenteredString(fontRenderer,
                    Tr.format("pocketmod.gui.credit_active", (long) Math.ceil(creditValue()),
                            formatTime(creditLeftMs())),
                    cx, 52, 0xFF8888);
        } else {
            double disc = 1.0 - priceMultiplier();
            if (disc > 0.001) {
                drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.discount", (int) Math.round(disc * 100)), cx, 52, COL_OK);
            }
        }

        // подложка вкладок
        int tw = Math.min(72, (width - 8) / 6);
        int tabY = 2;
        for (int i = 0; i < 6; i++) {
            int tx = cx - tw * 3 + i * tw;
            if (i == tab) {
                Gui.drawRect(tx, tabY, tx + tw - 2, tabY + 18, 0xCC2A2218);
                Gui.drawRect(tx, tabY + 16, tx + tw - 2, tabY + 18, COL_ACCENT);
            } else {
                Gui.drawRect(tx, tabY, tx + tw - 2, tabY + 16, 0x66101820);
            }
        }

        int lx = 8;
        if (tab <= 3) {
            // список способностей слева
            int listH = list().length * 22 + 8;
            drawFrame(4, top - 6, 8 + LIST_W + 4, top - 6 + listH, COL_PANEL, COL_PANEL_EDGE);
            AbilityType[] l = list();
            for (int i = 0; i < l.length; i++) {
                int ry = top + i * 22;
                if (i == selected) {
                    Gui.drawRect(6, ry, 6 + LIST_W, ry + 20, COL_ROW_SEL);
                    drawAccentBar(6, ry, 20);
                } else {
                    Gui.drawRect(6, ry, 6 + LIST_W, ry + 20, COL_ROW);
                }
            }
            drawAbilityPanel(dp);
        } else if (tab == 4) {
            drawFrame(4, top - 6, width - 4, height - 72, COL_PANEL, COL_PANEL_EDGE);
            for (int i = 0; i < BodyEffects.IDS.length; i++) {
                int y = top + i * rowH;
                int have = bodyLevel(i);
                String st = have > 0 ? TextFormatting.GREEN + " x" + have : "";
                fontRenderer.drawStringWithShadow(Tr.format("pocketmod.body." + BodyEffects.IDS[i]) + st, lx + 6, y + (rowH - 10) / 2, 0xE8ECF0);
            }
        } else {
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
                    (long) Math.ceil(creditValue()), formatTime(creditLeftMs())),
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
                Math.max(0, PocketConfig.lootPity - shopState().getInteger("lootPity"))), x, y, 0xAAAAAA);
        y += 12;
        for (Loot.Out o : Loot.Out.values()) {
            fontRenderer.drawStringWithShadow(
                    String.format("%4.1f%%  ", o.p * 100.0) + Tr.format("pocketmod.loot.out." + o.id), x, y, 0xBBBBBB);
            y += 9;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && inSlider(mouseX, mouseY) && !hasCredit()) {
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
        if (button.id == BTN_CLOSE) {
            mc.player.closeScreen();
            return;
        }
        if (button.id >= BTN_TAB && button.id < BTN_TAB + 6) {
            tab = button.id - BTN_TAB;
            selected = 0;
            initGui();
            return;
        }
        if (button.id >= BTN_BUY && button.id < BTN_BUY + 10 && tab <= 3) {
            selected = button.id - BTN_BUY;
            initGui();
            return;
        }

        if (button.id == BTN_DOOR_MANAGE) {
            mc.displayGuiScreen(new PocketDoorGui(player));
            return;
        }
        if (button.id == BTN_MORPHISM) {
            if (!PocketConfig.morphEnabled || !shopState().getBoolean("morphismAvailable")) return;
            if (shopState().getBoolean("morphismUnlocked")) {
                mc.displayGuiScreen(new MorphCatalogGui(player));
            } else {
                PocketDoorNetwork.upgradeAbility(AbilityType.MORPHISM);
            }
            return;
        }
        if (button.id == BTN_TAKE) {
            if (!hasCredit()) PocketDoorNetwork.takeCredit(creditAmount);
            return;
        }
        if (button.id == BTN_DECOY) {
            PocketDoorNetwork.toggleDecoy();
            return;
        }
        if (button.id == BTN_SILENCE) {
            PocketDoorNetwork.toggleSilence();
            return;
        }
        if (button.id == BTN_BLOOD) {
            PocketDoorNetwork.upgradeAbility(AbilityType.BLOODSUCKER);
            return;
        }
        if (button.id == BTN_BUYSEL && tab <= 3) {
            AbilityType[] abilities = list();
            if (abilities.length > 0) PocketDoorNetwork.upgradeAbility(abilities[Math.min(selected, abilities.length - 1)]);
            return;
        }
        if (button.id >= BTN_PERM && button.id < BTN_PERM + 5) {
            PocketDoorNetwork.buyBodyEffect(button.id - BTN_PERM);
            return;
        }
        if (button.id >= BTN_RENT && button.id < BTN_RENT + 5) {
            PocketDoorNetwork.toggleBodyRent(button.id - BTN_RENT);
            return;
        }
        if (button.id >= BTN_STAKE && button.id < BTN_STAKE + 3) {
            if (spinTicks > 0) return;
            int tier = button.id - BTN_STAKE;
            double real = PocketConfig.lootStakes[tier] * priceMultiplier();
            if (manaValue() < real) return;
            spinTicks = 30;
            shownResult = "";
            PocketDoorNetwork.spinLoot(tier);
        }
    }

    private int refreshTicks = 0;

    @Override
    public void updateScreen() {
        if (++refreshTicks >= 10) {
            refreshTicks = 0;
            PocketDoorNetwork.requestState();
        }
        EntityPlayer dp = dataPlayer();
        boolean hasCredit = hasCredit();
        if (spinTicks > 0) {
            spinTicks--;
            if (spinTicks % 3 == 0) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.4F, 1.4F);
            }
            if (spinTicks == 0) {
                shownResult = lastLoot();
            }
        }
        for (GuiButton b : buttonList) {
            if (b.id >= BTN_BUY && b.id < BTN_BUY + 10 && tab <= 3) {
                int idx = b.id - BTN_BUY;
                b.displayString = cardLabel(list()[idx], idx == selected);
            } else if (b.id == BTN_MORPHISM) {
                b.displayString = morphismLabel();
                b.enabled = PocketConfig.morphEnabled && shopState().getBoolean("morphismAvailable");
            } else if (b.id == BTN_BUYSEL) {
                AbilityType[] lst = list();
                if (lst.length == 0) {
                    b.enabled = false;
                    b.displayString = "-";
                } else {
                    AbilityType t = lst[Math.min(selected, lst.length - 1)];
                    int lvl = abilityLevel(t);
                    int cost = t.costForNextLevel(lvl);
                    b.displayString = buyLabel(t);
                    // активна пока не MAX; нехватка маны не блокирует клик (сообщение при покупке)
                    b.enabled = lvl < t.maxLevel && cost > 0;
                }
            } else if (b.id == BTN_SILENCE) {
                b.displayString = silenceLabel();
                b.enabled = abilityLevel(AbilityType.TELEPORT) >= PocketConfig.silenceMinLevel;
            } else if (b.id == BTN_DECOY) {
                b.displayString = decoyLabel();
                b.enabled = abilityLevel(AbilityType.DECOY) > 0;
            } else if (b.id >= BTN_PERM && b.id < BTN_PERM + 5) {
                int i = b.id - BTN_PERM;
                b.displayString = permLabel(i);
                b.enabled = bodyLevel(i) < BodyEffects.maxPerm(i);
            } else if (b.id >= BTN_RENT && b.id < BTN_RENT + 5) {
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
