package pocketmod;

import net.minecraft.entity.passive.EntityCow;
import net.minecraft.pathfinding.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Path choice, task ownership and locked-flight previews; never starts the game. */
public final class EscapeAndFlightSmoke {
    static final class Terrain extends TrialPlacementSmoke.Terrain {
        Terrain(){super();}
        @Override public boolean isBlockLoaded(BlockPos pos){return true;}
    }
    static final class Navigation extends PathNavigateGround {
        Path accepted;
        Navigation(EntityCow cow,World world){super(cow,world);}
        @Override public Path getPathToPos(BlockPos pos){return new Path(new PathPoint[]{new PathPoint(pos.getX(),pos.getY(),pos.getZ())});}
        @Override public boolean setPath(Path path,double speed){accepted=path;return true;}
        @Override public boolean noPath(){return accepted==null;}
    }
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        Terrain world=(Terrain)TrialPlacementSmoke.unsafe.allocateInstance(Terrain.class);
        TrialPlacementSmoke.Terrain fixture=TrialPlacementSmoke.terrain();
        world.blocks=fixture.blocks;world.chunk=fixture.chunk;world.storage=fixture.storage;world.info=fixture.info;
        BloodthirstSmoke.field(world,World.class,"provider",new net.minecraft.world.WorldProviderSurface());
        EntityCow cow=new EntityCow(world);cow.setPosition(8,65,8);
        Navigation navigation=new Navigation(cow,world);
        BloodthirstSmoke.field(cow,net.minecraft.entity.EntityLiving.class,"navigator",navigation);
        ExileEscapeTask task=new ExileEscapeTask(cow);
        check(!task.shouldExecute(),"clean area never forces escape");
        ExileWorldData data=ExileWorldData.get(world);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)data.setExposure(0,x,z,ExileConfig.fleeSeconds);
        check(task.shouldExecute()&&task.getMutexBits()==3,"infected creature owns movement and looking");
        task.startExecuting();task.updateTask();
        check(navigation.accepted!=null,"animal escapes even from centre of infected 3x3 region");
        PathPoint end=navigation.accepted.getFinalPathPoint();
        check(data.exposure(0,end.x>>4,end.z>>4)<ExileConfig.fleeSeconds,"path leads toward cleaner loaded terrain");
        cow.setPosition(end.x,end.y,end.z);
        check(!task.shouldContinueExecuting(),"ordinary behaviour resumes in clean terrain");
        check(Flight.heightForLevel(0)==0&&Flight.speedForLevel(0)==0,"locked flight preview is safe and has no flight");
        check(Flight.heightForLevel(99)==Flight.heightForLevel(3),"flight preview upper bounds are safe");
        System.out.println("EscapeAndFlightSmoke: path escape and flight preview checks passed");
    }
}
