package pocketmod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ShopGui extends GuiScreen {

    private static final int BTN_BUY_BASE = 100;
    private static final int BTN_CREDIT_100 = 1;
    private static final int BTN_CREDIT_500 = 2;
    private static final int BTN_CREDIT_1000 = 3;
    private static final int BTN_CLOSE = 0;

    private final EntityPlayer player;
    private final List<AbilityType> list = new ArrayList<AbilityType>();

    private EntityPlayer dataPlayer() {
        if (mc != null && mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null) {
            net.minecraft.entity.player.EntityPlayerMP mp =
                mc.getIntegratedServer().getPlayerList().getPlayerByUUID(player.getUniqueID());
            if (mp != null) return mp;
        }
        return player;
    }

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

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int top = 40;
        for (int i = 0; i < list.size(); i++) {
            AbilityType t = list.get(i);
            int y = top + i * 22;
            buttonList.add(new GuiButton(BTN_BUY_BASE + i, cx + 80, y, 70, 20, buyLabel(t)));
        }
        int cy = height - 52;
        buttonList.add(new GuiButton(BTN_CREDIT_100, cx - 160, cy, 60, 20, "+100"));
        buttonList.add(new GuiButton(BTN_CREDIT_500, cx - 90, cy, 70, 20, "+500"));
        buttonList.add(new GuiButton(BTN_CREDIT_1000, cx - 20, cy, 70, 20, "+1000"));
        buttonList.add(new GuiButton(BTN_CLOSE, cx + 70, cy, 70, 20, "\u0417\u0430\u043a\u0440\u044b\u0442\u044c"));
    }

    private String buyLabel(AbilityType t) {
        int lvl = AbilityStorage.getLevel(dataPlayer(), t);
        if (lvl >= t.maxLevel) return "MAX";
        int cost = t.costForNextLevel(lvl);
        if (cost < 0) return "\u2014";
        double real = cost * Mana.getPriceMultiplier(dataPlayer());
        return lvl == 0 ? ("\u041a\u0443\u043f\u0438\u0442\u044c " + (int) Math.ceil(real)) : ("\u2191 " + (int) Math.ceil(real));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRenderer, "\u041c\u0430\u0433\u0430\u0437\u0438\u043d \u0441\u043f\u043e\u0441\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439", cx, 12, 0xFFE080);
        drawCenteredString(fontRenderer,
                String.format("\u041c\u0430\u043d\u0430: %d   \u041c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u0446\u0435\u043d: \u00d7%.2f",
                        (int) Mana.get(dataPlayer()), Mana.getPriceMultiplier(dataPlayer())),
                cx, 24, 0xAAAAFF);

        if (Mana.hasCredit(dataPlayer())) {
            long left = Math.max(0, Mana.getCreditDue(dataPlayer()) - System.currentTimeMillis()) / 1000;
            drawCenteredString(fontRenderer,
                    String.format("\u041a\u0440\u0435\u0434\u0438\u0442: %.0f (\u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c %d \u0441)", Mana.getCredit(dataPlayer()), left),
                    cx, height - 64, 0xFF8888);
        }

        int top = 40;
        for (int i = 0; i < list.size(); i++) {
            AbilityType t = list.get(i);
            int y = top + i * 22;
            int lvl = AbilityStorage.getLevel(dataPlayer(), t);
            String status = lvl == 0 ? TextFormatting.GRAY + "\u0437\u0430\u043a\u0440\u044b\u0442\u043e"
                    : TextFormatting.GREEN + ("\u0443\u0440." + lvl + "/" + t.maxLevel);
            fontRenderer.drawStringWithShadow(t.title + "  " + status, cx - 150, y + 6, 0xFFFFFF);
        }
        fontRenderer.drawStringWithShadow("\u041a\u0440\u0435\u0434\u0438\u0442 (+20%):", cx - 160, height - 64, 0xCCCCCC);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_CLOSE) {
            mc.player.closeScreen();
            return;
        }
        if (button.id == BTN_CREDIT_100 || button.id == BTN_CREDIT_500 || button.id == BTN_CREDIT_1000) {
            if (Mana.hasCredit(dataPlayer())) return;
            int amount = button.id == BTN_CREDIT_100 ? 100 : button.id == BTN_CREDIT_500 ? 500 : 1000;
            net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
            if (server != null) {
                final int amt = amount;
                server.addScheduledTask(() -> {
                    net.minecraft.entity.player.EntityPlayerMP mp =
                            server.getPlayerList().getPlayerByUUID(player.getUniqueID());
                    if (mp != null && !Mana.hasCredit(mp)) {
                        Mana.takeCredit(mp, amt);
                    }
                });
            }
            return;
        }
        if (button.id >= BTN_BUY_BASE && button.id < BTN_BUY_BASE + list.size()) {
            AbilityType t = list.get(button.id - BTN_BUY_BASE);
            net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
            if (server != null) {
                server.addScheduledTask(() -> {
                    net.minecraft.entity.player.EntityPlayerMP mp =
                            server.getPlayerList().getPlayerByUUID(player.getUniqueID());
                    if (mp != null) {
                        AbilityStorage.tryUpgrade(mp, t);
                    }
                });
            }
        }
    }

    @Override
    public void updateScreen() {
        for (int i = 0; i < list.size() && i < buttonList.size(); i++) {
            GuiButton b = buttonList.get(i);
            if (b.id == BTN_BUY_BASE + i) {
                AbilityType t = list.get(i);
                b.displayString = buyLabel(t);
                int lvl = AbilityStorage.getLevel(dataPlayer(), t);
                b.enabled = lvl < t.maxLevel && t.costForNextLevel(lvl) > 0;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
