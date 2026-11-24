package pocketmod;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public final class ResurrectionTotemBlock extends Block {
    public ResurrectionTotemBlock() {
        super(Material.ROCK);
        setRegistryName(PocketMod.MODID, "resurrection_totem");
        setUnlocalizedName("pocketmod.resurrection_totem");
        setHardness(3);
        setResistance(10);
        setHarvestLevel("pickaxe", 0);
        setLightLevel(0.6f);
        setSoundType(SoundType.STONE);
        setCreativeTab(PocketCreativeTab.ITEMS);
    }

    @Override
    public void onBlockPlacedBy(
            World world,
            BlockPos pos,
            IBlockState state,
            @Nullable EntityLivingBase placer,
            ItemStack stack) {
        if (!world.isRemote
                && world.provider.getDimension() == 0
                && placer instanceof EntityPlayer) {
            ExileWorldData data = ExileWorldData.get(world);
            data.addResurrectionTotem(placer.getUniqueID(), pos);
            ((EntityPlayer) placer)
                    .sendStatusMessage(
                            new TextComponentTranslation("pocketmod.exile.totem_set"), false);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.provider.getDimension() == 0)
            ExileWorldData.get(world).removeResurrectionTotem(pos);
        super.breakBlock(world, pos, state);
    }

    public static final class TotemItem extends ItemBlock {
        public TotemItem(Block block) {
            super(block);
            setRegistryName(block.getRegistryName());
        }

        @Override
        public EnumActionResult onItemUse(
                EntityPlayer player,
                World world,
                BlockPos pos,
                EnumHand hand,
                EnumFacing facing,
                float hitX,
                float hitY,
                float hitZ) {
            if (world.provider.getDimension() != 0) {
                if (!world.isRemote)
                    player.sendStatusMessage(
                            new TextComponentTranslation("pocketmod.exile.totem_overworld"), true);
                return EnumActionResult.FAIL;
            }
            return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
        }
    }
}
