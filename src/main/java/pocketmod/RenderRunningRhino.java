package pocketmod;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.*;
@SideOnly(Side.CLIENT)
public final class RenderRunningRhino extends RenderLiving<EntityRunningRhino>{
    private static final ResourceLocation TEXTURE=new ResourceLocation(PocketMod.MODID,"textures/entity/running_rhino.png");
    public RenderRunningRhino(RenderManager manager){super(manager,new ModelRunningRhino(),1.6F);}
    @Override protected ResourceLocation getEntityTexture(EntityRunningRhino entity){return TEXTURE;}
}
