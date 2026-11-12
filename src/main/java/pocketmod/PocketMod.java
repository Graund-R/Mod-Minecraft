package pocketmod;

import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = PocketMod.MODID, name = "Pocket Mod", version = "0.4", acceptedMinecraftVersions = "[1.12.2]")
public class PocketMod {

    public static final String MODID = "pocketmod";
    public static final int DIM_ID = 100;
    public static DimensionType POCKET_TYPE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PocketConfig.load(event.getSuggestedConfigurationFile());
        POCKET_TYPE = DimensionType.register("pocket", "_pocket", DIM_ID, PocketWorldProvider.class, true);
        DimensionManager.registerDimension(DIM_ID, POCKET_TYPE);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            ClientHandler.register();
        }
    }
}
