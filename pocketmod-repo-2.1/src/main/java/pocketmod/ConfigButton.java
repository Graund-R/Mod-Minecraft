package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Запасной вход в настройки: кнопка «Pocket Mod: настройки» в левом верхнем углу меню паузы и экрана «Настройки».
 * Работает независимо от кнопки Config в списке модов.
 */
@SideOnly(Side.CLIENT)
public class ConfigButton {

    private static final int ID = 9137;

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (e.getGui() instanceof GuiIngameMenu || e.getGui() instanceof GuiOptions) {
            e.getButtonList().add(new GuiButton(ID, 4, 4, 140, 20, Tr.format("pocketmod.gui.config")));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (e.getButton() != null && e.getButton().id == ID
                && (e.getGui() instanceof GuiIngameMenu || e.getGui() instanceof GuiOptions)) {
            Minecraft.getMinecraft().displayGuiScreen(new PocketConfigGui(e.getGui()));
            e.setCanceled(true);
        }
    }
}
