package pocketmod;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Old-save compatibility and server gating with an in-memory world, no Minecraft process. */
public final class ExileExclusionsSmoke {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        TrialPlacementSmoke.main(args);
        TrialPlacementSmoke.Terrain world=TrialPlacementSmoke.terrain();
        ExileWorldData.get(world).enabled=true;
        BloodthirstSmoke.Player player=(BloodthirstSmoke.Player)TrialPlacementSmoke.unsafe.allocateInstance(BloodthirstSmoke.Player.class);
        player.data=new NBTTagCompound();
        BloodthirstSmoke.field(player,Entity.class,"world",world);
        AbilityStorage.setLevel(player,AbilityType.WAREHOUSE,3);
        ExileMode.state(player).setBoolean("open_warehouse",true);
        check(!ExileProgression.available(player,"warehouse"),"old unlock cannot expose warehouse in Exile shop");
        check(!ExileProgression.unopened(player).contains("warehouse"),"warehouse excluded from random rewards");
        check(!AbilityStorage.isUnlocked(player,AbilityType.WAREHOUSE),"server rejects old purchased warehouse");
        check(WarehouseSlots.slots(player)==0,"inventory warehouse panel hidden");
        check(AbilityStorage.getLevel(player,AbilityType.WAREHOUSE)==3,"old purchase retained for normal mode");
        WarehouseInventory inventory=new WarehouseInventory(player);
        inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));
        inventory.markDirty();
        NBTTagCompound saved=PlayerData.root(player).copy();
        WarehouseInventory.handleDeath(player,player);
        check(PlayerData.root(player).equals(saved),"retired warehouse contents are not charged or deleted in Exile");
        Totems.setPendingDeathTotem(player,0,4,65,4);
        check(!Totems.hasPendingDeathTotem(player),"no death-return prompt in Exile");
        Totems.setDeathTotem(player,0,4,65,4);
        check(!Totems.hasDeathTotem(player),"no death-return destination in Exile");
        ExileWorldData.get(world).enabled=false;
        check(ExileProgression.available(player,"warehouse"),"normal shop retains warehouse");
        check(AbilityStorage.isUnlocked(player,AbilityType.WAREHOUSE)&&WarehouseSlots.slots(player)==3,"normal mode restores old purchase");
        check(new WarehouseInventory(player).getStackInSlot(0).getCount()==7,"stored items survive mode change");
        Totems.setDeathTotem(player,0,4,65,4);
        check(Totems.hasDeathTotem(player),"normal mode retains death-return feature");
        System.out.println("ExileExclusionsSmoke: "+checks+" exclusion and old-save checks passed; no game launched");
    }
}
