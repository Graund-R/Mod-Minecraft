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
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ExileClient());
    }

    @Override public void registerClientMessages() {
        ClientPacketHandlers.register();
    }
}
