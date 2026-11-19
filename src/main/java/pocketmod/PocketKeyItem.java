package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class PocketKeyItem extends Item {
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!PocketKeys.isOwnerKey(stack, player)) {
            if (world.isRemote) return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.key_not_yours"), true);
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            PocketTeleporter.toggle((EntityPlayerMP) player);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }
}
