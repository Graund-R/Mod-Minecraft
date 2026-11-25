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
        JmHook.fire();
    }

    // ===== Уровни улучшений =====
    public static int maxSlots(EntityPlayer p) {
        return slotsForLevel(AbilityStorage.getLevel(p, AbilityType.TOTEM));
    }

    static int slotsForLevel(int lvl) {
        if (lvl >= 3) return PocketConfig.totemSlots3;
        if (lvl == 2) return PocketConfig.totemSlots2;
        if (lvl == 1) return PocketConfig.totemSlots1;
        return 0;
    }

    public static double discount(EntityPlayer p) {
        return discountForLevel(AbilityStorage.getLevel(p, AbilityType.TOTEM));
    }

    static double discountForLevel(int lvl) {
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
        if (Spector.isActive(p)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.teleport_spector"), true);
            return;
        }
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
        TeleportEffects.depart(p,(WorldServer)p.world,from);

        Dash.onTeleport(p);
        if (p.dimension == t.dim) {
            p.connection.setPlayerLocation(pos[0], pos[1], pos[2], p.rotationYaw, p.rotationPitch);
        } else {
            p.changeDimension(t.dim, new PocketTeleporter(pos[0], pos[1], pos[2]));
        }

        Vec3d to = new Vec3d(pos[0], pos[1] + 1.0, pos[2]);
        if(p.dimension==t.dim)TeleportEffects.arrive(p,(WorldServer)p.world,to);
    }

    // ===== Временный тотем смерти (x10, до использования или следующей смерти) =====
    private static final String DEATH_KEY = "pocketmod_death_totem";

    public static void setDeathTotem(EntityPlayer p, int dim, double x, double y, double z) {
        if (ExileProgression.inMode(p)) return;
        NBTTagCompound c = new NBTTagCompound();
        c.setInteger("dim", dim);
        c.setDouble("x", x);
        c.setDouble("y", y);
        c.setDouble("z", z);
        PlayerData.root(p).setTag(DEATH_KEY, c);
    }

    // ----- выбор после возрождения: тотем создаётся только с согласия игрока -----
    private static final String PENDING_KEY = "pocketmod_death_pending";

    public static void setPendingDeathTotem(EntityPlayer p, int dim, double x, double y, double z) {
        if (ExileProgression.inMode(p)) return;
        NBTTagCompound c = new NBTTagCompound();
        c.setInteger("dim", dim);
        c.setDouble("x", x);
        c.setDouble("y", y);
        c.setDouble("z", z);
        PlayerData.root(p).setTag(PENDING_KEY, c);
    }

    public static boolean hasPendingDeathTotem(EntityPlayer p) {
        return !ExileProgression.inMode(p) && PlayerData.root(p).hasKey(PENDING_KEY, 10);
    }

    /** Примерная цена телепорта к будущему тотему смерти (x10). */
    public static double pendingTeleportCost(EntityPlayer p) {
        if (!hasPendingDeathTotem(p)) return 0.0;
        NBTTagCompound c = PlayerData.root(p).getCompoundTag(PENDING_KEY);
        Totem t = new Totem("", c.getInteger("dim"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
        return teleportCost(p, t) * 10.0;
    }

    /** Плата за создание тотема смерти (по умолчанию 0). */
    public static double deathTotemCreateFee(EntityPlayer p) {
        return PocketConfig.deathTotemCreateCost * Mana.getPriceMultiplier(p);
    }

    /** Показать игроку вопрос «создать тотем смерти?», если он ещё не отвечал. */
    public static void promptIfPending(EntityPlayerMP p) {
        if (!hasPendingDeathTotem(p)) return;
        // способность «Тотемы» не куплена: вопрос не показываем и ждать ответа нечего
        if (!AbilityStorage.isUnlocked(p, AbilityType.TOTEM)) {
            PlayerData.root(p).removeTag(PENDING_KEY);
            return;
        }
        NetworkHandler.sendPrompt(p, 1, pendingTeleportCost(p), deathTotemCreateFee(p));
    }

    /** Ответ игрока: создать тотем смерти или нет. Мана тратится только при согласии (и только если задана плата). */
    public static void resolvePendingDeathTotem(EntityPlayerMP p, boolean create) {
        if (!hasPendingDeathTotem(p)) return;
        NBTTagCompound c = PlayerData.root(p).getCompoundTag(PENDING_KEY).copy();
        if (create && !AbilityStorage.isUnlocked(p, AbilityType.TOTEM)) create = false;
        if (create) {
            double fee = deathTotemCreateFee(p);
            if (fee > 0.0 && !Mana.spend(p, fee)) {
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(fee)), true);
                PlayerData.root(p).removeTag(PENDING_KEY);
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.death_totem_declined"), true);
                return;
            }
            setDeathTotem(p, c.getInteger("dim"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.death_totem_created"), true);
        } else {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.death_totem_declined"), true);
        }
        PlayerData.root(p).removeTag(PENDING_KEY);
    }

    public static void clearDeathTotem(EntityPlayer p) {
        PlayerData.root(p).removeTag(DEATH_KEY);
    }

    public static boolean hasDeathTotem(EntityPlayer p) {
        return !ExileProgression.inMode(p) && PlayerData.root(p).hasKey(DEATH_KEY, 10);
    }

    public static Totem getDeathTotem(EntityPlayer p) {
        if (!hasDeathTotem(p)) return null;
        NBTTagCompound c = PlayerData.root(p).getCompoundTag(DEATH_KEY);
        return new Totem("§cСмерть", c.getInteger("dim"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
    }

    public static double deathTeleportCost(EntityPlayer p) {
        Totem t = getDeathTotem(p);
        if (t == null) return 0;
        return teleportCost(p, t) * 10.0;
    }

    public static void teleportDeath(EntityPlayerMP p) {
        Totem t = getDeathTotem(p);
        if (t == null) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_death_totem"), true);
            return;
        }
        double cost = deathTeleportCost(p);
        if (!Mana.spend(p, cost)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(cost)), true);
            return;
        }
        clearDeathTotem(p);
        // reuse teleport logic
        double[] pos = new double[] { t.x, t.y, t.z };
        Vec3d from = new Vec3d(p.posX, p.posY + 1.0, p.posZ);
        Abilities.clearMobTargets(p, 16.0);
        p.dismountRidingEntity();
        p.fallDistance = 0f;
        TeleportEffects.depart(p,(WorldServer)p.world,from);
        Dash.onTeleport(p);
        if (p.dimension == t.dim) {
            p.connection.setPlayerLocation(pos[0], pos[1], pos[2], p.rotationYaw, p.rotationPitch);
        } else {
            p.changeDimension(t.dim, new PocketTeleporter(pos[0], pos[1], pos[2]));
        }
        Vec3d to = new Vec3d(pos[0], pos[1] + 1.0, pos[2]);
        if(p.dimension==t.dim)TeleportEffects.arrive(p,(WorldServer)p.world,to);
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.death_totem_used"), true);
    }
}
