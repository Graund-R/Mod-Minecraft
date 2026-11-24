package pocketmod;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.*;
@SideOnly(Side.CLIENT)
public final class RenderRiftWalker extends RenderLiving<EntityRiftWalker> {
    private static final ResourceLocation TEXTURE=new ResourceLocation(PocketMod.MODID,"textures/entity/rift_walker.png");
    public RenderRiftWalker(RenderManager manager) {
        super(manager,new ModelRiftWalker(),0.65F);
        addLayer(new LayerRenderer<EntityRiftWalker>() {
            @Override public void doRenderLayer(EntityRiftWalker e,float limb,float amount,float partial,float age,float yaw,float pitch,float scale) {
                bindTexture(TEXTURE);
                float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);
                GlStateManager.disableLighting();GlStateManager.depthMask(false);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
                float pulse=0.65F+0.15F*(float)Math.sin(age*0.15F)+e.windup(partial)*0.35F+e.phaseFlash(partial)*0.3F;
                if(e.enraged())GlStateManager.color(pulse,0.19F*pulse,0.38F*pulse,1);
                else GlStateManager.color(0.65F*pulse,0.45F*pulse,pulse,1);
                ((ModelRiftWalker)mainModel).renderGlow(scale);
                GlStateManager.color(1,1,1,1);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldX,oldY);
                GlStateManager.depthMask(true);GlStateManager.enableLighting();GlStateManager.disableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            @Override public boolean shouldCombineTextures(){return false;}
        });
    }
    @Override protected ResourceLocation getEntityTexture(EntityRiftWalker e){return TEXTURE;}
    @Override protected void preRenderCallback(EntityRiftWalker e,float partial) {
        float swell=0.87F*(1+e.phaseFlash(partial)*0.06F);
        GlStateManager.scale(swell,swell,swell);
    }
}
