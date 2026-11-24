package pocketmod;

import java.lang.reflect.Field;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import sun.misc.Unsafe;

/** Sun equipment and debug permission regressions, without running the game. */
public final class ExileSpawnerSmoke {
    static class Player extends BloodthirstSmoke.Player {
        boolean operator;
        @Override public boolean canUseCommand(int level,String command) { return operator; }
        @Override public MinecraftServer getServer() { return null; }
    }
    public static void main(String[] args) throws Exception {
        TrialPlacementSmoke.main(args);
        TrialPlacementSmoke.Terrain world=TrialPlacementSmoke.terrain();
        EntitySkeleton skeleton=new EntitySkeleton(world);
        ExileEncounters.protectFromSun(skeleton);
        ItemStack helmet=skeleton.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        BloodthirstSmoke.check(!helmet.isEmpty(),"skeleton head is protected from vanilla sun ignition");
        BloodthirstSmoke.check(helmet.hasTagCompound()&&helmet.getTagCompound().getBoolean("Unbreakable"),"sun protection does not wear out");
        helmet.damageItem(10000,skeleton);
        BloodthirstSmoke.check(!helmet.isEmpty()&&helmet.getItemDamage()==0,"vanilla item damage cannot remove sun protection");
        Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Player player=(Player)((Unsafe)f.get(null)).allocateInstance(Player.class);
        player.data=new NBTTagCompound(); player.world=world;
        ExileMode.state(player).setBoolean("debug_enabled",true);
        BloodthirstSmoke.check(!ExileDebug.mayEdit(player),"non-operator cannot mutate debug state");
        ExileDebug.action(player,1,4,true);
        BloodthirstSmoke.check(ExileWorldData.get(world).exposure(0,0,0)==0,"unauthorized stage packet changes no chunks");
        NBTTagCompound before=player.data.copy();
        for(int op:new int[]{6,7,8})ExileDebug.action(player,op,30,4);
        BloodthirstSmoke.check(before.equals(player.data),"new test controls reject unauthorized edits");
        player.operator=true;
        ExileDebug.action(player,1,4,500);
        BloodthirstSmoke.check(ExileWorldData.get(world).exposure(0,0,0)==0,"oversized edit region is rejected on server");
        BloodthirstSmoke.check(ExileDebug.mayEdit(player),"operator may use testing mutations");
        ExileDebug.action(player,1,5,true);
        BloodthirstSmoke.check(ExileWorldData.get(world).exposure(0,0,0)==0,"out-of-range stage is rejected");
        System.out.println("ExileSpawnerSmoke: 9 sun/permission checks passed; no game launched");
    }
}
