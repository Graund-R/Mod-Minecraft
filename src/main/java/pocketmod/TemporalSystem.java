package pocketmod;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.*;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Three branches of one ability. Every rewind restores one recorded server tick at a time. */
@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class TemporalSystem {
    static final int STEP=1,HISTORY=401,MAX_BYTES=32*1024*1024;
    static final int IDLE=0,STASIS=1,FUTURE=2,RETURN=4,REWIND=5;
    private static final Map<WorldServer,Long> retryAfter=new IdentityHashMap<>();
    static final Map<WorldServer,Track> tracks=new IdentityHashMap<>();
    public static volatile boolean clientFrozen,clientShare;
    public static volatile boolean clientCanMove=true;
    private static volatile Set<Integer> clientLive=Collections.emptySet();
    private static volatile Set<BlockPos> clientAwake=Collections.emptySet();
    private static volatile String clientOwner="";
    public static volatile long clientDay;
    public static volatile int clientDimension=Integer.MIN_VALUE;
    static final class Hit {
        final UUID target;final DamageSource source;final float amount;
        Hit(EntityLivingBase target,DamageSource source,float amount){this.target=target.getUniqueID();this.source=source;this.amount=amount;}
    }
    static final class Track {
        final WorldServer world;
        final TemporalHistory<TemporalSnapshot> history=new TemporalHistory<>(HISTORY);
        final Map<BlockPos,IBlockState> changed=new LinkedHashMap<>();
        final List<Hit> hits=new ArrayList<>();
        final Map<BlockPos,NBTTagCompound> changedTiles=new LinkedHashMap<>();
        final Set<UUID> players=new HashSet<>();
        final Map<BlockPos,Integer> awake=new HashMap<>();
        String sessionId="";
        UUID owner;
        // Kept for reading legacy nested-item snapshots; no item controls access or resources.
        String dialId="";NBTTagCompound dial=new NBTTagCompound();
        int mode,remaining,elapsed,budget,branchLevel;
        long clock;
        boolean restoring,restoringSpawn,applying,invalid;
        TemporalSnapshot checkpoint;
        Track(WorldServer world){this.world=world;}
    }
    private TemporalSystem(){}
    static boolean reversing(int mode){return mode==RETURN||mode==REWIND;}
    public static boolean frozen(World world){
        if(world==null)return false;
        if(world.isRemote)return clientFrozen&&world.provider.getDimension()==clientDimension;
        Track t=tracks.get(world);return t!=null&&!t.restoring&&(t.mode==STASIS||reversing(t.mode));
    }
    public static double drainPerSec(EntityPlayer p){
        Track t=p.world.isRemote?null:tracks.get(p.world);
        NBTTagCompound state=p.world.isRemote?PlayerData.root(p).getCompoundTag("temporal"):new NBTTagCompound();
        int mode=t==null?state.getInteger("mode"):t.mode,level=t==null?state.getInteger("branch_level"):t.branchLevel;
        double rate=mode==FUTURE?TimeBranches.futureRate(level):mode==REWIND?TimeBranches.rewindRate(level):0;
        return rate*Mana.getPriceMultiplier(p);
    }
    public static boolean busy(EntityPlayer p){
        if(p.world.isRemote)return PlayerData.root(p).getCompoundTag("temporal").getInteger("mode")!=IDLE;
        Track t=tracks.get(p.world);return t!=null&&t.mode!=IDLE;
    }
    public static boolean canMove(EntityPlayer p){
        if(p.world.isRemote)return !frozen(p.world)||clientCanMove;
        Track t=tracks.get(p.world);
        return t==null||t.mode==IDLE||t.mode==FUTURE||(t.mode==STASIS&&p.getUniqueID().equals(t.owner));
    }
    public static void clientState(NBTTagCompound n,int dimension){
        clientDimension=dimension;clientOwner=n.getString("owner_id");clientShare=false;clientDay=n.getLong("day");clientFrozen=n.getBoolean("frozen");clientCanMove=n.getBoolean("move");
        Set<Integer> live=new HashSet<>();for(int id:n.getIntArray("live"))live.add(id);clientLive=live;
        Set<BlockPos> awake=new HashSet<>();int[] positions=n.getIntArray("awake");for(int i=0;i+2<positions.length;i+=3)awake.add(new BlockPos(positions[i],positions[i+1],positions[i+2]));clientAwake=awake;
    }
    public static NBTTagCompound status(EntityPlayerMP p){
        NBTTagCompound n=new NBTTagCompound();Track t=tracks.get(p.world);
        n.setBoolean("move",canMove(p));n.setBoolean("frozen",frozen(p.world));
        if(t!=null&&t.mode!=IDLE){
            n.setString("owner_id",t.owner==null?"":t.owner.toString());n.setLong("day",t.world.getWorldTime());n.setInteger("mode",t.mode);n.setBoolean("owner",p.getUniqueID().equals(t.owner));
            n.setInteger("branch_level",t.branchLevel);
            n.setInteger("left",reversing(t.mode)?Math.min(t.budget,Math.max(0,t.history.position())):t.remaining);
            if(reversing(t.mode)&&t.history.current()!=null){
                NBTTagList poses=new NBTTagList();int count=0;
                for(Entity entity:t.world.loadedEntityList){
                    NBTTagCompound recorded=t.history.current().entities.get(entity.getUniqueID());
                    if(recorded==null||entity.getDistanceSq(p)>96*96)continue;
                    NBTTagCompound pose=recorded.getCompoundTag("pocket_time_pose").copy();pose.setInteger("entity",entity.getEntityId());poses.appendTag(pose);if(++count>=256)break;
                }
                n.setTag("poses",poses);
            }
            n.setInteger("past",Math.max(0,t.history.position()));n.setInteger("future",Math.max(0,t.history.size()-t.history.position()-1));
            List<Integer> live=new ArrayList<>();for(Entity entity:t.world.loadedEntityList)if(liveItem(entity))live.add(entity.getEntityId());
            int[] ids=new int[live.size()];for(int i=0;i<ids.length;i++)ids[i]=live.get(i);n.setIntArray("live",ids);
            List<BlockPos> awake=new ArrayList<>();for(Map.Entry<BlockPos,Integer> entry:t.awake.entrySet())if(entry.getValue()>t.elapsed)awake.add(entry.getKey());
            int[] positions=new int[awake.size()*3];for(int i=0;i<awake.size();i++){positions[i*3]=awake.get(i).getX();positions[i*3+1]=awake.get(i).getY();positions[i*3+2]=awake.get(i).getZ();}n.setIntArray("awake",positions);
        }
        return n;
    }
    static void message(EntityPlayer p,String id,Object... args){p.sendStatusMessage(new TextComponentTranslation("pocketmod.temporal."+id,args),true);}
    private static void sync(Track t){for(EntityPlayer p:t.world.playerEntities)if(p instanceof EntityPlayerMP)NetworkHandler.sync((EntityPlayerMP)p);}
    private static boolean sameDimension(EntityPlayerMP p){return p.getServer()!=null&&p.world.playerEntities.size()==p.getServer().getPlayerList().getPlayers().size();}
    private static boolean permitted(EntityPlayerMP p,AbilityType branch){
        if(!TimeBranches.unlocked(p,branch)){message(p,"need_branch");return false;}
        if(!pocketmod.temporal.TemporalCorePlugin.loaded){message(p,"hooks_missing");return false;}
        if(!sameDimension(p)){message(p,"same_dimension");return false;}
        if(p.isSpectator()||Spector.isActive(p)||MorphIntegration.isMorphed(p)||p.isRiding()||PocketDimensionManager.isPocketDimension(p.dimension)){message(p,"unavailable");return false;}
        return true;
    }
    private static void prepare(Track t,EntityPlayerMP p){
        t.players.clear();for(EntityPlayer member:t.world.playerEntities)t.players.add(member.getUniqueID());
        t.sessionId=UUID.randomUUID().toString();t.awake.clear();t.owner=p.getUniqueID();t.elapsed=0;t.invalid=false;
        p.getEntityData().setBoolean("pocketmod_slow",false);
        for(EntityPlayer player:t.world.playerEntities)player.closeScreen();
    }
    public static boolean toggleOwnedStop(EntityPlayerMP p){
        Track t=tracks.get(p.world);if(t==null||t.mode!=STASIS||!p.getUniqueID().equals(t.owner))return false;
        finish(t);return true;
    }
    public static void stopOwned(EntityPlayerMP p){Track t=tracks.get(p.world);if(t!=null&&p.getUniqueID().equals(t.owner)&&t.mode==STASIS)finish(t);}
    /** Legacy Java entry point delegates to the existing slow branch, without item access. */
    public static void use(EntityPlayerMP p,ItemStack ignored){Abilities.toggleSlow(p);}
    public static void stasis(EntityPlayerMP p){
        if(!permitted(p,AbilityType.SLOW)||AbilityStorage.getLevel(p,AbilityType.SLOW)<3)return;
        Track t=tracks.get(p.world);if(t!=null&&t.mode!=IDLE){message(p,"occupied");return;}
        if(Mana.get(p)<TimeSlow.drainPerSec(p)){message(p,"empty_mana");return;}
        if(t==null){t=new Track(p.getServerWorld());tracks.put(t.world,t);}
        prepare(t,p);t.mode=STASIS;t.remaining=Integer.MAX_VALUE;
        p.getEntityData().setBoolean("pocketmod_slow",true);
        if(t.history.current()==null&&!capture(t)){finish(t);return;}
        sync(t);message(p,"started");
    }
    public static void preview(EntityPlayerMP p){
        Track t=tracks.get(p.world);
        if(t!=null&&t.mode!=IDLE){
            if(!p.getUniqueID().equals(t.owner)){message(p,"occupied");return;}
            if(t.mode==FUTURE)startReturn(t);else if(t.mode==RETURN)finish(t);else message(p,"occupied");
            return;
        }
        if(!permitted(p,AbilityType.TIME_FUTURE))return;
        int level=TimeBranches.level(p,AbilityType.TIME_FUTURE);
        if(!TimeBranches.pay(p,TimeBranches.futureEntry(level))){message(p,"empty_mana");return;}
        if(t==null){t=new Track(p.getServerWorld());tracks.put(t.world,t);}
        t.history.clear();t.changed.clear();t.changedTiles.clear();prepare(t,p);t.branchLevel=level;t.mode=FUTURE;t.remaining=TimeBranches.futureTicks(level);
        if(!capture(t)){finish(t);message(p,"limit");return;}
        t.checkpoint=t.history.current();sync(t);message(p,"preview_started");
    }
    public static void rewind(EntityPlayerMP p){
        Track t=tracks.get(p.world);
        if(t!=null&&t.mode==REWIND&&p.getUniqueID().equals(t.owner)){finish(t);return;}
        if(!permitted(p,AbilityType.TIME_REWIND))return;
        if(t==null||t.invalid||t.history.previous()==null){message(p,"no_history");return;}
        if(t.mode!=IDLE&&!(t.mode==STASIS&&p.getUniqueID().equals(t.owner))){message(p,"occupied");return;}
        flush(t);prepare(t,p);t.branchLevel=TimeBranches.level(p,AbilityType.TIME_REWIND);
        t.mode=REWIND;t.budget=TimeBranches.rewindTicks(t.branchLevel);sync(t);
    }
    static boolean capture(Track t){
        if(t.restoring||t.invalid)return false;
        try{
            t.clock=t.world.getTotalWorldTime();TemporalSnapshot frame=TemporalSnapshot.capture(t.world,t);
            t.history.add(frame);t.changed.clear();t.changedTiles.clear();
            int bytes=0;for(int i=0;i<t.history.size();i++)bytes+=t.history.at(i).bytes;
            if(t.mode==FUTURE&&bytes>MAX_BYTES){t.mode=RETURN;t.budget=t.history.position();return true;}
            while(t.history.size()>2&&bytes>MAX_BYTES)bytes-=t.history.removeOldest().bytes;
            return true;
        }catch(Exception error){t.invalid=true;System.err.println("[PocketMod] Temporal history stopped: "+error);return false;}
    }
    static void flush(Track t){if(!t.changed.isEmpty()&&!t.invalid)capture(t);}
    public static boolean beforeBlock(World world,BlockPos pos){
        if(world.isRemote)return false;Track t=tracks.get(world);
        if(t==null||t.restoring||!world.isBlockLoaded(pos))return false;
        if(reversing(t.mode))return true;
        if(t.invalid)return t.mode==FUTURE;
        if(t.changed.size()>=8192&&!t.changed.containsKey(pos)){t.invalid=true;return t.mode==FUTURE;}
        if(!t.changed.containsKey(pos)){
            t.changed.put(pos.toImmutable(),world.getBlockState(pos));
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
            if(tile!=null)t.changedTiles.put(pos.toImmutable(),tile.writeToNBT(new NBTTagCompound()));
        }
        return false;
    }
    private static void startReturn(Track t){
        if(t.mode!=FUTURE)return;
        if(t.history.current()==null||t.history.current().clock!=t.world.getTotalWorldTime()||!t.changed.isEmpty())capture(t);
        t.mode=RETURN;t.budget=Math.max(0,t.history.position());t.remaining=t.budget;sync(t);
        if(t.budget==0)finish(t);
    }
    /** One frame per real server tick, including all block deltas and player/mob states. */
    static boolean reverseFrame(Track t){
        if(t.budget<=0||t.history.previous()==null)return false;
        EntityPlayer owner=t.world.getPlayerEntityByUUID(t.owner);
        if(t.mode==REWIND&&(owner==null||!TimeBranches.pay(owner,TimeBranches.rewindRate(t.branchLevel)/20)))return false;
        t.restoring=true;
        try{t.history.current().blocks(t.world,true);t.history.back();t.history.current().restore(t.world,t);t.budget--;}
        finally{t.restoring=false;}
        return true;
    }
    private static void finish(Track t){
        EntityPlayer owner=t.owner==null?null:t.world.getPlayerEntityByUUID(t.owner);
        if(owner!=null)owner.getEntityData().setBoolean("pocketmod_slow",false);
        t.history.branch();t.mode=IDLE;t.remaining=t.budget=0;
        List<Hit> hits=new ArrayList<>(t.hits);t.hits.clear();t.applying=true;
        try{for(Hit hit:hits){Entity entity=t.world.getEntityFromUuid(hit.target);if(entity instanceof EntityLivingBase&&!entity.isDead){((EntityLivingBase)entity).hurtResistantTime=0;entity.attackEntityFrom(hit.source,hit.amount);}}}
        finally{t.applying=false;}
        t.history.clear();t.changed.clear();t.changedTiles.clear();t.awake.clear();t.checkpoint=null;t.owner=null;sync(t);
    }
    /** Emergency boundaries restore the checkpoint synchronously before throwing away history. */
    private static void abort(Track t){
        if(t.mode==FUTURE||t.mode==RETURN){
            flush(t);t.restoring=true;
            try{
                List<Map.Entry<BlockPos,IBlockState>> pending=new ArrayList<>(t.changed.entrySet());Collections.reverse(pending);
                for(Map.Entry<BlockPos,IBlockState> change:pending)if(t.world.isBlockLoaded(change.getKey())){
                    t.world.setBlockState(change.getKey(),change.getValue(),2);
                    NBTTagCompound n=t.changedTiles.get(change.getKey());net.minecraft.tileentity.TileEntity tile=t.world.getTileEntity(change.getKey());if(n!=null&&tile!=null)tile.readFromNBT(n.copy());
                }
                while(t.history.previous()!=null){t.history.current().blocks(t.world,true);t.history.back();}
                if(t.checkpoint!=null)t.checkpoint.restore(t.world,t);
            }finally{t.restoring=false;}
        }
        finish(t);
    }
    public static void action(EntityPlayerMP p,int action,int value){
        if(action==1){preview(p);return;}
        if(action==0||action==3){rewind(p);return;}
        if(action==10){Abilities.toggleSlow(p);return;}
        if(action==4){redirect(p);return;}
        if(action==2){Track t=tracks.get(p.world);if(t!=null&&p.getUniqueID().equals(t.owner))abort(t);}
    }
    public static void redirect(EntityPlayerMP p) {
        Track t=tracks.get(p.world);if(t==null || t.mode!=1 || !canMove(p))return;
        Vec3d eye=p.getPositionEyes(1),end=eye.add(p.getLookVec().scale(6));Entity best=null;double distance=36;
        RayTraceResult block=p.world.rayTraceBlocks(eye,end,false,true,false);
        if(block!=null)distance=eye.squareDistanceTo(block.hitVec);
        for(Entity e:p.world.getEntitiesWithinAABB(Entity.class,p.getEntityBoundingBox().grow(6))) {
            if(!(e instanceof IProjectile) && !(e instanceof EntityFireball))continue;
            RayTraceResult hit=e.getEntityBoundingBox().grow(.4).calculateIntercept(eye,end);
            if(hit!=null && eye.squareDistanceTo(hit.hitVec)<distance){best=e;distance=eye.squareDistanceTo(hit.hitVec);}
        }
        if(best==null)return;
        double speed=Math.max(.5,Math.sqrt(best.motionX*best.motionX+best.motionY*best.motionY+best.motionZ*best.motionZ));
        Vec3d v=p.getLookVec().scale(speed);best.motionX=v.x;best.motionY=v.y;best.motionZ=v.z;
        if(best instanceof EntityArrow){((EntityArrow)best).shoot(v.x,v.y,v.z,(float)speed,0);((EntityArrow)best).shootingEntity=p;}
        if(best instanceof EntityFireball){EntityFireball f=(EntityFireball)best;f.accelerationX=p.getLookVec().x*.1;f.accelerationY=p.getLookVec().y*.1;f.accelerationZ=p.getLookVec().z*.1;f.shootingEntity=p;}
        best.velocityChanged=true;
        for(int i=1;i<=12;i++)p.getServerWorld().spawnParticle(EnumParticleTypes.END_ROD,best.posX+v.x*i*.2,best.posY+v.y*i*.2,best.posZ+v.z*i*.2,1,0.0,0.0,0.0,0.0);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void worldTick(TickEvent.WorldTickEvent event){
        if(event.world.isRemote||!(event.world instanceof WorldServer))return;
        WorldServer world=(WorldServer)event.world;Track t=tracks.get(world);
        if(event.phase==TickEvent.Phase.START){
            if(t==null&&retryAfter.getOrDefault(world,0L)<=world.getTotalWorldTime()){
                for(EntityPlayer p:world.playerEntities)if(TimeBranches.unlocked(p,AbilityType.TIME_REWIND)){
                    t=new Track(world);for(EntityPlayer member:world.playerEntities)t.players.add(member.getUniqueID());tracks.put(world,t);capture(t);break;
                }
            }
            return;
        }
        if(t==null)return;
        Set<UUID> participants=new HashSet<>();for(EntityPlayer p:world.playerEntities)participants.add(p.getUniqueID());
        if(!t.players.equals(participants)){
            if(t.mode!=IDLE)abort(t);t.history.clear();t.changed.clear();t.changedTiles.clear();t.players.clear();t.players.addAll(participants);return;
        }
        if(t.mode!=IDLE){
            t.elapsed++;EntityPlayer owner=world.getPlayerEntityByUUID(t.owner);
            if(owner==null){abort(t);return;}
            if(reversing(t.mode)){
                if(!reverseFrame(t)||t.budget<=0||t.history.previous()==null)finish(t);
                else sync(t);
                return;
            }
            if(t.mode==STASIS){
                if(!Abilities.isSlow(owner)){finish(t);return;}
                if(t.history.current()!=null)world.setWorldTime(t.history.current().day);
            }else if(t.mode==FUTURE){
                if(!TimeBranches.pay(owner,TimeBranches.futureRate(t.branchLevel)/20)||--t.remaining<=0){startReturn(t);return;}
            }
        }
        if(t.invalid){
            if(t.owner!=null){EntityPlayer p=world.getPlayerEntityByUUID(t.owner);if(p!=null)message(p,"limit");}
            abort(t);tracks.remove(world);retryAfter.put(world,world.getTotalWorldTime()+1200);return;
        }
        capture(t);
        if(t.mode!=IDLE&&t.elapsed%2==0)sync(t);
        if(t.mode==IDLE&&world.getTotalWorldTime()%20==0){
            boolean keep=false;for(EntityPlayer p:world.playerEntities)if(TimeBranches.unlocked(p,AbilityType.TIME_REWIND)){keep=true;break;}
            if(!keep)tracks.remove(world);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void damage(LivingAttackEvent event){
        Track t=tracks.get(event.getEntityLiving().world);if(t==null||t.restoring||t.applying||!frozen(t.world))return;
        if(reversing(t.mode)){event.setCanceled(true);return;}
        EntityLivingBase target=event.getEntityLiving();
        if(!(target instanceof EntityPlayer)||!canMove((EntityPlayer)target)||event.getSource().getTrueSource()==null){
            if(t.hits.size()<4096)t.hits.add(new Hit(target,event.getSource(),event.getAmount()));else t.invalid=true;
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent event){
        if(!(event.getEntityLiving() instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)event.getEntityLiving();Track t=tracks.get(p.world);
        if(t!=null&&t.mode==FUTURE&&!t.restoring&&p.getUniqueID().equals(t.owner)){
            event.setCanceled(true);p.setHealth(1);startReturn(t);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(PlayerInteractEvent event){
        if(!canMove(event.getEntityPlayer())){event.setCanceled(true);return;}
        if(event.getWorld().isRemote)return;Track t=tracks.get(event.getWorld());
        if(t!=null&&t.mode==STASIS&&event instanceof PlayerInteractEvent.RightClickBlock)t.awake.put(event.getPos().toImmutable(),t.elapsed+40);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(AttackEntityEvent e){if(!canMove(e.getEntityPlayer()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breakBlock(BlockEvent.BreakEvent e){if(!canMove(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void spawn(LivingSpawnEvent.CheckSpawn e){if(frozen(e.getWorld()))e.setResult(Event.Result.DENY);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void explosion(ExplosionEvent.Start e){if(frozen(e.getWorld()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void sleep(PlayerSleepInBedEvent e){if(busy(e.getEntityPlayer()))e.setResult(EntityPlayer.SleepResult.OTHER_PROBLEM);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.player.world.isRemote||event.phase!=TickEvent.Phase.END)return;
        Track t=tracks.get(event.player.world);if(t==null||t.mode==IDLE)return;EntityPlayer p=event.player;
        if(!canMove(p)&&t.history.current()!=null){
            NBTTagCompound n=t.history.current().players.get(p.getUniqueID());
            if(n!=null&&p instanceof EntityPlayerMP)((EntityPlayerMP)p).connection.setPlayerLocation(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"),n.getFloat("yaw"),n.getFloat("pitch"));
            p.motionX=p.motionY=p.motionZ=0;
        }
        if(t.mode==FUTURE&&t.checkpoint!=null&&p.getUniqueID().equals(t.owner)){
            NBTTagCompound start=t.checkpoint.players.get(p.getUniqueID());
            if(start!=null&&p.getDistanceSq(start.getDouble("x"),start.getDouble("y"),start.getDouble("z"))>48*48)startReturn(t);
        }
    }
    private static void boundary(World world){
        if(world.isRemote)return;Track t=tracks.get(world);if(t==null||t.restoring)return;
        if(t.mode!=IDLE)abort(t);t.history.clear();t.changed.clear();t.changedTiles.clear();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void chunkLoad(ChunkEvent.Load e){boundary(e.getWorld());}
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload e){boundary(e.getWorld());}
    @SubscribeEvent public static void unload(WorldEvent.Unload e){
        if(e.getWorld().isRemote){clientFrozen=false;clientCanMove=true;clientDimension=Integer.MIN_VALUE;return;}
        boundary(e.getWorld());retryAfter.remove(e.getWorld());tracks.remove(e.getWorld());
    }
    @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e){boundary(e.player.world);tracks.remove(e.player.world);}
    @SubscribeEvent public static void dimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e){for(Track t:new ArrayList<>(tracks.values()))if(t.players.contains(e.player.getUniqueID())){boundary(t.world);tracks.remove(t.world);}}
    public static boolean pinned(EntityPlayer p,ItemStack stack){return false;}
    public static boolean liveItem(Entity entity){
        if(!(entity instanceof EntityItem))return false;
        if(entity.world.isRemote)return clientLive.contains(entity.getEntityId());
        Track t=tracks.get(entity.world);return t!=null&&t.mode==STASIS&&entity.getEntityData().getString("pocket_temporal_live").equals(t.sessionId);
    }
    public static boolean tileAwake(net.minecraft.tileentity.TileEntity tile){
        if(tile.getWorld().isRemote)return clientAwake.contains(tile.getPos());
        Track t=tracks.get(tile.getWorld());return t!=null&&t.mode==STASIS&&t.awake.getOrDefault(tile.getPos(),0)>t.elapsed;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void entityJoin(EntityJoinWorldEvent event){
        if(event.getWorld().isRemote)return;Track t=tracks.get(event.getWorld());
        if(t!=null&&t.restoring&&!t.restoringSpawn){event.setCanceled(true);return;}
        if(t!=null&&!t.restoring&&t.mode==STASIS&&event.getEntity() instanceof EntityItem)event.getEntity().getEntityData().setString("pocket_temporal_live",t.sessionId);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void travel(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event){
        if(event.getEntity().world.isRemote)return;Track t=tracks.get(event.getEntity().world);
        if(t!=null&&t.mode!=IDLE){event.setCanceled(true);return;}
        for(Track history:tracks.values())if(history.mode==IDLE){history.history.clear();history.changed.clear();history.changedTiles.clear();}
    }
    public static void emergency(World world){Track t=tracks.get(world);if(t!=null)abort(t);}
}
