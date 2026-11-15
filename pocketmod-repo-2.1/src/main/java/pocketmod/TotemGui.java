package pocketmod;

import java.io.IOException;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Окно тотемов (Shift+G): создать с названием, список, клик = телепорт, стрелки = порядок, X = удалить (дважды). */
@SideOnly(Side.CLIENT)
public class TotemGui extends GuiScreen {

    private static final int BTN_CLOSE = 0;
    private static final int BTN_CREATE = 1;
    private static final int ROW_H = 16;
    private static final int ROW_W = 314;
    private static final int LIST_TOP = 66;

    private interface ServerAction {
        void run(EntityPlayerMP p);
    }

    private final EntityPlayer player;
    private GuiTextField nameField;
    private int scroll = 0;
    private int confirmIndex = -1;

    public TotemGui(EntityPlayer player) {
        this.player = player;
    }

    private EntityPlayer dataPlayer() {
        EntityPlayerMP mp = ClientHandler.sp();
        return mp != null ? mp : player;
    }

    private void server(final ServerAction action) {
        final IntegratedServer s = mc.getIntegratedServer();
        if (s == null) return;
        s.addScheduledTask(() -> {
            EntityPlayerMP mp = s.getPlayerList().getPlayerByUUID(player.getUniqueID());
            if (mp != null) action.run(mp);
        });
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int cx = width / 2;
        nameField = new GuiTextField(2, fontRenderer, cx - 172, 40, 160, 18);
        nameField.setMaxStringLength(24);
        nameField.setFocused(true);
        buttonList.add(new GuiButton(BTN_CREATE, cx - 6, 39, 148, 20, ""));
        buttonList.add(new GuiButton(BTN_CLOSE, cx - 40, height - 24, 80, 20, Tr.format("pocketmod.gui.close")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private int visibleRows() {
        return Math.max(3, (height - 32 - LIST_TOP) / ROW_H);
    }

    private void clampScroll(int size) {
        int max = Math.max(0, size - visibleRows());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private static String dimName(int dim) {
        if (dim == 0) return "Overworld";
        if (dim == -1) return "Nether";
        if (dim == 1) return "End";
        return "Dim " + dim;
    }

    private void createTotem() {
        final String name = nameField.getText();
        server(p -> Totems.create(p, name));
        nameField.setText("");
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        EntityPlayer dp = dataPlayer();
        double cost = Totems.createCost(dp);
        boolean full = Totems.get(dp).size() >= Totems.maxSlots(dp);
        for (GuiButton b : buttonList) {
            if (b.id == BTN_CREATE) {
                String s = Tr.format("pocketmod.gui.totem_create", (int) Math.ceil(cost));
                b.displayString = (Mana.get(dp) < cost ? TextFormatting.RED.toString() : "") + s;
                b.enabled = !full;
            }
        }
    }

    private void drawSmallButton(int x, int y, int w, String text, boolean hover, int color) {
        Gui.drawRect(x, y, x + w, y + ROW_H - 3, hover ? 0xFF666666 : 0xFF333333);
        drawCenteredString(fontRenderer, text, x + w / 2, y + 3, color);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        EntityPlayer dp = dataPlayer();
        List<Totems.Totem> list = Totems.get(dp);
        int cx = width / 2;
        int left = cx - 172;

        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.totems_title"), cx, 6, 0xFFE080);
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.mana", (long) Mana.get(dp),
                String.format("%.2f", Mana.getPriceMultiplier(dp))), cx, 18, 0xAAAAFF);
        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.totem_slots", list.size(), Totems.maxSlots(dp)),
                cx, 28, 0xCCCCCC);

        nameField.drawTextBox();
        if (!nameField.isFocused() && nameField.getText().isEmpty()) {
            fontRenderer.drawString(Tr.format("pocketmod.gui.totem_name_hint"), cx - 168, 46, 0x777777);
        }

        clampScroll(list.size());
        int rows = visibleRows();
        if (list.isEmpty()) {
            drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.totem_empty"), cx, LIST_TOP + 6, 0x999999);
        }
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i >= list.size()) break;
            Totems.Totem t = list.get(i);
            int y = LIST_TOP + r * ROW_H;
            boolean hover = mouseX >= left && mouseX <= left + ROW_W && mouseY >= y && mouseY < y + ROW_H - 1;

            Gui.drawRect(left, y, left + ROW_W, y + ROW_H - 1, hover ? 0x66FFFFFF : 0x44000000);
            Gui.drawRect(left + 2, y + 3, left + 8, y + 9, 0xFF000000 | PocketConfig.totemColor(i));

            String nm = (i + 1) + ". " + t.name;
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(nm, 104), left + 12, y + 4, 0xFFFFFF);
            fontRenderer.drawString(dimName(t.dim) + " " + (int) t.x + " " + (int) t.y + " " + (int) t.z,
                    left + 120, y + 4, 0xAAAAAA);

            double cost = Totems.teleportCost(dp, t);
            fontRenderer.drawString(String.valueOf((int) Math.ceil(cost)), left + 222, y + 4,
                    Mana.get(dp) < cost ? 0xFF5555 : 0x88FF88);

            drawSmallButton(left + 250, y, 14, "^", mouseX >= left + 250 && mouseX <= left + 264 && hover, 0xFFFFFF);
            drawSmallButton(left + 266, y, 14, "v", mouseX >= left + 266 && mouseX <= left + 280 && hover, 0xFFFFFF);
            boolean confirm = confirmIndex == i;
            drawSmallButton(left + 284, y, 28, confirm ? "?!" : "X", mouseX >= left + 284 && hover,
                    confirm ? 0xFF5555 : 0xFFAAAA);
        }

        drawCenteredString(fontRenderer, Tr.format("pocketmod.gui.totem_hint", (int) Math.ceil(Totems.deleteCost(dp))),
                cx, height - 36, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;

        List<Totems.Totem> list = Totems.get(dataPlayer());
        int left = width / 2 - 172;
        int rows = visibleRows();
        for (int r = 0; r < rows; r++) {
            final int i = scroll + r;
            if (i >= list.size()) break;
            int y = LIST_TOP + r * ROW_H;
            if (mouseY < y || mouseY >= y + ROW_H - 1 || mouseX < left || mouseX > left + ROW_W) continue;

            if (mouseX >= left + 250 && mouseX <= left + 264) {
                confirmIndex = -1;
                server(p -> Totems.move(p, i, -1));
            } else if (mouseX >= left + 266 && mouseX <= left + 280) {
                confirmIndex = -1;
                server(p -> Totems.move(p, i, 1));
            } else if (mouseX >= left + 284) {
                if (confirmIndex == i) {
                    confirmIndex = -1;
                    server(p -> Totems.delete(p, i));
                } else {
                    confirmIndex = i;
                }
            } else {
                confirmIndex = -1;
                server(p -> Totems.teleport(p, i));
                mc.player.closeScreen();
            }
            return;
        }
        confirmIndex = -1;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            super.keyTyped(typedChar, keyCode);
            return;
        }
        if (nameField.textboxKeyTyped(typedChar, keyCode)) return;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            createTotem();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_CLOSE) {
            mc.player.closeScreen();
        } else if (button.id == BTN_CREATE) {
            createTotem();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            scroll += dw > 0 ? -1 : 1;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
