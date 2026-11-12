package pocketmod;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Bullet time: мир замедляется, игрок — нет.
 * Каждый тик умножаем motion всех сущностей (кроме игроков) на фактор.
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class TimeSlow {

    private static final double RADIUS = 96.0;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        if (!(event.world instanceof WorldServer)) return;

        WorldServer world = (WorldServer) event.world;

        float strongest = 1.0f;
        EntityPlayer source = null;
        for (EntityPlayer p : world.playerEntities) {
            if (Abilities.isSlow(p)) {
                float f = Abilities.getSlowFactor(p);
                if (f < strongest) {
                    strongest = f;
                    source = p;
                }
            }
        }
        if (source == null || strongest >= 0.99f) return;

        AxisAlignedBB box = source.getEntityBoundingBox().grow(RADIUS);
        List<Entity> list = world.getEntitiesWithinAABB(Entity.class, box);
        for (Entity e : list) {
            if (e instanceof EntityPlayer) continue;
            if (!e.isEntityAlive() && !(e instanceof EntityItem) && !(e instanceof EntityXPOrb)) continue;

            e.motionX *= strongest;
            e.motionY *= strongest;
            e.motionZ *= strongest;
        }
    }

    public static void tickMana(EntityPlayerMP player) {
        if (!Abilities.isSlow(player)) return;
        float factor = Abilities.getSlowFactor(player);
        double costPerSec = (1.0 / Math.max(0.1, factor)) * 2.0 * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, costPerSec / 20.0)) {
            player.getEntityData().setBoolean("pocketmod_slow", false);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.slow_off"), true);
        }
    }
}
