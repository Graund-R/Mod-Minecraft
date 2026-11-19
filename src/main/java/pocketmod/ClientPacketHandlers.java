package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Обработчики входящих пакетов клиента изолированы от dedicated server classloader. */
@SideOnly(Side.CLIENT)
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void register() {
        NetworkHandler.CHANNEL_INSTANCE.registerMessage(SyncHandler.class,
                NetworkHandler.SyncMessage.class, 0, Side.CLIENT);
        NetworkHandler.CHANNEL_INSTANCE.registerMessage(PocketSyncHandler.class,
                NetworkHandler.PocketSyncMessage.class, 2, Side.CLIENT);
        NetworkHandler.CHANNEL_INSTANCE.registerMessage(PromptHandler.class,
                NetworkHandler.PromptMessage.class, 3, Side.CLIENT);
    }

    public static class PromptHandler implements IMessageHandler<NetworkHandler.PromptMessage, IMessage> {
        @Override public IMessage onMessage(NetworkHandler.PromptMessage message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                if (mc.player != null && message.kind == 1) {
                    mc.displayGuiScreen(new DeathTotemGui(message.a, message.b));
                }
            });
            return null;
        }
    }

    public static class SyncHandler implements IMessageHandler<NetworkHandler.SyncMessage, IMessage> {
        @Override public IMessage onMessage(NetworkHandler.SyncMessage message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player != null && message.data != null) {
                    PlayerData.replace(player, message.data);
                    JmHook.fire();
                }
            });
            return null;
        }
    }

    public static class PocketSyncHandler implements IMessageHandler<NetworkHandler.PocketSyncMessage, IMessage> {
        @Override public IMessage onMessage(NetworkHandler.PocketSyncMessage message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                if (mc.world != null && PocketDimensionManager.isPocketDimension(message.dimension)
                        && mc.world.provider.getDimension() == message.dimension) {
                    PocketData.get(mc.world).readFromNBT(message.data);
                }
            });
            return null;
        }
    }
}
