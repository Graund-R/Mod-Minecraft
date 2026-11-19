package pocketmod;

import net.minecraft.block.BlockDoor;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDoor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.util.NonNullList;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class PocketDoorItems {
    public static Item DOOR;
    private PocketDoorItems() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        DOOR = new ItemDoor((BlockDoor) Blocks.IRON_DOOR);
        DOOR.setRegistryName(PocketMod.MODID, "pocket_door");
        DOOR.setUnlocalizedName(PocketMod.MODID + ".pocket_door");
        DOOR.setCreativeTab(net.minecraft.creativetab.CreativeTabs.REDSTONE);
        event.getRegistry().register(DOOR);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        if (DOOR != null) ModelLoader.setCustomModelResourceLocation(DOOR, 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                        new ResourceLocation(PocketMod.MODID, "pocket_door"), "inventory"));
    }
}
