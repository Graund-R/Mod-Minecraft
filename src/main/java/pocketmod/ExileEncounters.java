package pocketmod;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileEncounters {
    /** Задача с приоритетом движения удерживает обычный AI от возвращения в опасную область. */
    private static final class EscapeTask extends net.minecraft.entity.ai.EntityAIBase {
        private final EntityLiving mob;

        EscapeTask(EntityLiving mob) {
            this.mob = mob;
            setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (mob.getEntityData().getBoolean(ExileMode.PURSUER)) return false;
            BlockPos p = mob.getPosition();
            return ExileWorldData.get(mob.world)
                            .exposure(mob.dimension, p.getX() >> 4, p.getZ() >> 4)
                    >= ExileConfig.fleeSeconds;
        }

        @Override
        public void updateTask() {
            mob.setAttackTarget(null);
        }
    }

    @SubscribeEvent
    public static void join(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.getWorld().isRemote
                || !(e.getEntity() instanceof EntityLiving)
                || !ExileMode.enabled(e.getWorld())
                || PocketDimensionManager.isPocketDimension(e.getEntity().dimension)) return;
        EntityLiving mob = (EntityLiving) e.getEntity();
        if (mob.isNonBoss() && !(mob instanceof EntityDecoy))
            mob.tasks.addTask(0, new EscapeTask(mob));
    }

    private ExileEncounters() {}

    public static void spawn(EntityPlayerMP player, int stage) {
        WorldServer world = player.getServerWorld();
        int nearby =
                world.getEntitiesWithinAABB(
                                EntityLiving.class,
                                player.getEntityBoundingBox().grow(56, 20, 56),
                                e -> e.getEntityData().getBoolean(ExileMode.PURSUER))
                        .size();
        if (nearby >= Math.min(8, 2 + stage * 2)) return;
        BlockPos origin = player.getPosition();
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            int radius = 16 + world.rand.nextInt(17);
            int x = origin.getX() + (int) (Math.cos(angle) * radius);
            int z = origin.getZ() + (int) (Math.sin(angle) * radius);
            // Высота игрока задаёт область поиска. Поверхность над шахтой не выбирается
            // автоматически.
            int y = origin.getY() + world.rand.nextInt(25) - 12;
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isBlockLoaded(pos) || !PocketTeleporter.standable(world, pos)) continue;
            EntityLiving mob =
                    stage >= 3
                            ? new EntityWitherSkeleton(world)
                            : stage >= 2 ? new EntitySkeleton(world) : new EntityZombie(world);
            mob.setLocationAndAngles(x + 0.5, y, z + 0.5, world.rand.nextFloat() * 360, 0);
            if (!world.checkNoEntityCollision(mob.getEntityBoundingBox())
                    || !world.getCollisionBoxes(mob, mob.getEntityBoundingBox()).isEmpty()
                    || world.containsAnyLiquid(mob.getEntityBoundingBox())
                    || visible(world, mob)) continue;
            Path path = mob.getNavigator().getPathToEntityLiving(player);
            if (path == null
                    || path.getFinalPathPoint() == null
                    || path.getFinalPathPoint()
                                    .distanceTo(
                                            new net.minecraft.pathfinding.PathPoint(
                                                    origin.getX(), origin.getY(), origin.getZ()))
                            > 2) continue;
            mob.onInitialSpawn(world.getDifficultyForLocation(pos), null);
            mob.getEntityData().setBoolean(ExileMode.PURSUER, true);
            mob.getEntityData().setUniqueId("pm_exile_owner", player.getUniqueID());
            mob.getEntityData().setLong("pm_exile_birth", ExileWorldData.get(world).seconds);
            mob.setAttackTarget(player);
            mob.getNavigator().setPath(path, 1.1);
            world.spawnEntity(mob);
            return;
        }
    }

    private static boolean visible(WorldServer world, EntityLiving mob) {
        for (EntityPlayer p : world.playerEntities) {
            Vec3d to =
                    mob.getPositionVector()
                            .addVector(0, mob.height * 0.5, 0)
                            .subtract(p.getPositionEyes(1))
                            .normalize();
            if (p.getLookVec().dotProduct(to) > 0.25 && p.canEntityBeSeen(mob)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void living(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityLiving)
                || event.getEntityLiving().world.isRemote) return;
        EntityLiving mob = (EntityLiving) event.getEntityLiving();
        if (mob.ticksExisted % 40 != 0
                || !ExileMode.enabled(mob.world)
                || PocketDimensionManager.isPocketDimension(mob.dimension)) return;
        NBTTagCompound n = mob.getEntityData();
        ExileWorldData data = ExileWorldData.get(mob.world);
        if (n.getBoolean(ExileMode.PURSUER)) {
            EntityPlayer target = mob.world.getPlayerEntityByUUID(n.getUniqueId("pm_exile_owner"));
            if (target == null
                    || !target.isEntityAlive()
                    || !ExileMode.active(target)
                    || target.isCreative()
                    || (target.isSpectator() && !Spector.isActive(target))
                    || target.getDistanceSq(mob) > 16384
                    || data.seconds - n.getLong("pm_exile_birth") > 300) {
                mob.setDead();
                return;
            }
            if (n.hasKey("pm_exile_trial") && !ExileMode.state(target).getBoolean("trial_active")) {
                mob.setDead();
                return;
            }
            mob.setAttackTarget(target);
            return;
        }
        if (!mob.isNonBoss() || mob instanceof EntityDecoy) return;
        BlockPos origin = mob.getPosition();
        int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
        double exposure = data.exposure(mob.dimension, cx, cz);
        if (exposure >= ExileConfig.deathSeconds) {
            // Загрязнение не создаёт автоматическую ферму предметов и опыта.
            mob.world.setEntityState(mob, (byte) 3);
            mob.setDead();
            return;
        }
        if (exposure < ExileConfig.fleeSeconds) return;
        mob.setAttackTarget(null);
        double cleanest = exposure;
        BlockPos destination = null;
        int pathsTried = 0;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0 || pathsTried >= 4) continue;
                BlockPos column =
                        new BlockPos(((cx + dx) << 4) + 8, origin.getY(), ((cz + dz) << 4) + 8);
                if (!mob.world.isBlockLoaded(column)) continue;
                double next = data.exposure(mob.dimension, cx + dx, cz + dz);
                if (next >= cleanest) continue;
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos feet = column.up(dy);
                    if (!PocketTeleporter.standable(mob.world, feet)) continue;
                    if (++pathsTried > 4) break;
                    Path path = mob.getNavigator().getPathToPos(feet);
                    if (path == null
                            || path.getFinalPathPoint() == null
                            || path.getFinalPathPoint()
                                            .distanceTo(
                                                    new net.minecraft.pathfinding.PathPoint(
                                                            feet.getX(), feet.getY(), feet.getZ()))
                                    > 2) continue;
                    destination = feet;
                    cleanest = next;
                    break;
                }
            }
        if (destination != null)
            mob.getNavigator()
                    .tryMoveToXYZ(
                            destination.getX() + 0.5,
                            destination.getY(),
                            destination.getZ() + 0.5,
                            1.3);
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (e.getEntityLiving().getEntityData().getBoolean(ExileMode.PURSUER)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void experience(LivingExperienceDropEvent e) {
        if (e.getEntityLiving().getEntityData().getBoolean(ExileMode.PURSUER))
            e.setDroppedExperience(0);
    }
}
