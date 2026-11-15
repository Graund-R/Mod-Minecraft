package pocketmod;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiWarehouse extends GuiContainer {
    private final int slots;

    public GuiWarehouse(EntityPlayer player) {
        super(new ContainerWarehouse(player));
        this.slots = Math.max(0, Math.min(3, AbilityStorage.getLevel(player, AbilityType.WAREHOUSE)));
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        Gui.drawRect(x, y, x + xSize, y + ySize, 0xEE101820);
        Gui.drawRect(x, y, x + xSize, y + 1, 0xFFE0A040);
        Gui.drawRect(x + 7, y + 26, x + xSize - 7, y + 59, 0xAA202A36);
        for (int i = 0; i < slots; i++) drawSlot(x + 62 + i * 24, y + 35);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) drawSlot(x + 8 + col * 18, y + 84 + row * 18);
        }
        for (int col = 0; col < 9; col++) drawSlot(x + 8 + col * 18, y + 142);
    }

    private void drawSlot(int x, int y) {
        Gui.drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF596575);
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF1A222C);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(Tr.format("pocketmod.gui.warehouse_title"), 8, 8, 0xE0A040);
        fontRenderer.drawString(Tr.format("pocketmod.gui.warehouse_slots", slots, 3), 8, 17, 0xAFC4D8);
        fontRenderer.drawString(Tr.format("pocketmod.gui.inventory"), 8, 72, 0xD0D8E0);
    }

    @Override public boolean doesGuiPauseGame() { return true; }
}
