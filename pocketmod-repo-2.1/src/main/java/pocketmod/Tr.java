package pocketmod;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Перевод для окон. Берём из .lang игры; если ключ не нашёлся — из встроенного словаря {@link Lang},
 * чтобы вместо названий никогда не показывались строки-ключи.
 */
@SideOnly(Side.CLIENT)
public class Tr {

    private static Map<String, String> dict() {
        String code = "en_us";
        try {
            code = Minecraft.getMinecraft().gameSettings.language;
        } catch (Throwable ignored) {
        }
        return code != null && code.toLowerCase().startsWith("ru") ? Lang.RU : Lang.EN;
    }

    public static String format(String key, Object... args) {
        try {
            String t = I18n.format(key, args);
            if (t != null && !t.equals(key)) return t;
        } catch (Exception ignored) {
        }
        String pat = dict().get(key);
        if (pat == null) pat = Lang.EN.get(key);
        if (pat == null) return key;
        if (args == null || args.length == 0) return pat.replace("%%", "%");
        try {
            return String.format(pat, args);
        } catch (Exception e) {
            return pat;
        }
    }

    private static boolean injected;

    /**
     * Если игра не подхватила наш .lang, кладём словарь прямо в общую карту переводов
     * (её читают сообщения над хотбаром, которые приходят с сервера).
     */
    public static void ensureLoaded() {
        try {
            String probe = "pocketmod.msg.no_mana";
            String t = I18n.format(probe);
            if (!t.equals(probe)) {
                injected = false;
                return;
            }
            Map<String, String> d = dict();
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> en : d.entrySet()) {
                sb.append(en.getKey()).append('=').append(en.getValue()).append('\n');
            }
            InputStream in = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
            Class<?> lm = Class.forName("net.minecraft.util.text.translation.LanguageMap");
            lm.getMethod("inject", InputStream.class).invoke(null, in);
            if (!injected) System.out.println("[PocketMod] lang files were not loaded by the game, injected built-in dictionary");
            injected = true;
        } catch (Throwable ignored) {
        }
    }
}
