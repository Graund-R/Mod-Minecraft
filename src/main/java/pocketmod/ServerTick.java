package pocketmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class ServerTick {

    /** Время прошлого тика игрока (для кредитного таймера: пауза и меню не считаются). */
    private static final Map<UUID, Long> lastTick = new HashMap<UUID, Long>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!TemporalSystem.canMove(player)) return;

        long now = System.nanoTime() / 1000000L;
        Long prev = lastTick.put(player.getUniqueID(), now);
        long delta = prev == null ? 50L : Math.max(0L, Math.min(100L, now - prev.longValue()));

        // В личном кармане активная форма Metamorph запрещена, даже при внешнем переносе.
        if (PocketDimensionManager.isPocketDimension(player.dimension)
                && MorphIntegration.isMorphed(player)) {
            MorphIntegration.demorph(player);
        }

        // При морфизме остаются доступны магазин и тотемы, все прочие эффекты отключены.
        if (MorphIntegration.isMorphed(player)) {
            MorphIntegration.suppressAbilities(player);
            BodyEffects.suppressForMorph(player);
            Mana.tickCredit(player, delta);
            return;
        }

        ActiveAbilities.tick(player);

        // Содержание измерения оплачивает только его владелец. Гостевой вход бесплатный.
        if (PocketDimensionManager.isPocketDimension(player.dimension)
                && PocketConfig.pocketTickCost > 0 && player.ticksExisted % 20 == 0) {
            java.util.UUID owner = PocketDimensionManager.getOwnerForDimension(player.getServer(), player.dimension);
            if (owner != null && owner.equals(player.getUniqueID())) {
                double fee = PocketConfig.pocketTickCost * Mana.getPriceMultiplier(player);
                if (!Mana.spend(player, fee)) {
                    player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                            "pocketmod.msg.pocket_fee_empty"), true);
                    PocketTeleporter.toggle(player);
                }
            }
        }
        if (AbilityStorage.isUnlocked(player, AbilityType.MAGNET)) Magnet.tick(player);
        Mana.tickCredit(player, delta);
    }
}
