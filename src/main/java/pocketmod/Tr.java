package pocketmod;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Перевод для окон. Берём из .lang (en_us / ru_ru).
 * Если ключ не нашёлся — возвращаем сам ключ.
 */
@SideOnly(Side.CLIENT)
public class Tr {

    public static String format(String key, Object... args) {
        try {
            String t = I18n.format(key, args);
            if (t != null && !t.equals(key)) return t;
        } catch (Exception ignored) {
        }
        // fallback: simple format if I18n failed
        if (args == null || args.length == 0) return key;
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }
}
