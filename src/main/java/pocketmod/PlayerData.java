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
        syncFlag(player, data, "pm_sync_slow", "pocketmod_slow");
        syncFlag(player, data, "pm_sync_invis", "pocketmod_invis");
        syncFlag(player, data, "pm_sync_thermal", "pm_thermal");
        syncFlag(player, data, "pm_sync_spector", "pm_spec");
        syncFlag(player, data, "pm_sync_softfall", "pm_softfall");
        syncFlag(player, data, "pm_sync_flight", "pm_flight");
        syncFlag(player, data, "pm_sync_shield", "pm_shield");
        syncFlag(player, data, "pm_sync_ore_scan", "pm_ore_scan");
        if (data.hasKey("pm_sync_slow_factor")) {
            player.getEntityData().setFloat("pocketmod_slow_factor", data.getFloat("pm_sync_slow_factor"));
        }
        data.removeTag("pm_sync_slow");
        data.removeTag("pm_sync_invis");
        data.removeTag("pm_sync_thermal");
        data.removeTag("pm_sync_spector");
        data.removeTag("pm_sync_softfall");
        data.removeTag("pm_sync_flight");
        data.removeTag("pm_sync_shield");
        data.removeTag("pm_sync_ore_scan");
        data.removeTag("pm_sync_slow_factor");
    }

    private static void syncFlag(EntityPlayer player, NBTTagCompound data, String source, String target) {
        if (data.hasKey(source)) player.getEntityData().setBoolean(target, data.getBoolean(source));
    }

    public static NBTTagCompound root(EntityPlayer player) {
        NBTTagCompound e = player.getEntityData();
        if (!e.hasKey(KEY, 10)) {
            e.setTag(KEY, new NBTTagCompound());
        }
        return e.getCompoundTag(KEY);
    }
}
