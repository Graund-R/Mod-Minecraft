package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Единая таблица состояния, которое сервер передаёт клиенту вместе с данными игрока.
 * Булевы флаги берутся из реестра способностей (ActiveAbility.syncedFlags): новая способность синхронизируется сама.
 * Прочие значения (числа и т.п.) перечислены ниже, в одном месте.
 */
public final class SyncedState {

    private static final String PREFIX = "pm_sync_";

    /** Дробные значения состояния. */
    private static final String[] FLOATS = { "pocketmod_slow_factor", Dash.DX, Dash.DZ };

    /** Целые значения состояния (номер рывка нужен клиенту, чтобы начать рывок один раз). */
    private static final String[] INTS = { Dash.ID };

    private SyncedState() {}

    /** Сервер: добавить состояние в снимок данных игрока. */
    public static void write(EntityPlayer player, NBTTagCompound snapshot) {
        NBTTagCompound e = player.getEntityData();
        for (String key : ActiveAbilities.syncedFlags()) {
            snapshot.setBoolean(PREFIX + key, e.getBoolean(key));
        }
        for (String key : FLOATS) {
            snapshot.setFloat(PREFIX + key, e.getFloat(key));
        }
        for (String key : INTS) {
            snapshot.setInteger(PREFIX + key, e.getInteger(key));
        }
    }

    /** Клиент: разложить состояние из снимка по данным сущности и убрать служебные ключи из данных мода. */
    public static void read(EntityPlayer player, NBTTagCompound data) {
        NBTTagCompound e = player.getEntityData();
        for (String key : ActiveAbilities.syncedFlags()) {
            if (data.hasKey(PREFIX + key)) e.setBoolean(key, data.getBoolean(PREFIX + key));
            data.removeTag(PREFIX + key);
        }
        for (String key : FLOATS) {
            if (data.hasKey(PREFIX + key)) e.setFloat(key, data.getFloat(PREFIX + key));
            data.removeTag(PREFIX + key);
        }
        for (String key : INTS) {
            if (data.hasKey(PREFIX + key)) e.setInteger(key, data.getInteger(PREFIX + key));
            data.removeTag(PREFIX + key);
        }
    }
}
