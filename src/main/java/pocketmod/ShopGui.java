package pocketmod;

import java.io.IOException;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ShopGui extends GuiScreen {

    private static final int BTN_TAB = 10;
    private static final int BTN_CLOSE = 0;
    private static final int BTN_TAKE = 1;
    private static final int BTN_SILENCE = 151;
    private static final int BTN_BUYSEL = 160;
    private static final int BTN_PERM = 200;
    private static final int BTN_RENT = 210;
    private static final int BTN_STAKE = 220;

    private static final String[] TAB_KEYS = { "move", "space", "sense", "guard", "body", "luck" };
    private static final AbilityType[][] TAB_ABIL = {
            { AbilityType.TELEPORT, AbilityType.FLIGHT, AbilityType.SOFTFALL, AbilityType.MAGNET },
            { AbilityType.POCKET, AbilityType.TOTEM, AbilityType.SLOW },
            { AbilityType.SPECTOR, AbilityType.THERMAL },
            { AbilityType.INVISIBILITY, AbilityType.DECOY, AbilityType.SHIELD },
            { AbilityType.BLOODSUCKER }, {} };

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
        EntityPlayerMP mp = ClientHandler.sp();
        return mp != null ? mp : player;
    }

    private AbilityType[] currentTab() {
        return TAB_ABIL[tab];
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            buttonList.add(new GuiButton(BTN_TAB + i, 8 + i * 52, 8, 50, 16, Tr.format("pocketmod.gui.tab." + TAB_KEYS[i])));
        }
        AbilityType[] l = currentTab();
        rowH = Math.max(14, Math.min(20, (height - 100 - top) / Math.max(1, l.length + 1)));
        if (selected >= l.length) selected = 0;
        buttonList.add(new GuiButton(BTN_BUYSEL, cx + 40, height - 48, 100, 18, "..."));
        buttonList.add(new GuiButton(BTN_TAKE, cx - 120, height - 48, 70, 18, Tr.format("pocketmod.gui.take")));
        buttonList.add(new GuiButton(BTN_CLOSE, cx + 150, height - 48, 50, 18, Tr.format("pocketmod.gui.close")));
        if (creditAmount == 0) creditAmount = PocketConfig.creditMin;
        sliderW = 120;
        sliderH = 10;
        sliderX = cx - 160;
        sliderY = height - 28;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        EntityPlayer dp = dataPlayer();
        int cx = width / 2;
        double mana = Mana.get(dp);
        double mult = Mana.getPriceMultiplier(dp);
        double drain = Abilities.totalDrainPerSec(dp);

        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.title"), cx, 28, 0xFFE080);
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.mana", (long) mana, String.format("%.2f", mult)), cx, 40, 0xAAAAFF);
        String drainLine = drain > 0.001
                ? Tr.format("pocketmod.gui.drain", String.format("%.1f", drain), (long) (mana / drain / 60.0))
                : Tr.format("pocketmod.gui.drain_none");
        drawCenteredString(fontRenderer, drainLine, cx, 52, 0xCCCCCC);

        AbilityType[] l = currentTab();
        for (int i = 0; i < l.length; i++) {
            AbilityType t = l[i];
            int y = top + i * rowH;
            int lvl = AbilityStorage.getLevel(dp, t);
            String status = lvl == 0 ? TextFormatting.GRAY + Tr.format("pocketmod.gui.locked")
                    : TextFormatting.GREEN + Tr.format("pocketmod.gui.level", lvl, t.maxLevel);
            int col = (i == selected) ? 0xFFFFFF : 0xCCCCCC;
            fontRenderer.drawStringWithShadow((i == selected ? "> " : "  ") + Tr.format(t.titleKey()) + "  " + status, 20, y + (rowH - 10) / 2, col);
        }

        if (l.length > 0 && selected < l.length) {
            AbilityType t = l[selected];
            int lx = cx - 10;
            fontRenderer.drawStringWithShadow(Tr.format(t.titleKey()), lx, top, 0xFFE080);
            fontRenderer.drawSplitString(ShopInfo.desc(t), lx, top + 14, 180, 0xCCCCCC);
            fontRenderer.drawStringWithShadow(ShopInfo.keys(t), lx, top + 50, 0xAAAAAA);
            int lvl = AbilityStorage.getLevel(dp, t);
            for (int lv = 1; lv <= t.maxLevel; lv++) {
                String line = ShopInfo.level(t, lv);
                int c = lv <= lvl ? 0x88FF88 : 0xAAAAAA;
                fontRenderer.drawStringWithShadow("L" + lv + ": " + line, lx, top + 64 + (lv - 1) * 12, c);
            }
        }

        if (!Mana.hasCredit(dp)) {
            int repay = (int) Math.round(creditAmount * (1.0 + PocketConfig.creditInterest));
            fontRenderer.drawStringWithShadow(Tr.format("pocketmod.gui.credit_amount", creditAmount, repay, PocketConfig.creditDurationSec / 60), sliderX, sliderY - 12, 0xCCCCCC);
        }
        Gui.drawRect(sliderX, sliderY + 3, sliderX + sliderW, sliderY + sliderH - 3, 0xFF444444);
        int kx = sliderX + (int) (((creditAmount - PocketConfig.creditMin) / (double) Math.max(1, PocketConfig.creditMax - PocketConfig.creditMin)) * (sliderW - 6));
        Gui.drawRect(kx, sliderY, kx + 6, sliderY + sliderH, Mana.hasCredit(dp) ? 0xFF888888 : 0xFFFFD34D);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) {
            AbilityType[] l = currentTab();
            for (int i = 0; i < l.length; i++) {
                int y = top + i * rowH;
                if (mouseX >= 20 && mouseX < width / 2 - 20 && mouseY >= y && mouseY < y + rowH) {
                    selected = i;
                    return;
                }
            }
            if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= sliderY - 2 && mouseY <= sliderY + sliderH + 2 && !Mana.hasCredit(dataPlayer())) {
                dragging = true;
                double f = (mouseX - sliderX) / (double) sliderW;
                f = Math.max(0, Math.min(1, f));
                creditAmount = PocketConfig.creditMin + (int) Math.round(f * (PocketConfig.creditMax - PocketConfig.creditMin));
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragging) {
            double f = (mouseX - sliderX) / (double) sliderW;
            f = Math.max(0, Math.min(1, f));
            creditAmount = PocketConfig.creditMin + (int) Math.round(f * (PocketConfig.creditMax - PocketConfig.creditMin));
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
        if (button.id >= BTN_TAB && button.id < BTN_TAB + TAB_KEYS.length) {
            tab = button.id - BTN_TAB;
            selected = 0;
            initGui();
            return;
        }
        net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        if (button.id == BTN_TAKE) {
            if (Mana.hasCredit(dataPlayer())) return;
            final int amt = creditAmount;
            server.addScheduledTask(() -> {
                EntityPlayerMP mp = server.getPlayerList().getPlayerByUUID(player.getUniqueID());
                if (mp != null && !Mana.hasCredit(mp)) {
                    double repay = Mana.takeCredit(mp, amt);
                    if (repay > 0) {
                        mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_taken", (int) Math.ceil(repay), PocketConfig.creditDurationSec / 60), true);
                    }
                }
            });
            return;
        }
        if (button.id == BTN_BUYSEL) {
            AbilityType[] l = currentTab();
            if (l.length == 0 || selected >= l.length) return;
            final AbilityType t = l[selected];
            server.addScheduledTask(() -> {
                EntityPlayerMP mp = server.getPlayerList().getPlayerByUUID(player.getUniqueID());
                if (mp != null) AbilityStorage.tryUpgrade(mp, t);
            });
        }
    }

    @Override
    public void updateScreen() {
        EntityPlayer dp = dataPlayer();
        AbilityType[] l = currentTab();
        for (GuiButton b : buttonList) {
            if (b.id == BTN_BUYSEL) {
                if (l.length == 0 || selected >= l.length) {
                    b.displayString = "-";
                    b.enabled = false;
                } else {
                    AbilityType t = l[selected];
                    int lvl = AbilityStorage.getLevel(dp, t);
                    if (lvl >= t.maxLevel) {
                        b.displayString = Tr.format("pocketmod.gui.max");
                        b.enabled = false;
                    } else {
                        int cost = t.costForNextLevel(lvl);
                        double real = cost * Mana.getPriceMultiplier(dp);
                        b.displayString = (Mana.get(dp) < real ? TextFormatting.RED.toString() : "") + Tr.format(lvl == 0 ? "pocketmod.gui.buy" : "pocketmod.gui.upgrade", (int) Math.ceil(real));
                        b.enabled = cost > 0;
                    }
                }
            } else if (b.id == BTN_TAKE) {
                b.enabled = !Mana.hasCredit(dp);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
