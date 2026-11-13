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

    public static void toggle(EntityPlayerMP player) {
        if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (player.dimension == PocketMod.DIM_ID) {
            leave(player, server);
        } else {
            enter(player, server);
        }
    }

    /** Цена входа = база × (1 + шаг × кольца) × (скидка уровня) × множитель цен. */
    private static double entryPrice(EntityPlayerMP player, PocketData d) {
        double base = PocketConfig.entryCost * (1.0 + PocketConfig.entryRingStep * d.maxRing);
        if (AbilityStorage.getLevel(player, AbilityType.POCKET) >= 2) {
            base *= (1.0 - PocketConfig.pocketDiscountL2);
        }
        return base * Mana.getPriceMultiplier(player);
    }

    private static void enter(EntityPlayerMP player, MinecraftServer server) {
        NBTTagCompound data = PlayerData.root(player);

        WorldServer pocket = server.getWorld(PocketMod.DIM_ID);
        Pocket.buildIfNeeded(pocket);
        PocketData pd = PocketData.get(pocket);

        boolean free = Mana.hasFreeEntry(player);
        if (!free) {
            double price = entryPrice(player, pd);
            if (!Mana.spend(player, price)) {
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                        (int) Math.ceil(price)), true);
                return;
            }
        }
        Mana.setFreeEntry(player, false);

        // Мобы вокруг точки ухода теряют цель
        Abilities.clearMobTargets(player, PocketConfig.mobResetRadius);

        data.setInteger("pocket_return_dim", player.dimension);
        data.setDouble("pocket_return_x", player.posX);
        data.setDouble("pocket_return_y", player.posY);
        data.setDouble("pocket_return_z", player.posZ);

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

        player.changeDimension(PocketMod.DIM_ID, new PocketTeleporter(ix, iy, iz));
    }

    private static void leave(EntityPlayerMP player, MinecraftServer server) {
        NBTTagCompound data = PlayerData.root(player);

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

    private static boolean standable(World w, BlockPos feet) {
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
