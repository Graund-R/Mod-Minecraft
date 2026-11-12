package pocketmod;

import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/**
 * Логика карманного измерения: построение стартовой комнаты 5x5,
 * кольца стен, оплата и расширение.
 */
public class Pocket {

    // Стартовая комната (центр около 0, 64, 0)
    public static final int X_MIN = 0, X_MAX = 4;
    public static final int Y_MIN = 63, Y_MAX = 67;
    public static final int Z_MIN = 0, Z_MAX = 4;

    public static void buildIfNeeded(WorldServer world) {
        PocketData d = PocketData.get(world);
        if (d.built) {
            return;
        }

        // Пол, потолок, стены из конфиг-блока
        for (int x = X_MIN - 1; x <= X_MAX + 1; x++) {
            for (int y = Y_MIN - 1; y <= Y_MAX + 1; y++) {
                for (int z = Z_MIN - 1; z <= Z_MAX + 1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    boolean edge = x < X_MIN || x > X_MAX || y < Y_MIN || y > Y_MAX || z < Z_MIN || z > Z_MAX;
                    if (edge) {
                        world.setBlockState(p, PocketConfig.wallState(), 2);
                        d.walls.add(p);
                    } else {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                        d.interior.add(p);
                    }
                }
            }
        }
        d.built = true;
        d.markDirty();
    }

    public static int ring(BlockPos p) {
        int dx = Math.max(Math.max(X_MIN - p.getX(), p.getX() - X_MAX), 0);
        int dy = Math.max(Math.max(Y_MIN - p.getY(), p.getY() - Y_MAX), 0);
        int dz = Math.max(Math.max(Z_MIN - p.getZ(), p.getZ() - Z_MAX), 0);
        return Math.max(dx, Math.max(dy, dz));
    }

    public static int cost(BlockPos p) {
        return PocketConfig.expandCost(ring(p));
    }

    public static boolean isPayable(PocketData d, BlockPos p) {
        if (!d.walls.contains(p)) {
            return false;
        }
        if (p.getY() < 2 || p.getY() > 253) {
            return false;
        }
        for (EnumFacing f : EnumFacing.values()) {
            if (d.interior.contains(p.offset(f))) {
                return true;
            }
        }
        return false;
    }

    public static void open(WorldServer w, PocketData d, BlockPos p) {
        d.walls.remove(p);
        d.paid.remove(p);
        d.interior.add(p);
        w.setBlockState(p, Blocks.AIR.getDefaultState(), 3);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = p.add(dx, dy, dz);
                    if (n.equals(p) || n.getY() < 0 || n.getY() > 255) {
                        continue;
                    }
                    if (d.interior.contains(n) || d.walls.contains(n)) {
                        continue;
                    }
                    if (w.isAirBlock(n)) {
                        w.setBlockState(n, PocketConfig.wallState(), 2);
                        d.walls.add(n);
                    }
                }
            }
        }
        d.markDirty();
    }
}
