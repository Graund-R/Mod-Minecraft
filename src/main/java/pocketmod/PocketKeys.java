package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class PocketKeys {
    public static Item POCKET_KEY;

    private PocketKeys() {}

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        POCKET_KEY = new PocketKeyItem();
        POCKET_KEY.setRegistryName(PocketMod.MODID, "pocket_key");
        POCKET_KEY.setUnlocalizedName(PocketMod.MODID + ".pocket_key");
        POCKET_KEY.setMaxStackSize(1);
        event.getRegistry().register(POCKET_KEY);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (POCKET_KEY != null) {
            ModelLoader.setCustomModelResourceLocation(POCKET_KEY, 0,
                    new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                            new ResourceLocation(PocketMod.MODID, "pocket_key"), "inventory"));
        }
    }

    /** Legacy network-wide key, retained only for doors migrated from older saves. */
    public static ItemStack createKeyFor(java.util.UUID owner) {
        ItemStack stack = new ItemStack(POCKET_KEY);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("pocket_owner", owner.toString());
        stack.setTagCompound(tag);
        return stack;
    }

    /** New key bound to one purchased door pair. */
    public static ItemStack createKeyFor(java.util.UUID owner, String pairId, boolean oneTime) {
        ItemStack stack = new ItemStack(POCKET_KEY);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("pocket_owner", owner.toString());
        tag.setString("pocket_pair", pairId);
        tag.setBoolean("pocket_one_time", oneTime);
        stack.setTagCompound(tag);
        return stack;
    }

    public static ItemStack createOwnerKey(EntityPlayer player) {
        return createKeyFor(player.getUniqueID());
    }

    public static boolean opensPair(ItemStack stack, java.util.UUID owner, String pairId) {
        if (stack.isEmpty() || POCKET_KEY == null || stack.getItem() != POCKET_KEY || pairId == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !owner.toString().equals(tag.getString("pocket_owner"))) return false;
        String keyPair = tag.getString("pocket_pair");
        // Old keys only continue to work on doors migrated from the previous system.
        if (keyPair.isEmpty()) return pairId.startsWith("legacy:");
        return pairId.equals(keyPair);
    }

    public static boolean isOneTime(ItemStack stack) {
        if (stack.isEmpty() || POCKET_KEY == null || stack.getItem() != POCKET_KEY) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean("pocket_one_time");
    }

    /** Kept for older callers. */
    public static boolean opensNetwork(ItemStack stack, java.util.UUID owner) {
        if (stack.isEmpty() || POCKET_KEY == null || stack.getItem() != POCKET_KEY) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && owner.toString().equals(tag.getString("pocket_owner"));
    }

    public static boolean hasOwnerKey(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isOwnerKey(stack, player)) return true;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isOwnerKey(stack, player)) return true;
        }
        return false;
    }

    public static boolean isOwnerKey(ItemStack stack, EntityPlayer player) {
        if (stack.isEmpty() || POCKET_KEY == null || stack.getItem() != POCKET_KEY) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && player.getUniqueID().toString().equals(tag.getString("pocket_owner"));
    }
}
