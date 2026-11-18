package pocketmod;

import java.io.IOException;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Выбор руд для сканера (Ctrl+C): каждая цель включается и выключается отдельно, выбор хранится в данных игрока. */
@SideOnly(Side.CLIENT)
public class ScannerGui extends GuiScreen {

    private static final int BTN_CLOSE = 1000;
    private int W = 150;
    private static final int H = 20;
    private static final int GAP = 6;
    private int COLS = 2;

    private final EntityPlayer player;

    public ScannerGui(EntityPlayer player) {
        this.player = player;
    }

    private int count() {
        return Math.min(PocketConfig.oreScannerTargets.length, 31);
    }

    private String label(int i) {
        boolean on = (OreScanner.disabledMask(player) & (1 << i)) == 0;
        String name = OreScannerClient.displayName(PocketConfig.oreScannerTargets[i]);
        return on ? "\u00a7a" + name + " \u00a77[" + Tr.format("pocketmod.gui.scan_on") + "]"
                  : "\u00a78" + name + " [" + Tr.format("pocketmod.gui.scan_off") + "]";
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int n = count();
        // руд может быть много (в том числе из других модов): тогда три колонки поуже
        COLS = n > 16 ? 3 : 2;
        W = n > 16 ? 120 : 150;
        int startX = width / 2 - (COLS * W + GAP) / 2;
        for (int i = 0; i < n; i++) {
            int col = i % COLS;
            int row = i / COLS;
            buttonList.add(new GuiButton(i, startX + col * (W + GAP), 52 + row * (H + 4), W, H, label(i)));
        }
        int rows = (n + COLS - 1) / COLS;
        buttonList.add(new GuiButton(BTN_CLOSE, width / 2 - 40, 52 + rows * (H + 4) + 8, 80, 20, Tr.format("pocketmod.gui.close")));
    }

    @Override
    public void updateScreen() {
        for (GuiButton b : buttonList) {
            if (b.id >= 0 && b.id < count()) b.displayString = label(b.id);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.scanner_title"), width / 2, 14, 0xE0A040);
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.scanner_hint"), width / 2, 28, 0x8A93A0);
        for (GuiButton b : buttonList) {
            if (b.id >= 0 && b.id < count()) {
                Gui.drawRect(b.x - 10, b.y + 4, b.x - 3, b.y + 16, 0xFF000000 | OreScannerClient.colorOf(b.id));
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_CLOSE) {
            mc.displayGuiScreen(null);
        } else if (button.id >= 0 && button.id < count()) {
            NetworkHandler.action(NetAction.TOGGLE_SCAN_TARGET, button.id, 0, 0, "");
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
