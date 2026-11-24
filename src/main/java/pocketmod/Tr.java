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

    /** Fill missing entries in BOTH client GUI locale and legacy component translations. */
    public static void ensureLoaded() {
        try {
            Object locale=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                    I18n.class,null,"i18nLocale","field_135054_a");
            Map<String,String> values=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                    net.minecraft.client.resources.Locale.class,(net.minecraft.client.resources.Locale)locale,
                    "properties","field_135032_a");
            if(values==null) return;
            for(Map.Entry<String,String> entry:dict().entrySet()) {
                // Existing resource-pack translations take precedence.
                if(!I18n.hasKey(entry.getKey())) values.put(entry.getKey(),entry.getValue());
            }
            for(Map.Entry<String,String> entry:Lang.EN.entrySet()) values.putIfAbsent(entry.getKey(),entry.getValue());
            StringBuilder text=new StringBuilder();
            for(Map.Entry<String,String> entry:dict().entrySet())
                text.append(entry.getKey()).append('=').append(values.get(entry.getKey())).append('\n');
            net.minecraft.util.text.translation.LanguageMap.inject(new ByteArrayInputStream(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(Exception ex) {
            // GUI calls still have the built-in dictionary if a mod changes the locale implementation.
        }
    }
}
