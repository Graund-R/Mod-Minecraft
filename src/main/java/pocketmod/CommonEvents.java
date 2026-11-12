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
        if (!ManaItems.isManaItem(stack)) {
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

        int cost = Pocket.cost(pos);
        int paid = data.paid.containsKey(pos) ? data.paid.get(pos) : 0;
        int give = Math.min(stack.getCount(), cost - paid);
        if (give <= 0) {
            return;
        }
        stack.shrink(give);
        paid += give;
        if (paid >= cost) {
            Pocket.open((WorldServer) world, data, pos);
        } else {
            data.paid.put(pos, paid);
            data.markDirty();
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

        if (world.provider.getDimension() == PocketMod.DIM_ID) {
            RayTraceResult hit = player.rayTrace(5.0D, 1.0F);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && PocketData.get(world).walls.contains(hit.getBlockPos())) {
                return;
            }
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

        Mana.setPriceMultiplier(now, Mana.getPriceMultiplier(old));
        if (Mana.hasCredit(old)) {
            now.getEntityData().setDouble("pocketmod_credit", Mana.getCredit(old));
            now.getEntityData().setLong("pocketmod_credit_due", Mana.getCreditDue(old));
        }

        double mana = Mana.get(old);
        if (event.isWasDeath()) {
            mana = mana * (1.0 - PocketConfig.deathManaLoss);
            if (old.dimension == PocketMod.DIM_ID) {
                Mana.setFreeEntry(now, true);
            }
        } else {
            String[] keys = { "pocket_return_dim", "pocket_return_x", "pocket_return_y", "pocket_return_z" };
            for (String k : keys) {
                if (old.getEntityData().hasKey(k)) {
                    now.getEntityData().setTag(k, old.getEntityData().getTag(k));
                }
            }
            if (Mana.hasFreeEntry(old)) {
                Mana.setFreeEntry(now, true);
            }
        }
        Mana.set(now, mana);
    }
}
