package pocketmod;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Серверная реализация sided proxy. Не содержит ссылок на клиентские классы Minecraft. */
public class CommonProxy {
    public void preInit() {}
    public void init() {}

    public void registerClientMessages() {
        NetworkHandler.CHANNEL_INSTANCE.registerMessage(IgnoreSyncHandler.class,
                NetworkHandler.SyncMessage.class, 0, net.minecraftforge.fml.relauncher.Side.CLIENT);
        NetworkHandler.CHANNEL_INSTANCE.registerMessage(IgnorePocketSyncHandler.class,
                NetworkHandler.PocketSyncMessage.class, 2, net.minecraftforge.fml.relauncher.Side.CLIENT);
        NetworkHandler.CHANNEL_INSTANCE.registerMessage(IgnorePromptHandler.class,
                NetworkHandler.PromptMessage.class, 3, net.minecraftforge.fml.relauncher.Side.CLIENT);
    }

    public static class IgnorePromptHandler implements IMessageHandler<NetworkHandler.PromptMessage, IMessage> {
        @Override public IMessage onMessage(NetworkHandler.PromptMessage message, MessageContext ctx) { return null; }
    }

    public static class IgnoreSyncHandler implements IMessageHandler<NetworkHandler.SyncMessage, IMessage> {
        @Override public IMessage onMessage(NetworkHandler.SyncMessage message, MessageContext ctx) { return null; }
    }

    public static class IgnorePocketSyncHandler implements IMessageHandler<NetworkHandler.PocketSyncMessage, IMessage> {
        @Override public IMessage onMessage(NetworkHandler.PocketSyncMessage message, MessageContext ctx) { return null; }
    }
}
