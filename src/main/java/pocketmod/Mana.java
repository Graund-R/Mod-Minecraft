package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentTranslation;

public class Mana {

    private static final String KEY_MANA = "pocketmod_mana";
    private static final String KEY_MULT = "pocketmod_price_mult";
    private static final String KEY_CREDIT = "pocketmod_credit";
    private static final String KEY_CREDIT_LEFT = "pocketmod_credit_left_ms";
    private static final String KEY_WARN5 = "pocketmod_credit_warn5";
    private static final String KEY_WARN1 = "pocketmod_credit_warn1";
    private static final String KEY_FREE_ENTRY = "pocketmod_free_entry";

    private static NBTTagCompound data(EntityPlayer player) {
        return PlayerData.root(player);
    }

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

    public static boolean spendScaled(EntityPlayer player, double baseAmount) {
        return spend(player, baseAmount * getPriceMultiplier(player));
    }

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

    public static double getCredit(EntityPlayer player) {
        return data(player).getDouble(KEY_CREDIT);
    }

    public static long getCreditLeftMs(EntityPlayer player) {
        return data(player).getLong(KEY_CREDIT_LEFT);
    }

    public static boolean hasCredit(EntityPlayer player) {
        return getCredit(player) > 0;
    }

    public static double takeCredit(EntityPlayer player, double amount) {
        if (hasCredit(player)) return 0;
        amount = Math.max(PocketConfig.creditMin, Math.min(PocketConfig.creditMax, amount));
        double toRepay = amount * (1.0 + PocketConfig.creditInterest);
        NBTTagCompound d = data(player);
        d.setDouble(KEY_CREDIT, toRepay);
        d.setLong(KEY_CREDIT_LEFT, (long) PocketConfig.creditDurationSec * 1000L);
        d.setBoolean(KEY_WARN5, false);
        d.setBoolean(KEY_WARN1, false);
        add(player, amount);
        return toRepay;
    }

    public static void clearCredit(EntityPlayer player) {
        NBTTagCompound d = data(player);
        d.setDouble(KEY_CREDIT, 0);
        d.setLong(KEY_CREDIT_LEFT, 0);
        d.setBoolean(KEY_WARN5, false);
        d.setBoolean(KEY_WARN1, false);
    }

    public static void expireCreditNow(EntityPlayer player) {
        data(player).setLong(KEY_CREDIT_LEFT, 1L);
    }

    public static void tickCredit(EntityPlayerMP player, long deltaMs) {
        if (!hasCredit(player)) return;
        NBTTagCompound d = data(player);
        long left = d.getLong(KEY_CREDIT_LEFT) - deltaMs;
        d.setLong(KEY_CREDIT_LEFT, left);

        if (left <= 0) {
            settleCredit(player);
            return;
        }
        if (PocketConfig.creditWarnings) {
            if (left <= 60000L) {
                if (!d.getBoolean(KEY_WARN1)) {
                    d.setBoolean(KEY_WARN1, true);
                    d.setBoolean(KEY_WARN5, true);
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_warn_1",
                            (int) Math.ceil(getCredit(player))), true);
                }
            } else if (left <= 300000L) {
                if (!d.getBoolean(KEY_WARN5)) {
                    d.setBoolean(KEY_WARN5, true);
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_warn_5",
                            (int) Math.ceil(getCredit(player))), true);
                }
            }
        }
    }

    private static void settleCredit(EntityPlayerMP player) {
        double debt = getCredit(player);
        double wallet = get(player);
        clearCredit(player);

        if (wallet >= debt) {
            set(player, wallet - debt);
            player.sendMessage(new TextComponentTranslation("pocketmod.msg.credit_paid", (int) Math.ceil(debt)));
            return;
        }

        double shortfall = debt - wallet;
        set(player, 0);
        applyCreditPenalty(player, shortfall, debt);
        player.sendMessage(new TextComponentTranslation("pocketmod.msg.credit_default",
                String.format("%.2f", getPriceMultiplier(player))));
        killAbsolute(player);
    }

    private static void killAbsolute(EntityPlayerMP player) {
        player.removePotionEffect(MobEffects.RESISTANCE);
        player.setAbsorptionAmount(0.0F);
        player.hurtResistantTime = 0;
        player.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
        if (player.getHealth() > 0.0F && !player.isDead) {
            player.setHealth(0.0F);
            player.onDeath(DamageSource.OUT_OF_WORLD);
        }
    }

    public static boolean hasFreeEntry(EntityPlayer player) {
        return data(player).getBoolean(KEY_FREE_ENTRY);
    }

    public static void setFreeEntry(EntityPlayer player, boolean v) {
        data(player).setBoolean(KEY_FREE_ENTRY, v);
    }
}
