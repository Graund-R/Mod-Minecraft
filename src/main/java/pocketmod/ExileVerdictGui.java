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
    private int scroll, maxScroll;

    public ExileVerdictGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, Tr.format("gui.done")));
    }

    @Override
    public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        drawCenteredString(
                fontRenderer, Tr.format("pocketmod.exile.verdict"), width / 2, 14, 0xFFFFFF);
        int top = 38 - scroll;
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc);
        int scale = res.getScaleFactor();
        org.lwjgl.opengl.GL11.glScissor(0, 40*scale, mc.displayWidth, Math.max(1,height-76)*scale);
        for (int i = 1; i <= 5; i++) {
            String text = Tr.format("pocketmod.exile.verdict_" + i);
            fontRenderer.drawSplitString(
                    text, Math.max(12, width / 2 - 220), top, Math.min(440, width - 24), 0xDDDDDD);
            top += fontRenderer.getWordWrappedHeight(text, Math.min(440, width - 24)) + 10;
        }
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        maxScroll=Math.max(0,top+scroll-(height-45));
        super.drawScreen(x, y, partial);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel=org.lwjgl.input.Mouse.getEventDWheel();
        if(wheel!=0) scroll=Math.max(0,Math.min(maxScroll,scroll-(wheel>0?24:-24)));
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
