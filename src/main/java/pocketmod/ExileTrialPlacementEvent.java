package pocketmod;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Pack integrations may veto a candidate before any block is placed. */
@Cancelable
public final class ExileTrialPlacementEvent extends Event {
    public final World world;
    public final BlockPos origin;
    public ExileTrialPlacementEvent(World world, BlockPos origin) {
        this.world = world;
        this.origin = origin;
    }
}
