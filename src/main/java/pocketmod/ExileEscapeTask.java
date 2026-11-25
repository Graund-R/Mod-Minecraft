package pocketmod;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;

/** Holds the navigation mutex so wandering, feeding and village routines cannot cancel escape. */
final class ExileEscapeTask extends EntityAIBase {
    private final EntityCreature mob;
    private int retry;
    ExileEscapeTask(EntityCreature mob){this.mob=mob;setMutexBits(3);}
    private boolean danger(){
        return ExileMode.enabled(mob.world)&&!mob.getEntityData().getBoolean(ExileMode.PURSUER)
            &&mob.isNonBoss()&&!(mob instanceof EntityDecoy)&&!PocketDimensionManager.isPocketDimension(mob.dimension)
            &&ExileWorldData.get(mob.world).exposure(mob.dimension,mob.getPosition().getX()>>4,mob.getPosition().getZ()>>4)>=ExileConfig.fleeSeconds;
    }
    @Override public boolean shouldExecute(){return danger();}
    @Override public boolean shouldContinueExecuting(){return danger();}
    @Override public void startExecuting(){retry=0;mob.setAttackTarget(null);}
    @Override public void resetTask(){mob.getNavigator().clearPath();}
    @Override public void updateTask(){
        mob.setAttackTarget(null);
        if(--retry>0&&!mob.getNavigator().noPath())return;
        retry=20;
        BlockPos origin=mob.getPosition();int cx=origin.getX()>>4,cz=origin.getZ()>>4;
        ExileWorldData data=ExileWorldData.get(mob.world);
        double current=data.exposure(mob.dimension,cx,cz),best=Double.MAX_VALUE;Path chosen=null;
        // Try nearby boundary crossings first, rather than a distant chunk centre at the wrong height.
        java.util.List<BlockPos> candidates=new java.util.ArrayList<>();
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            if(dx==0&&dz==0||data.exposure(mob.dimension,cx+dx,cz+dz)>=current)continue;
            int x=Math.max((cx+dx)*16+1,Math.min((cx+dx)*16+14,origin.getX()));
            int z=Math.max((cz+dz)*16+1,Math.min((cz+dz)*16+14,origin.getZ()));
            BlockPos column=new BlockPos(x,origin.getY(),z);
            if(mob.world.isBlockLoaded(column))candidates.add(column);
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos->data.exposure(mob.dimension,pos.getX()>>4,pos.getZ()>>4)*1000+origin.distanceSq(pos)));
        int attempts=0;
        for(BlockPos column:candidates) {
            if(attempts>=8)break;
            for(int n=0;n<=24;n++) {
                int dy=n==0?0:(n%2==1?(n+1)/2:-n/2);
                BlockPos feet=column.up(dy);
                if(!PocketTeleporter.standable(mob.world,feet))continue;
                attempts++;
                Path path=mob.getNavigator().getPathToPos(feet);
                if(path==null||path.getFinalPathPoint()==null)continue;
                net.minecraft.pathfinding.PathPoint end=path.getFinalPathPoint();
                BlockPos endpoint=new BlockPos(end.x,end.y,end.z);
                if(endpoint.distanceSq(feet)>=origin.distanceSq(feet)-1)continue;
                double score=data.exposure(mob.dimension,feet.getX()>>4,feet.getZ()>>4)*1000+origin.distanceSq(feet);
                if(score<best){best=score;chosen=path;}
                break;
            }
        }
        if(chosen!=null)mob.getNavigator().setPath(chosen,1.5);
    }
}
