package pocketmod;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockDropper;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Server-side permission level for a visitor in another player's pocket dimension. */
public final class PocketAccess {
    public static final int TEMPORARY = 0;
    public static final int BUILDER = 1;
    public static final int TRUSTED = 2;

    private static final String DIM_KEY = "pocket_access_dimension";
    private static final String OWNER_KEY = "pocket_access_owner";
    private static final String LEVEL_KEY = "pocket_access_level";

    private PocketAccess() {}

    public static void grant(EntityPlayer player, UUID owner, int level) {
        net.minecraft.nbt.NBTTagCompound data = PlayerData.root(player);
        data.setInteger(DIM_KEY, player.dimension);
        data.setString(OWNER_KEY, owner.toString());
        data.setInteger(LEVEL_KEY, level);
    }

    public static void clear(EntityPlayer player) {
        net.minecraft.nbt.NBTTagCompound data = PlayerData.root(player);
        data.removeTag(DIM_KEY);
        data.removeTag(OWNER_KEY);
        data.removeTag(LEVEL_KEY);
    }

    public static int level(EntityPlayer player, World world) {
        if (!PocketDimensionManager.isPocketDimension(world.provider.getDimension())) return TRUSTED;
        UUID owner = PocketDimensionManager.getOwnerForDimension(
                world.getMinecraftServer(), world.provider.getDimension());
        if (owner != null && owner.equals(player.getUniqueID())) return TRUSTED;
        net.minecraft.nbt.NBTTagCompound data = PlayerData.root(player);
        if (data.getInteger(DIM_KEY) != world.provider.getDimension()
                || owner == null || !owner.toString().equals(data.getString(OWNER_KEY))) {
            return TEMPORARY;
        }
        return Math.max(TEMPORARY, Math.min(TRUSTED, data.getInteger(LEVEL_KEY)));
    }

    public static boolean canBuild(EntityPlayer player, World world) {
        return level(player, world) >= BUILDER;
    }

    public static boolean canUseTemporary(EntityPlayer player, World world, Block block) {
        if (level(player, world) > TEMPORARY) return true;
        return block instanceof BlockChest
                || block instanceof BlockEnderChest
                || block instanceof BlockFurnace
                || block instanceof BlockWorkbench
                || block instanceof BlockBrewingStand
                || block instanceof BlockDispenser
                || block instanceof BlockDropper
                || block instanceof BlockHopper
                || block instanceof BlockAnvil
                || block instanceof BlockButton
                || block instanceof BlockLever;
    }
}
