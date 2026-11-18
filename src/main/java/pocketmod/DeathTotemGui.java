package pocketmod;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** После возрождения: игра на паузе, вопрос «создать тотем смерти?». Мана не тратится без согласия. */
@SideOnly(Side.CLIENT)
public class DeathTotemGui extends GuiScreen {

    private static final int BTN_YES = 1;
    private static final int BTN_NO = 2;

    private final double teleportCost;
    private final double createCost;
    private boolean answered;

    public DeathTotemGui(double teleportCost, double createCost) {
        this.teleportCost = teleportCost;
        this.createCost = createCost;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int y = height / 2 + 14;
        buttonList.add(new GuiButton(BTN_YES, cx - 154, y, 150, 20, Tr.format("pocketmod.gui.death_totem_yes")));
        buttonList.add(new GuiButton(BTN_NO, cx + 4, y, 150, 20, Tr.format("pocketmod.gui.death_totem_no")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int y = height / 2 - 48;
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.death_totem_title"), cx, y, 0xE04040);
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.death_totem_question"), cx, y + 16, 0xFFFFFF);
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.death_totem_info", (int) Math.ceil(teleportCost)), cx, y + 32, 0x9AA5B1);
        if (createCost > 0.0) {
            drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.death_totem_fee", (int) Math.ceil(createCost)), cx, y + 44, 0xE0A040);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void answer(boolean create) {
        if (answered) return;
        answered = true;
        NetworkHandler.action(NetAction.DEATH_TOTEM_CHOICE, create ? 1 : 0, 0, 0, "");
        mc.displayGuiScreen(null);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        answer(button.id == BTN_YES);
    }

    /** Esc считается отказом. */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) answer(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
