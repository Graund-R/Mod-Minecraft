package pocketmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Groups existing properties without moving or renaming saved configuration keys. */
@SideOnly(Side.CLIENT)
public final class ExileSettingsGui extends GuiConfig {
    public ExileSettingsGui(GuiScreen parent) {
        super(parent, elements(), PocketMod.MODID, false, false, Tr.format("pocketmod.config.exile"));
    }
    static String group(String key) {
        if (key.startsWith("debug")) return "tools";
        if (key.startsWith("respawn") || key.startsWith("spawn") || key.equals("pursuerCap")) return "enemies";
        if (key.startsWith("cyclic") || key.startsWith("simple") || key.startsWith("thirst") || key.startsWith("frenzy")
                || key.equals("combatRelief") || key.equals("heartbeat")) return "thirst";
        if (key.startsWith("trial") || key.startsWith("firstTrial") || key.startsWith("nextTrial")) return "trials";
        if (key.equals("initialSlots") || key.endsWith("Price") || key.equals("discoveryChance")
                || key.startsWith("pocket")) return "progress";
        return "infection";
    }
    static List<IConfigElement> elements() {
        List<IConfigElement> result = new ArrayList<>();
        if (PocketConfig.config == null) return result;
        for (String group : new String[]{"infection", "enemies", "thirst", "progress", "trials", "tools"}) {
            List<IConfigElement> children = new ArrayList<>();
            for (Property p : PocketConfig.config.getCategory("изгнанник").getOrderedValues())
                if (group.equals(group(p.getName()))) children.add(new ConfigElement(p));
            if (!children.isEmpty()) result.add(new DummyCategoryElement(group, "pocketmod.config.group." + group, children));
        }
        return result;
    }
}
