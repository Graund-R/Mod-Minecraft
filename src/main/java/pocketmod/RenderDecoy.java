package pocketmod;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Клон выглядит как игрок-владелец (его настоящий скин и тип рук). Приманку полупрозрачной видит только сам владелец,
 * остальные игроки видят её непрозрачной (как настоящего игрока); тело при Спекторе всегда непрозрачное.
 */
@SideOnly(Side.CLIENT)
public class RenderDecoy extends RenderLivingBase<EntityDecoy> {

    private final ModelPlayer wide = new ModelPlayer(0.0F, false);
    private final ModelPlayer slim = new ModelPlayer(0.0F, true);

    public RenderDecoy(RenderManager rm) {
        super(rm, new ModelPlayer(0.0F, false), 0.5F);
    }

    private static NetworkPlayerInfo infoOf(EntityDecoy e) {
        UUID id = e.getOwnerUuid();
        NetHandlerPlayClient conn = Minecraft.getMinecraft().getConnection();
        return id != null && conn != null ? conn.getPlayerInfo(id) : null;
    }

    @Override
    public void doRender(EntityDecoy e, double x, double y, double z, float yaw, float pt) {
        UUID id = e.getOwnerUuid();
        NetworkPlayerInfo info = infoOf(e);
        String type = info != null ? info.getSkinType() : (id != null ? DefaultPlayerSkin.getSkinType(id) : "default");
        mainModel = "slim".equals(type) ? slim : wide;

        EntityPlayerSP viewer = Minecraft.getMinecraft().player;
        boolean ghost = !e.isBody() && viewer != null && id != null && id.equals(viewer.getUniqueID());
        if (!ghost) {
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
        UUID id = e.getOwnerUuid();
        NetworkPlayerInfo info = infoOf(e);
        if (info != null) return info.getLocationSkin();
        if (id != null) return DefaultPlayerSkin.getDefaultSkin(id);
        return Minecraft.getMinecraft().player.getLocationSkin();
    }
}
