package pocketmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;

/**
 * Тотемы-чекпоинты: название + измерение + координаты, физического блока нет.
 * Создание, удаление и телепорт платные; порядок менять бесплатно.
 */
public class Totems {

    private static final String KEY = "pocketmod_totems";

    public static class Totem {
        public final String name;
        public final int dim;
        public final double x, y, z;

        public Totem(String name, int dim, double x, double y, double z) {
            this.name = name;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // ===== Хранение =====
    public static List<Totem> get(EntityPlayer player) {
        NBTTagList l = PlayerData.root(player).getTagList(KEY, 10);
        List<Totem> out = new ArrayList<Totem>();
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            out.add(new Totem(c.getString("name"), c.getInteger("dim"),
                    c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
        return out;
    }

    private static void save(EntityPlayer player, List<Totem> list) {
        NBTTagList l = new NBTTagList();
        for (Totem t : list) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("name", t.name);
            c.setInteger("dim", t.dim);
            c.setDouble("x", t.x);
            c.setDouble("y", t.y);
            c.setDouble("z", t.z);
            l.appendTag(c);
        }
        PlayerData.root(player).setTag(KEY, l);
    }

    // ===== Уровни улучшений =====
    public static int maxSlots(EntityPlayer p) {
        int lvl = AbilityStorage.getLevel(p, AbilityType.TOTEM);
        if (lvl >= 3) return PocketConfig.totemSlots3;
        if (lvl == 2) return PocketConfig.totemSlots2;
        if (lvl == 1) return PocketConfig.totemSlots1;
        return 0;
    }

    public static double discount(EntityPlayer p) {
        int lvl = AbilityStorage.getLevel(p, AbilityType.TOTEM);
        if (lvl >= 3) return PocketConfig.totemDiscount3;
        if (lvl == 2) return PocketConfig.totemDiscount2;
        return 0.0;
    }

    // ===== Цены (с множителем цен) =====
    public static double createCost(EntityPlayer p) {
        return PocketConfig.totemCreateCost * Mana.getPriceMultiplier(p);
    }

    public static double deleteCost(EntityPlayer p) {
        return PocketConfig.totemDeleteCost * Mana.getPriceMultiplier(p);
    }

    public static double teleportCost(EntityPlayer p, Totem t) {
        double base = PocketConfig.totemTeleportBase;
        if (p.dimension != t.dim) {
            base += PocketConfig.totemDimensionCost;
        } else {
            double dx = t.x - p.posX;
            double dy = t.y - p.posY;
            double dz = t.z - p.posZ;
            base += Math.sqrt(dx * dx + dy * dy + dz * dz) * PocketConfig.totemTeleportPerBlock;
        }
        return base * (1.0 - discount(p)) * Mana.getPriceMultiplier(p);
    }

    // ===== Действия (вызываются на сервере) =====
    private static boolean allowed(EntityPlayerMP p) {
        if (!AbilityStorage.isUnlocked(p, AbilityType.TOTEM)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return false;
        }
        return true;
    }

    public static void create(EntityPlayerMP p, String rawName) {
        if (!allowed(p)) return;
        if (p.dimension == PocketMod.DIM_ID) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.totem_in_pocket"), true);
            return;
        }
        List<Totem> list = get(p);
        if (list.size() >= maxSlots(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.totem_full"), true);
            return;
        }
        double cost = createCost(p);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() > 24) name = name.substring(0, 24);
        if (name.isEmpty()) name = "Totem " + (list.size() + 1);

        list.add(new Totem(name, p.dimension, p.posX, p.posY, p.posZ));
        save(p, list);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.totem_created", name), true);
    }

    public static void delete(EntityPlayerMP p, int index) {
        if (!allowed(p)) return;
        List<Totem> list = get(p);
        if (index < 0 || index >= list.size()) return;
        double cost = deleteCost(p);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        Totem removed = list.remove(index);
        save(p, list);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.totem_deleted", removed.name), true);
    }

    /** Порядок меняется бесплатно. */
    public static void move(EntityPlayerMP p, int index, int delta) {
        if (!allowed(p)) return;
        List<Totem> list = get(p);
        int to = index + delta;
        if (index < 0 || index >= list.size() || to < 0 || to >= list.size()) return;
        Totem t = list.remove(index);
        list.add(to, t);
        save(p, list);
    }

    public static void teleportLast(EntityPlayerMP p) {
        if (!allowed(p)) return;
        List<Totem> list = get(p);
        if (list.isEmpty()) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.totem_none"), true);
            return;
        }
        teleport(p, list.size() - 1);
    }

    public static void teleport(EntityPlayerMP p, int index) {
        if (!allowed(p)) return;
        if (p.dimension == PocketMod.DIM_ID) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.totem_in_pocket"), true);
            return;
        }
        List<Totem> list = get(p);
        if (index < 0 || index >= list.size()) return;
        Totem t = list.get(index);

        MinecraftServer server = p.getServer();
        if (server == null) return;
        WorldServer target = server.getWorld(t.dim);
        if (target == null) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.teleport_unsafe"), true);
            return;
        }

        double[] pos = PocketTeleporter.findStandable(target, t.x, t.y, t.z);
        if (pos == null) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.teleport_unsafe"), true);
            return;
        }

        double cost = teleportCost(p, t);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }

        Vec3d from = new Vec3d(p.posX, p.posY + 1.0, p.posZ);
        Abilities.clearMobTargets(p, 16.0);
        p.dismountRidingEntity();
        p.fallDistance = 0f;
        Abilities.teleportFx((WorldServer) p.world, from, from);

        if (p.dimension == t.dim) {
            p.connection.setPlayerLocation(pos[0], pos[1], pos[2], p.rotationYaw, p.rotationPitch);
        } else {
            p.changeDimension(t.dim, new PocketTeleporter(pos[0], pos[1], pos[2]));
        }

        Vec3d to = new Vec3d(pos[0], pos[1] + 1.0, pos[2]);
        Abilities.teleportFx((WorldServer) p.world, to, to);
    }
}
