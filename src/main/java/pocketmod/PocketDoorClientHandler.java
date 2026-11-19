package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class PocketDoorClientHandler implements IMessageHandler<PocketDoorNetwork.StateMessage, IMessage> {
    @Override
    public IMessage onMessage(final PocketDoorNetwork.StateMessage message, MessageContext context) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() {
                PocketDoorNetwork.setClientState(message.state);
            }
        });
        return null;
    }
}
