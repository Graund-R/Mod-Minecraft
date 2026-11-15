package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

/** Серверная часть сканера: состояние и расход маны. */
public final class OreScanner {
    private static final String KEY = "pm_ore_scan";
    private OreScanner() {}

    public static boolean isOn(EntityPlayer player) {
        return player != null && player.getEntityData().getBoolean(KEY);
    }

    public static double drainPerSec(EntityPlayer player) {
        return PocketConfig.oreScannerDrain * Mana.getPriceMultiplier(player);
    }

    public static int radius(EntityPlayer player) {
        int level = Math.max(1, Math.min(3, AbilityStorage.getLevel(player, AbilityType.ORE_SCANNER)));
        return PocketConfig.oreScannerRadius[Math.min(PocketConfig.oreScannerRadius.length - 1, level - 1)];
    }

    public static void toggle(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.ORE_SCANNER)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
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

    public static void tick(EntityPlayerMP player) {
        if (!isOn(player)) return;
        if (!AbilityStorage.isUnlocked(player, AbilityType.ORE_SCANNER)
                || !Mana.spend(player, drainPerSec(player) / 20.0)) {
            player.getEntityData().setBoolean(KEY, false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.scanner_empty"), true);
        }
    }
}
