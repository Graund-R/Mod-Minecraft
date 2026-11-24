package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Shop shortcut below the creative inventory's bottom row of tabs. */
@SideOnly(Side.CLIENT)
public final class CreativeInventoryButton {
    private static final class ShopButton extends GuiButton {
        ShopButton(GuiContainerCreative gui) {
            super(9452, 0, 0, 80, 20, "Pocket Mod");
            position(gui);
        }

        void position(GuiContainerCreative gui) {
            x = Math.max(0, Math.min(gui.getGuiLeft(), gui.width - width));
            y = Math.max(0, Math.min(gui.getGuiTop() + gui.getYSize() + 32,
                    gui.height - height - 2));
        }
    }

    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiContainerCreative)
            event.getButtonList().add(new ShopButton((GuiContainerCreative) event.getGui()));
    }

    @SubscribeEvent
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainerCreative)
                || !(event.getButton() instanceof ShopButton)) return;
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) mc.displayGuiScreen(new ShopGui(mc.player));
    }
}
