package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Каталог только тех обликов, которые игрок уже получил убийствами. */
@SideOnly(Side.CLIENT)
public class MorphCatalogGui extends GuiScreen {
    private static final int BTN_ROW = 100;
    private static final int BTN_APPLY = 200;
    private static final int BTN_DEMORPH = 201;
    private static final int BTN_CLOSE = 202;

    private final EntityPlayer player;
    private List<Object> morphs = new ArrayList<Object>();
    private int selected = 0;
    private int scroll = 0;
    private int visibleRows = 8;

    public MorphCatalogGui(EntityPlayer player) {
        this.player = player;
    }


    @Override
    public void initGui() {
        morphs = MorphIntegration.getMorphs(player);
        visibleRows = Math.max(1, Math.min(12, (height - 104) / 18));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, morphs.size() - visibleRows)));
        if (selected >= morphs.size()) selected = Math.max(0, morphs.size() - 1);
        buttonList.clear();

        int left = Math.max(8, width / 2 - 150);
        int rowWidth = Math.min(300, width - 16);
        for (int i = 0; i < visibleRows && scroll + i < morphs.size(); i++) {
            int index = scroll + i;
            buttonList.add(new GuiButton(BTN_ROW + i, left, 48 + i * 18, rowWidth, 16,
                    MorphIntegration.getMorphName(morphs.get(index))));
        }

        int bottom = height - 28;
        int center = width / 2;
        GuiButton apply = new GuiButton(BTN_APPLY, center - 154, bottom, 100, 20, "Превратиться (" + (int) PocketConfig.morphCost + ")");
        apply.enabled = !morphs.isEmpty();
        buttonList.add(apply);
        GuiButton demorph = new GuiButton(BTN_DEMORPH, center - 50, bottom, 100, 20, "Обычный облик");
        demorph.enabled = MorphIntegration.isMorphed(player);
        buttonList.add(demorph);
        buttonList.add(new GuiButton(BTN_CLOSE, center + 54, bottom, 100, 20, "Закрыть"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, TextFormatting.GOLD + "Каталог обликов", width / 2, 18, 0xFFFFFF);
        drawCenteredString(fontRenderer, "Мана: " + (long) Mana.get(player), width / 2, 32, 0xAACCFF);
        if (!MorphIntegration.isAvailable()) {
            drawCenteredString(fontRenderer, TextFormatting.RED + "Для морфизма нужны Metamorph и McLib",
                    width / 2, height / 2, 0xFFFFFF);
        } else if (morphs.isEmpty()) {
            drawCenteredString(fontRenderer, TextFormatting.GRAY + "Обликов пока нет. Убей моба, чтобы получить его облик.",
                    width / 2, height / 2, 0xFFFFFF);
        } else {
            drawCenteredString(fontRenderer, "Получено обликов: " + morphs.size()
                    + "   Выбрано: " + (selected + 1), width / 2, height - 48, 0xBBBBBB);
            if (morphs.size() > visibleRows) {
                drawCenteredString(fontRenderer, "Колесо мыши: прокрутка списка", width / 2, height - 38, 0x777777);
            }
            for (int i = 0; i < visibleRows && scroll + i < morphs.size(); i++) {
                if (scroll + i == selected) {
                    int left = Math.max(8, width / 2 - 150);
                    Gui.drawRect(left, 48 + i * 18, left + Math.min(300, width - 16), 64 + i * 18, 0x663D2B16);
                }
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && morphs.size() > visibleRows) {
            scroll = Math.max(0, Math.min(morphs.size() - visibleRows, scroll + (wheel > 0 ? -1 : 1)));
            initGui();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= BTN_ROW && button.id < BTN_ROW + visibleRows) {
            int index = scroll + button.id - BTN_ROW;
            if (index >= 0 && index < morphs.size()) {
                selected = index;
                initGui();
            }
            return;
        }
        if (button.id == BTN_CLOSE) {
            mc.displayGuiScreen(null);
            return;
        }

        if (button.id == BTN_APPLY && selected >= 0 && selected < morphs.size()) {
            PocketDoorNetwork.transform(selected);
            mc.displayGuiScreen(null);
        } else if (button.id == BTN_DEMORPH) {
            PocketDoorNetwork.demorph();
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
