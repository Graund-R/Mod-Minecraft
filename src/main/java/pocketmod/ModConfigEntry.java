package pocketmod;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Uses the Mods list's own Config button, including packs that lose the factory lookup. */
public final class ModConfigEntry {
    private static final Field SELECTED=ReflectionHelper.findField(GuiModList.class,"selectedMod");
    private static final Field BUTTON=ReflectionHelper.findField(GuiModList.class,"configModButton");
    private static boolean selected(Object gui) {
        if(!(gui instanceof GuiModList))return false;
        try {ModContainer mod=(ModContainer)SELECTED.get(gui);return mod!=null&&PocketMod.MODID.equals(mod.getModId());}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if(!selected(event.getGui()))return;
        try {GuiButton button=(GuiButton)BUTTON.get(event.getGui());button.visible=true;button.enabled=true;}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if(event.getButton().id!=20||!selected(event.getGui()))return;
        event.setCanceled(true);Minecraft.getMinecraft().displayGuiScreen(new PocketConfigGui(event.getGui()));
    }
}
