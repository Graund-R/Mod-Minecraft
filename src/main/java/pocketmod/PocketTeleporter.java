package pocketmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
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
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (player.dimension == PocketMod.DIM_ID) {
            leave(player, server);
        } else {
            enter(player, server);
        }
    }

    private static void enter(EntityPlayerMP player, MinecraftServer server) {
        NBTTagCompound data = player.getEntityData();

        boolean free = Mana.hasFreeEntry(player);
        if (!free && !Mana.spend(player, PocketConfig.entryCost)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", PocketConfig.entryCost), true);
            return;
        }
        Mana.setFreeEntry(player, false);

        data.setInteger("pocket_return_dim", player.dimension);
        data.setDouble("pocket_return_x", player.posX);
        data.setDouble("pocket_return_y", player.posY);
        data.setDouble("pocket_return_z", player.posZ);

        WorldServer pocket = server.getWorld(PocketMod.DIM_ID);
        Pocket.buildIfNeeded(pocket);

        player.changeDimension(PocketMod.DIM_ID, new PocketTeleporter(2.5, 64, 2.5));
    }

    private static void leave(EntityPlayerMP player, MinecraftServer server) {
        NBTTagCompound data = player.getEntityData();
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

        player.changeDimension(dim, new PocketTeleporter(rx, ry, rz));
    }
}
