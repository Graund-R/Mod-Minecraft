package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Клон выглядит как игрок (скин игрока); приманка полупрозрачная, тело при Спекторе — обычное. */
@SideOnly(Side.CLIENT)
public class RenderDecoy extends RenderLivingBase<EntityDecoy> {

    public RenderDecoy(RenderManager rm) {
        super(rm, new ModelPlayer(0.0F, false), 0.5F);
    }

    @Override
    public void doRender(EntityDecoy e, double x, double y, double z, float yaw, float pt) {
        if (e.body) {
            super.doRender(e, x, y, z, yaw, pt);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(0.7F, 0.8F, 1.0F, 0.5F);
        super.doRender(e, x, y, z, yaw, pt);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    @Override
    protected boolean canRenderName(EntityDecoy e) {
        return false;
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityDecoy e) {
        return Minecraft.getMinecraft().player.getLocationSkin();
    }
}
