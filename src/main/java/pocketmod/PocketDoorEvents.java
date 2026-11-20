package pocketmod;

import java.util.UUID;

import net.minecraft.block.BlockDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
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
        // The inner door of a pair may only be placed in the placer's own pocket.
        int placeDim = world.provider.getDimension();
        if (PocketDimensionManager.isPocketDimension(placeDim)
                && placeDim != PocketDimensionManager.getDimensionForOwner(player.getServer(), player.getUniqueID())) {
            event.setCanceled(true);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_inner_only_own"), true);
            return;
        }
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
        boolean ownerAccess = owner != null && owner.equals(event.getPlayer().getUniqueID());
        boolean trustedAccess = owner != null && data.isTrusted(owner, event.getPlayer().getName());
        boolean pocketBuilder = PocketDimensionManager.isPocketDimension(world.provider.getDimension())
                && PocketAccess.canBuild(event.getPlayer(), world);
        boolean ownerOutsideDoor = ownerAccess
                && !PocketDimensionManager.isPocketDimension(world.provider.getDimension());
        if (owner != null && !ownerOutsideDoor && !trustedAccess && !pocketBuilder) {
            event.setCanceled(true);
            return;
        }

        // The last inner door of an active pair cannot be broken while its outer door exists,
        // otherwise visitors could be trapped. Break the outer door first to dismantle a pair.
        final String brokenPair = data.pairId(world.provider.getDimension(), pos.toLong());
        if (owner != null && brokenPair != null
                && PocketDimensionManager.isPocketDimension(world.provider.getDimension())
                && data.roleDoor(brokenPair, false) != null && data.completePairs(owner) <= 1) {
            event.setCanceled(true);
            event.getPlayer().sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_last_inner"), true);
            return;
        }

        // Remove the physical door mapping after harvest so the drops event can
        // reliably identify this as a registered pocket door and suppress drops.
        final int dimension = world.provider.getDimension();
        final long packedPos = pos.toLong();
        final UUID doorOwner = owner;
        ((WorldServer) world).addScheduledTask(new Runnable() {
            @Override public void run() {
                data.remove(dimension, packedPos);
                // No active pair left: everyone except the owner is sent out so nobody is trapped.
                if (doorOwner != null && data.completePairs(doorOwner) == 0) {
                    ejectVisitors((WorldServer) world, doorOwner);
                }
            }
        });
    }

    private static void ejectVisitors(WorldServer world, UUID owner) {
        MinecraftServer server = world.getMinecraftServer();
        if (server == null) return;
        int dim = PocketDimensionManager.getDimensionForOwner(server, owner);
        java.util.List<EntityPlayerMP> players = new java.util.ArrayList<EntityPlayerMP>(server.getPlayerList().getPlayers());
        for (EntityPlayerMP p : players) {
            if (p.dimension == dim && !p.getUniqueID().equals(owner)) PocketTeleporter.toggle(p);
        }
    }

    /** Door items of a pair that is already fully placed are removed (e.g. leftovers from creative mode). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        if (player.world.isRemote || !(player.world instanceof WorldServer)
                || player.ticksExisted % 20 != 0 || PocketDoorItems.DOOR == null) return;
        PocketDoorData data = PocketDoorData.get((WorldServer) player.world);
        java.util.List<java.util.List<ItemStack>> lists = new java.util.ArrayList<java.util.List<ItemStack>>();
        lists.add(player.inventory.mainInventory);
        lists.add(player.inventory.offHandInventory);
        for (java.util.List<ItemStack> list : lists) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack s = list.get(i);
                if (s.isEmpty() || s.getItem() != PocketDoorItems.DOOR || !s.hasTagCompound()) continue;
                String pid = s.getTagCompound().getString("pocket_pair_id");
                if (!pid.isEmpty() && data.pairComplete(pid)) list.set(i, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onPocketDoorDrops(HarvestDropsEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !(world instanceof WorldServer)
                || !(event.getState().getBlock() instanceof BlockDoor)) return;
        BlockPos pos = event.getPos();
        if (event.getState().getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER) {
            pos = pos.down();
        }
        PocketDoorData data = PocketDoorData.get((WorldServer) world);
        if (data.pairId(world.provider.getDimension(), pos.toLong()) != null) {
            event.getDrops().clear();
            data.remove(world.provider.getDimension(), pos.toLong());
        }
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
        // In the temporary guest-test mode the owner is treated as a guest at the door as well.
        boolean ownerAccess = owner.equals(player.getUniqueID()) && !data.guestTestEnabled(owner);
        boolean trustedAccess = data.isTrusted(owner, player.getName());
        boolean keyAccess = data.keysEnabled(owner)
                && PocketKeys.opensPair(event.getItemStack(), owner, pairId, data.keyEpoch(owner));

        boolean leavingOwnerPocket = false;
        if (PocketDimensionManager.isPocketDimension(player.dimension)) {
            int ownerDimension = PocketDimensionManager.getDimensionForOwner(player.getServer(), owner);
            leavingOwnerPocket = ownerDimension == player.dimension;
        }

        if (!leavingOwnerPocket && !ownerAccess && data.accessPaused(owner)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.access_paused"), true);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
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
            // Exit through the inner door leads to the outer door of the same pair.
            if (!PocketTeleporter.exitThroughDoor(player, pairId)) PocketTeleporter.toggle(player);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            return;
        }

        boolean entered = PocketTeleporter.enterNetwork(player, owner, pairId);
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
