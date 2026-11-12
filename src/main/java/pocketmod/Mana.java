package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Единый кошелёк маны + данные кредита и множителя цен.
 * Всё лежит в persistent NBT игрока.
 */
public class Mana {

    private static final String KEY_MANA = "pocketmod_mana";
    private static final String KEY_MULT = "pocketmod_price_mult";
    private static final String KEY_CREDIT = "pocketmod_credit";
    private static final String KEY_CREDIT_DUE = "pocketmod_credit_due"; // real-time millis
    private static final String KEY_FREE_ENTRY = "pocketmod_free_entry";

    private static NBTTagCompound data(EntityPlayer player) {
        return player.getEntityData();
    }

    // ===== Мана =====
    public static double get(EntityPlayer player) {
        return data(player).getDouble(KEY_MANA);
    }

    public static void set(EntityPlayer player, double value) {
        data(player).setDouble(KEY_MANA, Math.max(0.0, value));
    }

    public static void add(EntityPlayer player, double amount) {
        set(player, get(player) + amount);
    }

    public static boolean spend(EntityPlayer player, double amount) {
        if (amount <= 0) return true;
        double cur = get(player);
        if (cur < amount) return false;
        set(player, cur - amount);
        return true;
    }

    /** Учитывает множитель цен */
    public static boolean spendScaled(EntityPlayer player, double baseAmount) {
        double real = baseAmount * getPriceMultiplier(player);
        return spend(player, real);
    }

    // ===== Множитель цен =====
    public static double getPriceMultiplier(EntityPlayer player) {
        double m = data(player).getDouble(KEY_MULT);
        return m < 1.0 ? 1.0 : m;
    }

    public static void setPriceMultiplier(EntityPlayer player, double mult) {
        data(player).setDouble(KEY_MULT, Math.max(1.0, mult));
    }

    public static void applyCreditPenalty(EntityPlayer player, double shortfall, double debt) {
        if (debt <= 0) return;
        double factor = 1.0 + PocketConfig.creditPenaltyFactor * (shortfall / debt);
        setPriceMultiplier(player, getPriceMultiplier(player) * factor);
    }

    // ===== Кредит =====
    public static double getCredit(EntityPlayer player) {
        return data(player).getDouble(KEY_CREDIT);
    }

    public static long getCreditDue(EntityPlayer player) {
        return data(player).getLong(KEY_CREDIT_DUE);
    }

    public static boolean hasCredit(EntityPlayer player) {
        return getCredit(player) > 0;
    }

    public static void takeCredit(EntityPlayer player, double amount) {
        double toRepay = amount * (1.0 + PocketConfig.creditInterest);
        data(player).setDouble(KEY_CREDIT, toRepay);
        long due = System.currentTimeMillis() + (long) PocketConfig.creditDurationSec * 1000L;
        data(player).setLong(KEY_CREDIT_DUE, due);
        add(player, amount);
    }

    public static void clearCredit(EntityPlayer player) {
        data(player).setDouble(KEY_CREDIT, 0);
        data(player).setLong(KEY_CREDIT_DUE, 0);
    }

    // ===== Бесплатный вход в карман после смерти внутри =====
    public static boolean hasFreeEntry(EntityPlayer player) {
        return data(player).getBoolean(KEY_FREE_ENTRY);
    }

    public static void setFreeEntry(EntityPlayer player, boolean v) {
        data(player).setBoolean(KEY_FREE_ENTRY, v);
    }
}
