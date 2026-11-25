package pocketmod;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Клиентская реализация sided proxy. Загружается только на физическом клиенте. */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override public void preInit() {
        ClientHandler.registerRenderers();
    }

    @Override public void init() {
        ClientHandler.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ModConfigEntry());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new DashVisuals());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TeleportVisuals());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TemporalClient());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new CreativeInventoryButton());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ExileClient());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ExileDebugClient());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ExileGrayClient());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ExileAtmosphereClient());
        Tr.ensureLoaded();
        if (net.minecraft.client.Minecraft.getMinecraft().getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager)
            ((net.minecraft.client.resources.IReloadableResourceManager)net.minecraft.client.Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(manager -> Tr.ensureLoaded());
    }

    @Override public void openTemporalPanel() { net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new TemporalGui()); }

    @Override public void registerClientMessages() {
        ClientPacketHandlers.register();
    }
}
