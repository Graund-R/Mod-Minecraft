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
                if (PocketDoorNetwork.consumeMorphCatalogPending()) {
                    if (message.state.getBoolean("morphismUnlocked")
                            && message.state.getBoolean("morphismAvailable")) {
                        Minecraft.getMinecraft().displayGuiScreen(
                                new MorphCatalogGui(Minecraft.getMinecraft().player));
                    } else if (Minecraft.getMinecraft().player != null) {
                        String key = message.state.getBoolean("morphismAvailable")
                                ? "pocketmod.msg.need_shop" : "pocketmod.msg.morph_need_metamorph";
                        Minecraft.getMinecraft().player.sendStatusMessage(
                                new net.minecraft.util.text.TextComponentTranslation(key), true);
                    }
                }
            }
        });
        return null;
    }
}
