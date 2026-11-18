package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Все данные мода лежат в ветке "PlayerPersisted" — Forge переносит её через смерть.
 */
public class PlayerData {

    public static final String KEY = "PlayerPersisted";

    public static void replace(EntityPlayer player, NBTTagCompound snapshot) {
        NBTTagCompound data = snapshot == null ? new NBTTagCompound() : snapshot.copy();
        player.getEntityData().setTag(KEY, data);
        SyncedState.read(player, data);
    }

    public static NBTTagCompound root(EntityPlayer player) {
        NBTTagCompound e = player.getEntityData();
        if (!e.hasKey(KEY, 10)) {
            e.setTag(KEY, new NBTTagCompound());
        }
        return e.getCompoundTag(KEY);
    }
}
