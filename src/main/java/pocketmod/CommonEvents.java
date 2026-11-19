package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class CommonEvents {

    @SubscribeEvent
    public static void onPlace(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !PocketDimensionManager.isPocketDimension(world.provider.getDimension())) {
            return;
        }
        if (!PocketAccess.canBuild(event.getPlayer(), world)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !PocketDimensionManager.isPocketDimension(world.provider.getDimension())) {
            return;
        }
        if (!PocketAccess.canBuild(event.getPlayer(), world)
                || PocketData.get(world).walls.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Оплата расширения: по СТОИМОСТИ, любыми слитками/самоцветами. Переплата → мана. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !PocketDimensionManager.isPocketDimension(world.provider.getDimension())) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        Block clicked = world.getBlockState(event.getPos()).getBlock();
        if (PocketAccess.level(player, world) == PocketAccess.TEMPORARY
                && !(clicked instanceof BlockDoor)
                && !PocketAccess.canUseTemporary(player, world, clicked)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        PocketData data = PocketData.get(world);
        BlockPos pos = event.getPos();
        if (!data.walls.contains(pos)) {
            return;
        }
        if (!PocketAccess.canBuild(player, world)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        ItemStack stack = player.getHeldItemMainhand();
        int itemValue = ManaItems.manaValue(stack);
        if (itemValue <= 0) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        if (!Pocket.isPayable(data, pos)) {
            return;
        }

        double cost = Pocket.costMana(pos, player);
        Double already = data.paid.get(pos);
        double paid = already == null ? 0.0 : already.doubleValue();
        double need = Math.max(0.0, cost - paid);

        int n = (int) Math.min((double) stack.getCount(), Math.ceil(need / (double) itemValue));
        if (n <= 0) {
            return;
        }
        stack.shrink(n);
        paid += n * (double) itemValue;

        if (paid >= cost - 1.0E-9) {
            double excess = Math.max(0.0, paid - cost);
            if (excess > 0.0) {
                Mana.add(player, excess);
            }
            Pocket.open((WorldServer) world, data, pos);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.expand_done", (int) excess), true);
        } else {
            data.paid.put(pos, paid);
            data.markDirty();
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.expand_progress",
                    (int) paid, (int) Math.ceil(cost)), true);
        }
    }

    /** ПКМ по воздуху со слитком/самоцветом: вся стопка → мана. Не срабатывает, если рядом блок в пределах руки. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = event.getItemStack();
        if (!ManaItems.isManaItem(stack)) {
            return;
        }
        RayTraceResult hit = player.rayTrace(5.0D, 1.0F);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            return;
        }

        int gained = ManaItems.manaValue(stack) * stack.getCount();
        Mana.add(player, gained);
        player.setHeldItem(event.getHand(), ItemStack.EMPTY);
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.mana_gained", gained), true);
    }

    /** Все данные мода лежат в PlayerPersisted — копируем ветку целиком. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        EntityPlayer old = event.getOriginal();
        EntityPlayer now = event.getEntityPlayer();

        now.getEntityData().setTag(PlayerData.KEY, PlayerData.root(old).copy());

        if (event.isWasDeath()) {
            Mana.set(now, Mana.get(now) * (1.0 - PocketConfig.deathManaLoss));
            boolean diedInOwnPocket = false;
            if (old.world instanceof net.minecraft.world.WorldServer
                    && PocketDimensionManager.isPocketDimension(old.dimension)) {
                java.util.UUID pocketOwner = PocketDimensionManager.getOwnerForDimension(
                        ((net.minecraft.world.WorldServer) old.world).getMinecraftServer(), old.dimension);
                diedInOwnPocket = pocketOwner != null && pocketOwner.equals(old.getUniqueID());
            }
            if (diedInOwnPocket) {
                Mana.setFreeEntry(now, true);
                // Death totem and free re-entry are owner-only.
                Totems.setDeathTotem(now, old.dimension, old.posX, old.posY, old.posZ);
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        if (!PocketDimensionManager.isPocketDimension(player.dimension)) return;
        java.util.UUID owner = PocketDimensionManager.getOwnerForDimension(player.getServer(), player.dimension);
        if (owner != null && !owner.equals(player.getUniqueID())) {
            PocketTeleporter.toggle(player);
        }
    }

}
