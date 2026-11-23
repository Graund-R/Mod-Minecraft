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
    private static final int BUTTON = 9451;
    private GuiButton createButton;
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

    private static final Field TYPE =
            ReflectionHelper.findField(GuiCreateWorld.class, "selectedIndex", "field_146331_K");
    private static final Field MODE =
            ReflectionHelper.findField(GuiCreateWorld.class, "gameMode", "field_146342_r");
    private static final Field HARDCORE =
            ReflectionHelper.findField(GuiCreateWorld.class, "hardCoreMode", "field_146337_w");
    private static final Field MORE =
            ReflectionHelper.findField(
                    GuiCreateWorld.class, "inMoreWorldOptionsDisplay", "field_146344_y");
    private static final Field MODE_BUTTON =
            ReflectionHelper.findField(GuiCreateWorld.class, "btnGameMode", "field_146343_z");

    private static boolean selected(GuiCreateWorld gui) {
        try {
            return TYPE.getInt(gui) == ExileMode.worldType().getId();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(GuiCreateWorld gui, boolean exile) {
        try {
            TYPE.setInt(gui, exile ? ExileMode.worldType().getId() : 0);
            if (exile) {
                MODE.set(gui, "survival");
                HARDCORE.setBoolean(gui, false);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.getGui() instanceof GuiCreateWorld)) return;
        GuiCreateWorld gui = (GuiCreateWorld) e.getGui();
        createButton =
                new GuiButton(
                        BUTTON,
                        gui.width / 2 + 5,
                        115,
                        150,
                        20,
                        I18n.format("pocketmod.exile.mode"));
        e.getButtonList().add(createButton);
    }

    @SubscribeEvent
    public void drawCreate(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (!(e.getGui() instanceof GuiCreateWorld)) return;
        GuiCreateWorld gui = (GuiCreateWorld) e.getGui();
        try {
            GuiButton mode = (GuiButton) MODE_BUTTON.get(gui);
            mode.x = gui.width / 2 - 155;
            mode.width = 150;
            if (createButton != null) {
                createButton.visible = !MORE.getBoolean(gui);
                createButton.displayString =
                        (selected(gui) ? "\u00a7a" : "") + I18n.format("pocketmod.exile.mode");
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SubscribeEvent
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (!(e.getGui() instanceof GuiCreateWorld)) return;
        GuiCreateWorld gui = (GuiCreateWorld) e.getGui();
        if (e.getButton().id == BUTTON) {
            set(gui, !selected(gui));
            e.setCanceled(true);
        } else if (e.getButton().id == 2 && selected(gui)) set(gui, false);
        else if (e.getButton().id == 0 && selected(gui)) set(gui, true);
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
            int thirst =
                    (int)
                            Math.min(
                                    100,
                                    s.getInteger("thirst")
                                            * 100.0
                                            / ExileProgression.config(
                                                    mc.player,
                                                    "thirst",
                                                    ExileConfig.thirstSeconds));
            int y = 28;
            mc.fontRenderer.drawStringWithShadow(
                    I18n.format("pocketmod.exile.hud", s.getInteger("contamination"), thirst),
                    10,
                    y,
                    0xDEAAAA);
            if (ExileConfig.visuals && s.getInteger("stage") > 0) {
                int alpha = Math.min(55, s.getInteger("stage") * 9);
                Gui.drawRect(
                        0,
                        0,
                        e.getResolution().getScaledWidth(),
                        e.getResolution().getScaledHeight(),
                        (alpha << 24) | 0x333340);
            }
        }
        if (s.getBoolean("compass_target_set")
                && (ExileTrials.isCompass(mc.player.getHeldItemMainhand())
                        || ExileTrials.isCompass(mc.player.getHeldItemOffhand()))) {
            if (mc.player.dimension != 0) {
                mc.fontRenderer.drawStringWithShadow(
                        I18n.format("pocketmod.exile.compass_overworld"), 10, 42, 0xFFD080);
                return;
            }
            BlockPos target = BlockPos.fromLong(s.getLong("compass_target"));
            double dx = target.getX() + 0.5 - mc.player.posX,
                    dz = target.getZ() + 0.5 - mc.player.posZ;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            float delta = MathHelper.wrapDegrees((float) (yaw - mc.player.rotationYaw));
            String direction =
                    Math.abs(delta) < 20
                            ? I18n.format("pocketmod.exile.forward")
                            : Math.abs(delta) > 150
                                    ? I18n.format("pocketmod.exile.behind")
                                    : delta > 0
                                            ? I18n.format("pocketmod.exile.right")
                                            : I18n.format("pocketmod.exile.left");
            mc.fontRenderer.drawStringWithShadow(
                    I18n.format(
                            "pocketmod.exile.direction",
                            direction,
                            (int) Math.sqrt(dx * dx + dz * dz)),
                    10,
                    42,
                    0xFFD080);
        }
    }

    @SubscribeEvent
    public void fog(EntityViewRenderEvent.FogDensity e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null
                || !ExileConfig.visuals
                || !ExileProgression.inMode(mc.player)
                || mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)
                || mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.LAVA)) return;
        int stage = ExileMode.state(mc.player).getInteger("stage");
        if (stage > 0
                && !ExileMode.state(mc.player).getBoolean("cured")
                && !PocketDimensionManager.isPocketDimension(mc.player.dimension)) {
            org.lwjgl.opengl.GL11.glFogi(
                    org.lwjgl.opengl.GL11.GL_FOG_MODE, org.lwjgl.opengl.GL11.GL_EXP);
            e.setDensity(stage * 0.009f);
            e.setCanceled(true);
        }
    }
}
