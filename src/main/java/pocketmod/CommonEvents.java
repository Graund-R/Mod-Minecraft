package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
    public static void onBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != PocketMod.DIM_ID) {
            return;
        }
        if (PocketData.get(world).walls.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != PocketMod.DIM_ID) {
            return;
        }
        PocketData data = PocketData.get(world);
        BlockPos pos = event.getPos();
        if (!data.walls.contains(pos)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
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

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!ManaItems.isManaItem(stack)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();

        RayTraceResult hit = player.rayTrace(5.0D, 1.0F);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            return;
        }

        int gained = ManaItems.manaValue(stack) * stack.getCount();
        Mana.add(player, gained);
        player.setHeldItem(event.getHand(), ItemStack.EMPTY);
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.mana_gained", gained), true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        EntityPlayer old = event.getOriginal();
        EntityPlayer now = event.getEntityPlayer();

        now.getEntityData().setTag(PlayerData.KEY, PlayerData.root(old).copy());

        if (event.isWasDeath()) {
            Mana.set(now, Mana.get(now) * (1.0 - PocketConfig.deathManaLoss));
            if (old.dimension == PocketMod.DIM_ID) {
                Mana.setFreeEntry(now, true);
            }
        }
    }
}
