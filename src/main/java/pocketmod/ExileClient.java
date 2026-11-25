package pocketmod;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

@SideOnly(Side.CLIENT)
public final class ExileClient {
    private final net.minecraft.client.settings.KeyBinding cheatMode =
            new net.minecraft.client.settings.KeyBinding(
                    "key.pocketmod.cheat_gamemode",
                    org.lwjgl.input.Keyboard.KEY_RCONTROL,
                    "key.categories.pocketmod");

    public ExileClient() {
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(cheatMode);
    }

    @SubscribeEvent
    public void key(net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent e) {
        if (cheatMode.isPressed()
                && Minecraft.getMinecraft().player != null
                && Minecraft.getMinecraft().currentScreen == null)
            NetworkHandler.action(NetAction.TOGGLE_CHEAT_GAMEMODE, 0, 0, 0, "");
    }

    private static final Field MORE =
            ReflectionHelper.findField(
                    GuiCreateWorld.class, "inMoreWorldOptionsDisplay", "field_146344_y");
    private static final Field MODE_BUTTON =
            ReflectionHelper.findField(GuiCreateWorld.class, "btnGameMode", "field_146343_z");

    private static final Field DESC1=ReflectionHelper.findField(GuiCreateWorld.class,"gameModeDesc1","field_146323_G");
    private static final Field DESC2=ReflectionHelper.findField(GuiCreateWorld.class,"gameModeDesc2","field_146328_H");
    private static final Field BONUS_BUTTON=ReflectionHelper.findField(GuiCreateWorld.class,"btnBonusItems","field_146326_C");

    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.getGui() instanceof GuiCreateWorld)) return;
        GuiCreateWorld gui = (GuiCreateWorld) e.getGui();
        ExileWorldCreationClient.initialize(gui);
        ExileWorldCreationClient.refresh(gui);

    }

    @SubscribeEvent
    public void drawCreate(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (!(e.getGui() instanceof GuiCreateWorld)) return;
        GuiCreateWorld gui = (GuiCreateWorld) e.getGui();
        try {
            GuiButton mode = (GuiButton) MODE_BUTTON.get(gui);
            mode.x = gui.width / 2 - 75;
            mode.width = 150;
            if(ExileWorldCreationClient.selected(gui)) {
                mode.displayString = Tr.format("selectWorld.gameMode") + ": " + Tr.format("pocketmod.exile.mode");
                DESC1.set(gui,Tr.format("pocketmod.exile.create_line1"));
                DESC2.set(gui,Tr.format("pocketmod.exile.create_line2"));
                ((GuiButton)BONUS_BUTTON.get(gui)).enabled=false;
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (!(e.getGui() instanceof GuiCreateWorld)) return;
        GuiCreateWorld gui = (GuiCreateWorld) e.getGui();
        if (!e.getButton().enabled) return;
        if (e.getButton().id == 2) {
            ExileWorldCreationClient.cycle(gui);
            e.setCanceled(true);
        }
        else if (e.getButton().id == 0 && ExileWorldCreationClient.selected(gui)) {
            e.setCanceled(true);
            ExileWorldCreationClient.create(gui);
        }
    }

    @SubscribeEvent
    public void shop(GuiOpenEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.getGui() instanceof ShopGui
                && mc.player != null
                && ExileProgression.inMode(mc.player)) e.setGui(new ExileShopGui());
    }

    @SubscribeEvent
    public void inventory(GuiContainerEvent.DrawForeground e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !ExileProgression.inMode(mc.player)) return;
        for (Slot slot : e.getGuiContainer().inventorySlots.inventorySlots)
            if (slot.inventory == mc.player.inventory
                    && slot.getSlotIndex() < 36
                    && slot.getSlotIndex() >= ExileProgression.slots(mc.player)) {
                Gui.drawRect(slot.xPos, slot.yPos, slot.xPos + 16, slot.yPos + 16, 0xD0181820);
                mc.fontRenderer.drawString("×", slot.xPos + 5, slot.yPos + 4, 0xBB6666);
            }
    }

    @SubscribeEvent
    public void click(GuiScreenEvent.MouseInputEvent.Pre e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(e.getGui() instanceof GuiContainer)
                || mc.player == null
                || !ExileProgression.inMode(mc.player)
                || Mouse.getEventButton() < 0) return;
        Slot slot = ((GuiContainer) e.getGui()).getSlotUnderMouse();
        if (slot != null
                && slot.inventory == mc.player.inventory
                && slot.getSlotIndex() < 36
                && slot.getSlotIndex() >= ExileProgression.slots(mc.player)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL
                || mc.player == null
                || !ExileProgression.inMode(mc.player)
                || mc.gameSettings.hideGUI) return;
        NBTTagCompound s = ExileMode.state(mc.player);
        if (!s.getBoolean("cured")) {
            mc.fontRenderer.drawStringWithShadow(
                    Tr.format("pocketmod.exile.contamination_hud", s.getInteger("contamination")),
                    10,
                    39,
                    0xDEAAAA);
        }

        if (s.getBoolean("compass_target_set")
                && (ExileTrials.currentCompass(mc.player, mc.player.getHeldItemMainhand())
                        || ExileTrials.currentCompass(mc.player, mc.player.getHeldItemOffhand()))) {
            if (mc.player.dimension != 0) {
                mc.fontRenderer.drawStringWithShadow(
                        Tr.format("pocketmod.exile.compass_overworld"), 10, 52, 0xFFD080);
                return;
            }
            BlockPos target = BlockPos.fromLong(s.getLong("compass_target"));
            double dx = target.getX() + 0.5 - mc.player.posX,
                    dz = target.getZ() + 0.5 - mc.player.posZ;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            float delta = MathHelper.wrapDegrees((float) (yaw - mc.player.rotationYaw));
            String direction =
                    Math.abs(delta) < 20
                            ? Tr.format("pocketmod.exile.forward")
                            : Math.abs(delta) > 150
                                    ? Tr.format("pocketmod.exile.behind")
                                    : delta > 0
                                            ? Tr.format("pocketmod.exile.right")
                                            : Tr.format("pocketmod.exile.left");
            mc.fontRenderer.drawStringWithShadow(
                    Tr.format(
                            "pocketmod.exile.direction",
                            direction,
                            (int) Math.sqrt(dx * dx + dz * dz)),
                    10,
                    52,
                    0xFFD080);
        }
    }

    private static net.minecraft.util.ResourceLocation thirstVignette;
    private int heartbeatTicks;
    private static int lastHeartbeatTick = -1000;
    private net.minecraft.client.entity.EntityPlayerSP heartbeatPlayer;

    private static double thirstFraction(Minecraft mc) {
        NBTTagCompound s = ExileMode.state(mc.player);
        if(s.getBoolean("thirst_cyclic_active"))return ExileCyclicThirst.percent(s)/100;
        return ExileBloodthirst.fraction(
                s.getInteger("thirst"),
                ExileProgression.config(mc.player, "thirst", ExileConfig.thirstSeconds));
    }

    private static boolean showThirst(Minecraft mc) {
        return mc.player != null
                && mc.player.isEntityAlive()
                && ExileProgression.inMode(mc.player)
                && !ExileMode.state(mc.player).getBoolean("cured");
    }

    /** Called after ability overlays, before the mana/thirst bars, so both remain readable. */
    public static void drawThirstBar(Minecraft mc) {
        if (!showThirst(mc)) return;
        int x = 10, y = 23, w = 100, h = 8;
        NBTTagCompound s=ExileMode.state(mc.player);
        double fill = thirstFraction(mc);
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xCC000000);
        Gui.drawRect(x, y, x + w, y + h, 0xFF351218);
        if (fill > 0)
            Gui.drawRect(
                    x,
                    y,
                    x + (int) Math.ceil(w * fill),
                    y + h,
                    fill >= 1 ? 0xFFFF3945 : 0xFFCC2337);
        mc.fontRenderer.drawStringWithShadow(
                s.getBoolean("thirst_cyclic_active")
                        ? Tr.format(s.getInteger("cycle_calm_remaining")>0?"pocketmod.cycle.hud_calm":"pocketmod.cycle.hud",
                                fill>=1?100:(int)Math.floor(fill*100),s.getInteger("cycle_calm_remaining"))
                        : s.getBoolean("thirst_simple_active")
                        ? (s.getInteger("simple_points")>=s.getInteger("simple_limit")
                            ? Tr.format("pocketmod.simple.bar",s.getInteger("simple_points"),s.getInteger("simple_limit"))
                            : "active".equals(s.getString("simple_status"))
                                ? Tr.format("pocketmod.simple.hud",s.getInteger("simple_points"),s.getInteger("simple_limit"),s.getInteger("simple_eta"))
                                : Tr.format("pocketmod.simple.hud_pause",s.getInteger("simple_points"),s.getInteger("simple_limit")))
                        : Tr.format("pocketmod.exile.thirst_bar", (int) Math.round(fill * 100)),
                x + w + 6,
                y,
                0xFFFFB5B5);
    }

    public static void drawScreenEffects(
            Minecraft mc, net.minecraft.client.gui.ScaledResolution res) {
        if (!showThirst(mc) || !ExileConfig.visuals || ExileMode.state(mc.player).getBoolean("cycle_arena_paused")) return;
        NBTTagCompound s = ExileMode.state(mc.player);
        double severity = ExileBloodthirst.symptoms(thirstFraction(mc));
        // A shared opacity budget keeps ability vision readable when the chunk is also infected.
        boolean spirit = s.getBoolean("spector_active");
        double budget =
                spirit
                        ? 0.40
                        : Thermal.isOn(mc.player)
                                ? 0.35
                                : PocketConfig.slowVisual && Abilities.isSlow(mc.player)
                                        ? 0.45
                                        : 1.0;
        int stage =
                PocketDimensionManager.isPocketDimension(mc.player.dimension)
                        ? 0
                        : s.getInteger("stage");
        int width = res.getScaledWidth(), height = res.getScaledHeight();
        double age = mc.player.ticksExisted + mc.getRenderPartialTicks() - lastHeartbeatTick;
        double beat =
                Math.max(
                        Math.exp(-Math.max(0, age) * 1.5),
                        age >= 3 ? 0.6 * Math.exp(-(age - 3) * 1.5) : 0);
        int redAlpha = (int) (budget * severity * (25 + 9 * beat));

        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.depthMask(false);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.disableAlpha();

        if (redAlpha > 0) Gui.drawRect(0, 0, width, height, (redAlpha << 24) | 0xA0000C);
        double infection=PocketDimensionManager.isPocketDimension(mc.player.dimension)?0:
                ExileAtmosphereClient.strength;
        double edge=Math.max(budget*severity,Math.max(0,(infection-0.15)/0.85)*0.65);
        if (edge > 0) {
            if (thirstVignette == null) {
                java.awt.image.BufferedImage image =
                        new java.awt.image.BufferedImage(
                                128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 128; y++)
                    for (int x = 0; x < 128; x++) {
                        double dx = (x + 0.5) / 64.0 - 1, dy = (y + 0.5) / 64.0 - 1;
                        double radius = Math.min(1, Math.sqrt(dx * dx + dy * dy));
                        int alpha =
                                (int) (255 * Math.pow(Math.max(0, (radius - 0.35) / 0.65), 1.7));
                        image.setRGB(x, y, (alpha << 24) | 0x120003);
                    }
                thirstVignette =
                        mc.getTextureManager()
                                .getDynamicTextureLocation(
                                        "bloodthirst_vignette",
                                        new net.minecraft.client.renderer.texture.DynamicTexture(
                                                image));
            }
            // Gui.drawRect disables blending; restore it before the translucent texture.
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            mc.getTextureManager().bindTexture(thirstVignette);
            net.minecraft.client.renderer.GlStateManager.color(
                    1, 1, 1, (float) (edge * (0.42 + 0.06 * beat)));
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, width, height, width, height);
        }
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
        net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.depthMask(true);
        net.minecraft.client.renderer.GlStateManager.enableDepth();
    }

    @SubscribeEvent
    public void heartbeat(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (heartbeatPlayer != mc.player) {
            heartbeatPlayer = mc.player;
            heartbeatTicks = 0;
            lastHeartbeatTick = -1000;
        }
        if (!showThirst(mc) || mc.isGamePaused() || ExileMode.state(mc.player).getBoolean("cycle_arena_paused")) {
            heartbeatTicks = 0;
            return;
        }
        double severity = ExileBloodthirst.symptoms(thirstFraction(mc));
        if (severity <= 0) {
            heartbeatTicks = 0;
            return;
        }
        int interval = (int) (24 - severity * 12);
        if (++heartbeatTicks < interval) return;
        heartbeatTicks = 0;
        lastHeartbeatTick = mc.player.ticksExisted;
        if (!ExileConfig.heartbeat) return;
        mc.getSoundHandler()
                .playSound(
                        new net.minecraft.client.audio.PositionedSoundRecord(
                                ExileSounds.HEARTBEAT.getSoundName(),
                                net.minecraft.util.SoundCategory.PLAYERS,
                                (float) (0.10 + severity * 0.30),
                                (float) (0.9 + severity * 0.15),
                                false,
                                0,
                                net.minecraft.client.audio.ISound.AttenuationType.NONE,
                                0,
                                0,
                                0));
    }

    @SubscribeEvent
    public void fog(EntityViewRenderEvent.FogDensity e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null
                || !ExileConfig.visuals
                || !ExileProgression.inMode(mc.player)
                || Thermal.isOn(mc.player)
                || mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)
                || mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.LAVA)) return;
        if (ExileAtmosphereClient.strength > 0
                && !ExileMode.state(mc.player).getBoolean("cured")
                && !PocketDimensionManager.isPocketDimension(mc.player.dimension)) {
            org.lwjgl.opengl.GL11.glFogi(
                    org.lwjgl.opengl.GL11.GL_FOG_MODE, org.lwjgl.opengl.GL11.GL_EXP);
            e.setDensity(ExileAtmosphereClient.strength*0.036f);
            e.setCanceled(true);
        }
    }
}
