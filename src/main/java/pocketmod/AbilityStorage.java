package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;

public class AbilityStorage {

    private static final String ROOT = "pocketmod_abilities";

    private static NBTTagCompound root(EntityPlayer player) {
        NBTTagCompound data = PlayerData.root(player);
        if (!data.hasKey(ROOT, 10)) {
            data.setTag(ROOT, new NBTTagCompound());
        }
        return data.getCompoundTag(ROOT);
    }

    public static int getLevel(EntityPlayer player, AbilityType type) {
        return root(player).getInteger(type.id);
    }

    public static void setLevel(EntityPlayer player, AbilityType type, int level) {
        int v = Math.max(0, Math.min(type.maxLevel, level));
        root(player).setInteger(type.id, v);
    }

    public static boolean isUnlocked(EntityPlayer player, AbilityType type) {
        return getLevel(player, type) > 0;
    }

    /** Учитывает уровни, предоставленные другой способностью, при выводе цены и покупке. */
    static int getUpgradeLevel(EntityPlayer player, AbilityType type) {
        int purchasedLevel = getLevel(player, type);
        if (type == AbilityType.ORE_SCANNER && getLevel(player, AbilityType.THERMAL) >= 3) {
            return Math.max(2, purchasedLevel);
        }
        return purchasedLevel;
    }

    public static boolean tryUpgrade(EntityPlayer player, AbilityType type) {
        if (!ExileProgression.require(player, type.id)) return false;
        int cur = getUpgradeLevel(player, type);
        int cost = type.costForNextLevel(cur);
        if (cost < 0) {
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).sendStatusMessage(
                        new TextComponentTranslation("pocketmod.gui.max"), true);
            }
            return false;
        }
        double real = Mana.shopPrice(player, cost);
        if (!Mana.spend(player, real)) {
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).sendStatusMessage(
                        new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(real)), true);
            }
            return false;
        }
        Mana.consumeDiscount(player);
        setLevel(player, type, cur + 1);
        ExileMode.progress(player, "upgrade_" + type.id + "_" + (cur + 1), 1);
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).sendStatusMessage(
                    new TextComponentTranslation("pocketmod.msg.upgraded",
                            new TextComponentTranslation(type.titleKey()), cur + 1, type.maxLevel), true);
        }
        return true;
    }
}
