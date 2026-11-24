package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.world.*;

/** Exercises the real vanilla creative count restoration, without starting a server. */
public final class DiscoveryConsumeSmoke {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static class Player extends BloodthirstSmoke.Player {
        ItemStack held;
        net.minecraft.util.CooldownTracker cooldown;
        int syncs;
        Player(){super();}
        @Override public ItemStack getHeldItem(EnumHand hand){return held;}
        @Override public void setHeldItem(EnumHand hand,ItemStack stack){held=stack;}
        @Override public net.minecraft.util.CooldownTracker getCooldownTracker(){return cooldown;}
        @Override public boolean isHandActive(){return false;}
        @Override public void sendContainerToPlayer(Container container){syncs++;}
    }
    static class UsedIcon extends Item {
        boolean legacy;
        UsedIcon(boolean legacy){this.legacy=legacy;setMaxStackSize(1);}
        @Override public ActionResult<ItemStack> onItemRightClick(World w,EntityPlayer p,EnumHand hand){
            ItemStack stack=p.getHeldItem(hand);
            if(legacy){stack.shrink(1);return new ActionResult<>(EnumActionResult.SUCCESS,stack);}
            return new ActionResult<>(EnumActionResult.SUCCESS,AbilityDiscoveryItem.consume(stack));
        }
    }
    public static void main(String[] args)throws Exception{
        TrialPlacementSmoke.main(args);
        Player p=(Player)TrialPlacementSmoke.unsafe.allocateInstance(Player.class);
        p.cooldown=new net.minecraft.util.CooldownTracker();
        World w=(World)TrialPlacementSmoke.unsafe.allocateInstance(TrialPlacementSmoke.Terrain.class);
        p.world=w;
        PlayerInteractionManager manager=new PlayerInteractionManager(w);
        BloodthirstSmoke.field(manager,PlayerInteractionManager.class,"gameType",GameType.CREATIVE);
        p.held=new ItemStack(new UsedIcon(true));
        manager.processRightClick(p,w,p.held,EnumHand.MAIN_HAND);
        check(!p.held.isEmpty(),"reproduce old creative icon reappearance in vanilla handler");
        for(GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}){
            BloodthirstSmoke.field(manager,PlayerInteractionManager.class,"gameType",mode);
            for(EnumHand hand:EnumHand.values()){
                p.held=new ItemStack(new UsedIcon(false));int before=p.syncs;
                check(manager.processRightClick(p,w,p.held,hand)==EnumActionResult.SUCCESS,"successful use");
                check(p.held.isEmpty(),"consumed in "+mode+" "+hand);
                check(p.syncs>before,"vanilla inventory update sent");
            }
        }
        System.out.println("DiscoveryConsumeSmoke: "+checks+" checks passed; no game launched");
    }
}
