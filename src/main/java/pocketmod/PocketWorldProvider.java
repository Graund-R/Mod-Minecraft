package pocketmod;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class PocketWorldProvider extends WorldProvider {

    @Override
    public DimensionType getDimensionType() {
        return PocketMod.POCKET_TYPE;
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new PocketChunkGenerator(world);
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return false;
    }
}
