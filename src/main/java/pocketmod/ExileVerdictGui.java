package pocketmod;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ExileVerdictGui extends GuiScreen {
    private final GuiScreen parent;

    public ExileVerdictGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, I18n.format("gui.done")));
    }

    @Override
    public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        drawCenteredString(
                fontRenderer, I18n.format("pocketmod.exile.verdict"), width / 2, 14, 0xFFFFFF);
        int top = 38;
        for (int i = 1; i <= 5; i++) {
            String text = I18n.format("pocketmod.exile.verdict_" + i);
            fontRenderer.drawSplitString(
                    text, Math.max(12, width / 2 - 220), top, Math.min(440, width - 24), 0xDDDDDD);
            top += fontRenderer.getWordWrappedHeight(text, Math.min(440, width - 24)) + 10;
        }
        super.drawScreen(x, y, partial);
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
