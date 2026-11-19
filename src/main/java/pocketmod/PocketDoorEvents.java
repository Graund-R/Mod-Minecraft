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
        BlockPos pos = lowerPos(world, event.getPos());
        PocketDoorData.get((WorldServer) world).register(world.provider.getDimension(), pos.toLong(), player.getUniqueID());
    }

    @SubscribeEvent
    public static void onDoorBroken(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !(world instanceof WorldServer)) return;
        BlockPos pos = lowerPos(world, event.getPos());
        PocketDoorData.get((WorldServer) world).remove(world.provider.getDimension(), pos.toLong());
    }

    @SubscribeEvent
    public static void onDoorUsed(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        BlockPos pos = lowerPos(world, event.getPos());
        if (!(world.getBlockState(pos).getBlock() instanceof BlockDoor)) return;
        UUID owner = PocketDoorData.get((WorldServer) world).owner(world.provider.getDimension(), pos.toLong());
        if (owner == null) return;

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        boolean ownerAccess = owner.equals(player.getUniqueID());
        boolean keyAccess = PocketKeys.opensNetwork(event.getItemStack(), owner);
        if (!ownerAccess && !keyAccess) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_locked"), true);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        if (PocketDimensionManager.isPocketDimension(player.dimension)) {
            PocketTeleporter.toggle(player);
        } else if (PocketTeleporter.enterNetwork(player, owner)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }
}
