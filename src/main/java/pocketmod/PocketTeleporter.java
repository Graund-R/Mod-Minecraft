package pocketmod;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;

public class PocketTeleporter implements ITeleporter {

    private final double x, y, z;

    public PocketTeleporter(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void placeEntity(World world, Entity entity, float yaw) {
        entity.setLocationAndAngles(x, y, z, yaw, entity.rotationPitch);
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
        entity.fallDistance = 0;
    }

    /**
     * Единственная проверка права входа в карманное измерение.
     * Покупка дверей/ключей сама по себе не открывает измерение:
     * сначала игрок должен приобрести AbilityType.POCKET.
     * Эту проверку должны использовать и будущие двери.
     */
    public static boolean canEnterPocket(EntityPlayerMP player) {
        if (AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
            return true;
        }
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
        return false;
    }

    /** Вход гостя в измерение владельца сети через проверенную дверь. */
    public static boolean enterNetwork(EntityPlayerMP player, java.util.UUID owner) {
        return enterNetwork(player, owner, null);
    }

    /** Вход через внешнюю дверь пары: гость появляется у внутренней двери этой же пары. */
    public static boolean enterNetwork(EntityPlayerMP player, java.util.UUID owner, String pairId) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        // The door owner already had to unlock the pocket ability before placing this door.
        // Create their persistent dimension on first door entry if they have not used K yet.
        int targetId = PocketDimensionManager.getOrCreateDimension(server, owner);
        WorldServer target = server.getWorld(targetId);
        if (target == null) {
            net.minecraftforge.common.DimensionManager.initDimension(targetId);
            target = server.getWorld(targetId);
        }
        if (target == null) return false;
        Pocket.buildIfNeeded(target);
        PocketData.get(target);
        double destX = 2.5D, destY = 64.0D, destZ = 2.5D;
        if (pairId != null) {
            PocketDoorData doors = PocketDoorData.get(server.getWorld(0));
            String inner = doors.roleDoor(pairId, true);
            if (inner == null || PocketDoorData.keyDim(inner) != targetId) {
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_incomplete"), true);
                return false;
            }
            BlockPos ip = BlockPos.fromLong(PocketDoorData.keyPos(inner));
            double[] spot = findStandable(target, ip.getX() + 0.5D, ip.getY(), ip.getZ() + 0.5D);
            if (spot != null) {
                destX = spot[0];
                destY = spot[1];
                destZ = spot[2];
            }
        }
        // Door guests always enter for free. The owner's free-entry-on-death
        // flag belongs only to the owner's own pocket and is never consumed here.
        MorphIntegration.demorph(player);
        NBTTagCompound data = PlayerData.root(player);
        data.setInteger("pocket_return_dim", player.dimension);
        data.setDouble("pocket_return_x", player.posX);
        data.setDouble("pocket_return_y", player.posY);
        data.setDouble("pocket_return_z", player.posZ);
        Spector.forceReturnIfActive(player);
        Flight.stop(player);
        Dash.onTeleport(player);
        player.changeDimension(targetId, new PocketTeleporter(destX, destY, destZ));
        return true;
    }

    /** Выход через внутреннюю дверь: игрок оказывается у внешней двери той же пары. */
    public static boolean exitThroughDoor(EntityPlayerMP player, String pairId) {
        MinecraftServer server = player.getServer();
        if (server == null || pairId == null) return false;
        String outer = PocketDoorData.get(server.getWorld(0)).roleDoor(pairId, false);
        if (outer == null) return false;
        int dim = PocketDoorData.keyDim(outer);
        WorldServer target = server.getWorld(dim);
        if (target == null) {
            net.minecraftforge.common.DimensionManager.initDimension(dim);
            target = server.getWorld(dim);
        }
        if (target == null) return false;
        BlockPos op = BlockPos.fromLong(PocketDoorData.keyPos(outer));
        double[] spot = findStandable(target, op.getX() + 0.5D, op.getY(), op.getZ() + 0.5D);
        if (spot == null) spot = new double[] { op.getX() + 0.5D, op.getY(), op.getZ() + 0.5D };

        NBTTagCompound data = PlayerData.root(player);
        PocketAccess.clear(player);
        data.setDouble("pocket_inner_x", player.posX);
        data.setDouble("pocket_inner_y", player.posY);
        data.setDouble("pocket_inner_z", player.posZ);
        Dash.onTeleport(player);
        player.changeDimension(dim, new PocketTeleporter(spot[0], spot[1], spot[2]));
        Abilities.clearMobTargets(player, PocketConfig.mobResetRadius);
        return true;
    }

    public static void toggle(EntityPlayerMP player) {
        if (!PocketDimensionManager.isPocketDimension(player.dimension) && !canEnterPocket(player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (PocketDimensionManager.isPocketDimension(player.dimension)) {
            leave(player, server);
        } else {
            enter(player, server);
        }
    }

    /** Цена входа = база × (1 + шаг × кольца) × (скидка уровня) × множитель цен. */
    static double baseEntryPrice(int rings, int level) {
        double base = PocketConfig.entryCost * (1.0 + PocketConfig.entryRingStep * rings);
        return level >= 2 ? base * (1.0 - PocketConfig.pocketDiscountL2) : base;
    }

    private static void enter(EntityPlayerMP player, MinecraftServer server) {
        NBTTagCompound data = PlayerData.root(player);

        int pocketDimension = PocketDimensionManager.getOrCreateDimension(server, player.getUniqueID());
        WorldServer pocket = server.getWorld(pocketDimension);
        if (pocket == null) return;
        Pocket.buildIfNeeded(pocket);
        PocketData pd = PocketData.get(pocket);

        // Входим туда, откуда вышли в прошлый раз (если там ещё безопасно), иначе в центр
        double ix = 2.5, iy = 64, iz = 2.5;
        if (data.hasKey("pocket_inner_x")) {
            double sx = data.getDouble("pocket_inner_x");
            double sy = data.getDouble("pocket_inner_y");
            double sz = data.getDouble("pocket_inner_z");
            BlockPos feet = new BlockPos(MathHelper.floor(sx), MathHelper.floor(sy), MathHelper.floor(sz));
            if (pd.interior.contains(feet) && pd.interior.contains(feet.up())) {
                ix = sx;
                iy = sy;
                iz = sz;
            }
        }

        boolean freeEntry = Mana.hasFreeEntry(player);
        int pocketLevel = AbilityStorage.getLevel(player, AbilityType.POCKET);
        double price = freeEntry ? 0.0 : baseEntryPrice(pd.maxRing, pocketLevel) * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, price)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "pocketmod.msg.no_mana", (int) Math.ceil(price)), true);
            return;
        }

        MorphIntegration.demorph(player);
        Spector.forceReturnIfActive(player);
        Flight.stop(player);
        int returnDimension = player.dimension;
        double returnX = player.posX;
        double returnY = player.posY;
        double returnZ = player.posZ;
        Abilities.clearMobTargets(player, PocketConfig.mobResetRadius);
        Dash.onTeleport(player);

        try {
            player.changeDimension(pocketDimension, new PocketTeleporter(ix, iy, iz));
        } finally {
            if (player.dimension == pocketDimension) {
                NBTTagCompound destinationData = PlayerData.root(player);
                destinationData.setInteger("pocket_return_dim", returnDimension);
                destinationData.setDouble("pocket_return_x", returnX);
                destinationData.setDouble("pocket_return_y", returnY);
                destinationData.setDouble("pocket_return_z", returnZ);
                if (freeEntry) Mana.setFreeEntry(player, false);
            } else if (price > 0.0) {
                Mana.add(player, price);
            }
        }
    }

    private static void leave(EntityPlayerMP player, MinecraftServer server) {
        NBTTagCompound data = PlayerData.root(player);
        PocketAccess.clear(player);

        // Запоминаем, где стоял внутри
        data.setDouble("pocket_inner_x", player.posX);
        data.setDouble("pocket_inner_y", player.posY);
        data.setDouble("pocket_inner_z", player.posZ);

        int dim;
        double rx, ry, rz;
        if (data.hasKey("pocket_return_dim")) {
            dim = data.getInteger("pocket_return_dim");
            rx = data.getDouble("pocket_return_x");
            ry = data.getDouble("pocket_return_y");
            rz = data.getDouble("pocket_return_z");
        } else {
            WorldServer overworld = server.getWorld(0);
            BlockPos top = overworld.getTopSolidOrLiquidBlock(overworld.getSpawnPoint());
            dim = 0;
            rx = top.getX() + 0.5;
            ry = top.getY();
            rz = top.getZ() + 0.5;
        }

        WorldServer target = server.getWorld(dim);
        double[] safe = findSafeExit(target, rx, ry, rz);

        Dash.onTeleport(player);
        player.changeDimension(dim, new PocketTeleporter(safe[0], safe[1], safe[2]));

        // Мобы вокруг точки выхода теряют цель
        Abilities.clearMobTargets(player, PocketConfig.mobResetRadius);
    }

    /** Ищет ближайшую безопасную точку возле места, откуда игрок ушёл в карман. */
    private static double[] findSafeExit(WorldServer w, double x, double y, double z) {
        double[] r = findStandable(w, x, y, z);
        return r != null ? r : new double[] { x, y, z };
    }

    /** Ближайшая точка, где можно стоять (радиус до 6 блоков), или null. */
    public static double[] findStandable(WorldServer w, double x, double y, double z) {
        BlockPos origin = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int k = 0; k <= 8; k++) {
                        int dy = k == 0 ? 0 : ((k % 2 == 1) ? (k + 1) / 2 : -(k / 2));
                        BlockPos feet = origin.add(dx, dy, dz);
                        if (standable(w, feet)) {
                            return new double[] { feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5 };
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean standable(World w, BlockPos feet) {
        if (feet.getY() < 1 || feet.getY() > 250) return false;
        if (w.provider.getDimension() == -1 && feet.getY() >= 126) return false;

        IBlockState f = w.getBlockState(feet);
        IBlockState h = w.getBlockState(feet.up());
        IBlockState g = w.getBlockState(feet.down());

        if (f.getMaterial().blocksMovement() || f.getMaterial().isLiquid() || f.getMaterial() == Material.FIRE) return false;
        if (h.getMaterial().blocksMovement() || h.getMaterial().isLiquid() || h.getMaterial() == Material.FIRE) return false;
        if (!g.getMaterial().isSolid()) return false;
        if (g.getBlock() == Blocks.CACTUS || g.getBlock() == Blocks.MAGMA) return false;
        return true;
    }
}
