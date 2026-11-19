package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class PocketDoorClientHandler {
    private PocketDoorClientHandler() {}

    /** Called reflectively by the common packet relay; always schedules onto the client thread. */
    public static void receive(final NBTTagCompound state) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() {
                PocketDoorNetwork.setClientState(state);
                if (PocketDoorNetwork.consumeTotemGuiPending()) {
                    if (state.getBoolean("totemUnlocked")) {
                        Minecraft.getMinecraft().displayGuiScreen(
                                new TotemGui(Minecraft.getMinecraft().player));
                    } else if (Minecraft.getMinecraft().player != null) {
                        Minecraft.getMinecraft().player.sendStatusMessage(
                                new net.minecraft.util.text.TextComponentTranslation("pocketmod.msg.need_shop"), true);
                    }
                }
                if (PocketDoorNetwork.consumeMorphCatalogPending()) {
                    if (state.getBoolean("morphismUnlocked")
                            && state.getBoolean("morphismAvailable")) {
                        Minecraft.getMinecraft().displayGuiScreen(
                                new MorphCatalogGui(Minecraft.getMinecraft().player));
                    } else if (Minecraft.getMinecraft().player != null) {
                        String key = state.getBoolean("morphismAvailable")
                                ? "pocketmod.msg.need_shop" : "pocketmod.msg.morph_need_metamorph";
                        Minecraft.getMinecraft().player.sendStatusMessage(
                                new net.minecraft.util.text.TextComponentTranslation(key), true);
                    }
                }
            }
        });
    }
}
