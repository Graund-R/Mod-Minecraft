package pocketmod;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
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

    /** Состояние одной клавиши «нажатие / удержание». */
    private static class Hold {
        boolean was;
        boolean held;
        boolean wait; // ждём отпускания (после закрытия окна)
        long down;
    }

    private static final Hold hG = new Hold();
    private static final Hold hV = new Hold();
    private static final Hold hZ = new Hold();

    private static final int EV_NONE = 0, EV_HOLD = 1, EV_TAP = 2;

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

    /** Серверный (настоящий) объект игрока во встроенном сервере. */
    public static EntityPlayerMP sp() {
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

    private static void resetHold(Hold h) {
        h.was = false;
        h.held = false;
        h.down = 0;
        h.wait = true;
    }

    private static int poll(boolean down, Hold h, long now, int holdMs) {
        if (h.wait) {
            if (!down) {
                h.wait = false;
            } else {
                down = false;
            }
        }
        int ev = EV_NONE;
        if (down && !h.was) {
            h.down = now;
            h.held = false;
        }
        if (down && !h.held && h.down > 0 && now - h.down >= holdMs) {
            h.held = true;
            ev = EV_HOLD;
        }
        if (!down && h.was) {
            if (!h.held && h.down > 0) {
                ev = EV_TAP;
            }
            h.down = 0;
            h.held = false;
        }
        h.was = down;
        return ev;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        TotemMarkers.tick(mc);

        // Пока открыто окно — клавиши молчат, а после закрытия ждём отпускания
        if (mc.currentScreen != null) {
            resetHold(hG);
            resetHold(hV);
            resetHold(hZ);
            return;
        }

        long now = System.currentTimeMillis();
        int holdMs = PocketConfig.holdMs;

        boolean gDown = keyTeleport.isKeyDown();
        // Shift+G — окно тотемов (сразу при нажатии)
        if (gDown && !hG.was && !hG.wait && GuiScreen.isShiftKeyDown()) {
            EntityPlayerMP sp0 = sp();
            if (sp0 != null && !AbilityStorage.isUnlocked(sp0, AbilityType.TOTEM)) {
                mc.player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
                hG.wait = true;
            } else {
                mc.displayGuiScreen(new TotemGui(mc.player));
            }
            return;
        }

        int g = poll(gDown, hG, now, holdMs);
        if (g == EV_HOLD) {
            // удержание G — быстрый телепорт к последнему тотему
            EntityPlayerMP p = sp();
            if (p != null) server(() -> Totems.teleportLast(p));
        } else if (g == EV_TAP) {
            EntityPlayerMP p = sp();
            if (p != null) server(() -> Abilities.teleportLook(p));
        }

        int v = poll(keyInvis.isKeyDown(), hV, now, holdMs);
        if (v == EV_HOLD) {
            mc.displayGuiScreen(new ShopGui(mc.player));
        } else if (v == EV_TAP) {
            EntityPlayerMP p = sp();
            if (p != null) server(() -> Abilities.toggleInvisibility(p));
        }

        int z = poll(keySlow.isKeyDown(), hZ, now, holdMs);
        if (z == EV_TAP) {
            EntityPlayerMP p = sp();
            if (p != null) server(() -> Abilities.toggleSlow(p));
        }

        if (keyPocket.isPressed()) {
            EntityPlayerMP p = sp();
            if (p != null) server(() -> PocketTeleporter.toggle(p));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!hZ.held || !keySlow.isKeyDown()) return;
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

    /** Длина шкалы: цена самого дорогого уже открытого действия (пока ничего нет — hudBarScale, по умолчанию 100). */
    private static double barScale(EntityPlayerMP sp) {
        double max = Math.max(1, PocketConfig.hudBarScale);
        for (AbilityType t : AbilityType.values()) {
            int lvl = AbilityStorage.getLevel(sp, t);
            if (lvl > 0 && lvl <= t.levelCosts.length) {
                max = Math.max(max, t.levelCosts[lvl - 1]);
            }
        }
        return max;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI || !mc.isIntegratedServerRunning()) return;

        try {
            EntityPlayerMP sp = sp();
            if (sp == null) return;

            double mana = Mana.get(sp);
            int x = 10, y = 10, w = 100, h = 8;
            double fill = Math.min(1.0, mana / barScale(sp));
            Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
            Gui.drawRect(x, y, x + (int) (w * fill), y + h, 0xFF55AAFF);
            mc.fontRenderer.drawStringWithShadow(String.valueOf((long) mana), x + w + 6, y, 0xFFFFFF);

            if (Abilities.isSlow(sp)) {
                float factor = Abilities.getSlowFactor(sp);
                float intensity = (1.0f - factor) / 0.9f;
                intensity = Math.min(1.0f, Math.max(0.15f, intensity));
                drawSlowVignette(event.getResolution(), intensity);
            }

            ItemStack held = mc.player.getHeldItemMainhand();
            if (mc.player.dimension == PocketMod.DIM_ID
                    && mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                    && ManaItems.isManaItem(held)) {
                IntegratedServer server = mc.getIntegratedServer();
                WorldServer pw = server.getWorld(PocketMod.DIM_ID);
                PocketData d = PocketData.get(pw);
                BlockPos pos = mc.objectMouseOver.getBlockPos();
                if (Pocket.isPayable(d, pos)) {
                    double cost = Pocket.costMana(pos, sp);
                    Double pd = d.paid.get(pos);
                    double paid = pd == null ? 0.0 : pd.doubleValue();
                    int itemValue = Math.max(1, ManaItems.manaValue(held));

                    ScaledResolution res = event.getResolution();
                    int cx = res.getScaledWidth() / 2;
                    int cy = res.getScaledHeight() / 2 + 14;
                    Gui.drawRect(cx - 31, cy - 1, cx + 31, cy + 7, 0xAA000000);
                    Gui.drawRect(cx - 30, cy, cx - 30 + (int) (60.0 * paid / Math.max(1.0, cost)), cy + 6, 0xFFFFD34D);
                    String t = String.format("%.1f / %.1f", paid / itemValue, cost / itemValue);
                    mc.fontRenderer.drawStringWithShadow(t, cx - mc.fontRenderer.getStringWidth(t) / 2, cy + 10, 0xFFFFFF);
                    String t2 = held.getDisplayName();
                    mc.fontRenderer.drawStringWithShadow(t2, cx - mc.fontRenderer.getStringWidth(t2) / 2, cy + 20, 0xFFE080);
                }
            }
        } catch (Exception ignored) {
            // данные сервера читаются из потока рендера — редкая гонка не должна ронять игру
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
