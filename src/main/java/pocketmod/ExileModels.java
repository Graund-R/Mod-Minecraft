package pocketmod;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = PocketMod.MODID, value = Side.CLIENT)
public final class ExileModels {
    private ExileModels() {}

    @SubscribeEvent
    public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (!AbilityDiscoveryItem.id(event.getItemStack()).isEmpty())
            event.getToolTip()
                    .add(
                            net.minecraft.client.resources.I18n.format(
                                    "pocketmod.exile.discovery_use"));
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for(int tier=0;tier<3;tier++) ModelLoader.setCustomModelResourceLocation(TemporalItems.DIAL,tier,new ModelResourceLocation(new ResourceLocation(PocketMod.MODID,"temporal_dial_"+tier),"inventory"));
        for (int i = 0; i < AbilityDiscoveryItem.IDS.length; i++) {
            String id = AbilityDiscoveryItem.IDS[i];
            ModelLoader.setCustomModelResourceLocation(
                    ExileItems.DISCOVERY,
                    i,
                    new ModelResourceLocation(
                            new ResourceLocation(PocketMod.MODID, "discovery_" + id), "inventory"));
        }
        ModelLoader.setCustomModelResourceLocation(
                ExileItems.TOTEM_ITEM,
                0,
                new ModelResourceLocation(
                        new ResourceLocation(PocketMod.MODID, "resurrection_totem"), "inventory"));
    }
}
