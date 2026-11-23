package pocketmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
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

/** Первое испытание использует ванильные блоки и стражей. Источники отмечены в данных мира. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileTrials implements IWorldGenerator {
    private static final int GRID = 12;

    private static int coordinate(int cell) {
        return (cell * GRID + GRID / 2) * 16 + 8;
    }

    private static String key(BlockPos p) {
        return p.getX() + "_" + p.getZ();
    }

    @Override
    public void generate(
            Random random,
            int cx,
            int cz,
            World world,
            IChunkGenerator generator,
            IChunkProvider provider) {
        if (world.provider.getDimension() == 0
                && Math.floorMod(cx, GRID) == GRID / 2
                && Math.floorMod(cz, GRID) == GRID / 2
                && ExileMode.enabled(world)) build(world, cx * 16 + 8, cz * 16 + 8);
    }

    private static BlockPos build(World world, int x, int z) {
        ExileWorldData data = ExileWorldData.get(world);
        String key = x + "_" + z;
        if (data.trials.hasKey(key, 99)) return BlockPos.fromLong(data.trials.getLong(key));
        BlockPos top = world.getHeight(new BlockPos(x, 0, z));
        if (top.getY() < 2 || top.getY() > 240) return null;
        // Вся площадка лежит внутри одного чанка. Существующие блоки с данными сохраняются.
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = -1; dy <= 3; dy++)
                    if (world.getTileEntity(top.add(dx, dy, dz)) != null) return null;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                world.setBlockState(
                        top.add(dx, -1, dz), Blocks.MOSSY_COBBLESTONE.getDefaultState(), 2);
                for (int dy = 0; dy <= 3; dy++) world.setBlockToAir(top.add(dx, dy, dz));
            }
        world.setBlockState(top, Blocks.ENCHANTING_TABLE.getDefaultState(), 2);
        for (int dx : new int[] {-3, 3})
            for (int dz : new int[] {-3, 3})
                world.setBlockState(top.add(dx, 0, dz), Blocks.GLOWSTONE.getDefaultState(), 2);
        data.trials.setLong(key, top.toLong());
        data.markDirty();
        return top;
    }

    public static boolean hasCompass(EntityPlayerMP p) {
        for (ItemStack stack : p.inventory.mainInventory) if (isCompass(stack)) return true;
        return isCompass(p.getHeldItemOffhand());
    }

    public static boolean isCompass(ItemStack stack) {
        return stack.getItem() == Items.COMPASS
                && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean("pm_exile_compass");
    }

    public static void compass(EntityPlayerMP p) {
        if (!ExileMode.active(p) || p.dimension != 0) {
            ExileMode.message(p, "compass_overworld");
            return;
        }
        if (!hasCompass(p)) {
            if (p.inventory.getFirstEmptyStack() < 0
                    || p.inventory.getFirstEmptyStack() >= ExileProgression.slots(p)) {
                ExileMode.message(p, "compass_space");
                return;
            }
            double cost = Mana.shopPrice(p, ExileConfig.compassPrice);
            if (Mana.get(p) < cost) {
                p.sendStatusMessage(
                        new TextComponentTranslation(
                                "pocketmod.msg.no_mana", (int) Math.ceil(cost)),
                        true);
                return;
            }
            BlockPos target = find(p);
            if (target == null) {
                ExileMode.message(p, "compass_missing");
                return;
            }
            if (!Mana.spend(p, cost)) return;
            Mana.consumeDiscount(p);
            ItemStack stack = new ItemStack(Items.COMPASS);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("pm_exile_compass", true);
            stack.setTagCompound(tag);
            stack.setStackDisplayName(
                    new TextComponentTranslation("pocketmod.exile.compass_name")
                            .getUnformattedText());
            if (!p.inventory.addItemStackToInventory(stack)) p.dropItem(stack, false);
        } else if (find(p) == null) ExileMode.message(p, "compass_missing");
    }

    private static BlockPos find(EntityPlayerMP p) {
        int cellX = Math.floorDiv(p.getPosition().getX(), GRID * 16);
        int cellZ = Math.floorDiv(p.getPosition().getZ(), GRID * 16);
        List<BlockPos> candidates = new ArrayList<>();
        NBTTagCompound s = ExileMode.state(p);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos point = new BlockPos(coordinate(cellX + dx), 0, coordinate(cellZ + dz));
                if (!s.getBoolean("trial_done_" + key(point))) candidates.add(point);
            }
        candidates.sort(Comparator.comparingDouble(point -> point.distanceSq(p.posX, 0, p.posZ)));
        int attempts = 0;
        for (BlockPos point : candidates) {
            if (++attempts > 4) break;
            // Ограниченный поиск. Генерируется только ближайший выбранный источник.
            BlockPos target = build(p.world, point.getX(), point.getZ());
            if (target == null) continue;
            s.setLong("compass_target", target.toLong());
            s.setBoolean("compass_target_set", true);
            ExileMode.message(p, "compass_set");
            return target;
        }
        return null;
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntityPlayer() instanceof EntityPlayerMP)
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
        int count = 0;
        double power = Math.min(30, s.getDouble("power"));
        for (int dx : new int[] {-2, 2}) {
            EntityLiving guard =
                    power >= 8 ? new EntitySkeleton(p.world) : new EntityZombie(p.world);
            BlockPos feet = e.getPos().add(dx, 0, 2);
            guard.setPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            if (!PocketTeleporter.standable(p.world, feet)
                    || !p.world.getCollisionBoxes(guard, guard.getEntityBoundingBox()).isEmpty())
                continue;
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
            if (p.world.spawnEntity(guard)) count++;
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

    public static void defeated(EntityPlayerMP p, EntityLiving mob) {
        NBTTagCompound s = ExileMode.state(p), n = mob.getEntityData();
        if (!n.hasKey("pm_exile_trial")
                || !s.getBoolean("trial_active")
                || n.getLong("pm_exile_trial") != s.getLong("trial_pos")
                || !p.getUniqueID().equals(n.getUniqueId("pm_exile_owner"))) return;
        int left = s.getInteger("trial_left") - 1;
        s.setInteger("trial_left", left);
        if (left > 0) return;
        s.setBoolean("trial_active", false);
        s.setBoolean("trial_done_" + key(BlockPos.fromLong(s.getLong("trial_pos"))), true);
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
        if (!s.getBoolean("trial_active")) return;
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
