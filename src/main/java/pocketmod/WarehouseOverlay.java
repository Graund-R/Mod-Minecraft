package pocketmod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

/**
 * Слоты карманного склада сбоку окна инвентаря (E). Рисуем колонку справа от окна; клики уходят на сервер,
 * который меняет содержимое склада и предмет на курсоре (WarehouseSlots).
 */
@SideOnly(Side.CLIENT)
public class WarehouseOverlay {

    private static final int SLOT = 18;
    private static final int GUI_WIDTH = 176;

    private static int originX(GuiInventory gui) {
        return gui.getGuiLeft() + GUI_WIDTH + 8;
    }

    private static int originY(GuiInventory gui) {
        return gui.getGuiTop() + 14;
    }

    private static int slotAt(GuiInventory gui, int mx, int my, int n) {
        int x = originX(gui);
        int y = originY(gui);
        if (mx < x || mx >= x + SLOT) return -1;
        for (int i = 0; i < n; i++) {
            int sy = y + i * SLOT;
            if (my >= sy && my < sy + SLOT) return i;
        }
        return -1;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiInventory)) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        if (p == null) return;
        int n = WarehouseSlots.slots(p);
        if (n <= 0) return;
        GuiInventory gui = (GuiInventory) event.getGui();
        int x = originX(gui);
        int y = originY(gui);
        int mx = event.getMouseX();
        int my = event.getMouseY();

        Gui.drawRect(x - 4, y - 14, x + SLOT + 4, y + n * SLOT + 4, 0xDD101820);
        Gui.drawRect(x - 4, y - 14, x + SLOT + 4, y - 13, 0xFFE0A040);
        mc.fontRenderer.drawStringWithShadow(Tr.format("pocketmod.gui.warehouse_short"), x - 2, y - 11, 0xE0A040);

        WarehouseInventory inv = new WarehouseInventory(p);
        ItemStack hover = ItemStack.EMPTY;
        int hoverSlot = slotAt(gui, mx, my, n);
        for (int i = 0; i < n; i++) {
            int sy = y + i * SLOT;
            Gui.drawRect(x, sy, x + SLOT, sy + SLOT, 0xFF596575);
            Gui.drawRect(x + 1, sy + 1, x + SLOT - 1, sy + SLOT - 1, i == hoverSlot ? 0xFF2C3A4A : 0xFF1A222C);
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(s, x + 1, sy + 1);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, s, x + 1, sy + 1, null);
                RenderHelper.disableStandardItemLighting();
                if (i == hoverSlot) hover = s;
            }
        }

        // предмет на курсоре рисуем поверх колонки (ванильный рисуется до нас)
        ItemStack cursor = p.inventory.getItemStack();
        if (!cursor.isEmpty() && hoverSlot >= 0) {
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().zLevel = 200.0F;
            mc.getRenderItem().renderItemAndEffectIntoGUI(cursor, mx - 8, my - 8);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, cursor, mx - 8, my - 8, null);
            mc.getRenderItem().zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
        }
        if (!hover.isEmpty() && cursor.isEmpty()) {
            List<String> tip = hover.getTooltip(p, mc.gameSettings.advancedItemTooltips
                    ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
            GuiUtils.drawHoveringText(tip, mx, my, gui.width, gui.height, -1, mc.fontRenderer);
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiInventory)) return;
        if (!Mouse.getEventButtonState()) return;
        int button = Mouse.getEventButton();
        if (button != 0 && button != 1) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        int n = WarehouseSlots.slots(mc.player);
        if (n <= 0) return;
        GuiInventory gui = (GuiInventory) event.getGui();
        int mx = Mouse.getEventX() * gui.width / mc.displayWidth;
        int my = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        int slot = slotAt(gui, mx, my, n);
        if (slot < 0) return;
        int flags = (button == 1 ? 1 : 0) | (GuiInventory.isShiftKeyDown() ? 2 : 0);
        NetworkHandler.action(NetAction.WAREHOUSE_CLICK, slot, flags, 0, "");
        event.setCanceled(true);
    }
}
