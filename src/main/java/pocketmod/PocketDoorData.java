package pocketmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Persistent ownership and access settings for purchased door pairs. */
public class PocketDoorData extends WorldSavedData {
    public static final int MAX_PAIRS = 3;
    private static final String ID = "pocketmod_doors";
    private static PocketDoorData instance;
    private static WorldServer loadedWorld;

    /** dimension:packed-position -> pair id */
    private final Map<String, String> doorPairs = new LinkedHashMap<String, String>();
    /** pair id -> owner UUID */
    private final Map<String, String> pairOwners = new LinkedHashMap<String, String>();
    /** owner UUID -> trusted player names */
    private final Map<String, Set<String>> trustedPlayers = new LinkedHashMap<String, Set<String>>();
    /** owners whose keys are globally disabled */
    private final Set<String> lockedOwners = new LinkedHashSet<String>();
    /** Owners who have temporarily suspended all visitor access. */
    private final Set<String> pausedOwners = new LinkedHashSet<String>();
    /** Owners currently testing the temporary-guest permission tier in their own pocket. */
    private final Set<String> guestTestOwners = new LinkedHashSet<String>();
    /** Incrementing owner key epoch invalidates every key issued before the rotation. */
    private final Map<String, Integer> keyEpochs = new LinkedHashMap<String, Integer>();

    public PocketDoorData() {
        super(ID);
    }

    public static PocketDoorData get(WorldServer world) {
        WorldServer overworld = world.getMinecraftServer().getWorld(0);
        if (instance == null || loadedWorld != overworld) {
            MapStorage storage = overworld.getMapStorage();
            instance = (PocketDoorData) storage.getOrLoadData(PocketDoorData.class, ID);
            if (instance == null) {
                instance = new PocketDoorData();
                storage.setData(ID, instance);
            }
            loadedWorld = overworld;
        }
        return instance;
    }

    private static String doorKey(int dim, long pos) {
        return dim + ":" + pos;
    }

    public int pairCount(UUID owner) {
        int count = 0;
        String id = owner.toString();
        for (String value : pairOwners.values()) {
            if (id.equals(value)) count++;
        }
        return count;
    }

    public boolean canBuyPair(UUID owner) {
        return pairCount(owner) < MAX_PAIRS;
    }

    /** Creates and reserves one purchased pair slot. Returns null at the limit. */
    public String createPair(UUID owner) {
        if (!canBuyPair(owner)) return null;
        String pairId = UUID.randomUUID().toString();
        pairOwners.put(pairId, owner.toString());
        markDirty();
        return pairId;
    }

    public boolean ownsPair(UUID owner, String pairId) {
        return pairId != null && owner.toString().equals(pairOwners.get(pairId));
    }

    public List<String> pairIds(UUID owner) {
        List<String> result = new ArrayList<String>();
        String id = owner.toString();
        for (Map.Entry<String, String> entry : pairOwners.entrySet()) {
            if (id.equals(entry.getValue())) result.add(entry.getKey());
        }
        return result;
    }

    public int doorCount(String pairId) {
        int count = 0;
        for (String value : doorPairs.values()) {
            if (pairId.equals(value)) count++;
        }
        return count;
    }

    public boolean registerDoor(int dim, long pos, UUID owner, String pairId) {
        if (!ownsPair(owner, pairId)) return false;
        String key = doorKey(dim, pos);
        String current = doorPairs.get(key);
        if (pairId.equals(current)) return true;
        if (current != null || doorCount(pairId) >= 2) return false;
        doorPairs.put(key, pairId);
        markDirty();
        return true;
    }

    public UUID owner(int dim, long pos) {
        String pairId = doorPairs.get(doorKey(dim, pos));
        if (pairId == null) return null;
        String value = pairOwners.get(pairId);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String pairId(int dim, long pos) {
        return doorPairs.get(doorKey(dim, pos));
    }

    public void remove(int dim, long pos) {
        String pairId = doorPairs.remove(doorKey(dim, pos));
        if (pairId == null) return;
        // Keep the pair slot and its stable ID: replacement doors must remain
        // accessible with already issued keys bound to this pair.
        markDirty();
    }

    public int keyEpoch(UUID owner) {
        Integer epoch = keyEpochs.get(owner.toString());
        return epoch == null ? 0 : epoch.intValue();
    }

    public int rotateKeyEpoch(UUID owner) {
        String id = owner.toString();
        int next = keyEpoch(id) + 1;
        keyEpochs.put(id, next);
        markDirty();
        return next;
    }

    private int keyEpoch(String ownerId) {
        Integer epoch = keyEpochs.get(ownerId);
        return epoch == null ? 0 : epoch.intValue();
    }

    public boolean keysEnabled(UUID owner) {
        return !lockedOwners.contains(owner.toString());
    }

    public void setKeysEnabled(UUID owner, boolean enabled) {
        String id = owner.toString();
        boolean changed = enabled ? lockedOwners.remove(id) : lockedOwners.add(id);
        if (changed) markDirty();
    }

    public boolean accessPaused(UUID owner) {
        return pausedOwners.contains(owner.toString());
    }

    public void setAccessPaused(UUID owner, boolean paused) {
        String id = owner.toString();
        boolean changed = paused ? pausedOwners.add(id) : pausedOwners.remove(id);
        if (changed) markDirty();
    }

    public boolean guestTestEnabled(UUID owner) {
        return guestTestOwners.contains(owner.toString());
    }

    public void setGuestTestEnabled(UUID owner, boolean enabled) {
        String id = owner.toString();
        boolean changed = enabled ? guestTestOwners.add(id) : guestTestOwners.remove(id);
        if (changed) markDirty();
    }

    public boolean isTrusted(UUID owner, String playerName) {
        if (playerName == null) return false;
        Set<String> names = trustedPlayers.get(owner.toString());
        if (names == null) return false;
        for (String name : names) {
            if (name.equalsIgnoreCase(playerName)) return true;
        }
        return false;
    }

    public boolean addTrusted(UUID owner, String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) return false;
        String id = owner.toString();
        Set<String> names = trustedPlayers.get(id);
        if (names == null) {
            names = new LinkedHashSet<String>();
            trustedPlayers.put(id, names);
        }
        for (String existing : names) {
            if (existing.equalsIgnoreCase(playerName)) return false;
        }
        names.add(playerName);
        markDirty();
        return true;
    }

    public boolean removeTrusted(UUID owner, String playerName) {
        if (playerName == null) return false;
        Set<String> names = trustedPlayers.get(owner.toString());
        if (names == null) return false;
        String found = null;
        for (String existing : names) {
            if (existing.equalsIgnoreCase(playerName)) {
                found = existing;
                break;
            }
        }
        if (found == null) return false;
        names.remove(found);
        if (names.isEmpty()) trustedPlayers.remove(owner.toString());
        markDirty();
        return true;
    }

    public List<String> trustedNames(UUID owner) {
        Set<String> names = trustedPlayers.get(owner.toString());
        if (names == null) return Collections.emptyList();
        return new ArrayList<String>(names);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        doorPairs.clear();
        pairOwners.clear();
        trustedPlayers.clear();
        lockedOwners.clear();
        pausedOwners.clear();
        guestTestOwners.clear();
        keyEpochs.clear();

        NBTTagList pairs = nbt.getTagList("pairs", 10);
        for (int i = 0; i < pairs.tagCount(); i++) {
            NBTTagCompound entry = pairs.getCompoundTagAt(i);
            pairOwners.put(entry.getString("id"), entry.getString("owner"));
        }

        NBTTagList doors = nbt.getTagList("doorPairs", 10);
        for (int i = 0; i < doors.tagCount(); i++) {
            NBTTagCompound entry = doors.getCompoundTagAt(i);
            doorPairs.put(entry.getString("pos"), entry.getString("pair"));
        }

        // Migrate worlds saved by the earlier owner-per-door registry.
        NBTTagList oldDoors = nbt.getTagList("doors", 10);
        for (int i = 0; i < oldDoors.tagCount(); i++) {
            NBTTagCompound entry = oldDoors.getCompoundTagAt(i);
            String pos = entry.getString("pos");
            String owner = entry.getString("owner");
            String legacyId = "legacy:" + pos;
            if (!pairOwners.containsKey(legacyId)) pairOwners.put(legacyId, owner);
            doorPairs.put(pos, legacyId);
        }

        NBTTagList trusted = nbt.getTagList("trusted", 10);
        for (int i = 0; i < trusted.tagCount(); i++) {
            NBTTagCompound entry = trusted.getCompoundTagAt(i);
            String owner = entry.getString("owner");
            Set<String> names = trustedPlayers.get(owner);
            if (names == null) {
                names = new LinkedHashSet<String>();
                trustedPlayers.put(owner, names);
            }
            names.add(entry.getString("name"));
        }

        NBTTagList paused = nbt.getTagList("pausedAccess", 8);
        for (int i = 0; i < paused.tagCount(); i++) {
            pausedOwners.add(paused.getStringTagAt(i));
        }

        NBTTagList locked = nbt.getTagList("lockedKeys", 8);
        for (int i = 0; i < locked.tagCount(); i++) {
            lockedOwners.add(locked.getStringTagAt(i));
        }

        NBTTagList guestTests = nbt.getTagList("guestTest", 8);
        for (int i = 0; i < guestTests.tagCount(); i++) {
            guestTestOwners.add(guestTests.getStringTagAt(i));
        }

        NBTTagList epochs = nbt.getTagList("keyEpochs", 10);
        for (int i = 0; i < epochs.tagCount(); i++) {
            NBTTagCompound entry = epochs.getCompoundTagAt(i);
            String owner = entry.getString("owner");
            if (!owner.isEmpty()) keyEpochs.put(owner, entry.getInteger("epoch"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList pairs = new NBTTagList();
        for (Map.Entry<String, String> entry : pairOwners.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", entry.getKey());
            tag.setString("owner", entry.getValue());
            pairs.appendTag(tag);
        }
        nbt.setTag("pairs", pairs);

        NBTTagList doors = new NBTTagList();
        for (Map.Entry<String, String> entry : doorPairs.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("pos", entry.getKey());
            tag.setString("pair", entry.getValue());
            doors.appendTag(tag);
        }
        nbt.setTag("doorPairs", doors);

        NBTTagList trusted = new NBTTagList();
        for (Map.Entry<String, Set<String>> entry : trustedPlayers.entrySet()) {
            for (String name : entry.getValue()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("owner", entry.getKey());
                tag.setString("name", name);
                trusted.appendTag(tag);
            }
        }
        nbt.setTag("trusted", trusted);

        NBTTagList locked = new NBTTagList();
        for (String owner : lockedOwners) {
            locked.appendTag(new net.minecraft.nbt.NBTTagString(owner));
        }
        nbt.setTag("lockedKeys", locked);

        NBTTagList paused = new NBTTagList();
        for (String owner : pausedOwners) {
            paused.appendTag(new net.minecraft.nbt.NBTTagString(owner));
        }
        nbt.setTag("pausedAccess", paused);

        NBTTagList guestTests = new NBTTagList();
        for (String owner : guestTestOwners) {
            guestTests.appendTag(new net.minecraft.nbt.NBTTagString(owner));
        }
        nbt.setTag("guestTest", guestTests);

        NBTTagList epochs = new NBTTagList();
        for (Map.Entry<String, Integer> entry : keyEpochs.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("owner", entry.getKey());
            tag.setInteger("epoch", entry.getValue().intValue());
            epochs.appendTag(tag);
        }
        nbt.setTag("keyEpochs", epochs);

        return nbt;
    }
}
