package pocketmod;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DimensionType;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = PocketMod.MODID, name = "Pocket Mod", version = "2.13", acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "pocketmod.ConfigGuiFactory")
public class PocketMod {

    public static final String MODID = "pocketmod";

    @Mod.Instance(MODID)
    public static PocketMod instance;
    @SidedProxy(clientSide = "pocketmod.ClientProxy", serverSide = "pocketmod.CommonProxy")
    public static CommonProxy proxy;

    public static final int DIM_ID = 100;
    public static final int GUI_WAREHOUSE = 23;
    public static DimensionType POCKET_TYPE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PocketConfig.load(event.getSuggestedConfigurationFile());
        ExileMode.register();
        NetworkHandler.register();
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, new PocketGuiHandler());
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(this, new net.minecraftforge.common.ForgeChunkManager.LoadingCallback() {
            @Override
            public void ticketsLoaded(java.util.List<net.minecraftforge.common.ForgeChunkManager.Ticket> tickets, net.minecraft.world.World world) {
                // старые билеты снимаем: PocketKeep выдаст новые, когда мир кармана начнёт тикать
                for (net.minecraftforge.common.ForgeChunkManager.Ticket t : tickets) {
                    net.minecraftforge.common.ForgeChunkManager.releaseTicket(t);
                }
            }
        });
        POCKET_TYPE = DimensionType.register("pocket", "_pocket", DIM_ID, PocketWorldProvider.class, true);
        DimensionManager.registerDimension(DIM_ID, POCKET_TYPE);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "decoy"), EntityDecoy.class, "decoy", 0, this, 64, 3, false);
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MorphIntegration.register();
        PocketDoorNetwork.registerServer();
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // к этому моменту все моды уже зарегистрировали свои руды в OreDictionary
        PocketConfig.finishOreTargets();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        PocketDimensionManager.load(event.getServer().getWorld(0));
        event.registerServerCommand(new CommandExile());
        event.registerServerCommand(new CommandManaAdd());
        event.registerServerCommand(new CommandPocketDebug());
    }
}
