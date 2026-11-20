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
        if (world.isRemote || !PocketDimensionManager.isPocketDimension(world.provider.getDimension())) {
            return;
        }
        if (PocketData.get(world).walls.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Оплата расширения: по СТОИМОСТИ, любыми слитками/самоцветами. Переплата → мана. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !PocketDimensionManager.isPocketDimension(world.provider.getDimension()) || event.isCanceled()) {
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
            NetworkHandler.syncPocketWorld((WorldServer) world);
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
        // В кармане слитки не превращаются в ману по ПКМ в воздух: при удержании ПКМ у стены
        // луч иногда не попадает в блок, и вся стопка уходила в ману вместо покупки стены.
        // Сдача от покупки стены (Mana.add(excess) в onRightClickBlock) не затрагивается.
        if (PocketDimensionManager.isPocketDimension(world.provider.getDimension())) {
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

    /** Все данные мода лежат в PlayerPersisted — копируем ветку целиком. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        EntityPlayer old = event.getOriginal();
        EntityPlayer now = event.getEntityPlayer();

        now.getEntityData().setTag(PlayerData.KEY, PlayerData.root(old).copy());

        if (event.isWasDeath()) {
            Mana.set(now, Mana.get(now) * (1.0 - PocketConfig.deathManaLoss));
            WarehouseInventory.handleDeath(old, now);

            boolean diedInOwnPocket = false;
            if (old.world instanceof WorldServer
                    && PocketDimensionManager.isPocketDimension(old.dimension)) {
                java.util.UUID owner = PocketDimensionManager.getOwnerForDimension(
                        ((WorldServer) old.world).getMinecraftServer(), old.dimension);
                diedInOwnPocket = owner != null && owner.equals(old.getUniqueID());
            }
            if (diedInOwnPocket) {
                // Старое бесплатное возвращение сохраняем в данных, хотя K теперь бесплатен всегда.
                Mana.setFreeEntry(now, true);
            }

            Totems.clearDeathTotem(now);
            if (AbilityStorage.isUnlocked(now, AbilityType.TOTEM)) {
                Totems.setPendingDeathTotem(now, old.dimension, old.posX, old.posY, old.posZ);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof net.minecraft.entity.player.EntityPlayerMP)) return;
        net.minecraft.entity.player.EntityPlayerMP player =
                (net.minecraft.entity.player.EntityPlayerMP) event.player;
        MorphIntegration.demorph(player);
        if (!PocketDimensionManager.isPocketDimension(player.dimension)) return;

        java.util.UUID owner = PocketDimensionManager.getOwnerForDimension(player.getServer(), player.dimension);
        if (owner != null && !owner.equals(player.getUniqueID())) {
            // Гость не остаётся в чужом измерении после возрождения.
            PocketTeleporter.toggle(player);
        }
    }}
