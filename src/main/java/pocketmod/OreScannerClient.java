package pocketmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class OreScannerClient {
    private static final List<BlockPos> ORES = new ArrayList<BlockPos>();
    private static int lastScanTick = Integer.MIN_VALUE;
    private OreScannerClient() {}

    public static void clientTick(Minecraft mc) {
        if (mc.world == null || mc.player == null || !OreScanner.isOn(mc.player)) {
            ORES.clear();
            lastScanTick = Integer.MIN_VALUE;
            return;
        }
        if (lastScanTick == mc.player.ticksExisted || mc.player.ticksExisted % 10 != 0) return;
        lastScanTick = mc.player.ticksExisted;
        ORES.clear();
        int radius = OreScanner.radius(mc.player);
        int height = PocketConfig.oreScannerVertical;
        BlockPos center = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -height; dy <= height; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!mc.world.isBlockLoaded(pos)) continue;
                    IBlockState state = mc.world.getBlockState(pos);
                    if (isOre(state)) ORES.add(pos.toImmutable());
                }
            }
        }
    }

    private static boolean isOre(IBlockState state) {
        if (state.getBlock() == Blocks.AIR) return false;
        ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
        for (int id : OreDictionary.getOreIDs(stack)) {
            if (matchesTarget(OreDictionary.getOreName(id))) return true;
        }
        net.minecraft.util.ResourceLocation key = state.getBlock().getRegistryName();
        return key != null && matchesTarget(key.toString());
    }

    private static boolean matchesTarget(String name) {
        if (name == null) return false;
        for (String target : PocketConfig.oreScannerTargets) {
            if (target != null && target.trim().equalsIgnoreCase(name.trim())) return true;
        }
        return false;
    }

    public static void render(Minecraft mc) {
        if (mc.world == null || mc.player == null || !OreScanner.isOn(mc.player) || ORES.isEmpty()) return;
        RenderManager manager = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2.0F);
        for (BlockPos pos : ORES) {
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.002)
                    .offset(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
            RenderGlobal.drawSelectionBoundingBox(box, 0.25F, 0.85F, 1.0F, 0.9F);
        }
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
