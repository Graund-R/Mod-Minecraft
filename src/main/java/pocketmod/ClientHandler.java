package pocketmod;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
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
    private static KeyBinding keyShop;
    private static KeyBinding keySlow;
    private static KeyBinding keySpector;
    private static KeyBinding keyPhase;
    private static boolean phWas;
    private static KeyBinding keyThermal;
    private static KeyBinding keyShield;

    private static boolean phaseLatch;
    private static boolean xWas, xWait, shiftWas, jWas, zWheelUsed;
    /** Дух активен (читается из события ввода, которое идёт до движения игрока). */
    private static volatile boolean specClient;

    /** Реальное состояние клавиши напрямую с клавиатуры/мыши: не зависит от модификаторов (Shift/Ctrl). */
    private static boolean raw(KeyBinding k) {
        int c = k.getKeyCode();
        if (c == 0) return false;
        if (c < 0) return Mouse.isButtonDown(c + 100);
        return Keyboard.isKeyDown(c);
    }
    private static long lastJump;
    private static boolean cWas;
    private static ResourceLocation vignetteTex;

    /** Название клавиши для окон (с учётом переназначения). */
    public static String keyName(String id) {
        if ("scanner".equals(id)) return "Shift+" + keyName("thermal");
        KeyBinding k = null;
        if ("pocket".equals(id)) k = keyPocket;
        else if ("teleport".equals(id)) k = keyTeleport;
        else if ("invis".equals(id)) k = keyInvis;
        else if ("slow".equals(id)) k = keySlow;
        else if ("spector".equals(id)) k = keySpector;
        else if ("thermal".equals(id)) k = keyThermal;
        else if ("shield".equals(id)) k = keyShield;
        return k == null ? "?" : k.getDisplayName();
    }
    private static boolean slowShaderOn;
    private static final java.util.Set<Entity> glowing = new java.util.HashSet<Entity>();

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
    private static final Hold hEnter = new Hold();

    private static final int EV_NONE = 0, EV_HOLD = 1, EV_TAP = 2;

    public static void register() {
        keyPocket   = new KeyBinding("key.pocketmod.pocket",   Keyboard.KEY_K, "key.categories.pocketmod");
        keyTeleport = new KeyBinding("key.pocketmod.teleport", Keyboard.KEY_G, "key.categories.pocketmod");
        keyInvis    = new KeyBinding("key.pocketmod.invis",    Keyboard.KEY_V, "key.categories.pocketmod");
        keyShop     = new KeyBinding("key.pocketmod.shop",     Keyboard.KEY_RETURN, "key.categories.pocketmod");
        keySlow     = new KeyBinding("key.pocketmod.slow",     Keyboard.KEY_Z, "key.categories.pocketmod");
        keySpector  = new KeyBinding("key.pocketmod.spector",  Keyboard.KEY_X, "key.categories.pocketmod");
        keyPhase    = new KeyBinding("key.pocketmod.phase",    Keyboard.KEY_R, "key.categories.pocketmod");
        keyThermal  = new KeyBinding("key.pocketmod.thermal",  Keyboard.KEY_C, "key.categories.pocketmod");
        keyShield   = new KeyBinding("key.pocketmod.shield",   Keyboard.KEY_B, "key.categories.pocketmod");
        ClientRegistry.registerKeyBinding(keySpector);
        ClientRegistry.registerKeyBinding(keyPhase);
        ClientRegistry.registerKeyBinding(keyThermal);
        ClientRegistry.registerKeyBinding(keyShield);
        ClientRegistry.registerKeyBinding(keyPocket);
        ClientRegistry.registerKeyBinding(keyTeleport);
        ClientRegistry.registerKeyBinding(keyInvis);
        ClientRegistry.registerKeyBinding(keyShop);
        ClientRegistry.registerKeyBinding(keySlow);
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
        MinecraftForge.EVENT_BUS.register(new ConfigButton());
        MinecraftForge.EVENT_BUS.register(new ClientTimeSlow());
        MinecraftForge.EVENT_BUS.register(new ClientDash());
        MinecraftForge.EVENT_BUS.register(new WarehouseOverlay());
    }

    public static void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityDecoy.class, manager -> new RenderDecoy(manager));
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

    private static boolean mmKeysFixed;
    private static boolean mmMenuBound;

    /**
     * Клавиши Metamorph по умолчанию совпадают с клавишами мода (меню образов на B перехватывало щит).
     * Один раз после загрузки настроек переносим меню образов на Shift+V, а любую другую клавишу Metamorph,
     * совпавшую с клавишей Pocket Mod, на Alt+та же клавиша. Уже изменённые игроком клавиши не трогаем.
     */
    private static void fixMetamorphKeys(Minecraft mc) {
        if (mmKeysFixed) return;
        mmKeysFixed = true;
        try {
            KeyBinding[] own = new KeyBinding[] { keyPocket, keyTeleport, keyInvis, keyShop, keySlow,
                    keySpector, keyPhase, keyThermal, keyShield };
            for (KeyBinding kb : mc.gameSettings.keyBindings) {
                String d = kb.getKeyDescription();
                if (d == null || !d.startsWith("key.metamorph.")) continue;
                if (d.equals("key.metamorph.survival_menu")) mmMenuBound = true;
                if (kb.getKeyCode() <= 0 || kb.getKeyModifier() != KeyModifier.NONE) continue;
                boolean conflict = false;
                for (KeyBinding pk : own) {
                    if (pk != null && pk.getKeyCode() == kb.getKeyCode()) conflict = true;
                }
                if (!conflict) continue;
                if (d.equals("key.metamorph.survival_menu")) {
                    kb.setKeyModifierAndCode(KeyModifier.SHIFT, Keyboard.KEY_V);
                } else {
                    kb.setKeyModifierAndCode(KeyModifier.ALT, kb.getKeyCode());
                }
            }
            KeyBinding.resetKeyBindingArrayAndHash();
        } catch (Throwable ignored) {
            // привязки Metamorph не критичны для работы мода
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) fixMetamorphKeys(mc);
        if (mc.player == null) {
            if (slowShaderOn) updateSlowShader(mc, false);
            return;
        }

        if (mc.player.ticksExisted % 100 == 1) Tr.ensureLoaded();
        TotemMarkers.tick(mc);
        OreScannerClient.clientTick(mc);
        if (mc.player.ticksExisted % 4 == 0) updateThermalGlow(mc);
        EntityPlayer spt = mc.player;
        updateSlowShader(mc, spt != null && Abilities.isSlow(spt));
        if (spt != null && SoftFall.isActive(spt) && !mc.player.onGround) {
            double lim = -PocketConfig.softFallSpeed;
            if (mc.player.motionY < lim) mc.player.motionY = lim;
        }

        // Пока открыто окно — клавиши молчат, а после закрытия ждём отпускания
        if (mc.currentScreen != null) {
            resetHold(hG);
            resetHold(hV);
            resetHold(hZ);
            resetHold(hEnter);
            xWait = raw(keySpector);
            specClient = false;
            return;
        }

        long now = System.currentTimeMillis();
        int holdMs = PocketConfig.holdMs;

        boolean gDown = raw(keyTeleport);
        // Shift+G — окно тотемов (сразу при нажатии)
        if (gDown && !hG.was && !hG.wait && GuiScreen.isShiftKeyDown()) {
            EntityPlayer sp0 = mc.player;
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
            // удержание G — тотем смерти (если есть), иначе последний обычный тотем
            EntityPlayer p = mc.player;
            if (p != null) NetworkHandler.action(NetAction.TELEPORT_LAST_TOTEM, 0, 0, 0, "");
        } else if (g == EV_TAP) {
            EntityPlayer p = mc.player;
            if (p != null) NetworkHandler.action(NetAction.TELEPORT_LOOK, 0, 0, 0, "");
        }

        boolean vDown = raw(keyInvis);
        // Alt+V: быстрое превращение в последний полученный образ и обратно.
        // Shift+V: меню образов (родное меню Metamorph; если его клавиши не найдены, наш каталог).
        // Обычный короткий V переключает невидимость.
        if (vDown && !hV.was && !hV.wait && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU))) {
            PocketDoorNetwork.quickMorph();
            hV.wait = true;
        } else if (vDown && !hV.was && !hV.wait && GuiScreen.isShiftKeyDown()) {
            if (!mmMenuBound) PocketDoorNetwork.requestMorphCatalog();
            hV.wait = true;
        }
        int v = poll(vDown, hV, now, holdMs);
        if (v == EV_TAP) {
            EntityPlayer p = mc.player;
            if (p != null) NetworkHandler.action(NetAction.TOGGLE_INVISIBILITY, 0, 0, 0, "");
        }

        int enter = poll(raw(keyShop), hEnter, now, holdMs);
        if (enter == EV_HOLD) {
            mc.displayGuiScreen(new ShopGui(mc.player));
        }

        int z = poll(keySlow.isKeyDown(), hZ, now, holdMs);
        if (z == EV_TAP) {
            if (zWheelUsed) {
                zWheelUsed = false; // крутили колесо при зажатой Z — это не тап
            } else {
                EntityPlayer p = mc.player;
                if (p != null) NetworkHandler.action(NetAction.TOGGLE_SLOW, 0, 0, 0, "");
            }
        } else if (!keySlow.isKeyDown()) {
            zWheelUsed = false;
        }

        // Спектор: X вкл/выкл; Shift+X фазовый шаг. Физические клавиши, не зависящие от GUI.
        boolean xPhys = Keyboard.isKeyDown(Keyboard.KEY_X);
        int xCode = keySpector.getKeyCode();
        if (xCode > 0 && xCode < 256 && Keyboard.isKeyDown(xCode)) xPhys = true;
        if (xWait) {
            if (!xPhys) xWait = false;
            xPhys = false;
        }
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        EntityPlayer px = mc.player;
        boolean specActive = px != null && Spector.isActive(px);
        if (specActive) specClient = true;
        // отдельная клавиша фазового шага (по умолчанию R) — тот же вызов, что и Shift+X
        boolean phKey = raw(keyPhase);
        boolean phaseRequest = phKey && !phWas;
        phWas = phKey;
        if (px != null && ((xPhys && !xWas) || phaseRequest)) {
            final boolean doPhase = phaseRequest || (shiftDown && (specActive || specClient));
            if (doPhase) {
                mc.player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.phase_try"), true);
                NetworkHandler.action(NetAction.PHASE_STEP, 0, 0, 0, "");
            } else {
                NetworkHandler.action(NetAction.TOGGLE_SPECTOR, 0, 0, 0, "");
            }
        }
        xWas = xPhys;
        shiftWas = shiftDown;
        specClient = px != null && Spector.isActive(px);

        // Волна: Shift + пробел
        boolean jDown = mc.gameSettings.keyBindJump.isKeyDown();
        if (jDown && !jWas && shiftDown && !specActive) {
            EntityPlayer p = mc.player;
            if (p != null) NetworkHandler.action(NetAction.SHOCKWAVE, 0, 0, 0, "");
        }

        // C: тепловизор; Shift+C: сканер руд (режим тепловизора); Ctrl+C: выбор руд для сканера
        boolean cDown = raw(keyThermal);
        while (keyThermal.isPressed()) {
            // сбросить счётчик нажатий, чтобы клавиша не срабатывала дважды
        }
        if (cDown && !cWas && mc.player != null) {
            if (GuiScreen.isCtrlKeyDown()) {
                if (OreScanner.available(mc.player)) mc.displayGuiScreen(new ScannerGui(mc.player));
                else mc.player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.scanner_need"), true);
            } else if (GuiScreen.isShiftKeyDown()) {
                NetworkHandler.action(NetAction.TOGGLE_ORE_SCANNER, 0, 0, 0, "");
            } else {
                NetworkHandler.action(NetAction.TOGGLE_THERMAL, 0, 0, 0, "");
            }
        }
        cWas = cDown;
        if (keyShield.isPressed()) {
            EntityPlayer p = mc.player;
            if (p != null) NetworkHandler.action(NetAction.TOGGLE_SHIELD, 0, 0, 0, "");
        }

        // Мягкое падение: пробел в воздухе выключает его, повторный пробел включает обратно
        // (быстрый двойной пробел при этом по-прежнему запускает полёт)
        if (jDown && !jWas && !shiftDown && !specActive && mc.player != null && !mc.player.onGround
                && !mc.player.capabilities.isFlying && !mc.player.isRiding()
                && AbilityStorage.isUnlocked(mc.player, AbilityType.SOFTFALL)) {
            NetworkHandler.action(NetAction.SOFTFALL_TOGGLE, 0, 0, 0, "");
        }

        // Полёт: двойной пробел в воздухе (без Shift)
        if (jDown && !jWas && !shiftDown) {
            if (now - lastJump < 350L && !mc.player.onGround && !mc.player.capabilities.allowFlying
                    && !mc.player.isRiding() && !mc.player.isInWater()) {
                EntityPlayer p = mc.player;
                if (p != null) NetworkHandler.action(NetAction.START_FLIGHT, 0, 0, 0, "");
                lastJump = 0L;
            } else {
                lastJump = now;
            }
        }
        jWas = jDown;

        if (keyPocket.isPressed()) {
            EntityPlayer p = mc.player;
            if (p != null) NetworkHandler.action(NetAction.TOGGLE_POCKET, 0, 0, 0, "");
        }
    }

    /**
     * Полная невидимость для онлайна: ванильное зелье прячет только тело, а броня, предметы в руках, элитры и
     * ник остаются висеть в воздухе. Поэтому игрока с флагом невидимости не рисуем вообще (ни тело, ни броню, ни ник).
     * Спектаторы по-прежнему видят невидимых, как в ваниле.
     */
    @SubscribeEvent
    public void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        EntityPlayer ep = event.getEntityPlayer();
        if (ep == null || !ep.isInvisible()) return;
        EntityPlayer viewer = Minecraft.getMinecraft().player;
        if (viewer != null && viewer.isSpectator()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) { OreScannerClient.render(Minecraft.getMinecraft()); }

    private static final String TEAM_HOT = "pm_hot";
    private static final java.util.Set<String> hotNames = new java.util.HashSet<String>();

    private static boolean hostile(EntityLivingBase e) {
        if (e instanceof net.minecraft.entity.monster.IMob) return true;
        if (e instanceof net.minecraft.entity.passive.EntityWolf && ((net.minecraft.entity.passive.EntityWolf) e).isAngry()) return true;
        return false;
    }

    private static net.minecraft.scoreboard.ScorePlayerTeam hotTeam(Minecraft mc) {
        net.minecraft.scoreboard.Scoreboard sb = mc.world.getScoreboard();
        net.minecraft.scoreboard.ScorePlayerTeam t = sb.getTeam(TEAM_HOT);
        if (t == null) {
            t = sb.createTeam(TEAM_HOT);
        }
        // в 1.12.2 цвет контура свечения берётся из префикса команды (§c), а не из setColor
        t.setColor(net.minecraft.util.text.TextFormatting.RED);
        if (!"\u00a7c".equals(t.getPrefix())) t.setPrefix("\u00a7c");
        return t;
    }

    private static void clearHot(Minecraft mc) {
        if (hotNames.isEmpty() || mc.world == null) {
            hotNames.clear();
            return;
        }
        try {
            net.minecraft.scoreboard.Scoreboard sb = mc.world.getScoreboard();
            net.minecraft.scoreboard.ScorePlayerTeam t = sb.getTeam(TEAM_HOT);
            if (t != null) {
                for (String n : hotNames) {
                    if (sb.getPlayersTeam(n) == t) sb.removePlayerFromTeam(n, t);
                }
            }
        } catch (Throwable ignored) {
        }
        hotNames.clear();
    }

    /** Тепловизор: контур сквозь стены. Враги красные, остальные белые. */
    private static void updateThermalGlow(Minecraft mc) {
        boolean on = false;
        int radius = 0;
        try {
            EntityPlayer sp = mc.player;
            if (sp != null && Thermal.isOn(sp)) {
                on = true;
                radius = Thermal.radius(sp);
            }
        } catch (Exception ignored) {
        }
        if (!on) {
            if (!glowing.isEmpty()) {
                for (Entity e : glowing) e.setGlowing(false);
                glowing.clear();
            }
            clearHot(mc);
            return;
        }
        double r2 = (double) radius * radius;
        java.util.Set<Entity> now = new java.util.HashSet<Entity>();
        java.util.Set<String> hotNow = new java.util.HashSet<String>();
        net.minecraft.scoreboard.ScorePlayerTeam hot = null;
        try {
            hot = hotTeam(mc);
        } catch (Throwable ignored) {
        }
        for (Object o : mc.world.loadedEntityList) {
            if (!(o instanceof EntityLivingBase) || o == mc.player) continue;
            EntityLivingBase e = (EntityLivingBase) o;
            if (e.isDead || e.getDistanceSq(mc.player) > r2) continue;
            e.setGlowing(true);
            now.add(e);
            if (hot != null && hostile(e)) {
                String n = e.getCachedUniqueIdString();
                hotNow.add(n);
                try {
                    if (mc.world.getScoreboard().getPlayersTeam(n) != hot) mc.world.getScoreboard().addPlayerToTeam(n, TEAM_HOT);
                } catch (Throwable ignored) {
                }
            }
        }
        for (Entity e : glowing) {
            if (!now.contains(e)) e.setGlowing(false);
        }
        if (hot != null) {
            for (String n : new java.util.ArrayList<String>(hotNames)) {
                if (!hotNow.contains(n)) {
                    try {
                        if (mc.world.getScoreboard().getPlayersTeam(n) == hot) mc.world.getScoreboard().removePlayerFromTeam(n, hot);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        hotNames.clear();
        hotNames.addAll(hotNow);
        glowing.clear();
        glowing.addAll(now);
    }

    private void drawThermalOverlay(ScaledResolution res) {
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();
        Gui.drawRect(0, 0, sw, sh, 0x78081018);
        java.util.Random r = new java.util.Random(System.currentTimeMillis() / 90L);
        for (int i = 0; i < 6; i++) {
            int y = r.nextInt(sh);
            Gui.drawRect(0, y, sw, y + 1, 0x18FFA040);
        }
    }

    /**
     * Спектор: движение строго по взгляду. Событие идёт сразу после чтения клавиш и ДО движения игрока,
     * поэтому Space/Shift (вверх/вниз в режиме наблюдателя) и приседание отключаются по-настоящему.
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!specClient) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || event.getEntityPlayer() != mc.player || mc.currentScreen != null) return;
        net.minecraft.util.MovementInput mi = event.getMovementInput();
        net.minecraft.client.settings.GameSettings gs = mc.gameSettings;
        float f = (raw(gs.keyBindForward) ? 1f : 0f) - (raw(gs.keyBindBack) ? 1f : 0f);
        float s = (raw(gs.keyBindLeft) ? 1f : 0f) - (raw(gs.keyBindRight) ? 1f : 0f);
        // ваниль больше ничего не двигает: ни вверх/вниз, ни приседание, ни разгон от WASD
        mi.jump = false;
        mi.sneak = false;
        mi.moveForward = 0f;
        mi.moveStrafe = 0f;
        double speed = mc.player.capabilities.getFlySpeed() * 3.0;
        if (GuiScreen.isCtrlKeyDown()) speed *= 2.0;
        float yaw = mc.player.rotationYaw;
        float pitch = mc.player.rotationPitch;
        double lookX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double lookY = -Math.sin(Math.toRadians(pitch));
        double lookZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double leftX = Math.cos(Math.toRadians(yaw));
        double leftZ = Math.sin(Math.toRadians(yaw));
        double mx = lookX * f + leftX * s;
        double my = lookY * f;
        double mz = lookZ * f + leftZ * s;
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 1e-4) {
            mx = mx / len * speed;
            my = my / len * speed;
            mz = mz / len * speed;
        } else {
            mx = my = mz = 0;
        }
        mc.player.motionX = mx;
        mc.player.motionY = my;
        mc.player.motionZ = mz;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!keySlow.isKeyDown() || mc.currentScreen != null || mc.player == null) return;
        int wheel = event.getDwheel();
        if (wheel == 0) return;
        event.setCanceled(true);
        zWheelUsed = true;
        EntityPlayer player = mc.player;
        float cur = Abilities.getSlowFactor(player);
        float raw = Math.round((cur + (wheel > 0 ? 0.05f : -0.05f)) * 100f) / 100f;
        float next = Math.max(Abilities.minSlowFactor(player), Math.min(0.75f, raw));
        NetworkHandler.action(NetAction.SET_SLOW_FACTOR, 0, 0, next, "");
    }

    /** Длина шкалы: цена самого дорогого уже открытого действия (пока ничего нет — hudBarScale, по умолчанию 100). */
    private static double barScale(EntityPlayer sp) {
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
        if (mc.player == null || mc.gameSettings.hideGUI) return;

        try {
            EntityPlayer sp = mc.player;
            if (sp == null) return;

            double mana = Mana.get(sp);
            int x = 10, y = 10, w = 100, h = 8;
            double fill = Math.min(1.0, mana / barScale(sp));
            Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
            Gui.drawRect(x, y, x + (int) (w * fill), y + h, 0xFF55AAFF);
            mc.fontRenderer.drawStringWithShadow(String.valueOf((long) mana), x + w + 6, y, 0xFFFFFF);

            if (Thermal.isOn(sp)) {
                drawThermalOverlay(event.getResolution());
                // шкала маны поверх затемнения
                Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
                Gui.drawRect(x, y, x + (int) (w * fill), y + h, 0xFF55AAFF);
                mc.fontRenderer.drawStringWithShadow(String.valueOf((long) mana), x + w + 6, y, 0xFFFFFF);
            }

            if (PocketConfig.slowVisual && Abilities.isSlow(sp)) {
                drawSlowVignette(mc, event.getResolution(), Abilities.getSlowFactor(sp));
            }

            ExileClient.drawScreenEffects(mc, event.getResolution());
            // Draw the paired meters after every screen effect.
            Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
            Gui.drawRect(x, y, x + (int) (w * fill), y + h, 0xFF55AAFF);
            mc.fontRenderer.drawStringWithShadow(String.valueOf((long) mana), x + w + 6, y, 0xFFFFFF);
            ExileClient.drawThirstBar(mc);
            OreScannerClient.drawHud(mc);

            ItemStack held = mc.player.getHeldItemMainhand();
            if (PocketDimensionManager.isPocketDimension(mc.player.dimension)
                    && mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                    && ManaItems.isManaItem(held)) {
                PocketData d = PocketData.get(mc.world);
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
            // клиентский снимок данных может обновиться между кадрами
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Плавная виньетка: радиальный градиент (текстура строится один раз), сила зависит от степени замедления. */
    private static ResourceLocation vignette(Minecraft mc) {
        if (vignetteTex == null) {
            int n = 256;
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double dx = (x + 0.5) / n * 2.0 - 1.0;
                    double dy = (y + 0.5) / n * 2.0 - 1.0;
                    double d = Math.sqrt(dx * dx + dy * dy) / 1.4142;
                    double a = Math.pow(Math.max(0.0, (d - 0.28) / 0.72), 1.7);
                    int alpha = (int) (Math.min(1.0, a) * 255.0);
                    img.setRGB(x, y, (alpha << 24) | 0x060B16);
                }
            }
            vignetteTex = mc.getTextureManager().getDynamicTextureLocation("pocketmod_vignette", new DynamicTexture(img));
        }
        return vignetteTex;
    }

    private void drawSlowVignette(Minecraft mc, ScaledResolution res, float factor) {
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();
        float t = Math.max(0f, Math.min(1f, 1f - factor)); // 0.25 при x0.75 ... 1.0 при полном стопе
        double pulse = 0.04 * Math.sin(System.currentTimeMillis() / 700.0);
        float a = (float) Math.max(0.0, Math.min(1.0, 0.30 + 0.70 * t + pulse));

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();

        // лёгкая холодная дымка по всему экрану (запасной вариант, если шейдер десатурации не загрузился)
        int tint = (int) (10 + 26 * t);
        Gui.drawRect(0, 0, sw, sh, (tint << 24) | 0x4A5A78);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, a);
        mc.getTextureManager().bindTexture(vignette(mc));
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, sw, sh, (float) sw, (float) sh);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
    }

    /** Десатурация картинки при замедлении: свой пост-шейдер на ванильной программе color_convolve. */
    private static void updateSlowShader(Minecraft mc, boolean slow) {
        boolean want = slow && PocketConfig.slowVisual && PocketConfig.slowDesaturate;
        try {
            if (want && !slowShaderOn) {
                mc.entityRenderer.loadShader(new ResourceLocation("pocketmod", "shaders/post/slow.json"));
                slowShaderOn = true;
            } else if (!want && slowShaderOn) {
                mc.entityRenderer.stopUseShader();
                slowShaderOn = false;
            }
        } catch (Throwable t) {
            slowShaderOn = false;
            if (want) {
                System.err.println("[PocketMod] slow shader failed, vignette only: " + t);
                PocketConfig.slowDesaturate = false;
            }
        }
    }
}
