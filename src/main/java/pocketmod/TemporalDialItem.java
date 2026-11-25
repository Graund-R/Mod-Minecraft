package pocketmod;

import java.util.List;
import java.util.UUID;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraftforge.fml.relauncher.*;

public final class TemporalDialItem extends Item {
    public TemporalDialItem() {
        setRegistryName(PocketMod.MODID,"temporal_dial");setUnlocalizedName("pocketmod.temporal_dial");
        setMaxStackSize(1);setHasSubtypes(true);
    }
    public static boolean isDial(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof TemporalDialItem; }
    public static int tier(ItemStack stack) { return Math.max(0,Math.min(2,stack.getMetadata())); }
    public static NBTTagCompound data(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound n=stack.getTagCompound();
        if (!n.hasKey("dial_id")) {
            n.setString("dial_id",UUID.randomUUID().toString());n.setInteger("lapse",10);
            n.setInteger("charges",0);n.setInteger("rewind",0);n.setBoolean("share",true);
        }
        return n;
    }
    public static ItemStack find(EntityPlayer player) {
        ItemStack held=held(player);if(!held.isEmpty())return held;
        for (ItemStack s:player.inventory.mainInventory) if (isDial(s)) return s;
        for (ItemStack s:player.inventory.offHandInventory) if (isDial(s)) return s;
        return ItemStack.EMPTY;
    }
    public static ItemStack held(EntityPlayer player) {
        if (isDial(player.getHeldItemMainhand())) return player.getHeldItemMainhand();
        return isDial(player.getHeldItemOffhand()) ? player.getHeldItemOffhand() : ItemStack.EMPTY;
    }
    @Override public String getUnlocalizedName(ItemStack stack) { return "item.pocketmod.temporal_dial."+tier(stack); }
    @Override public void getSubItems(CreativeTabs tab,NonNullList<ItemStack> out) {
        if (isInCreativeTab(tab)) for(int i=0;i<3;i++) out.add(new ItemStack(this,1,i));
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        if(world.isRemote)PocketMod.proxy.openTemporalPanel();
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS,player.getHeldItem(hand));
    }

    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack,World world,List<String> lines,ITooltipFlag flag) {
        lines.add(net.minecraft.client.resources.I18n.format("pocketmod.temporal.legacy"));
    }
}
