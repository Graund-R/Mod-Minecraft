package pocketmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Игровой экран настроек: все категории из config/pocketmod.cfg (цены, дальности, расходы...). */
@SideOnly(Side.CLIENT)
public class PocketConfigGui extends GuiConfig {

    public PocketConfigGui(GuiScreen parent) {
        super(parent, elements(), PocketMod.MODID, false, false, Tr.format("pocketmod.config.title"));
    }

    private static List<IConfigElement> elements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        if (PocketConfig.config != null) {
            for (String name : PocketConfig.config.getCategoryNames()) {
                if (ConfigMigrations.META.equals(name)) continue;
                if ("изгнанник".equals(name)) {
                    list.add(new net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement(
                            "exile", "pocketmod.config.exile", ExileSettingsGui.elements()));
                } else list.add(new ConfigElement(PocketConfig.config.getCategory(name)));
            }
        }
        return list;
    }
}
