package pocketmod;

import java.util.Random;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Three reserved destinations. Navigation never places blocks or generates chunks. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileTrials implements IWorldGenerator {
    private static String key(BlockPos p) { return p.getX() + "_" + p.getZ(); }
    static int completed(EntityPlayerMP p) {
        return Math.max(0, Math.min(3, ExileMode.state(p).getInteger("trial_route_completed")));
    }

    private static void plan(World world) {
        ExileWorldData data = ExileWorldData.get(world);
        if (data.route.hasKey("target_0")) return;
        BlockPos anchor = world.getSpawnPoint();
        for (int i = 0; i < 3; i++) {
            anchor = destination(world, i, anchor, 0);
            data.route.setLong("target_" + i, anchor.toLong());
        }
        data.markDirty();
    }

    static BlockPos destination(World world, int stage, BlockPos anchor, int attempt) {
        Random random = new Random(world.getSeed() ^ (0x504F434B45544CL + stage * 99991L + attempt * 104729L));
        int min = stage == 0 ? ExileConfig.firstTrialMin : ExileConfig.nextTrialMin;
        int max = stage == 0 ? ExileConfig.firstTrialMax : ExileConfig.nextTrialMax;
        double angle = random.nextDouble() * Math.PI * 2;
        // Leave room for snapping to the centre of a chunk.
        int distance = min + 24 + random.nextInt(Math.max(1, max - min - 47));
        int x = anchor.getX() + (int)(Math.cos(angle) * distance);
        int z = anchor.getZ() + (int)(Math.sin(angle) * distance);
        return new BlockPos((x >> 4) * 16 + 8, 64, (z >> 4) * 16 + 8);
    }

    @Override
    public void generate(Random random, int cx, int cz, World world,
            IChunkGenerator generator, IChunkProvider provider) {
        // Placement is deferred to the world tick after all synchronous population callbacks.
    }

    @SubscribeEvent
    public static void worldTick(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent e) {
        if (TemporalSystem.frozen(e.world)) return;
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END
                || e.world.isRemote || e.world.provider.getDimension() != 0
                || e.world.getTotalWorldTime() % 20 != 0 || !ExileMode.enabled(e.world)
                || e.world.playerEntities.isEmpty()) return;
        plan(e.world);
        ExileWorldData data = ExileWorldData.get(e.world);
        // At most one candidate is inspected per second; never load a chunk for placement.
        int i = 0;
        while (i < 3 && data.route.getBoolean("built_" + i)) i++;
        if (i == 3 || !ExileConfig.trialGeneration) return;
        BlockPos planned = BlockPos.fromLong(data.route.getLong("target_" + i));
        if (!e.world.isAreaLoaded(planned.add(-16, -64, -16), planned.add(16, 128, 16))) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (!e.world.getChunkFromChunkCoords((planned.getX() >> 4) + dx,
                    (planned.getZ() >> 4) + dz).isTerrainPopulated()) return;
        if (!e.world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class,
                new net.minecraft.util.math.AxisAlignedBB(planned.getX()-8, 0, planned.getZ()-8,
                        planned.getX()+9, 256, planned.getZ()+9)).isEmpty()) return;
        BlockPos built = build(e.world, planned.getX(), planned.getZ());
        if (built != null) {
            data.route.setLong("target_" + i, built.toLong());
            data.route.setBoolean("built_" + i, true);
        } else {
            int attempt = data.route.getInteger("attempt_" + i) + 1;
            data.route.setInteger("attempt_" + i, attempt);
            BlockPos anchor = i == 0 ? e.world.getSpawnPoint()
                    : BlockPos.fromLong(data.route.getLong("target_" + (i - 1)));
            data.route.setLong("target_" + i, destination(e.world, i, anchor, attempt).toLong());
            for (int next = i + 1; next < 3; next++) {
                anchor = BlockPos.fromLong(data.route.getLong("target_" + (next - 1)));
                data.route.setLong("target_" + next, destination(e.world, next, anchor, 0).toLong());
            }
        }
        data.markDirty();
    }

    private static boolean natural(net.minecraft.block.Block b) {
        for (String name : ExileConfig.trialGroundBlocks) {
            try {
                if (net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(name)) == b) return true;
            } catch (IllegalArgumentException ignored) {
                // Invalid optional config entries must not stop the world tick.
            }
        }
        return b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.STONE
                || b == Blocks.SAND || b == Blocks.SANDSTONE || b == Blocks.GRAVEL
                || b == Blocks.SNOW || b == Blocks.MYCELIUM;
    }

    private static BlockPos build(World world, int x, int z) {
        // Strict allowlist: no clearing, excavation, replacement of blocks, or trees.
        if (world.getChunkFromBlockCoords(new BlockPos(x, 64, z)).getInhabitedTime() > 200) return null;
        int y = world.getHeight(new BlockPos(x, 0, z)).getY();
        if (y < 3 || y > 240) return null;
        BlockPos top = new BlockPos(x, y, z);
        if (!world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class,
                new net.minecraft.util.math.AxisAlignedBB(top.add(-8, -2, -8), top.add(9, 6, 9))).isEmpty()) return null;
        for (String name : ExileConfig.trialStructureData) {
            net.minecraft.world.gen.structure.MapGenStructureData saved =
                    (net.minecraft.world.gen.structure.MapGenStructureData) world.getPerWorldStorage()
                    .getOrLoadData(net.minecraft.world.gen.structure.MapGenStructureData.class, name);
            if (saved == null) continue;
            NBTTagCompound structures = saved.getTagCompound();
            for (String id : structures.getKeySet()) {
                int[] box = structures.getCompoundTag(id).getIntArray("BB");
                if (box.length == 6 && x + 8 >= box[0] && x - 8 <= box[3]
                        && z + 8 >= box[2] && z - 8 <= box[5]) return null;
            }
        }
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ExileTrialPlacementEvent(world, top))) return null;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            BlockPos ground = world.getHeight(top.add(dx, 0, dz)).down();
            if (Math.abs(ground.getY() - (y - 1)) > 1
                    || !natural(world.getBlockState(ground).getBlock())) return null;
            for (int dy = -2; dy <= 5; dy++) {
                BlockPos at = top.add(dx, dy, dz);
                if (world.getTileEntity(at) != null) return null;
                if (dy >= 0 && !world.isAirBlock(at)) return null;
                if (dy < 0 && !world.isAirBlock(at)
                        && !natural(world.getBlockState(at).getBlock())) return null;
            }
        }
        // Floor occupies air ABOVE the ground, keeping the complete existing terrain intact.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            world.setBlockState(top.add(dx, 0, dz), Blocks.MOSSY_COBBLESTONE.getDefaultState(), 2);
        top = top.up();
        world.setBlockState(top, Blocks.ENCHANTING_TABLE.getDefaultState(), 2);
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
            world.setBlockState(top.add(dx, 0, dz), Blocks.GLOWSTONE.getDefaultState(), 2);
        ExileWorldData data = ExileWorldData.get(world);
        data.trials.setLong(key(top), top.toLong());
        data.markDirty();
        return top;
    }

    public static boolean isCompass(ItemStack stack) {
        return stack.getItem() == Items.COMPASS && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean("pm_exile_compass");
    }

    public static boolean currentCompass(net.minecraft.entity.player.EntityPlayer p, ItemStack stack) {
        int stage = ExileMode.state(p).getInteger("trial_route_completed");
        return isCompass(stack) && stage < 3 && stack.getTagCompound().getInteger("pm_trial_stage") == stage
                && (!stack.getTagCompound().hasUniqueId("pm_compass_owner")
                    || p.getUniqueID().equals(stack.getTagCompound().getUniqueId("pm_compass_owner")));
    }

    public static boolean hasCompass(EntityPlayerMP p) {
        int stage = completed(p);
        for (ItemStack stack : p.inventory.mainInventory) if (currentCompass(p, stack)) return true;
        return currentCompass(p, p.getHeldItemOffhand());
    }

    private static ItemStack createCompass(EntityPlayerMP p, int stage) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("pm_exile_compass", true);
        tag.setInteger("pm_trial_stage", stage);
        tag.setUniqueId("pm_compass_owner", p.getUniqueID());
        stack.setTagCompound(tag);
        stack.setStackDisplayName(new TextComponentTranslation("pocketmod.exile.compass_name")
                .getUnformattedText() + " " + (stage + 1));
        return stack;
    }

    public static void compass(EntityPlayerMP p) {
        if (!ExileMode.active(p) || p.dimension != 0) {
            ExileMode.message(p, "compass_overworld"); return;
        }
        plan(p.world);
        int stage = completed(p);
        if (stage >= 3) { ExileMode.message(p, "route_complete"); return; }
        if (!hasCompass(p)) {
            if (p.inventory.getFirstEmptyStack() < 0
                    || p.inventory.getFirstEmptyStack() >= ExileProgression.slots(p)) {
                ExileMode.message(p, "compass_space"); return;
            }
            double cost = Mana.shopPrice(p, ExileConfig.compassPrice);
            if (!Mana.spend(p, cost)) {
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int)Math.ceil(cost)), true);
                return;
            }
            Mana.consumeDiscount(p);
            ItemStack stack = createCompass(p, stage);
            if (!p.inventory.addItemStackToInventory(stack)) p.dropItem(stack, false);
        }
        refreshTarget(p);
        ExileMode.message(p, "compass_set");
    }

    private static void refreshTarget(EntityPlayerMP p) {
        int stage = completed(p);
        NBTTagCompound s = ExileMode.state(p);
        if (stage >= 3) { s.setBoolean("compass_target_set", false); return; }
        if (!hasCompass(p)) { s.setBoolean("compass_target_set", false); return; }
        NBTTagCompound route = ExileWorldData.get(p.world).route;
        if (!route.hasKey("target_" + stage)) return;
        s.setBoolean("compass_target_set", true);
        s.setLong("compass_target", route.getLong("target_" + stage));
    }

    @SubscribeEvent
    public static void protectCore(net.minecraftforge.event.world.BlockEvent.BreakEvent e) {
        if (e.getWorld().isRemote || e.getWorld().provider.getDimension() != 0
                || !ExileMode.enabled(e.getWorld()) || e.getPlayer().isCreative()) return;
        NBTTagCompound route = ExileWorldData.get(e.getWorld()).route;
        for (int i = 0; i < 3; i++) {
            if (route.getBoolean("built_" + i) && route.getLong("target_" + i) == e.getPos().toLong()) {
                e.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.RightClickBlock e) {
        if (e.isCanceled() || !(e.getEntityPlayer() instanceof EntityPlayerMP)
                || e.getHand() != EnumHand.MAIN_HAND
                || !ExileMode.active(e.getEntityPlayer())) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityPlayer();
        if (p.dimension != 0
                || !ExileWorldData.get(p.world).trials.hasKey(key(e.getPos()), 99)
                || ExileWorldData.get(p.world).trials.getLong(key(e.getPos()))
                        != e.getPos().toLong()) return;
        e.setCanceled(true);
        if (p.world.getBlockState(e.getPos()).getBlock() != Blocks.ENCHANTING_TABLE) return;
        NBTTagCompound s = ExileMode.state(p);
        if (s.getBoolean("trial_active") || s.getBoolean("trial_done_" + key(e.getPos()))) {
            ExileMode.message(p, "trial_used");
            return;
        }
        int stage = completed(p);
        if (stage >= 3 || !ExileWorldData.get(p.world).route.getBoolean("built_" + stage)
                || ExileWorldData.get(p.world).route.getLong("target_" + stage) != e.getPos().toLong()) {
            ExileMode.message(p, "trial_locked");
            return;
        }
        java.util.List<EntityLiving> guards = new java.util.ArrayList<>();
        int count = 0;
        double power = Math.min(30, s.getDouble("power"));
        for (int dx : new int[] {-2, 2}) {
            EntityLiving guard =
                    stage == 0 ? new EntityVex(p.world) : stage == 1 ? new EntitySkeleton(p.world) : new EntityWitherSkeleton(p.world);
            BlockPos feet = e.getPos().add(dx, 0, 2);
            guard.setPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            if (!PocketTeleporter.standable(p.world, feet)
                    || !p.world.getCollisionBoxes(guard, guard.getEntityBoundingBox()).isEmpty())
                { ExileMode.message(p, "trial_blocked"); return; }
            guard.onInitialSpawn(p.world.getDifficultyForLocation(feet), null);
            guard.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
                    .setBaseValue(24 + power * 2);
            guard.setHealth(guard.getMaxHealth());
            guard.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)
                    .setBaseValue(3 + power * 0.2);
            guard.setCustomNameTag(
                    new TextComponentTranslation("pocketmod.exile.guardian").getUnformattedText());
            guard.getEntityData().setBoolean(ExileMode.PURSUER, true);
            guard.getEntityData().setUniqueId("pm_exile_owner", p.getUniqueID());
            guard.getEntityData().setLong("pm_exile_birth", ExileWorldData.get(p.world).seconds);
            guard.getEntityData().setLong("pm_exile_trial", e.getPos().toLong());
            guard.setAttackTarget(p);
            ExileEncounters.protectFromSun(guard);
            guards.add(guard);
        }
        for (EntityLiving guard : guards) {
            if (!p.world.spawnEntity(guard)) {
                for (EntityLiving spawned : guards) spawned.setDead();
                ExileMode.message(p, "trial_blocked");
                return;
            }
            count++;
        }
        if (count == 0) {
            ExileMode.message(p, "trial_blocked");
            return;
        }
        s.setBoolean("trial_active", true);
        s.setInteger("trial_left", count);
        s.setLong("trial_pos", e.getPos().toLong());
        s.setLong("trial_started", ExileWorldData.get(p.world).seconds);
        ExileMode.message(p, "trial_started");
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void guardianDamage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        if (e.isCanceled() || e.getAmount() <= 0 || e.getEntityLiving().world.isRemote
                || !(e.getSource().getTrueSource() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP)e.getSource().getTrueSource();
        NBTTagCompound n = e.getEntityLiving().getEntityData();
        if (!n.hasKey("pm_exile_trial") || !p.getUniqueID().equals(n.getUniqueId("pm_exile_owner"))) return;
        n.setFloat("pm_trial_damage", n.getFloat("pm_trial_damage") + e.getAmount());
        n.setLong("pm_trial_hit", p.world.getTotalWorldTime());
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void guardianDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (e.isCanceled() || e.getEntityLiving().world.isRemote
                || !(e.getEntityLiving() instanceof EntityLiving)) return;
        EntityLiving mob = (EntityLiving) e.getEntityLiving();
        if (!mob.getEntityData().hasKey("pm_exile_trial")) return;
        EntityPlayerMP owner = mob.world.getMinecraftServer().getPlayerList()
                .getPlayerByUUID(mob.getEntityData().getUniqueId("pm_exile_owner"));
        if (owner == null || owner.dimension != mob.dimension || !ExileMode.active(owner)
                || owner.isCreative() || (owner.isSpectator() && !Spector.isActive(owner))) return;
        BlockPos arena = BlockPos.fromLong(ExileMode.state(owner).getLong("trial_pos"));
        if (arena.distanceSq(owner.posX, owner.posY, owner.posZ) > 80 * 80) return;
        // Completion has its own participation rule, independent of thirst/AI reachability.
        if (e.getSource().getTrueSource() == owner
                || (mob.getEntityData().getFloat("pm_trial_damage") >= Math.min(10, mob.getMaxHealth() * 0.3)
                    && mob.world.getTotalWorldTime() - mob.getEntityData().getLong("pm_trial_hit") <= 600))
            defeated(owner, mob);
    }

    public static void defeated(EntityPlayerMP p, EntityLiving mob) {
        NBTTagCompound s = ExileMode.state(p), n = mob.getEntityData();
        if (!n.hasKey("pm_exile_trial")
                || !s.getBoolean("trial_active")
                || n.getLong("pm_exile_trial") != s.getLong("trial_pos")
                || !p.getUniqueID().equals(n.getUniqueId("pm_exile_owner"))) return;
        if (n.getBoolean("pm_trial_counted")) return;
        n.setBoolean("pm_trial_counted", true);
        int left = s.getInteger("trial_left") - 1;
        s.setInteger("trial_left", left);
        if (left > 0) return;
        s.setBoolean("trial_active", false);
        s.setBoolean("trial_done_" + key(BlockPos.fromLong(s.getLong("trial_pos"))), true);
        int next = Math.min(3, completed(p) + 1);
        s.setInteger("trial_route_completed", next);
        if (next < 3) {
            // Physical guaranteed reward; the shop can restore it after loss.
            mob.entityDropItem(createCompass(p, next), 0.4f);
            s.setLong("compass_target", ExileWorldData.get(p.world).route.getLong("target_" + next));
        } else s.setBoolean("compass_target_set", false);
        ExileProgression.discover(p, mob);
        Mana.add(p, 100);
        ExileMode.message(p, "trial_complete");
    }

    private static void fail(EntityPlayerMP p) {
        NBTTagCompound s = ExileMode.state(p);
        if (p.dimension == 0) {
            for (EntityLiving mob :
                    p.world.getEntitiesWithinAABB(
                            EntityLiving.class,
                            p.getEntityBoundingBox().grow(128),
                            e ->
                                    e.getEntityData().hasKey("pm_exile_trial")
                                            && p.getUniqueID()
                                                    .equals(
                                                            e.getEntityData()
                                                                    .getUniqueId(
                                                                            "pm_exile_owner"))))
                mob.setDead();
        }
        s.setBoolean("trial_active", false);
        ExileMode.message(p, "trial_failed");
    }

    public static void tick(EntityPlayerMP p) {
        NBTTagCompound s = ExileMode.state(p);
        refreshTarget(p);
        if (!s.getBoolean("trial_active")) return;
        int stage = completed(p);
        if (stage >= 3 || ExileWorldData.get(p.world).route.getLong("target_" + stage) != s.getLong("trial_pos")) {
            fail(p); return;
        }
        BlockPos pos = BlockPos.fromLong(s.getLong("trial_pos"));
        if (p.dimension != 0
                || p.isDead
                || p.getHealth() <= 0
                || pos.distanceSq(p.posX, p.posY, p.posZ) > 80 * 80
                || ExileWorldData.get(p.world).seconds - s.getLong("trial_started") > 180) {
            fail(p);
            return;
        }
        int living =
                p.world
                        .getEntitiesWithinAABB(
                                EntityLiving.class,
                                p.getEntityBoundingBox().grow(96),
                                mob ->
                                        mob.isEntityAlive()
                                                && mob.getEntityData().hasKey("pm_exile_trial")
                                                && mob.getEntityData().getLong("pm_exile_trial")
                                                        == s.getLong("trial_pos")
                                                && p.getUniqueID()
                                                        .equals(
                                                                mob.getEntityData()
                                                                        .getUniqueId(
                                                                                "pm_exile_owner")))
                        .size();
        if (living < s.getInteger("trial_left")) fail(p);
    }
}
