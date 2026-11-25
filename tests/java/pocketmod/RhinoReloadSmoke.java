package pocketmod;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;

/** Save/reload and replacing a disconnected player object without launching Minecraft. */
public final class RhinoReloadSmoke {
    static final class Terrain extends EncounterWaveSmoke.Terrain {
        @Override public net.minecraft.util.math.RayTraceResult rayTraceBlocks(net.minecraft.util.math.Vec3d a,net.minecraft.util.math.Vec3d b,boolean liquid,boolean noBox,boolean last){
            return new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.BLOCK,b,net.minecraft.util.EnumFacing.UP,new net.minecraft.util.math.BlockPos(b));
        }
    }
    static EncounterWaveSmoke.Player player(World world)throws Exception {
        EncounterWaveSmoke.Player p=(EncounterWaveSmoke.Player)TrialPlacementSmoke.unsafe.allocateInstance(EncounterWaveSmoke.Player.class);
        p.world=world;p.data=new NBTTagCompound();p.posX=10;p.posY=65;
        net.minecraft.network.datasync.EntityDataManager manager=new net.minecraft.network.datasync.EntityDataManager(p);
        BloodthirstSmoke.field(p,Entity.class,"dataManager",manager);
        java.lang.reflect.Field health=EntityLivingBase.class.getDeclaredField("HEALTH");health.setAccessible(true);
        manager.register((net.minecraft.network.datasync.DataParameter<Float>)health.get(null),20f);
        return p;
    }
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        Terrain world=(Terrain)TrialPlacementSmoke.unsafe.allocateInstance(Terrain.class);
        world.blocks=new HashMap<>();world.chunks=new HashMap<>();world.spawned=new ArrayList<>();world.storage=new MapStorage(null);
        world.info=new WorldInfo(new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.DEFAULT),"rhino-reload");
        BloodthirstSmoke.field(world,World.class,"provider",new WorldProviderSurface());
        BloodthirstSmoke.field(world,World.class,"worldInfo",world.info);
        BloodthirstSmoke.field(world,World.class,"profiler",new net.minecraft.profiler.Profiler());
        BloodthirstSmoke.field(world,World.class,"rand",new Random(1));
        EncounterWaveSmoke.Player before=player(world);
        BloodthirstSmoke.field(world,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(before)));
        EntityRunningRhino boss=new EntityRunningRhino(world);boss.setPosition(0,65,0);boss.setAttackTarget(before);
        NBTTagCompound save=new NBTTagCompound();boss.writeEntityToNBT(save);
        check(save.hasUniqueId("RhinoOpponent"),"save retains engaged player UUID");
        EntityRunningRhino restored=new EntityRunningRhino(world);restored.setPosition(0,65,0);restored.readEntityFromNBT(save);
        check(restored.action()==EntityRunningRhino.ROAM&&!restored.isAIDisabled(),"reload resets attack animation without disabling AI");
        check(restored.recoverTarget()==before,"reload reacquires engaged player behind a barrier without teleporting");
        EncounterWaveSmoke.Player after=player(world);
        BloodthirstSmoke.field(world,World.class,"playerEntities",new ArrayList<>(Collections.singletonList(after)));
        check(restored.recoverTarget()==after&&restored.getAttackTarget()==after,"reconnect replaces stale player reference with live object of same UUID");
        after.creative=true;check(restored.recoverTarget()==null,"creative player is never attacked");after.creative=false;
        world.playerEntities.clear();check(restored.recoverTarget()==null,"offline target is not attacked");
        world.playerEntities.add(after);check(restored.recoverTarget()==after,"returning player resumes encounter without movement");
        save.removeTag("RhinoOpponentMost");save.removeTag("RhinoOpponentLeast");restored.readEntityFromNBT(save);
        check(restored.recoverTarget()==after,"old saves missing opponent UUID recover nearby player");
        System.out.println("RhinoReloadSmoke: reload, obstacle, reconnect, offline, creative and legacy recovery passed");
    }
}
