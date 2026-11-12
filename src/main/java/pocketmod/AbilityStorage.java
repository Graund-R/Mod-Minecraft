package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

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

    public static boolean tryUpgrade(EntityPlayer player, AbilityType type) {
        int cur = getLevel(player, type);
        int cost = type.costForNextLevel(cur);
        if (cost < 0) return false;
        double real = cost * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, real)) return false;
        setLevel(player, type, cur + 1);
        return true;
    }
}
