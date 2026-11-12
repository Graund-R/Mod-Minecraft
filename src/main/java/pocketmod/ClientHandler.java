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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientHandler {

    private static KeyBinding keyPocket;
    private static KeyBinding keyTeleport;
    private static KeyBinding keyInvis;
    private static KeyBinding keySlow;

    private static boolean wasG, wasV, wasZ;
    private static long downG, downV, downZ;
    private static boolean holdG, holdV, holdZ;

    public static void register() {
        keyPocket   = new KeyBinding("key.pocketmod.pocket",   Keyboard.KEY_K, "key.categories.pocketmod");
        keyTeleport = new KeyBinding("key.pocketmod.teleport", Keyboard.KEY_G, "key.categories.pocketmod");
        keyInvis    = new KeyBinding("key.pocketmod.invis",    Keyboard.KEY_V, "key.categories.pocketmod");
        keySlow     = new KeyBinding("key.pocketmod.slow",     Keyboard.KEY_Z, "key.categories.pocketmod");
        ClientRegistry.registerKeyBinding(keyPocket);
        ClientRegistry.registerKeyBinding(keyTeleport);
        ClientRegistry.registerKeyBinding(keyInvis);
        ClientRegistry.registerKeyBinding(keySlow);
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
    }

    private static EntityPlayerMP sp() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !mc.isIntegratedServerRunning()) return null;
        IntegratedServer s = mc.getIntegratedServer();
        return s == null ? null : s.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
    }

    private static void server(Runnable r) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null)
            mc.getIntegratedServer().addScheduledTask(r);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (mc.currentScreen != null) {
            wasG = wasV = wasZ = false;
            downG = downV = downZ = 0;
            holdG = holdV = holdZ = false;
            return;
        }

        long now = System.currentTimeMillis();
        int holdMs = PocketConfig.holdMs;

        boolean g = keyTeleport.isKeyDown();
        if (g && !wasG) { downG = now; holdG = false; }
        if (g && !holdG && downG > 0 && now - downG >= holdMs) {
            holdG = true;
            mc.player.sendStatusMessage(new TextComponentString("Totem (soon)"), true);
        }
        if (!g && wasG) {
            if (!holdG && downG > 0) {
                EntityPlayerMP p = sp();
                if (p != null) server(() -> Abilities.teleportLook(p));
            }
            downG = 0; holdG = false;
        }
        wasG = g;

        boolean v = keyInvis.isKeyDown();
        if (v && !wasV) { downV = now; holdV = false; }
        if (v && !holdV && downV > 0 && now - downV >= holdMs) {
            holdV = true;
            mc.player.sendStatusMessage(new TextComponentString("Shop (soon)"), true);
        }
        if (!v && wasV) {
            if (!holdV && downV > 0) {
                EntityPlayerMP p = sp();
                if (p != null) server(() -> Abilities.toggleInvisibility(p));
            }
            downV = 0; holdV = false;
        }
        wasV = v;

        boolean z = keySlow.isKeyDown();
        if (z && !wasZ) { downZ = now; holdZ = false; }
        if (z && !holdZ && downZ > 0 && now - downZ >= holdMs) {
            holdZ = true;
        }
        if (!z && wasZ) {
            if (!holdZ && downZ > 0) {
                EntityPlayerMP p = sp();
                if (p != null) server(() -> Abilities.toggleSlow(p));
            }
            downZ = 0; holdZ = false;
        }
        wasZ = z;

        if (keyPocket.isPressed()) {
            EntityPlayerMP p = sp();
            if (p != null) server(() -> PocketTeleporter.toggle(p));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!keySlow.isKeyDown() || !holdZ) return;
        int wheel = event.getDwheel();
        if (wheel == 0) return;
        event.setCanceled(true);
        EntityPlayerMP p = sp();
        if (p == null) return;
        float cur = Abilities.getSlowFactor(p);
        final float next = Math.max(0.1f, Math.min(0.75f, cur + (wheel > 0 ? 0.05f : -0.05f)));
        server(() -> {
            Abilities.setSlowFactor(p, next);
            p.sendStatusMessage(new TextComponentString(String.format("Slow: x%.2f", next)), true);
        });
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
        double fill = Math.min(1.0, mana / Math.max(1, PocketConfig.hudBarScale));
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        Gui.drawRect(x, y, x + (int) (w * fill), y + h, 0xFF55AAFF);
        mc.fontRenderer.drawStringWithShadow(String.valueOf((int) mana), x + w + 6, y, 0xFFFFFF);

        if (Abilities.isSlow(sp)) {
            float factor = Abilities.getSlowFactor(sp);
            float intensity = (1.0f - factor) / 0.9f;
            intensity = Math.min(1.0f, Math.max(0.15f, intensity));
            drawSlowVignette(event.getResolution(), intensity);
        }

        if (mc.player.dimension == PocketMod.DIM_ID
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                && ManaItems.isManaItem(mc.player.getHeldItemMainhand())) {
            WorldServer pw = server.getWorld(PocketMod.DIM_ID);
            PocketData d = PocketData.get(pw);
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            if (Pocket.isPayable(d, pos)) {
                int cost = Pocket.cost(pos);
                int paid = d.paid.containsKey(pos) ? d.paid.get(pos) : 0;
                ScaledResolution res = event.getResolution();
                int cx = res.getScaledWidth() / 2;
                int cy = res.getScaledHeight() / 2 + 14;
                Gui.drawRect(cx - 31, cy - 1, cx + 31, cy + 7, 0xAA000000);
                Gui.drawRect(cx - 30, cy, cx - 30 + (int) (60.0 * paid / Math.max(1, cost)), cy + 6, 0xFFFFD34D);
                String t = paid + " / " + cost;
                mc.fontRenderer.drawStringWithShadow(t, cx - mc.fontRenderer.getStringWidth(t) / 2, cy + 10, 0xFFFFFF);
            }
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawSlowVignette(ScaledResolution res, float intensity) {
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();
        int edge = (int) (40 + intensity * 80);
        int alpha = (int) (0x40 + intensity * 0x90);
        if (alpha > 220) alpha = 220;
        int color = (alpha << 24);

        Gui.drawRect(0, 0, sw, edge, color);
        Gui.drawRect(0, sh - edge, sw, sh, color);
        Gui.drawRect(0, edge, edge, sh - edge, color);
        Gui.drawRect(sw - edge, edge, sw, sh - edge, color);

        int edge2 = edge / 2;
        int alpha2 = alpha / 2;
        int color2 = (alpha2 << 24);
        Gui.drawRect(edge, edge, sw - edge, edge + edge2, color2);
        Gui.drawRect(edge, sh - edge - edge2, sw - edge, sh - edge, color2);
        Gui.drawRect(edge, edge + edge2, edge + edge2, sh - edge - edge2, color2);
        Gui.drawRect(sw - edge - edge2, edge + edge2, sw - edge, sh - edge - edge2, color2);
    }
}
