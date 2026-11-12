package pocketmod;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientHandler {

    private static KeyBinding pocketKey;

    public static void register() {
        pocketKey = new KeyBinding("key.pocketmod.pocket", Keyboard.KEY_K, "key.categories.pocketmod");
        ClientRegistry.registerKeyBinding(pocketKey);
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!pocketKey.isPressed()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !mc.isIntegratedServerRunning()) return;

        IntegratedServer server = mc.getIntegratedServer();
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if (player != null) {
            server.addScheduledTask(() -> PocketTeleporter.toggle(player));
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI || !mc.isIntegratedServerRunning()) return;

        IntegratedServer server = mc.getIntegratedServer();
        EntityPlayerMP sp = server.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if (sp == null) return;

        double mana = Mana.get(sp);
        int x = 10, y = 10, w = 100, h = 8;
        double fill = Math.min(1.0, mana / PocketConfig.hudBarScale);
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        Gui.drawRect(x, y, x + (int) (w * fill), y + h, 0xFF55AAFF);
        mc.fontRenderer.drawStringWithShadow(String.valueOf((int) mana), x + w + 6, y, 0xFFFFFF);

        if (mc.player.dimension == PocketMod.DIM_ID
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                && ManaItems.isManaItem(mc.player.getHeldItemMainhand())) {
            WorldServer pocketWorld = server.getWorld(PocketMod.DIM_ID);
            PocketData d = PocketData.get(pocketWorld);
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            if (Pocket.isPayable(d, pos)) {
                int cost = Pocket.cost(pos);
                int paid = d.paid.containsKey(pos) ? d.paid.get(pos) : 0;
                ScaledResolution res = event.getResolution();
                int cx = res.getScaledWidth() / 2;
                int cy = res.getScaledHeight() / 2 + 14;
                Gui.drawRect(cx - 31, cy - 1, cx + 31, cy + 7, 0xAA000000);
                Gui.drawRect(cx - 30, cy, cx - 30 + (int) (60.0 * paid / cost), cy + 6, 0xFFFFD34D);
                String t = paid + " / " + cost;
                mc.fontRenderer.drawStringWithShadow(t, cx - mc.fontRenderer.getStringWidth(t) / 2, cy + 10, 0xFFFFFF);
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
