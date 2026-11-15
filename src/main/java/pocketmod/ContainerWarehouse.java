package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Обычный контейнер с переносом предметов и shift-кликом. */
public class ContainerWarehouse extends Container {
    private final int warehouseSlots;

    public ContainerWarehouse(EntityPlayer player) {
        warehouseSlots = Math.max(0, Math.min(3, AbilityStorage.getLevel(player, AbilityType.WAREHOUSE)));
        WarehouseInventory warehouse = new WarehouseInventory(player);
        for (int i = 0; i < warehouseSlots; i++) {
            addSlotToContainer(new Slot(warehouse, i, 62 + i * 24, 35));
        }
        InventoryPlayer inv = player.inventory;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override public boolean canInteractWith(EntityPlayer player) { return player.isEntityAlive(); }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved = index < warehouseSlots
                ? mergeItemStack(source, warehouseSlots, inventorySlots.size(), true)
                : mergeItemStack(source, 0, warehouseSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        slot.onTake(player, original);
        return original;
    }
}
