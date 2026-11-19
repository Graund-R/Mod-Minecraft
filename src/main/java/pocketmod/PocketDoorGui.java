package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Управление купленными парами дверей и доступом к ним. */
@SideOnly(Side.CLIENT)
public class PocketDoorGui extends GuiScreen {
    private static final int BTN_BACK = 0;
    private static final int BTN_BUY_PAIR = 1;
    private static final int BTN_BUY_KEY = 2;
    private static final int BTN_BUY_TEMP = 3;
    private static final int BTN_TOGGLE_KEYS = 4;
    private static final int BTN_ADD_TRUST = 5;
    private static final int BTN_REMOVE_TRUST = 6;
    private static final int BTN_PAUSE_ACCESS = 7;
    private static final int BTN_TEST_GUEST = 8;
    private static final int BTN_KICK_ALL = 9;
    private static final int BTN_PRESERVE_KEYS = 10;
    private static final int BTN_PAIR_BASE = 20;

    private final EntityPlayer player;
    private final List<String> pairIds = new ArrayList<String>();
    private String selectedPairId;
    private GuiTextField nameField;
    private boolean preserveOldKeys = false;

    public PocketDoorGui(EntityPlayer player) {
        this.player = player;
    }

    private net.minecraft.nbt.NBTTagCompound state() {
        return PocketDoorNetwork.getClientState();
    }

    private List<String> pairIdsFromState() {
        List<String> result = new ArrayList<String>();
        net.minecraft.nbt.NBTTagList list = state().getTagList("pairs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            String id = list.getCompoundTagAt(i).getString("id");
            if (!id.isEmpty()) result.add(id);
        }
        return result;
    }

    private int pairCount() {
        return state().getInteger("pairCount");
    }

    private int doorCount(String pairId) {
        net.minecraft.nbt.NBTTagList list = state().getTagList("pairs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound pair = list.getCompoundTagAt(i);
            if (pairId.equals(pair.getString("id"))) return pair.getInteger("doors");
        }
        return 0;
    }

    private List<String> trustedNames() {
        List<String> result = new ArrayList<String>();
        net.minecraft.nbt.NBTTagList list = state().getTagList("trusted", 8);
        for (int i = 0; i < list.tagCount(); i++) result.add(list.getStringTagAt(i));
        return result;
    }

    private void refreshPairs() {
        List<String> current = pairIdsFromState();
        if (!current.equals(pairIds)) {
            pairIds.clear();
            pairIds.addAll(current);
            if (selectedPairId == null || !pairIds.contains(selectedPairId)) {
                selectedPairId = pairIds.isEmpty() ? null : pairIds.get(pairIds.size() - 1);
            }
            initGui();
        }
    }

    @Override
    public void initGui() {
        PocketDoorNetwork.requestState();
        buttonList.clear();
        refreshPairListOnly();
        int leftX = 8;
        int leftW = Math.max(112, width / 2 - 18);
        int rightX = leftX + leftW + 8;
        int rightW = Math.max(110, width - rightX - 8);

        buttonList.add(new GuiButton(BTN_BUY_PAIR, rightX, 42, rightW, 19, "Пара дверей"));
        buttonList.add(new GuiButton(BTN_BUY_KEY, rightX, 65, rightW, 19, "Золотой ключ"));
        buttonList.add(new GuiButton(BTN_BUY_TEMP, rightX, 88, rightW, 19, "Железный ключ"));
        buttonList.add(new GuiButton(BTN_TOGGLE_KEYS, rightX, 111, rightW, 19, "Ключи: включены"));

        buttonList.add(new GuiButton(BTN_PAUSE_ACCESS, rightX, 134, rightW, 19, "Пауза доступа"));
        buttonList.add(new GuiButton(BTN_KICK_ALL, rightX, 157, rightW, 19, "Выкинуть всех"));
        buttonList.add(new GuiButton(BTN_PRESERVE_KEYS, rightX, 180, rightW, 19, "Старые ключи: не сохранять"));

        int inputY = 221;
        int fieldW = Math.max(48, rightW - 78);
        nameField = new GuiTextField(0, fontRenderer, rightX, inputY, fieldW, 18);
        nameField.setMaxStringLength(16);
        nameField.setCanLoseFocus(true);
        buttonList.add(new GuiButton(BTN_ADD_TRUST, rightX + fieldW + 2, inputY, 36, 18, "+"));
        buttonList.add(new GuiButton(BTN_REMOVE_TRUST, rightX + fieldW + 40, inputY, 36, 18, "−"));

        buttonList.add(new GuiButton(BTN_BACK, width / 2 - 45, height - 24, 90, 20, "Назад"));
        buttonList.add(new GuiButton(BTN_TEST_GUEST, leftX, 145, leftW, 19, "[ ] Тест гостя"));

        for (int i = 0; i < pairIds.size() && i < PocketDoorData.MAX_PAIRS; i++) {
            String id = pairIds.get(i);
            int count = doorCount(id);
            GuiButton pairButton = new GuiButton(BTN_PAIR_BASE + i, leftX, 42 + i * 19, leftW, 17,
                    "Пара " + (i + 1) + "  " + count + "/2");
            pairButton.enabled = !id.equals(selectedPairId);
            buttonList.add(pairButton);
        }
    }

    private void refreshPairListOnly() {
        List<String> current = pairIdsFromState();
        if (!current.equals(pairIds)) {
            pairIds.clear();
            pairIds.addAll(current);
        }
        if (selectedPairId == null || !pairIds.contains(selectedPairId)) {
            selectedPairId = pairIds.isEmpty() ? null : pairIds.get(pairIds.size() - 1);
        }
    }

    private void purchasePair() { PocketDoorNetwork.buyPair(selectedPairId); }

    private void purchaseKey(boolean oneTime) {
        if (selectedPairId == null) return;
        if (oneTime) PocketDoorNetwork.buyIronKey(selectedPairId, preserveOldKeys);
        else PocketDoorNetwork.buyGoldKey(selectedPairId, preserveOldKeys);
    }

    private void toggleKeys() { PocketDoorNetwork.toggleKeys(); }
    private void toggleGuestTestMode() { PocketDoorNetwork.toggleGuestTest(); }
    private void toggleVisitorAccess() { PocketDoorNetwork.togglePause(); }
    private void kickAllVisitors() { PocketDoorNetwork.kickAll(); }

    private void updateTrusted(boolean add) {
        String name = nameField == null ? "" : nameField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
        if (add) PocketDoorNetwork.addTrusted(name);
        else PocketDoorNetwork.removeTrusted(name);
        nameField.setText("");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_BACK) {
            mc.displayGuiScreen(new ShopGui(player));
            return;
        }
        if (button.id >= BTN_PAIR_BASE && button.id < BTN_PAIR_BASE + PocketDoorData.MAX_PAIRS) {
            int index = button.id - BTN_PAIR_BASE;
            if (index < pairIds.size()) {
                selectedPairId = pairIds.get(index);
                initGui();
            }
            return;
        }
        if (button.id == BTN_BUY_PAIR) {
            purchasePair();
        } else if (button.id == BTN_BUY_KEY) {
            purchaseKey(false);
        } else if (button.id == BTN_BUY_TEMP) {
            purchaseKey(true);
        } else if (button.id == BTN_TOGGLE_KEYS) {
            toggleKeys();
        } else if (button.id == BTN_PAUSE_ACCESS) {
            toggleVisitorAccess();
        } else if (button.id == BTN_TEST_GUEST) {
            toggleGuestTestMode();
        } else if (button.id == BTN_KICK_ALL) {
            kickAllVisitors();
        } else if (button.id == BTN_PRESERVE_KEYS) {
            preserveOldKeys = !preserveOldKeys;
        } else if (button.id == BTN_ADD_TRUST) {
            updateTrusted(true);
        } else if (button.id == BTN_REMOVE_TRUST) {
            updateTrusted(false);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (nameField != null && nameField.isFocused() && keyCode != 1 && keyCode != 28) {
            nameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (nameField != null && nameField.isFocused() && keyCode == 28) {
            updateTrusted(true);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (nameField != null) nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        refreshPairs();
        net.minecraft.nbt.NBTTagCompound snapshot = state();
        boolean hasPair = selectedPairId != null && pairIds.contains(selectedPairId);
        for (GuiButton button : buttonList) {
            if (button.id >= BTN_PAIR_BASE && button.id < BTN_PAIR_BASE + PocketDoorData.MAX_PAIRS) {
                int index = button.id - BTN_PAIR_BASE;
                if (index < pairIds.size()) {
                    button.displayString = "Пара " + (index + 1) + "  " + doorCount(pairIds.get(index)) + "/2";
                }
            } else if (button.id == BTN_BUY_PAIR) {
                button.displayString = "Пара дверей: " + snapshot.getInteger("pairPrice") + " (" + pairCount() + "/" + PocketDoorData.MAX_PAIRS + ")";
                button.enabled = pairCount() < PocketDoorData.MAX_PAIRS
                        || (hasPair && doorCount(selectedPairId) < 2);
            } else if (button.id == BTN_BUY_KEY) {
                button.displayString = "Золотой ключ: " + snapshot.getInteger("goldKeyPrice");
                button.enabled = hasPair;
            } else if (button.id == BTN_BUY_TEMP) {
                button.displayString = "Железный ключ: " + snapshot.getInteger("ironKeyPrice");
                button.enabled = hasPair;
            } else if (button.id == BTN_TOGGLE_KEYS) {
                button.displayString = snapshot.getBoolean("keysEnabled")
                        ? "Все ключи: включены" : "Все ключи: заблокированы";
            } else if (button.id == BTN_PAUSE_ACCESS) {
                button.displayString = snapshot.getBoolean("accessPaused")
                        ? "Пауза: включена" : "Пауза: выключена";
            } else if (button.id == BTN_PRESERVE_KEYS) {
                button.displayString = preserveOldKeys ? "Старые ключи: сохранять" : "Старые ключи: не сохранять";
            } else if (button.id == BTN_TEST_GUEST) {
                button.displayString = snapshot.getBoolean("guestTest")
                        ? "[x] Тест гостя" : "[ ] Тест гостя";
            }
        }
        if (nameField != null) nameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int leftW = Math.max(112, width / 2 - 18);
        int rightX = 8 + leftW + 8;
        int rightW = Math.max(110, width - rightX - 8);
        Gui.drawRect(4, 4, width - 4, height - 4, 0xCC101418);
        Gui.drawRect(4, 4, width - 4, 5, 0xFFE0A040);
        drawCenteredString(fontRenderer, "Управление дверями", width / 2, 12, 0xFFE0A040);
        fontRenderer.drawString("Куплено пар: " + pairCount()
                + "/" + PocketDoorData.MAX_PAIRS, 10, 28, 0xFFCCCCCC);
        fontRenderer.drawString("Выбрана: " + (selectedPairId == null ? "нет пары"
                : "пара " + (pairIds.indexOf(selectedPairId) + 1)), rightX, 28, 0xFFCCCCCC);
        fontRenderer.drawString("Доверенные игроки:", rightX, 203, 0xFFE0A040);

        {
            List<String> trusted = trustedNames();
            String names = trusted.isEmpty() ? "список пуст" : String.join(", ", trusted);
            int y = 259;
            for (Object line : fontRenderer.listFormattedStringToWidth(names, rightW)) {
                fontRenderer.drawStringWithShadow((String) line, rightX, y, 0xFFB9C6D4);
                y += 10;
                if (y > height - 32) break;
            }
        }
        if (nameField != null) nameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
