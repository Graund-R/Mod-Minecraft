package pocketmod;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Слоты склада у окна инвентаря (E): клики приходят пакетом, сервер меняет склад и предмет на курсоре. */
public final class WarehouseSlots {
    private WarehouseSlots() {}

    public static int slots(net.minecraft.entity.player.EntityPlayer p) {
        return ExileProgression.inMode(p) ? 0 : slotsForLevel(AbilityStorage.getLevel(p, AbilityType.WAREHOUSE));
    }

    static int slotsForLevel(int level) {
        return Math.max(0, Math.min(3, level));
    }

    /** flags: бит 0 — правая кнопка, бит 1 — Shift. */
    public static void click(EntityPlayerMP p, int slot, int flags) {
        if (p.openContainer != p.inventoryContainer) return;
        if (slot < 0 || slot >= slots(p)) return;
        boolean right = (flags & 1) != 0;
        boolean shift = (flags & 2) != 0;
        WarehouseInventory inv = new WarehouseInventory(p);
        ItemStack in = inv.getStackInSlot(slot);
        ItemStack cursor = p.inventory.getItemStack();

        if (shift) {
            if (cursor.isEmpty() && !in.isEmpty()) {
                ItemStack move = in.copy();
                p.inventory.addItemStackToInventory(move);
                inv.setInventorySlotContents(slot, move.isEmpty() ? ItemStack.EMPTY : move);
            }
        } else if (cursor.isEmpty()) {
            if (!in.isEmpty()) {
                if (right) {
                    ItemStack half = in.splitStack((in.getCount() + 1) / 2);
                    p.inventory.setItemStack(half);
                    inv.setInventorySlotContents(slot, in.isEmpty() ? ItemStack.EMPTY : in);
                } else {
                    p.inventory.setItemStack(in);
                    inv.setInventorySlotContents(slot, ItemStack.EMPTY);
                }
            }
        } else if (in.isEmpty()) {
            if (right) {
                ItemStack one = cursor.splitStack(1);
                inv.setInventorySlotContents(slot, one);
                p.inventory.setItemStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            } else {
                inv.setInventorySlotContents(slot, cursor);
                p.inventory.setItemStack(ItemStack.EMPTY);
            }
        } else if (ItemStack.areItemsEqual(in, cursor) && ItemStack.areItemStackTagsEqual(in, cursor)) {
            int space = Math.min(in.getMaxStackSize(), inv.getInventoryStackLimit()) - in.getCount();
            int move = right ? Math.min(1, space) : Math.min(space, cursor.getCount());
            if (move > 0) {
                in.grow(move);
                cursor.shrink(move);
                inv.setInventorySlotContents(slot, in);
                p.inventory.setItemStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            }
        } else {
            inv.setInventorySlotContents(slot, cursor);
            p.inventory.setItemStack(in);
        }
        inv.markDirty();
        p.inventoryContainer.detectAndSendChanges();
        p.updateHeldItem();
    }
}
