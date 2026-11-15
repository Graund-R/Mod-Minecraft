package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class PocketGuiHandler implements IGuiHandler {
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == PocketMod.GUI_WAREHOUSE && AbilityStorage.isUnlocked(player, AbilityType.WAREHOUSE)) {
            return new ContainerWarehouse(player);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == PocketMod.GUI_WAREHOUSE && AbilityStorage.isUnlocked(player, AbilityType.WAREHOUSE)) {
            return new GuiWarehouse(player);
        }
        return null;
    }
}
