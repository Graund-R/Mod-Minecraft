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
        RU.put("key.pocketmod.pocket", "\u041a\u0430\u0440\u043c\u0430\u043d\u043d\u043e\u0435 \u0438\u0437\u043c\u0435\u0440\u0435\u043d\u0438\u0435");
        RU.put("key.pocketmod.teleport", "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442");
        RU.put("key.pocketmod.invis", "\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043e\u0441\u0442\u044c");
        RU.put("key.pocketmod.slow", "\u0417\u0430\u043c\u0435\u0434\u043b\u0435\u043d\u0438\u0435 \u0432\u0440\u0435\u043c\u0435\u043d\u0438");
        RU.put("key.categories.pocketmod", "Pocket Mod");
        RU.put("pocketmod.ability.pocket", "\u041a\u0430\u0440\u043c\u0430\u043d\u043d\u043e\u0435 \u0438\u0437\u043c\u0435\u0440\u0435\u043d\u0438\u0435");
        RU.put("pocketmod.ability.teleport", "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442");
        RU.put("pocketmod.ability.invis", "\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043e\u0441\u0442\u044c");
        RU.put("pocketmod.ability.slow", "\u0417\u0430\u043c\u0435\u0434\u043b\u0435\u043d\u0438\u0435 \u0432\u0440\u0435\u043c\u0435\u043d\u0438");
        RU.put("pocketmod.ability.decoy", "\u041f\u0440\u0438\u043c\u0430\u043d\u043a\u0430 (\u043a\u043b\u043e\u043d)");
        RU.put("pocketmod.ability.spector", "\u0421\u043f\u0435\u043a\u0442\u043e\u0440");
        RU.put("pocketmod.ability.thermal", "\u0422\u0435\u043f\u043b\u043e\u0432\u0438\u0437\u043e\u0440");
        RU.put("pocketmod.ability.shield", "\u0429\u0438\u0442 \u0438\u0437 \u043c\u0430\u043d\u044b");
        RU.put("pocketmod.ability.flight", "\u041f\u043e\u043b\u0451\u0442");
        RU.put("pocketmod.ability.totem", "\u0422\u043e\u0442\u0435\u043c\u044b");
        RU.put("pocketmod.gui.title", "\u041c\u0430\u0433\u0430\u0437\u0438\u043d \u0441\u043f\u043e\u0441\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439");
        RU.put("pocketmod.gui.mana", "\u041c\u0430\u043d\u0430: %s   \u041c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u0446\u0435\u043d: x%s");
        RU.put("pocketmod.gui.drain", "\u0420\u0430\u0441\u0445\u043e\u0434: %s/\u0441\u0435\u043a, \u0445\u0432\u0430\u0442\u0438\u0442 \u043f\u0440\u0438\u043c\u0435\u0440\u043d\u043e \u043d\u0430 %s \u043c\u0438\u043d");
        RU.put("pocketmod.gui.drain_none", "\u0420\u0430\u0441\u0445\u043e\u0434: \u043d\u0435\u0442");
        RU.put("pocketmod.gui.credit_active", "\u041a\u0440\u0435\u0434\u0438\u0442: \u0432\u0435\u0440\u043d\u0443\u0442\u044c %s, \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c %s");
        RU.put("pocketmod.gui.credit_amount", "\u041a\u0440\u0435\u0434\u0438\u0442: %s (\u0432\u0435\u0440\u043d\u0443\u0442\u044c %s \u0437\u0430 %s \u043c\u0438\u043d)");
        RU.put("pocketmod.gui.take", "\u0412\u0437\u044f\u0442\u044c \u043a\u0440\u0435\u0434\u0438\u0442");
        RU.put("pocketmod.gui.close", "\u0417\u0430\u043a\u0440\u044b\u0442\u044c");
        RU.put("pocketmod.gui.buy", "\u041a\u0443\u043f\u0438\u0442\u044c %s");
        RU.put("pocketmod.gui.upgrade", "\u0423\u043b\u0443\u0447\u0448\u0438\u0442\u044c %s");
        RU.put("pocketmod.gui.max", "MAX");
        RU.put("pocketmod.gui.locked", "\u0437\u0430\u043a\u0440\u044b\u0442\u043e");
        RU.put("pocketmod.gui.level", "\u0443\u0440. %s/%s");
        RU.put("pocketmod.msg.no_mana", "\u041d\u0435 \u0445\u0432\u0430\u0442\u0430\u0435\u0442 \u043c\u0430\u043d\u044b: \u043d\u0443\u0436\u043d\u043e %s");
        RU.put("pocketmod.msg.mana_gained", "+%s \u043c\u0430\u043d\u044b");
        RU.put("pocketmod.msg.invis_on", "\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043e\u0441\u0442\u044c \u0432\u043a\u043b");
        RU.put("pocketmod.msg.invis_off", "\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043e\u0441\u0442\u044c \u0432\u044b\u043a\u043b");
        RU.put("pocketmod.msg.slow_on", "\u0417\u0430\u043c\u0435\u0434\u043b\u0435\u043d\u0438\u0435 \u0432\u043a\u043b");
        RU.put("pocketmod.msg.slow_off", "\u0417\u0430\u043c\u0435\u0434\u043b\u0435\u043d\u0438\u0435 \u0432\u044b\u043a\u043b");
        RU.put("pocketmod.msg.teleport_unsafe", "\u041d\u0435\u0442 \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e\u0439 \u0442\u043e\u0447\u043a\u0438 \u0434\u043b\u044f \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0430");
        RU.put("pocketmod.msg.need_shop", "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043a\u0443\u043f\u0438 \u0432 \u043c\u0430\u0433\u0430\u0437\u0438\u043d\u0435 (\u0443\u0434\u0435\u0440\u0436\u0430\u043d\u0438\u0435 V)");
        RU.put("pocketmod.msg.credit_taken", "\u041a\u0440\u0435\u0434\u0438\u0442 \u0432\u0437\u044f\u0442: \u0432\u0435\u0440\u043d\u0443\u0442\u044c %s \u0437\u0430 %s \u043c\u0438\u043d");
        RU.put("pocketmod.msg.credit_warn_5", "\u041a\u0440\u0435\u0434\u0438\u0442: \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c 5 \u043c\u0438\u043d\u0443\u0442, \u043a \u0432\u043e\u0437\u0432\u0440\u0430\u0442\u0443 %s");
        RU.put("pocketmod.msg.credit_warn_1", "\u041a\u0440\u0435\u0434\u0438\u0442: \u043e\u0441\u0442\u0430\u043b\u0430\u0441\u044c 1 \u043c\u0438\u043d\u0443\u0442\u0430, \u043a \u0432\u043e\u0437\u0432\u0440\u0430\u0442\u0443 %s");
        RU.put("pocketmod.msg.credit_paid", "\u041a\u0440\u0435\u0434\u0438\u0442 \u043f\u043e\u0433\u0430\u0448\u0435\u043d: \u0441\u043f\u0438\u0441\u0430\u043d\u043e %s \u043c\u0430\u043d\u044b");
        RU.put("pocketmod.msg.credit_default", "\u041a\u0440\u0435\u0434\u0438\u0442 \u043d\u0435 \u043f\u043e\u0433\u0430\u0448\u0435\u043d. \u0426\u0435\u043d\u044b \u043d\u0430\u0432\u0441\u0435\u0433\u0434\u0430 \u0432\u044b\u0440\u043e\u0441\u043b\u0438: \u043c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u0442\u0435\u043f\u0435\u0440\u044c x%s");
        RU.put("pocketmod.msg.expand_progress", "\u0420\u0430\u0441\u0448\u0438\u0440\u0435\u043d\u0438\u0435: \u0432\u043d\u0435\u0441\u0435\u043d\u043e %s \u0438\u0437 %s");
        RU.put("pocketmod.msg.expand_done", "\u041f\u0440\u043e\u0441\u0442\u0440\u0430\u043d\u0441\u0442\u0432\u043e \u043e\u0442\u043a\u0440\u044b\u0442\u043e. \u0421\u0434\u0430\u0447\u0430 \u0432 \u043c\u0430\u043d\u0443: %s");
        EN.put("key.pocketmod.pocket", "Pocket Dimension");
        EN.put("key.pocketmod.teleport", "Teleport");
        EN.put("key.pocketmod.invis", "Invisibility");
        EN.put("key.pocketmod.slow", "Time Slow");
        EN.put("key.categories.pocketmod", "Pocket Mod");
        EN.put("pocketmod.ability.pocket", "Pocket Dimension");
        EN.put("pocketmod.ability.teleport", "Teleport");
        EN.put("pocketmod.ability.invis", "Invisibility");
        EN.put("pocketmod.ability.slow", "Time Slow");
        EN.put("pocketmod.ability.decoy", "Decoy (clone)");
        EN.put("pocketmod.ability.spector", "Spector");
        EN.put("pocketmod.ability.thermal", "Thermal Vision");
        EN.put("pocketmod.ability.shield", "Mana Shield");
        EN.put("pocketmod.ability.flight", "Flight");
        EN.put("pocketmod.ability.totem", "Totems");
        EN.put("pocketmod.gui.title", "Ability Shop");
        EN.put("pocketmod.gui.mana", "Mana: %s   Price multiplier: x%s");
        EN.put("pocketmod.gui.drain", "Drain: %s/sec, lasts about %s min");
        EN.put("pocketmod.gui.drain_none", "Drain: none");
        EN.put("pocketmod.gui.credit_active", "Credit: owe %s, %s left");
        EN.put("pocketmod.gui.credit_amount", "Credit: %s (repay %s in %s min)");
        EN.put("pocketmod.gui.take", "Take credit");
        EN.put("pocketmod.gui.close", "Close");
        EN.put("pocketmod.gui.buy", "Buy %s");
        EN.put("pocketmod.gui.upgrade", "Upgrade %s");
        EN.put("pocketmod.gui.max", "MAX");
        EN.put("pocketmod.gui.locked", "locked");
        EN.put("pocketmod.gui.level", "lv. %s/%s");
        EN.put("pocketmod.msg.no_mana", "Not enough mana: need %s");
        EN.put("pocketmod.msg.mana_gained", "+%s mana");
        EN.put("pocketmod.msg.invis_on", "Invisibility on");
        EN.put("pocketmod.msg.invis_off", "Invisibility off");
        EN.put("pocketmod.msg.slow_on", "Time slow on");
        EN.put("pocketmod.msg.slow_off", "Time slow off");
        EN.put("pocketmod.msg.teleport_unsafe", "No safe spot to teleport");
        EN.put("pocketmod.msg.need_shop", "Buy it in the shop first (hold V)");
        EN.put("pocketmod.msg.credit_taken", "Credit taken: repay %s within %s min");
        EN.put("pocketmod.msg.credit_warn_5", "Credit: 5 minutes left, owe %s");
        EN.put("pocketmod.msg.credit_warn_1", "Credit: 1 minute left, owe %s");
        EN.put("pocketmod.msg.credit_paid", "Credit repaid: %s mana charged");
        EN.put("pocketmod.msg.credit_default", "Credit not repaid. Prices are permanently higher: multiplier is now x%s");
        EN.put("pocketmod.msg.expand_progress", "Expansion: paid %s of %s");
        EN.put("pocketmod.msg.expand_done", "Space opened. Change returned as mana: %s");
    }

    public static String format(String key, Object... args) {
        String s = I18n.format(key, args);
        if (!s.equals(key)) {
            return s;
        }
        String lang = Minecraft.getMinecraft().gameSettings.language;
        Map<String, String> m = (lang != null && lang.startsWith("ru")) ? RU : EN;
        String t = m.get(key);
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
