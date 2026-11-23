package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ExileShopGui extends GuiScreen {
    private int page;
    private int ticks;
    private final List<String> entries = new ArrayList<>();

    private int rows() {
        return Math.max(1, (height - 155) / 24);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        entries.clear();
        if (mc.player == null) return;
        EntityPlayer p = mc.player;
        int x = width / 2 - 150;
        int inventoryPrice = ExileProgression.basePrice(p, 0),
                weaknessPrice = ExileProgression.basePrice(p, 1);
        GuiButton inv =
                new GuiButton(
                        1,
                        x,
                        52,
                        145,
                        20,
                        I18n.format(
                                "pocketmod.exile.inventory",
                                ExileProgression.slots(p),
                                inventoryPrice < 0 ? "MAX" : inventoryPrice));
        inv.enabled = inventoryPrice >= 0;
        buttonList.add(inv);
        GuiButton weak =
                new GuiButton(
                        2,
                        x + 155,
                        52,
                        145,
                        20,
                        I18n.format(
                                "pocketmod.exile.weakness",
                                ExileMode.state(p).getInteger("weakness_level"),
                                weaknessPrice < 0 ? "MAX" : weaknessPrice));
        weak.enabled = weaknessPrice >= 0;
        buttonList.add(weak);
        buttonList.add(
                new GuiButton(
                        3,
                        x,
                        78,
                        300,
                        20,
                        I18n.format(
                                "pocketmod.exile.buy_compass",
                                ExileProgression.config(
                                        p, "compass_price", ExileConfig.compassPrice))));
        for (AbilityType t : AbilityType.values())
            if (ExileProgression.available(p, t.id)) entries.add(t.id);
        for (String id : BodyEffects.IDS)
            if (ExileProgression.available(p, "body_" + id)) entries.add("body_" + id);
        page = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / rows())));
        for (int i = 0; i < rows() && page * rows() + i < entries.size(); i++) {
            String id = entries.get(page * rows() + i);
            int level, price, max;
            String title;
            if (id.startsWith("body_")) {
                int index = bodyIndex(id);
                level = BodyEffects.perm(p, index);
                max = BodyEffects.maxPerm(index);
                price = level >= max ? -1 : BodyEffects.permCost(index, level);
                title = I18n.format("pocketmod.body." + id.substring(5));
            } else {
                AbilityType type = type(id);
                level = AbilityStorage.getUpgradeLevel(p, type);
                max = type.maxLevel;
                price = type.costForNextLevel(level);
                title = I18n.format(type.titleKey());
            }
            GuiButton b =
                    new GuiButton(
                            100 + i,
                            x,
                            122 + i * 24,
                            300,
                            20,
                            title
                                    + "  "
                                    + level
                                    + "/"
                                    + max
                                    + "  "
                                    + (price < 0
                                            ? "MAX"
                                            : (int) Math.ceil(Mana.shopPrice(p, price)) + " ✦"));
            b.enabled = price >= 0;
            buttonList.add(b);
        }
        buttonList.add(new GuiButton(4, x, height - 28, 55, 20, "<"));
        buttonList.add(new GuiButton(5, x + 60, height - 28, 55, 20, ">"));
        buttonList.add(
                new GuiButton(
                        6, x + 120, height - 28, 85, 20, I18n.format("pocketmod.exile.verdict")));
        buttonList.add(new GuiButton(0, x + 210, height - 28, 90, 20, I18n.format("gui.done")));
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

    @Override
    public void updateScreen() {
        if (++ticks % 10 == 0) initGui();
    }

    @Override
    public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        drawCenteredString(
                fontRenderer, I18n.format("pocketmod.exile.shop"), width / 2, 12, 0xFFFFFF);
        drawCenteredString(
                fontRenderer,
                I18n.format("pocketmod.exile.mana", (long) Mana.get(mc.player)),
                width / 2,
                30,
                0x80BFFF);
        drawCenteredString(
                fontRenderer,
                I18n.format(
                        entries.isEmpty()
                                ? "pocketmod.exile.empty_shop"
                                : "pocketmod.exile.opened_shop"),
                width / 2,
                106,
                0xBBBBBB);
        super.drawScreen(x, y, partial);
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id == 0) {
            mc.displayGuiScreen(null);
            return;
        }
        if (b.id == 6) {
            mc.displayGuiScreen(new ExileVerdictGui(this));
            return;
        }
        if (b.id == 4) {
            page = Math.max(0, page - 1);
            initGui();
            return;
        }
        if (b.id == 5) {
            page++;
            initGui();
            return;
        }
        if (b.id == 1 || b.id == 2) NetworkHandler.action(NetAction.EXILE_BASE, b.id - 1, 0, 0, "");
        if (b.id == 3) NetworkHandler.action(NetAction.EXILE_COMPASS, 0, 0, 0, "");
        if (b.id >= 100) {
            String id = entries.get(page * rows() + b.id - 100);
            NetworkHandler.action(
                    id.startsWith("body_")
                            ? NetAction.BUY_BODY_PERMANENT
                            : NetAction.UPGRADE_ABILITY,
                    id.startsWith("body_") ? bodyIndex(id) : type(id).ordinal(),
                    0,
                    0,
                    "");
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
