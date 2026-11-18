package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Сканер руд: режим тепловизора (Shift+C), а не отдельная способность. Открывается на 3 уровне тепловизора.
 * Игроки, купившие прежнюю отдельную способность, сохраняют её. Серверная часть: состояние, расход маны, выбор целей.
 */
public final class OreScanner {
    private static final String KEY = "pm_ore_scan";
    /** Битовая маска ОТКЛЮЧЁННЫХ целей (хранится в постоянных данных игрока). */
    private static final String MASK = "pm_scan_off";
    private OreScanner() {}

    public static boolean available(EntityPlayer player) {
        return AbilityStorage.getLevel(player, AbilityType.THERMAL) >= 3
                || AbilityStorage.isUnlocked(player, AbilityType.ORE_SCANNER);
    }

    public static boolean isOn(EntityPlayer player) {
        return player != null && player.getEntityData().getBoolean(KEY);
    }

    public static double drainPerSec(EntityPlayer player) {
        return PocketConfig.oreScannerDrain * Mana.getPriceMultiplier(player);
    }

    public static int radius(EntityPlayer player) {
        int level = AbilityStorage.getLevel(player, AbilityType.ORE_SCANNER);
        if (level <= 0) level = 2; // доступ через тепловизор 3 уровня
        level = Math.max(1, Math.min(3, level));
        return PocketConfig.oreScannerRadius[Math.min(PocketConfig.oreScannerRadius.length - 1, level - 1)];
    }

    public static int disabledMask(EntityPlayer player) {
        return PlayerData.root(player).getInteger(MASK);
    }

    public static void toggleTarget(EntityPlayerMP player, int index) {
        if (index < 0 || index >= 31 || index >= PocketConfig.oreScannerTargets.length) return;
        PlayerData.root(player).setInteger(MASK, disabledMask(player) ^ (1 << index));
    }

    public static void toggle(EntityPlayerMP player) {
        if (!available(player)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.scanner_need"), true);
            return;
        }
        boolean next = !isOn(player);
        if (next && Mana.get(player) < drainPerSec(player)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                    (int) Math.ceil(drainPerSec(player))), true);
            return;
        }
        player.getEntityData().setBoolean(KEY, next);
        player.sendStatusMessage(new TextComponentTranslation(
                next ? "pocketmod.msg.scanner_on" : "pocketmod.msg.scanner_off"), true);
    }

    /** Ману списывает реестр способностей; здесь только выключение, если сканер перестал быть доступен. */
    public static void tick(EntityPlayerMP player) {
        if (isOn(player) && !available(player)) onManaExhausted(player);
    }

    /** Мана кончилась (вызывает реестр способностей). */
    public static void onManaExhausted(EntityPlayerMP player) {
        player.getEntityData().setBoolean(KEY, false);
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.scanner_empty"), true);
    }
}
