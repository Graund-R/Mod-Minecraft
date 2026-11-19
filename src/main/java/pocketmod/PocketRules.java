package pocketmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.terraingen.SaplingGrowTreeEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Правила карманного измерения: нельзя призывать существ (яйца, /summon, спавн), расти растениям,
 * спать в кроватях и выходить за границу телепортом (жемчуг эндера, плод хоруса).
 */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class PocketRules {

    private static boolean inPocket(World w) {
        return PocketDimensionManager.isPocketDimension(w.provider.getDimension());
    }

    /** Любые живые существа, кроме игрока и нашей приманки/тела Спектора, в кармане не появляются. */
    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent e) {
        World w = e.getWorld();
        if (w.isRemote || !inPocket(w)) return;
        Entity en = e.getEntity();
        if (en instanceof EntityPlayer || en instanceof EntityDecoy) return;
        if (en instanceof EntityLivingBase) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre e) {
        if (!e.getWorld().isRemote && inPocket(e.getWorld())) e.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onSaplingGrow(SaplingGrowTreeEvent e) {
        if (!e.getWorld().isRemote && inPocket(e.getWorld())) e.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent e) {
        if (!e.getWorld().isRemote && inPocket(e.getWorld())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent e) {
        if (inPocket(e.getEntityPlayer().world)) {
            e.setResult(EntityPlayer.SleepResult.NOT_POSSIBLE_HERE);
            if (!e.getEntityPlayer().world.isRemote) {
                e.getEntityPlayer().sendStatusMessage(new TextComponentTranslation("pocketmod.msg.pocket_no_sleep"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBedClick(PlayerInteractEvent.RightClickBlock e) {
        World w = e.getWorld();
        if (w.isRemote || !inPocket(w)) return;
        if (w.getBlockState(e.getPos()).getBlock() == Blocks.BED) {
            e.setCanceled(true);
        }
    }

    /** Жемчуг эндера и плод хоруса работают только внутри кармана. */
    @SubscribeEvent
    public static void onEnderTeleport(EnderTeleportEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayer)) return;
        World w = e.getEntityLiving().world;
        if (w.isRemote || !inPocket(w)) return;
        PocketData d = PocketData.get(w);
        BlockPos feet = new BlockPos(MathHelper.floor(e.getTargetX()), MathHelper.floor(e.getTargetY()), MathHelper.floor(e.getTargetZ()));
        if (!d.interior.contains(feet) || !d.interior.contains(feet.up())) {
            e.setCanceled(true);
        }
    }
}
