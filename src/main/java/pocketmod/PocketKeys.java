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
    public static Item IRON_KEY;
    public static Item GOLD_KEY;

    private PocketKeys() {}

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        POCKET_KEY = register(event, "pocket_key");
        IRON_KEY = register(event, "pocket_iron_key");
        GOLD_KEY = register(event, "pocket_gold_key");
    }

    private static Item register(RegistryEvent.Register<Item> event, String name) {
        Item item = new PocketKeyItem();
        item.setRegistryName(PocketMod.MODID, name);
        item.setUnlocalizedName(PocketMod.MODID + "." + name);
        item.setMaxStackSize(1);
        item.setCreativeTab(PocketCreativeTab.ITEMS);
        event.getRegistry().register(item);
        return item;
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        registerModel(POCKET_KEY, "pocket_key");
        registerModel(IRON_KEY, "pocket_iron_key");
        registerModel(GOLD_KEY, "pocket_gold_key");
    }

    @SideOnly(Side.CLIENT)
    private static void registerModel(Item item, String name) {
        if (item != null) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                            new ResourceLocation(PocketMod.MODID, name), "inventory"));
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
        return createKeyFor(owner, pairId, oneTime, 0);
    }

    public static ItemStack createKeyFor(java.util.UUID owner, String pairId, boolean oneTime, int epoch) {
        ItemStack stack = new ItemStack(oneTime ? IRON_KEY : GOLD_KEY);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("pocket_owner", owner.toString());
        tag.setString("pocket_pair", pairId);
        tag.setBoolean("pocket_one_time", oneTime);
        tag.setInteger("pocket_key_epoch", Math.max(0, epoch));
        stack.setTagCompound(tag);
        return stack;
    }

    public static ItemStack createOwnerKey(EntityPlayer player) {
        return createKeyFor(player.getUniqueID());
    }

    public static boolean opensPair(ItemStack stack, java.util.UUID owner, String pairId) {
        return opensPair(stack, owner, pairId, 0);
    }

    public static boolean opensPair(ItemStack stack, java.util.UUID owner, String pairId, int currentEpoch) {
        if (!isKeyItem(stack) || pairId == null) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !owner.toString().equals(tag.getString("pocket_owner"))) return false;
        // Old NBT keys have no epoch and are treated as epoch 0 for migration.
        if (tag.getInteger("pocket_key_epoch") != currentEpoch) return false;
        String keyPair = tag.getString("pocket_pair");
        // Old keys only continue to work on doors migrated from the previous system.
        if (keyPair.isEmpty()) return pairId.startsWith("legacy:");
        return pairId.equals(keyPair);
    }

    public static boolean isOneTime(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == IRON_KEY) return true;
        if (stack.getItem() != POCKET_KEY && stack.getItem() != GOLD_KEY) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean("pocket_one_time");
    }

    /** Kept for older callers. */
    public static boolean opensNetwork(ItemStack stack, java.util.UUID owner) {
        if (!isKeyItem(stack)) return false;
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

    private static boolean isKeyItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == POCKET_KEY || stack.getItem() == IRON_KEY
                || stack.getItem() == GOLD_KEY);
    }

    public static boolean isOwnerKey(ItemStack stack, EntityPlayer player) {
        if (!isKeyItem(stack)) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && player.getUniqueID().toString().equals(tag.getString("pocket_owner"));
    }
}
