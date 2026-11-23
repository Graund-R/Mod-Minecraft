package pocketmod;

import java.util.ArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ResurrectionTotems {
    private ResurrectionTotems() {}

    /** Именно этот взрыв безопасен для игроков. Остальные взрывы сохраняют обычный урон. */
    private static final class TotemExplosion extends Explosion {
        TotemExplosion(World world, BlockPos pos) {
            super(
                    world,
                    null,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    4,
                    false,
                    true);
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate e) {
        if (e.getExplosion() instanceof TotemExplosion)
            e.getAffectedEntities().removeIf(entity -> entity instanceof EntityPlayer);
    }

    @SubscribeEvent
    public static void place(BlockEvent.PlaceEvent e) {
        if (e.getPlacedBlock().getBlock() == ExileItems.RESURRECTION_TOTEM
                && e.getWorld().provider.getDimension() != 0) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) e.getEntityLiving();
        if (player.isCreative() || (player.isSpectator() && !Spector.isActive(player))) return;
        WorldServer overworld = player.getServer().getWorld(0);
        ExileWorldData data = ExileWorldData.get(overworld);
        BlockPos pos = data.takeResurrectionTotem(overworld, player.getUniqueID());
        NBTTagCompound state = ExileMode.state(player);
        state.setBoolean("resurrection_pending", false);
        if (pos == null) {
            if (data.enabled) {
                state.setBoolean("permadead", true);
                player.sendMessage(new TextComponentTranslation("pocketmod.exile.permadeath"));
            }
            return;
        }
        // Захват тотема и удаление блока происходят до взрыва. Повторная смерть не использует его
        // снова.
        state.setBoolean("permadead", false);
        state.setBoolean("resurrection_pending", true);
        state.setDouble("resurrection_x", pos.getX() + 0.5);
        state.setDouble("resurrection_y", pos.getY() + 1);
        state.setDouble("resurrection_z", pos.getZ() + 0.5);
        data.pendingResurrections.setTag(player.getUniqueID().toString(), state.copy());
        data.markDirty();
        overworld.setBlockToAir(pos);
        explode(overworld, pos);
        double[] safe =
                PocketTeleporter.findStandable(
                        overworld, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        if (safe == null) {
            BlockPos top = overworld.getTopSolidOrLiquidBlock(pos);
            safe = new double[] {top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5};
        }
        state.setBoolean("permadead", false);
        state.setBoolean("resurrection_pending", true);
        state.setDouble("resurrection_x", safe[0]);
        state.setDouble("resurrection_y", safe[1]);
        state.setDouble("resurrection_z", safe[2]);
        data.pendingResurrections.setTag(player.getUniqueID().toString(), state.copy());
        data.markDirty();
        player.sendMessage(new TextComponentTranslation("pocketmod.exile.totem_used"));
    }

    private static void explode(WorldServer world, BlockPos pos) {
        TotemExplosion explosion = new TotemExplosion(world, pos);
        if (ForgeEventFactory.onExplosionStart(world, explosion)) return;
        explosion.doExplosionA();
        explosion.doExplosionB(true);
        for (EntityPlayer player : new ArrayList<>(world.playerEntities)) {
            if (!(player instanceof EntityPlayerMP) || player.getDistanceSq(pos) >= 4096) continue;
            Vec3d knockback = explosion.getPlayerKnockbackMap().get(player);
            ((EntityPlayerMP) player)
                    .connection.sendPacket(
                            new SPacketExplosion(
                                    pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5,
                                    4,
                                    explosion.getAffectedBlockPositions(),
                                    knockback));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) e.player;
        NBTTagCompound state = ExileMode.state(player);
        ExileWorldData data = ExileWorldData.get(player.world);
        NBTTagCompound claim =
                data.pendingResurrections.getCompoundTag(player.getUniqueID().toString());
        if (!state.getBoolean("resurrection_pending") && claim.getBoolean("resurrection_pending")) {
            state.setBoolean("resurrection_pending", true);
            state.setDouble("resurrection_x", claim.getDouble("resurrection_x"));
            state.setDouble("resurrection_y", claim.getDouble("resurrection_y"));
            state.setDouble("resurrection_z", claim.getDouble("resurrection_z"));
        }
        if (state.getBoolean("resurrection_pending")) {
            double x = state.getDouble("resurrection_x"),
                    y = state.getDouble("resurrection_y"),
                    z = state.getDouble("resurrection_z");
            if (player.dimension != 0) player.changeDimension(0, new PocketTeleporter(x, y, z));
            else
                player.connection.setPlayerLocation(
                        x, y, z, player.rotationYaw, player.rotationPitch);
            player.setGameType(GameType.SURVIVAL);
            state = ExileMode.state(player);
            player.fallDistance = 0;
            player.motionX = player.motionY = player.motionZ = 0;
            state.setBoolean("resurrection_pending", false);
            state.setBoolean("trial_active", false);
            data.pendingResurrections.removeTag(player.getUniqueID().toString());
            data.markDirty();
        } else if (ExileMode.enabled(player.world) && state.getBoolean("permadead")) {
            Spector.forceReturnIfActive(player);
            player.setGameType(GameType.SPECTATOR);
        }
        NetworkHandler.sync(player);
    }
}
