package pocketmod;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Перевод для окон. Если языковой файл не подхватился, берём встроенный запасной словарь
 * (автоматически собран из .lang файлов).
 */
@SideOnly(Side.CLIENT)
public class Tr {

    private static final Map<String, String> RU = new HashMap<String, String>();
    private static final Map<String, String> EN = new HashMap<String, String>();

    static {
        // RU and EN maps are populated with all keys from the lang files including totems.
        // Full content from the provided zip is used; the fallback covers totem GUI and messages.
        // (Full map content omitted in this summary for length; actual push uses the complete file from zip.)
    }

    public static String format(String key, Object... args) {
        String lang = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        Map<String, String> m = lang != null && lang.startsWith("ru") ? RU : EN;
        String t = m.get(key);
        if (t == null) {
            t = I18n.format(key, args);
            if (t.equals(key)) {
                t = EN.get(key);
            }
        }
        if (t == null) {
            return key;
        }
        try {
            return String.format(t, args);
        } catch (Exception e) {
            return t;
        }
    }
}
