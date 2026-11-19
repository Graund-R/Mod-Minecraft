package pocketmod;

import java.util.UUID;

import net.minecraft.block.BlockDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class PocketDoorEvents {
    private PocketDoorEvents() {}

    private static BlockPos lowerPos(World world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() instanceof BlockDoor
                && world.getBlockState(pos).getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER) {
            return pos.down();
        }
        return pos;
    }

    @SubscribeEvent
    public static void onDoorPlaced(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !(event.getPlayer() instanceof EntityPlayerMP)) return;
        ItemStack held = event.getItemInHand();
        if (held.isEmpty() || held.getItem() != PocketDoorItems.DOOR) return;

        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
            event.setCanceled(true);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }

        PocketDoorData data = PocketDoorData.get((WorldServer) world);
        String pairId = null;
        String itemOwner = null;
        if (held.hasTagCompound()) {
            pairId = held.getTagCompound().getString("pocket_pair_id");
            itemOwner = held.getTagCompound().getString("pocket_owner");
        }

        // Old, already-purchased untagged doors are assigned a fresh pair slot on placement.
        if (pairId == null || pairId.isEmpty()) {
            pairId = data.createPair(player.getUniqueID());
            if (pairId == null) {
                event.setCanceled(true);
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_limit"), true);
                return;
            }
            if (!held.hasTagCompound()) held.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
            held.getTagCompound().setString("pocket_pair_id", pairId);
            held.getTagCompound().setString("pocket_owner", player.getUniqueID().toString());
        } else if (itemOwner == null || !player.getUniqueID().toString().equals(itemOwner)) {
            event.setCanceled(true);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_not_yours"), true);
            return;
        }

        BlockPos pos = lowerPos(world, event.getPos());
        if (!data.registerDoor(world.provider.getDimension(), pos.toLong(), player.getUniqueID(), pairId)) {
            event.setCanceled(true);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_full"), true);
        }
    }

    @SubscribeEvent
    public static void onDoorBroken(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !(world instanceof WorldServer)) return;
        BlockPos pos = lowerPos(world, event.getPos());
        PocketDoorData data = PocketDoorData.get((WorldServer) world);
        UUID owner = data.owner(world.provider.getDimension(), pos.toLong());
        if (owner != null && !owner.equals(event.getPlayer().getUniqueID())) {
            event.setCanceled(true);
            return;
        }
        data.remove(world.provider.getDimension(), pos.toLong());
    }

    @SubscribeEvent
    public static void onDoorUsed(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        BlockPos pos = lowerPos(world, event.getPos());
        if (!(world.getBlockState(pos).getBlock() instanceof BlockDoor)) return;

        PocketDoorData data = PocketDoorData.get((WorldServer) world);
        UUID owner = data.owner(world.provider.getDimension(), pos.toLong());
        String pairId = data.pairId(world.provider.getDimension(), pos.toLong());
        if (owner == null || pairId == null) return;

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        boolean ownerAccess = owner.equals(player.getUniqueID());
        boolean trustedAccess = data.isTrusted(owner, player.getName());
        boolean keyAccess = data.keysEnabled(owner)
                && PocketKeys.opensPair(event.getItemStack(), owner, pairId);

        boolean leavingOwnerPocket = false;
        if (PocketDimensionManager.isPocketDimension(player.dimension)) {
            int ownerDimension = PocketDimensionManager.getDimensionForOwner(player.getServer(), owner);
            leavingOwnerPocket = ownerDimension == player.dimension;
        }

        if (!leavingOwnerPocket && !ownerAccess && !trustedAccess && !keyAccess) {
            player.sendStatusMessage(new TextComponentTranslation(
                    data.keysEnabled(owner) ? "pocketmod.msg.door_locked" : "pocketmod.msg.keys_disabled"), true);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }

        if (leavingOwnerPocket) {
            // Leaving the pocket entered through this owner's door never consumes a key.
            PocketTeleporter.toggle(player);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            return;
        }

        boolean entered = PocketTeleporter.enterNetwork(player, owner);
        if (entered) {
            int accessLevel = trustedAccess ? PocketAccess.TRUSTED
                    : (keyAccess && !PocketKeys.isOneTime(event.getItemStack())
                        ? PocketAccess.BUILDER : PocketAccess.TEMPORARY);
            PocketAccess.grant(player, owner, accessLevel);
        }
        event.setCanceled(true);
        event.setCancellationResult(entered ? EnumActionResult.SUCCESS : EnumActionResult.FAIL);
        boolean keyWasRequired = !ownerAccess && !trustedAccess && keyAccess;
        if (entered && keyWasRequired && PocketKeys.isOneTime(event.getItemStack())) {
            ItemStack used = player.getHeldItem(event.getHand());
            if (!used.isEmpty()) used.shrink(1);
        }
    }
}
