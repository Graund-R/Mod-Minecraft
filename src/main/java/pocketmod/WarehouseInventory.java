package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Отдельное хранилище игрока в PlayerPersisted. */
public class WarehouseInventory implements IInventory {
    private static final String KEY = "pocketmod_warehouse";
    private final EntityPlayer player;
    private final NonNullList<ItemStack> stacks = NonNullList.withSize(3, ItemStack.EMPTY);

    public WarehouseInventory(EntityPlayer player) {
        this.player = player;
        NBTTagList list = PlayerData.root(player).getTagList(KEY, 10);
        for (int n = 0; n < list.tagCount(); n++) {
            NBTTagCompound tag = list.getCompoundTagAt(n);
            int slot = tag.getByte("Slot") & 255;
            if (slot < stacks.size()) stacks.set(slot, new ItemStack(tag));
        }
    }

    @Override public int getSizeInventory() { return stacks.size(); }
    @Override public boolean isEmpty() {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }
    @Override public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= stacks.size()) return ItemStack.EMPTY;
        ItemStack result = ItemStackHelper.getAndSplit(stacks, index, count);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= stacks.size()) return ItemStack.EMPTY;
        ItemStack result = ItemStackHelper.getAndRemove(stacks, index);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= stacks.size()) return;
        stacks.set(index, stack == null ? ItemStack.EMPTY : stack);
        if (!stacks.get(index).isEmpty() && stacks.get(index).getCount() > getInventoryStackLimit()) {
            stacks.get(index).setCount(getInventoryStackLimit());
        }
        markDirty();
    }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public void markDirty() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;
            NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
            tag.setByte("Slot", (byte) i);
            list.appendTag(tag);
        }
        PlayerData.root(player).setTag(KEY, list);
    }
    @Override public boolean isUsableByPlayer(EntityPlayer p) { return p == player && p.isEntityAlive(); }
    @Override public void openInventory(EntityPlayer p) {}
    @Override public void closeInventory(EntityPlayer p) { markDirty(); }
    @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return index >= 0 && index < stacks.size(); }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) {}
    @Override public int getFieldCount() { return 0; }
    @Override public void clear() {
        for (int i = 0; i < stacks.size(); i++) stacks.set(i, ItemStack.EMPTY);
        markDirty();
    }
    @Override public String getName() { return "pocketmod.warehouse"; }
    @Override public boolean hasCustomName() { return false; }
    @Override public ITextComponent getDisplayName() { return new TextComponentTranslation("pocketmod.gui.warehouse_title"); }

    /** Каждый занятый слот оплачивается отдельно при смерти. Неоплаченные предметы выпадают. */
    public static void handleDeath(EntityPlayer oldPlayer, EntityPlayer newPlayer) {
        if (oldPlayer.world.isRemote) return;
        if (ExileProgression.inMode(newPlayer)) return;
        WarehouseInventory inventory = new WarehouseInventory(newPlayer);
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            double fee = PocketConfig.warehouseDeathCost * Mana.getPriceMultiplier(newPlayer);
            if (!Mana.spend(newPlayer, fee)) {
                InventoryHelper.spawnItemStack(oldPlayer.world, oldPlayer.posX, oldPlayer.posY, oldPlayer.posZ, stack.copy());
                inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
        inventory.markDirty();
    }
}
