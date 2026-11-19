package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
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
    private static final int BTN_PAIR_BASE = 20;

    private static final double PAIR_PRICE = 1000.0D;
    private static final double KEY_PRICE = 100.0D;
    private static final double TEMP_KEY_PRICE = 50.0D;

    private final EntityPlayer player;
    private final List<String> pairIds = new ArrayList<String>();
    private String selectedPairId;
    private GuiTextField nameField;

    public PocketDoorGui(EntityPlayer player) {
        this.player = player;
    }

    private IntegratedServer server() {
        return mc == null ? null : mc.getIntegratedServer();
    }

    private PocketDoorData data() {
        IntegratedServer server = server();
        return server == null ? null : PocketDoorData.get(server.getWorld(0));
    }

    private UUID ownerId() {
        return player.getUniqueID();
    }

    private void refreshPairs() {
        PocketDoorData data = data();
        if (data == null) return;
        List<String> current = data.pairIds(ownerId());
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
        buttonList.clear();
        refreshPairListOnly();
        int leftX = 8;
        int leftW = Math.max(112, width / 2 - 18);
        int rightX = leftX + leftW + 8;
        int rightW = Math.max(110, width - rightX - 8);

        buttonList.add(new GuiButton(BTN_BUY_PAIR, rightX, 42, rightW, 19, "Пара дверей: 1000"));
        buttonList.add(new GuiButton(BTN_BUY_KEY, rightX, 65, rightW, 19, "Ключ пары: 100"));
        buttonList.add(new GuiButton(BTN_BUY_TEMP, rightX, 88, rightW, 19, "Одноразовый ключ: 50"));
        buttonList.add(new GuiButton(BTN_TOGGLE_KEYS, rightX, 111, rightW, 19, "Ключи: включены"));

        int inputY = 157;
        int fieldW = Math.max(48, rightW - 78);
        nameField = new GuiTextField(0, fontRenderer, rightX, inputY, fieldW, 18);
        nameField.setMaxStringLength(16);
        nameField.setCanLoseFocus(true);
        buttonList.add(new GuiButton(BTN_ADD_TRUST, rightX + fieldW + 2, inputY, 36, 18, "+"));
        buttonList.add(new GuiButton(BTN_REMOVE_TRUST, rightX + fieldW + 40, inputY, 36, 18, "−"));

        buttonList.add(new GuiButton(BTN_BACK, width / 2 - 45, height - 24, 90, 20, "Назад"));

        for (int i = 0; i < pairIds.size() && i < PocketDoorData.MAX_PAIRS; i++) {
            String id = pairIds.get(i);
            int count = data() == null ? 0 : data().doorCount(id);
            GuiButton pairButton = new GuiButton(BTN_PAIR_BASE + i, leftX, 42 + i * 19, leftW, 17,
                    "Пара " + (i + 1) + "  " + count + "/2");
            pairButton.enabled = !id.equals(selectedPairId);
            buttonList.add(pairButton);
        }
    }

    private void refreshPairListOnly() {
        PocketDoorData d = data();
        if (d == null) return;
        List<String> current = d.pairIds(ownerId());
        if (!current.equals(pairIds)) {
            pairIds.clear();
            pairIds.addAll(current);
        }
        if (selectedPairId == null || !pairIds.contains(selectedPairId)) {
            selectedPairId = pairIds.isEmpty() ? null : pairIds.get(pairIds.size() - 1);
        }
    }

    private void schedule(java.util.function.Consumer<EntityPlayerMP> action) {
        final IntegratedServer server = server();
        if (server == null) return;
        final UUID id = ownerId();
        server.addScheduledTask(() -> {
            EntityPlayerMP mp = server.getPlayerList().getPlayerByUUID(id);
            if (mp != null) action.accept(mp);
        });
    }

    private void give(EntityPlayerMP player, net.minecraft.item.ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
    }

    private void purchasePair() {
        schedule(mp -> {
            PocketDoorData data = PocketDoorData.get(mp.getServer().getWorld(0));
            if (!AbilityStorage.isUnlocked(mp, AbilityType.POCKET)) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
                return;
            }
            if (!data.canBuyPair(mp.getUniqueID())) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_limit"), true);
                return;
            }
            double price = Mana.shopPrice(mp, PAIR_PRICE);
            if (!Mana.spend(mp, price)) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                        (int) Math.ceil(price)), true);
                return;
            }
            String pairId = data.createPair(mp.getUniqueID());
            if (pairId == null) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_limit"), true);
                return;
            }
            Mana.consumeDiscount(mp);
            give(mp, PocketDoorItems.createPairItem(mp.getUniqueID(), pairId));
            give(mp, PocketDoorItems.createPairItem(mp.getUniqueID(), pairId));
            mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_bought"), true);
        });
    }

    private void purchaseKey(final boolean oneTime) {
        final String pairId = selectedPairId;
        if (pairId == null) return;
        schedule(mp -> {
            PocketDoorData data = PocketDoorData.get(mp.getServer().getWorld(0));
            if (!AbilityStorage.isUnlocked(mp, AbilityType.POCKET)) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
                return;
            }
            if (!data.ownsPair(mp.getUniqueID(), pairId)) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_missing"), true);
                return;
            }
            double base = oneTime ? TEMP_KEY_PRICE : KEY_PRICE;
            double price = Mana.shopPrice(mp, base);
            if (!Mana.spend(mp, price)) {
                mp.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                        (int) Math.ceil(price)), true);
                return;
            }
            Mana.consumeDiscount(mp);
            give(mp, PocketKeys.createKeyFor(mp.getUniqueID(), pairId, oneTime));
            mp.sendStatusMessage(new TextComponentTranslation(
                    oneTime ? "pocketmod.msg.temp_key_bought" : "pocketmod.msg.key_bought"), true);
        });
    }

    private void toggleKeys() {
        schedule(mp -> {
            PocketDoorData data = PocketDoorData.get(mp.getServer().getWorld(0));
            boolean enabled = !data.keysEnabled(mp.getUniqueID());
            data.setKeysEnabled(mp.getUniqueID(), enabled);
            mp.sendStatusMessage(new TextComponentTranslation(
                    enabled ? "pocketmod.msg.keys_enabled" : "pocketmod.msg.keys_disabled"), true);
        });
    }

    private void updateTrusted(final boolean add) {
        final String name = nameField == null ? "" : nameField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
        schedule(mp -> {
            PocketDoorData data = PocketDoorData.get(mp.getServer().getWorld(0));
            boolean changed = add ? data.addTrusted(mp.getUniqueID(), name)
                    : data.removeTrusted(mp.getUniqueID(), name);
            mp.sendStatusMessage(new TextComponentTranslation(
                    changed ? (add ? "pocketmod.msg.trusted_added" : "pocketmod.msg.trusted_removed")
                            : "pocketmod.msg.trusted_unchanged", name), true);
        });
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
        PocketDoorData data = data();
        boolean hasPair = selectedPairId != null && data != null
                && data.ownsPair(ownerId(), selectedPairId);
        for (GuiButton button : buttonList) {
            if (button.id == BTN_BUY_PAIR) {
                button.displayString = "Пара дверей: 1000 (" + (data == null ? 0 : data.pairCount(ownerId()))
                        + "/" + PocketDoorData.MAX_PAIRS + ")";
                button.enabled = data != null && data.canBuyPair(ownerId());
            } else if (button.id == BTN_BUY_KEY || button.id == BTN_BUY_TEMP) {
                button.enabled = hasPair;
            } else if (button.id == BTN_TOGGLE_KEYS) {
                button.displayString = data != null && data.keysEnabled(ownerId())
                        ? "Все ключи: включены" : "Все ключи: заблокированы";
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
        fontRenderer.drawString("Куплено пар: " + (data() == null ? 0 : data().pairCount(ownerId()))
                + "/" + PocketDoorData.MAX_PAIRS, 10, 28, 0xFFCCCCCC);
        fontRenderer.drawString("Выбрана: " + (selectedPairId == null ? "нет пары"
                : "пара " + (pairIds.indexOf(selectedPairId) + 1)), rightX, 28, 0xFFCCCCCC);
        fontRenderer.drawString("Доверенные игроки:", rightX, 142, 0xFFE0A040);

        if (data() != null) {
            List<String> trusted = data().trustedNames(ownerId());
            String names = trusted.isEmpty() ? "список пуст" : String.join(", ", trusted);
            int y = 205;
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
        return true;
    }
}
