package pocketmod;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class PocketWorldProvider extends WorldProvider {

    @Override
    public DimensionType getDimensionType() {
        return PocketDimensionManager.typeFor(getDimension());
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new PocketChunkGenerator(world);
    }

    /** Карман не выгружается, пока в него никто не зашёл: печки и т.п. продолжают работать. */
    @Override
    public boolean canDropChunk(int x, int z) {
        return false;
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return false;
    }

    /** Нет неба как источника — яркость задаём таблицей (всегда день). */
    @Override
    public boolean hasSkyLight() {
        return false;
    }

    /** Всегда как днём: любой уровень света рисуется максимально ярким. */
    @Override
    protected void generateLightBrightnessTable() {
        for (int i = 0; i < 16; i++) {
            this.lightBrightnessTable[i] = 1.0F;
        }
    }

    /** Вечный полдень. */
    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        return 0.0F;
    }
}
