package pocketmod;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Injected API fixture tests the real optional bridge; no Metamorph classes are linked. */
public final class MorphDuplicateSmoke {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static class Collection {
        Set<Object> forms=new HashSet<>();
        public boolean acquiredMorph(Object form){return forms.contains(form);}
    }
    public static Collection collection=new Collection();
    public static Object capability(EntityPlayer player){return collection;}
    public static class Controller extends LoadController {
        Controller(){super(null);}
        @Override public LoaderState.ModState getModState(ModContainer container){return LoaderState.ModState.AVAILABLE;}
    }
    public static class Pre extends Event {
        public EntityPlayer player;public Object morph;
        Pre(EntityPlayer p,Object m){player=p;morph=m;}
        @Override public boolean isCancelable(){return true;}
    }
    static void set(Object target,Class<?> type,String name,Object value)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static boolean denied(Method gate,EntityPlayer player,Object morph)throws Exception {
        Pre event=new Pre(player,morph);gate.invoke(null,event);return event.isCanceled();
    }
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        Loader loader=(Loader)TrialPlacementSmoke.unsafe.allocateInstance(Loader.class);
        set(null,Loader.class,"instance",loader);
        Map<String,ModContainer> mods=new HashMap<>();set(loader,Loader.class,"namedMods",mods);
        check(!MorphIntegration.isAvailable(),"optional bridge loads while Metamorph absent");
        mods.put("metamorph",null);set(loader,Loader.class,"modController",TrialPlacementSmoke.unsafe.allocateInstance(Controller.class));
        Class<?> api=Class.forName("pocketmod.MorphIntegration$Api");Object fixture=TrialPlacementSmoke.unsafe.allocateInstance(api);
        set(fixture,api,"getCapability",MorphDuplicateSmoke.class.getMethod("capability",EntityPlayer.class));
        set(fixture,api,"hasAcquiredMorph",Collection.class.getMethod("acquiredMorph",Object.class));
        set(null,MorphIntegration.class,"api",fixture);
        BloodthirstSmoke.Player p=(BloodthirstSmoke.Player)TrialPlacementSmoke.unsafe.allocateInstance(BloodthirstSmoke.Player.class);
        p.data=new NBTTagCompound();p.world=TrialPlacementSmoke.terrain();
        AbilityStorage.setLevel(p,AbilityType.MORPHISM,1);PocketConfig.morphEnabled=true;
        Method gate=MorphIntegration.class.getDeclaredMethod("cancelAcquisitionWhenLocked",Event.class);gate.setAccessible(true);
        check(!denied(gate,p,"cow/adult"),"uncollected morph allowed");
        collection.forms.add("cow/adult");
        check(denied(gate,p,new String("cow/adult")),"owned equivalent morph suppressed");
        check(!denied(gate,p,"cow/baby"),"distinct native variant remains obtainable");
        collection.forms.remove("cow/adult");
        check(!denied(gate,p,"cow/adult"),"deleted morph immediately obtainable again");
        AbilityStorage.setLevel(p,AbilityType.MORPHISM,0);
        check(denied(gate,p,"new mob"),"locked ability still blocks acquisition");
        System.out.println("MorphDuplicateSmoke: "+checks+" checks passed; no game launched");
    }
}
