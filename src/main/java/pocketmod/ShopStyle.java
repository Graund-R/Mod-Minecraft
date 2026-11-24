package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Shared, texture-independent shop chrome. Uses the discovery artwork for abilities. */
@SideOnly(Side.CLIENT)
final class ShopStyle {
    static final int BG = 0xF2101622, PANEL = 0xF0182130, EDGE = 0xFF334355;
    static final int GOLD = 0xFFE6BC73, TEXT = 0xFFDFE7F0, MUTED = 0xFF91A1B5;
    static final int MANA = 0xFF79D9E8, GREEN = 0xFF8ACFA0;

    static void panel(int x, int y, int w, int h, int fill, int edge) {
        Gui.drawRect(x, y, x + w, y + h, fill);
        Gui.drawRect(x, y, x + w, y + 1, edge);
        Gui.drawRect(x, y + h - 1, x + w, y + h, edge);
        Gui.drawRect(x, y, x + 1, y + h, edge);
        Gui.drawRect(x + w - 1, y, x + w, y + h, edge);
    }

    static void icon(Minecraft mc, String id, int x, int y, int size) {
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(new ResourceLocation("pocketmod",
                "textures/items/discovery_" + id + ".png"));
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        GlStateManager.disableBlend();
    }

    static void progress(int x, int y, int w, int level, int max) {
        if (max <= 0) return;
        for (int i = 0; i < max; i++) {
            int left = x + i * w / max;
            int right = x + (i + 1) * w / max - 2;
            Gui.drawRect(left, y, Math.max(left + 1, right), y + 3,
                    i < level ? GREEN : i == level ? GOLD : EDGE);
        }
    }

    static final class Button extends GuiButton {
        boolean active;
        boolean primary;
        String icon;
        String subtitle;
        int level, max;

        Button(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my, float partial) {
            if (!visible) return;
            hovered = mx >= x && my >= y && mx < x + width && my < y + height;
            int fill = active ? 0xFF303C4C : enabled && hovered ? 0xFF2B3A4D : PANEL;
            int edge = active || primary && enabled ? GOLD : hovered && enabled ? 0xFF7289A1 : EDGE;
            panel(x, y, width, height, fill, edge);
            if (active) Gui.drawRect(x, y + 1, x + 3, y + height - 1, GOLD);
            int left = x + 7;
            if (icon != null) {
                int size = height >= 30 ? 22 : 14;
                ShopStyle.icon(mc, icon, left, y + (height - size) / 2, size);
                left += size + 7;
            }
            String label = mc.fontRenderer.trimStringToWidth(displayString,
                    Math.max(0, width - (left - x) - 6));
            int textY = subtitle == null ? y + (height - 8) / 2 : y + 6;
            int textX = icon == null && subtitle == null
                    ? x + (width - mc.fontRenderer.getStringWidth(label)) / 2 : left;
            mc.fontRenderer.drawStringWithShadow(label, textX, textY,
                    active ? GOLD : !enabled ? MUTED : primary ? GOLD : TEXT);
            if (subtitle != null) {
                String sub = mc.fontRenderer.trimStringToWidth(subtitle, Math.max(0, width - (left - x) - 6));
                mc.fontRenderer.drawStringWithShadow(sub, left, y + 17, MUTED);
                progress(left, y + height - 6, Math.max(1, width - (left - x) - 8), level, max);
            }
            GlStateManager.color(1, 1, 1, 1);
        }
    }
    private ShopStyle() {}
}
