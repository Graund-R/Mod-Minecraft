package pocketmod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.GameType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import sun.misc.Unsafe;

/** Tests placement against an in-memory terrain fixture, without starting Minecraft. */
public final class TrialPlacementSmoke {
    static Unsafe unsafe;
    static class Terrain extends BloodthirstSmoke.TestWorld {
        Map<BlockPos, IBlockState> blocks;
        Chunk chunk;
        int writes;
        @Override public MapStorage getPerWorldStorage() { return storage; }
        @Override public Chunk getChunkFromBlockCoords(BlockPos pos) { return chunk; }
        @Override public BlockPos getHeight(BlockPos pos) { return new BlockPos(pos.getX(), 65, pos.getZ()); }
        @Override public IBlockState getBlockState(BlockPos pos) {
            IBlockState custom = blocks.get(pos);
            if (custom != null) return custom;
            return (pos.getY() >= 65 ? Blocks.AIR : pos.getY() == 64 ? Blocks.GRASS : Blocks.DIRT).getDefaultState();
        }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.AIR; }
        @Override public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos) { return null; }
        @Override public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type, AxisAlignedBB box) {
            return Collections.emptyList();
        }
        @Override public boolean setBlockState(BlockPos pos, IBlockState block, int flags) {
            if (!isAirBlock(pos)) throw new AssertionError("existing block overwritten at " + pos);
            blocks.put(pos.toImmutable(), block);
            writes++;
            return true;
        }
    }
    static Terrain terrain() throws Exception {
        Terrain world = (Terrain)unsafe.allocateInstance(Terrain.class);
        world.blocks = new HashMap<>();
        world.chunk = (Chunk)unsafe.allocateInstance(Chunk.class);
        world.storage = new MapStorage(null);
        world.info = new WorldInfo(new WorldSettings(1, GameType.SURVIVAL, true, false,
                WorldType.parseWorldType("exile")), "test");
        BloodthirstSmoke.field(world, World.class, "provider", new WorldProviderSurface());
        return world;
    }
    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (Unsafe)f.get(null);
        Bootstrap.register();
        new WorldType("exile");
        Method build = ExileTrials.class.getDeclaredMethod("build", World.class, int.class, int.class);
        build.setAccessible(true);
        Terrain clean = terrain();
        BlockPos target = (BlockPos)build.invoke(null, clean, 8, 8);
        BloodthirstSmoke.check(target != null && clean.writes == 54, "flat clear site builds without replacing terrain");
        BloodthirstSmoke.check(ExileWorldData.get(clean).trials.hasKey("8_8"), "built site is registered");
        Terrain occupied = terrain();
        occupied.blocks.put(new BlockPos(8, 66, 8), Blocks.PLANKS.getDefaultState());
        BloodthirstSmoke.check(build.invoke(null, occupied, 8, 8) == null && occupied.writes == 0,
                "occupied volume rejected atomically");
        Terrain floor = terrain();
        floor.blocks.put(new BlockPos(10, 64, 10), Blocks.COBBLESTONE.getDefaultState());
        BloodthirstSmoke.check(build.invoke(null, floor, 8, 8) == null && floor.writes == 0,
                "constructed foundation rejected atomically");
        Terrain lived = terrain();
        lived.chunk.setInhabitedTime(201);
        BloodthirstSmoke.check(build.invoke(null, lived, 8, 8) == null && lived.writes == 0,
                "inhabited sites rejected without changes");
        System.out.println("TrialPlacementSmoke: 5 placement checks passed");
    }
}
