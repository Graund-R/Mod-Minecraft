package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ShopGui extends GuiScreen {

    private static final int BTN_BUY_BASE = 100;
    private static final int BTN_TAKE = 1;
    private static final int BTN_CLOSE = 0;

    private final EntityPlayer player;
    private final List<AbilityType> list = new ArrayList<AbilityType>();

    private int creditAmount = 0;
    private boolean dragging = false;
    private int sliderX, sliderY, sliderW, sliderH;
    private int top = 58;
    private int rowH = 20;

    public ShopGui(EntityPlayer player) {
        this.player = player;
        list.add(AbilityType.POCKET);
        list.add(AbilityType.TELEPORT);
        list.add(AbilityType.INVISIBILITY);
        list.add(AbilityType.SLOW);
        list.add(AbilityType.DECOY);
        list.add(AbilityType.TOTEM);
        list.add(AbilityType.SPECTOR);
        list.add(AbilityType.THERMAL);
        list.add(AbilityType.SHIELD);
        list.add(AbilityType.FLIGHT);
    }

    /** Данные хранятся на серверном игроке встроенного сервера. */
    private EntityPlayer dataPlayer() {
        EntityPlayerMP mp = ClientHandler.sp();
        return mp != null ? mp : player;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        rowH = Math.max(14, Math.min(20, (height - 78 - top) / list.size()));
        for (int i = 0; i < list.size(); i++) {
            AbilityType t = list.get(i);
            buttonList.add(new GuiButton(BTN_BUY_BASE + i, cx + 70, top + i * rowH, 90, rowH - 2, buyLabel(t)));
        }
        sliderW = 150;
        sliderH = 10;
        sliderX = cx - 160;
        sliderY = height - 34;
        buttonList.add(new GuiButton(BTN_TAKE, cx, height - 40, 80, 18, I18n.format("pocketmod.gui.take")));
        buttonList.add(new GuiButton(BTN_CLOSE, cx + 90, height - 40, 70, 18, I18n.format("pocketmod.gui.close")));
        if (creditAmount == 0) {
            creditAmount = PocketConfig.creditMin;
        }
    }

    private String buyLabel(AbilityType t) {
        EntityPlayer dp = dataPlayer();
        int lvl = AbilityStorage.getLevel(dp, t);
        if (lvl >= t.maxLevel) return I18n.format("pocketmod.gui.max");
        int cost = t.costForNextLevel(lvl);
        if (cost < 0) return "-";
        double real = cost * Mana.getPriceMultiplier(dp);
        String s = I18n.format(lvl == 0 ? "pocketmod.gui.buy" : "pocketmod.gui.upgrade", (int) Math.ceil(real));
        // красная цена, если не хватает маны
        return (Mana.get(dp) < real ? TextFormatting.RED.toString() : "") + s;
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        EntityPlayer dp = dataPlayer();
        int cx = width / 2;

        double mana = Mana.get(dp);
        double mult = Mana.getPriceMultiplier(dp);
        double drain = Abilities.totalDrainPerSec(dp);

        drawCenteredString(fontRenderer, I18n.format("pocketmod.gui.title"), cx, 6, 0xFFE080);
        drawCenteredString(fontRenderer,
                I18n.format("pocketmod.gui.mana", (long) mana, String.format("%.2f", mult)), cx, 18, 0xAAAAFF);
        String drainLine = drain > 0.001
                ? I18n.format("pocketmod.gui.drain", String.format("%.1f", drain), (long) (mana / drain / 60.0))
                : I18n.format("pocketmod.gui.drain_none");
        drawCenteredString(fontRenderer, drainLine, cx, 30, 0xCCCCCC);

        boolean hasCredit = Mana.hasCredit(dp);
        if (hasCredit) {
            drawCenteredString(fontRenderer,
                    I18n.format("pocketmod.gui.credit_active", (long) Math.ceil(Mana.getCredit(dp)),
                            formatTime(Mana.getCreditLeftMs(dp))),
                    cx, 42, 0xFF8888);
        }

        for (int i = 0; i < list.size(); i++) {
            AbilityType t = list.get(i);
            int y = top + i * rowH;
            int lvl = AbilityStorage.getLevel(dp, t);
            String status = lvl == 0
                    ? TextFormatting.GRAY + I18n.format("pocketmod.gui.locked")
                    : TextFormatting.GREEN + I18n.format("pocketmod.gui.level", lvl, t.maxLevel);
            fontRenderer.drawStringWithShadow(I18n.format(t.titleKey()) + "  " + status, cx - 160, y + (rowH - 10) / 2, 0xFFFFFF);
        }

        // Кредит: ползунок суммы
        if (!hasCredit) {
            int repay = (int) Math.round(creditAmount * (1.0 + PocketConfig.creditInterest));
            fontRenderer.drawStringWithShadow(
                    I18n.format("pocketmod.gui.credit_amount", creditAmount, repay, PocketConfig.creditDurationSec / 60),
                    sliderX, sliderY - 14, 0xCCCCCC);
        }
        Gui.drawRect(sliderX, sliderY + 3, sliderX + sliderW, sliderY + sliderH - 3, 0xFF444444);
        int kx = sliderX + (int) (sliderFraction() * (sliderW - 6));
        Gui.drawRect(kx, sliderY, kx + 6, sliderY + sliderH, hasCredit ? 0xFF888888 : 0xFFFFD34D);

        super.drawScreen(mouseX, mouseY, partialTicks);
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
        if (button.id == BTN_CLOSE) {
            mc.player.closeScreen();
            return;
        }
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;

        if (button.id == BTN_TAKE) {
            if (Mana.hasCredit(dataPlayer())) return;
            final int amt = creditAmount;
            server.addScheduledTask(() -> {
                EntityPlayerMP mp = server.getPlayerList().getPlayerByUUID(player.getUniqueID());
                if (mp != null && !Mana.hasCredit(mp)) {
                    double repay = Mana.takeCredit(mp, amt);
                    if (repay > 0) {
                        mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_taken",
                                (int) Math.ceil(repay), PocketConfig.creditDurationSec / 60), true);
                    }
                }
            });
            return;
        }
        if (button.id >= BTN_BUY_BASE && button.id < BTN_BUY_BASE + list.size()) {
            final AbilityType t = list.get(button.id - BTN_BUY_BASE);
            server.addScheduledTask(() -> {
                EntityPlayerMP mp = server.getPlayerList().getPlayerByUUID(player.getUniqueID());
                if (mp != null) {
                    AbilityStorage.tryUpgrade(mp, t);
                }
            });
        }
    }

    @Override
    public void updateScreen() {
        EntityPlayer dp = dataPlayer();
        boolean hasCredit = Mana.hasCredit(dp);
        for (GuiButton b : buttonList) {
            if (b.id >= BTN_BUY_BASE && b.id < BTN_BUY_BASE + list.size()) {
                AbilityType t = list.get(b.id - BTN_BUY_BASE);
                b.displayString = buyLabel(t);
                int lvl = AbilityStorage.getLevel(dp, t);
                b.enabled = lvl < t.maxLevel && t.costForNextLevel(lvl) > 0;
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
