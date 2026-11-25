package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Mouse;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Discovery-aware shop: categories, selectable cards and a separate purchase action. */
@SideOnly(Side.CLIENT)
public final class ExileShopGui extends GuiScreen {
    private static int category=Math.max(0,Math.min(ShopCatalog.TABS.length,ClientViewState.number("exile.category",0))),
        offset=ClientViewState.number("exile.offset",0), detailScroll=ClientViewState.number("exile.detail",0);
    private int detailMax, ticks;
    private static String selected = ClientViewState.text("exile.selected","base_inventory");
    private final List<String> entries = new ArrayList<>();
    private int left, right, top, bottom, listWidth;

    private int rows() { return Math.max(1, (bottom - top) / 38); }
    private int panelX() { return left + listWidth + 10; }
    private int panelWidth() { return right - panelX(); }
    private String categoryKey(int c) {
        return c == 0 ? "pocketmod.shop.base" : "pocketmod.tab." + ShopCatalog.TABS[c - 1].id;
    }

    @Override
    public void initGui() {
        if (mc.player == null) return;
        int w = Math.min(760, width - 16);
        left = (width - w) / 2;
        right = left + w;
        top = 82;
        bottom = height - 36;
        listWidth = Math.max(110, Math.min(220, w * 2 / 5));
        refresh();
    }

    private void refresh() {
        EntityPlayer p = mc.player;
        entries.clear();
        if (category == 0) {
            entries.add("base_inventory");
            entries.add("base_weakness");
            entries.add("base_compass");
        } else {
            for (ShopCatalog.Card card : ShopCatalog.TABS[category - 1].cards) {
                if (card.group || card.opensPocketAccess()) continue;
                String id = card.ability != null ? card.ability.id : "body_" + BodyEffects.IDS[card.effect];
                if (ExileProgression.available(p, id)) entries.add(id);
                if (card.ability == AbilityType.INVISIBILITY && ExileProgression.available(p, "decoy"))
                    entries.add("decoy");
            }
        }
        if (!entries.contains(selected)) {
            selected = entries.isEmpty() ? null : entries.get(0);
            detailScroll = 0;
        }
        offset = Math.max(0, Math.min(offset, Math.max(0, entries.size() - rows())));
        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        for (int c = 0; c < 6; c++) {
            int x = left + (right - left) * c / 6;
            int end = left + (right - left) * (c + 1) / 6;
            ShopStyle.Button b = new ShopStyle.Button(10 + c, x, 52, end - x - 3, 22,
                    Tr.format(categoryKey(c)));
            b.active = category == c;
            buttonList.add(b);
        }
        for (int i = 0; i < rows() && offset + i < entries.size(); i++) {
            String id = entries.get(offset + i);
            ShopStyle.Button b = new ShopStyle.Button(100 + i, left, top + i * 38,
                    listWidth, 35, title(id));
            b.active = id.equals(selected);
            b.icon = icon(id);
            b.level = level(id);
            b.max = max(id);
            b.subtitle = id.equals("base_compass") ? Tr.format("pocketmod.shop.navigation")
                    : Tr.format("pocketmod.gui.level", b.level, b.max);
            buttonList.add(b);
        }
        if (selected != null) {
            int price = price(selected);
            ShopStyle.Button buy = new ShopStyle.Button(3, panelX() + 8, bottom - 27,
                    panelWidth() - 16, 20, price < 0 ? Tr.format("pocketmod.gui.max")
                    : Tr.format("pocketmod.gui.buy", price));
            buy.primary = true;
            buy.enabled = price >= 0 && Mana.get(mc.player) >= price;
            if (price >= 0 && !buy.enabled) buy.displayString = Tr.format("pocketmod.shop.need_mana", price);
            buttonList.add(buy);
        }
        int footer = height - 27;
        ShopStyle.Button prev = new ShopStyle.Button(4, left, footer, 22, 19, "<");
        prev.enabled = offset > 0;
        buttonList.add(prev);
        ShopStyle.Button next = new ShopStyle.Button(5, left + 26, footer, 22, 19, ">");
        next.enabled = offset + rows() < entries.size();
        buttonList.add(next);
        int closeWidth = Math.min(82, (right - left) / 4);
        buttonList.add(new ShopStyle.Button(6, right - closeWidth * 2 - 5, footer,
                closeWidth, 19, Tr.format("pocketmod.exile.verdict")));
        buttonList.add(new ShopStyle.Button(0, right - closeWidth, footer,
                closeWidth, 19, Tr.format("pocketmod.gui.close")));
    }

    private static int bodyIndex(String id) {
        for (int i = 0; i < BodyEffects.IDS.length; i++)
            if (id.equals("body_" + BodyEffects.IDS[i])) return i;
        throw new IllegalArgumentException(id);
    }
    private static AbilityType type(String id) {
        for (AbilityType t : AbilityType.values()) if (t.id.equals(id)) return t;
        throw new IllegalArgumentException(id);
    }
    private boolean base(String id) { return id.startsWith("base_"); }
    private String icon(String id) {
        if (id.equals("base_inventory")) return "warehouse";
        if (id.equals("base_weakness")) return "body_strength";
        if (id.equals("base_compass")) return "scanner";
        return id;
    }
    private String title(String id) {
        if (base(id)) return Tr.format("pocketmod.shop." + id);
        return Tr.format(id.startsWith("body_") ? "pocketmod.body." + id.substring(5) : type(id).titleKey());
    }
    private int level(String id) {
        EntityPlayer p = mc.player;
        if (id.equals("base_inventory")) return ExileProgression.unlockedInventorySlots(p);
        if (id.equals("base_weakness")) return ExileMode.state(p).getInteger("weakness_level");
        if (id.equals("base_compass")) return 0;
        return id.startsWith("body_") ? BodyEffects.perm(p, bodyIndex(id))
                : AbilityStorage.getUpgradeLevel(p, type(id));
    }
    private int max(String id) {
        if (id.equals("base_inventory")) return 36;
        if (id.equals("base_weakness")) return 2;
        if (id.equals("base_compass")) return 0;
        return id.startsWith("body_") ? BodyEffects.maxPerm(bodyIndex(id)) : type(id).maxLevel;
    }
    private int price(String id) {
        EntityPlayer p = mc.player;
        if (id.equals("base_inventory") || id.equals("base_weakness")) {
            int raw = ExileProgression.basePrice(p, id.equals("base_inventory") ? 0 : 1);
            return raw < 0 ? -1 : id.equals("base_inventory") ? raw
                    : (int) Math.ceil(Mana.shopPrice(p, raw));
        }
        if (id.equals("base_compass")) {
            if (ExileMode.state(p).getInteger("trial_route_completed") >= 3) return -1;
            for (net.minecraft.item.ItemStack stack : p.inventory.mainInventory)
                if (ExileTrials.currentCompass(p, stack)) return 0;
            if (ExileTrials.currentCompass(p, p.getHeldItemOffhand())) return 0;
            return (int) Math.ceil(Mana.shopPrice(p, ExileConfig.compassPrice));
        }
        int level = level(id);
        if (level >= max(id)) return -1;
        int raw = id.startsWith("body_") ? BodyEffects.permCost(bodyIndex(id), level)
                : type(id).costForNextLevel(level);
        return raw < 0 ? -1 : (int) Math.ceil(Mana.shopPrice(p, raw));
    }

    @Override
    public void updateScreen() {
        if (mc.player != null && ++ticks % 10 == 0) refresh();
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawGradientRect(0, 0, width, height, 0xED0B1020, 0xF2192333);
        if (mc.player == null) return;
        ShopStyle.panel(left, 10, right - left, 35, ShopStyle.BG, ShopStyle.EDGE);
        drawRect(left, 10, left + 3, 45, ShopStyle.GOLD);
        fontRenderer.drawStringWithShadow(Tr.format("pocketmod.exile.shop"), left + 11, 17, ShopStyle.GOLD);
        String balance = Tr.format("pocketmod.exile.mana", (long) Mana.get(mc.player));
        fontRenderer.drawStringWithShadow(balance, right - 10 - fontRenderer.getStringWidth(balance), 28, ShopStyle.MANA);
        fontRenderer.drawStringWithShadow(Tr.format("pocketmod.shop.subtitle"), left + 11, 29, ShopStyle.MUTED);
        ShopStyle.panel(panelX(), top, panelWidth(), bottom - top, ShopStyle.PANEL, ShopStyle.EDGE);
        if (selected != null) drawDetails();
        else {
            int x = panelX() + 10, y = top + 13;
            fontRenderer.drawStringWithShadow(Tr.format("pocketmod.shop.undiscovered"), x, y, ShopStyle.GOLD);
            y += 20;
            for (String line : fontRenderer.listFormattedStringToWidth(Tr.format("pocketmod.exile.empty_shop"), panelWidth() - 20)) {
                if (y + 9 > bottom - 8) break;
                fontRenderer.drawStringWithShadow(line, x, y, ShopStyle.MUTED);
                y += 11;
            }
        }
        String count = entries.isEmpty() ? "0 / 0" : (offset + 1) + "–" + Math.min(entries.size(), offset + rows()) + " / " + entries.size();
        fontRenderer.drawStringWithShadow(count, left + 55, height - 21, ShopStyle.MUTED);
        super.drawScreen(mx, my, partial);
        for (GuiButton b : buttonList) {
            if (b.isMouseOver() && (b.id >= 100 || b.id >= 10 && b.id < 16)) {
                drawHoveringText(b.displayString, mx, my);
                break;
            }
        }
    }

    private void drawDetails() {
        int x = panelX() + 10, w = panelWidth() - 20;
        ShopStyle.icon(mc, icon(selected), x, top + 9, 24);
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(title(selected), Math.max(1, w - 32)),
                x + 32, top + 10, ShopStyle.GOLD);
        int current = level(selected), max = max(selected);
        String status = max > 0 ? Tr.format("pocketmod.gui.level", current, max) : Tr.format("pocketmod.shop.navigation");
        fontRenderer.drawStringWithShadow(status, x + 32, top + 23, ShopStyle.MUTED);
        ShopStyle.progress(x, top + 39, w, current, max);
        int textTop = top + 49, textBottom = bottom - 34;
        List<String> paragraphs = new ArrayList<>();
        if (base(selected)) paragraphs.add(Tr.format("pocketmod.shop." + selected + ".desc"));
        else if (selected.startsWith("body_")) {
            int effect = bodyIndex(selected);
            if (effect == BodyEffects.REGENERATION) {
                for (int i = 1; i <= max; i++) paragraphs.add((i <= current ? "\u00a7a" : i == current + 1 ? "\u00a7e" : "\u00a77")
                        + i + ". " + Tr.format("pocketmod.shop.regen." + i, PocketConfig.regenSaturationPercent));
            } else paragraphs.add(Tr.format("pocketmod.shop.effect." + BodyEffects.IDS[effect], PocketConfig.heartsRent));
        } else {
            AbilityType t = type(selected);
            paragraphs.add(ShopInfo.desc(t));
            paragraphs.add("\u00a7b" + Tr.format("pocketmod.gui.key_line", ShopInfo.keys(t)));
            if (current < max) {
                paragraphs.add("\u00a7e" + Tr.format("pocketmod.gui.next_level_changes", current + 1));
                paragraphs.addAll(ShopInfo.levelChanges(t, current, mc.player));
            }
            for (int i = 1; i <= max; i++) paragraphs.add((i <= current ? "\u00a7a" : i == current + 1 ? "\u00a7e" : "\u00a77")
                    + i + ". " + ShopInfo.level(t, i));
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : paragraphs) {
            lines.addAll(fontRenderer.listFormattedStringToWidth(paragraph, Math.max(1, w - 6)));
            lines.add("");
        }
        detailMax = Math.max(0, lines.size() * 11 - Math.max(0, textBottom - textTop));
        detailScroll = Math.min(detailScroll, detailMax);
        int y = textTop - detailScroll;
        for (String line : lines) {
            if (y >= textTop && y + fontRenderer.FONT_HEIGHT <= textBottom)
                fontRenderer.drawStringWithShadow(line, x, y, ShopStyle.TEXT);
            y += 11;
        }
        if (detailMax > 0 && textBottom > textTop + 8) {
            int barY = textTop + (textBottom - textTop - 8) * detailScroll / detailMax;
            drawRect(right - 5, textTop, right - 3, textBottom, ShopStyle.EDGE);
            drawRect(right - 5, barY, right - 3, barY + 8, ShopStyle.GOLD);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (y < top || y >= bottom) return;
        int direction = wheel > 0 ? -1 : 1;
        if (x >= left && x < panelX()) {
            offset = Math.max(0, Math.min(offset + direction, Math.max(0, entries.size() - rows())));
            rebuild();
        } else if (x >= panelX() && x < right) {
            detailScroll = Math.max(0, Math.min(detailMax, detailScroll + direction * 22));
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id == 0) { mc.displayGuiScreen(null); return; }
        if (b.id == 6) { mc.displayGuiScreen(new ExileVerdictGui(this)); return; }
        if (b.id >= 10 && b.id < 16) {
            category = b.id - 10;
            offset = detailScroll = 0;
            refresh();
            return;
        }
        if (b.id == 4 || b.id == 5) {
            offset = Math.max(0, Math.min(offset + (b.id == 4 ? -rows() : rows()), Math.max(0, entries.size() - rows())));
            rebuild();
            return;
        }
        if (b.id >= 100) {
            int index = offset + b.id - 100;
            if (index < entries.size()) selected = entries.get(index);
            detailScroll = 0;
            rebuild();
            return;
        }
        if (b.id != 3 || selected == null || price(selected) < 0 || Mana.get(mc.player) < price(selected)) return;
        if (selected.equals("base_inventory") || selected.equals("base_weakness")) {
            NetworkHandler.action(NetAction.EXILE_BASE, selected.equals("base_inventory") ? 0 : 1, 0, 0, "");
        } else if (selected.equals("base_compass")) {
            NetworkHandler.action(NetAction.EXILE_COMPASS, 0, 0, 0, "");
        } else {
            NetworkHandler.action(selected.startsWith("body_") ? NetAction.BUY_BODY_PERMANENT : NetAction.UPGRADE_ABILITY,
                    selected.startsWith("body_") ? bodyIndex(selected) : type(selected).ordinal(), 0, 0, "");
        }
    }

    @Override
    public void onGuiClosed(){ClientViewState.save("exile.","category",category,"offset",offset,"detail",detailScroll,"selected",selected==null?"":selected);}
    @Override
    public boolean doesGuiPauseGame() { return true; }
}
