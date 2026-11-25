package pocketmod;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.*;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Dimension-scoped authoritative temporal sessions. Nothing is saved frozen across server restarts. */
@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class TemporalSystem {
    static final int STEP=2, HISTORY=401, MAX_BYTES=32*1024*1024;
    private static final Map<WorldServer,Long> retryAfter=new IdentityHashMap<WorldServer,Long>();
    static final Map<WorldServer,Track> tracks=new IdentityHashMap<WorldServer,Track>();
    public static volatile boolean clientFrozen;
    public static volatile boolean clientCanMove=true;
    private static volatile Set<Integer> clientLive=Collections.emptySet();
    private static volatile Set<BlockPos> clientAwake=Collections.emptySet();
    private static volatile String clientOwner="";
    private static volatile boolean clientShare;
    public static volatile long clientDay;
    public static volatile int clientDimension=Integer.MIN_VALUE;
    static final class Hit {
        final UUID target;final DamageSource source;final float amount;
        Hit(EntityLivingBase target,DamageSource source,float amount){this.target=target.getUniqueID();this.source=source;this.amount=amount;}
    }
    static final class Track {
        final WorldServer world;
        final TemporalHistory<TemporalSnapshot> history=new TemporalHistory<TemporalSnapshot>(HISTORY);
        final Map<BlockPos,IBlockState> changed=new LinkedHashMap<BlockPos,IBlockState>();
        final List<Hit> hits=new ArrayList<Hit>();
        final Map<BlockPos,NBTTagCompound> changedTiles=new LinkedHashMap<BlockPos,NBTTagCompound>();
        final Set<UUID> players=new HashSet<UUID>();
        final Map<BlockPos,Integer> awake=new HashMap<BlockPos,Integer>();
        String sessionId="";
        UUID owner;String dialId="";NBTTagCompound dial=new NBTTagCompound();
        int mode; // 0 rolling history, 1 stasis, 2 preview, 3 death choice
        int remaining,browseUntil,deathWait,elapsed;
        long clock,lastAction=-1;
        boolean restoring,restoringSpawn,applying,invalid,recover;
        TemporalSnapshot checkpoint;
        Track(WorldServer world){this.world=world;}
    }
    private TemporalSystem() {}
    public static boolean frozen(World world) {
        if(world==null)return false;
        if(world.isRemote)return clientFrozen && world.provider.getDimension()==clientDimension;
        Track t=tracks.get(world);return t!=null && !t.restoring && (t.mode==1 || t.mode==3);
    }
    public static boolean busy(EntityPlayer p) {if(p.world.isRemote)return PlayerData.root(p).getCompoundTag("temporal").getInteger("mode")!=0;Track t=tracks.get(p.world);return t!=null && t.mode!=0;}
    public static boolean canMove(EntityPlayer p) {
        if(p.world.isRemote)return !frozen(p.world) || (clientCanMove && (clientShare || p.getUniqueID().toString().equals(clientOwner)));
        Track t=tracks.get(p.world);
        return t==null || t.mode==0 || t.mode==2 || (t.mode!=3 && (t.dial.getBoolean("share") || p.getUniqueID().equals(t.owner)));
    }
    public static void clientState(NBTTagCompound n,int dimension) {
        clientDimension=dimension;clientOwner=n.getString("owner_id");clientShare=n.getBoolean("share");clientDay=n.getLong("day");clientFrozen=n.getBoolean("frozen");clientCanMove=n.getBoolean("move");
        Set<Integer> live=new HashSet<Integer>();for(int id:n.getIntArray("live"))live.add(id);clientLive=live;
        Set<BlockPos> awake=new HashSet<BlockPos>();int[] positions=n.getIntArray("awake");for(int i=0;i+2<positions.length;i+=3)awake.add(new BlockPos(positions[i],positions[i+1],positions[i+2]));clientAwake=awake;
    }
    public static NBTTagCompound status(EntityPlayerMP p) {
        NBTTagCompound n=new NBTTagCompound();Track t=tracks.get(p.world);
        n.setBoolean("move",canMove(p));n.setBoolean("frozen",frozen(p.world));
        if(t!=null && t.mode!=0) {
            n.setString("owner_id",t.owner==null?"":t.owner.toString());n.setBoolean("share",t.dial.getBoolean("share"));
            n.setLong("day",t.world.getWorldTime());n.setInteger("mode",t.mode);n.setBoolean("owner",p.getUniqueID().equals(t.owner));
            n.setInteger("left",t.mode==3?t.deathWait:t.remaining);
            n.setInteger("past",Math.max(0,t.history.position())*STEP);
            n.setInteger("future",Math.max(0,t.history.size()-t.history.position()-1)*STEP);
            n.setInteger("rewind",t.dial.getInteger("rewind"));
            java.util.List<Integer> live=new ArrayList<Integer>();for(Entity entity:t.world.loadedEntityList)if(liveItem(entity))live.add(entity.getEntityId());
            int[] ids=new int[live.size()];for(int i=0;i<ids.length;i++)ids[i]=live.get(i);n.setIntArray("live",ids);
            java.util.List<BlockPos> awake=new ArrayList<BlockPos>();for(Map.Entry<BlockPos,Integer> entry:t.awake.entrySet())if(entry.getValue()>t.elapsed)awake.add(entry.getKey());
            int[] positions=new int[awake.size()*3];for(int i=0;i<awake.size();i++){positions[i*3]=awake.get(i).getX();positions[i*3+1]=awake.get(i).getY();positions[i*3+2]=awake.get(i).getZ();}n.setIntArray("awake",positions);
        }
        return n;
    }
    static void message(EntityPlayer p,String id,Object... args) {p.sendStatusMessage(new TextComponentTranslation("pocketmod.temporal."+id,args),true);}
    private static void sync(Track t) {
        for(EntityPlayer p:t.world.playerEntities)if(p instanceof EntityPlayerMP)NetworkHandler.sync((EntityPlayerMP)p);
    }
    private static ItemStack activeDial(Track t) {
        EntityPlayer p=t.world.getPlayerEntityByUUID(t.owner);
        if(p==null)return ItemStack.EMPTY;
        for(ItemStack s:p.inventory.mainInventory)if(matches(s,t))return s;
        for(ItemStack s:p.inventory.offHandInventory)if(matches(s,t))return s;
        if(matches(p.inventory.getItemStack(),t))return p.inventory.getItemStack();
        return ItemStack.EMPTY;
    }
    private static boolean matches(ItemStack s,Track t) {
        return TemporalDialItem.isDial(s)&&TemporalDialItem.data(s).getString("dial_id").equals(t.dialId);
    }
    private static void persistDial(Track t) {
        ItemStack stack=activeDial(t);if(!stack.isEmpty())stack.setTagCompound(t.dial.copy());
    }
    private static void prepare(Track t,EntityPlayerMP p,ItemStack stack) {
        t.players.clear();for(EntityPlayer member:t.world.playerEntities)t.players.add(member.getUniqueID());
        t.sessionId=UUID.randomUUID().toString();t.awake.clear();
        t.owner=p.getUniqueID();t.dialId=TemporalDialItem.data(stack).getString("dial_id");
        t.dial=TemporalDialItem.data(stack).copy();t.elapsed=0;t.lastAction=-1;t.invalid=false;
        for(EntityPlayer player:t.world.playerEntities)player.closeScreen();
    }
    public static void use(EntityPlayerMP p,ItemStack stack) {
        Track t=tracks.get(p.world);
        if(t!=null && t.mode!=0) {
            if(p.getUniqueID().equals(t.owner)) {
                if(t.mode==2) returnPreview(t);else if(t.mode==3)deathRewind(t,5);else finish(t);
            } else message(p,"occupied");
            return;
        }
        if(!pocketmod.temporal.TemporalCorePlugin.loaded){message(p,"hooks_missing");return;}
        if(ExileMode.enabled(p.world) && !p.isCreative() && !AbilityStorage.isUnlocked(p,AbilityType.SLOW)){message(p,"need_unlock");return;}
        if(!sameDimension(p)){message(p,"same_dimension");return;}
        if(p.isSpectator() || Spector.isActive(p) || MorphIntegration.isMorphed(p) || p.isRiding() || PocketDimensionManager.isPocketDimension(p.dimension)) {
            message(p,"unavailable");return;
        }
        NBTTagCompound n=TemporalDialItem.data(stack);
        if(n.getInteger("charges")<=0){message(p,"empty");return;}
        if(t==null){t=new Track(p.getServerWorld());tracks.put(p.getServerWorld(),t);}
        flush(t);t.history.clear();t.changed.clear();t.changedTiles.clear();prepare(t,p,stack);
        t.dial.setInteger("charges",t.dial.getInteger("charges")-1);persistDial(t);
        t.mode=1;t.remaining=Math.max(1,Math.min(TemporalDialItem.tier(stack)==2?60:(TemporalDialItem.tier(stack)+1)*10,t.dial.getInteger("lapse")))*20;
        if(!capture(t)){t.dial.setInteger("charges",t.dial.getInteger("charges")+1);persistDial(t);t.mode=0;tracks.remove(t.world);sync(t);message(p,"limit");return;}
        sync(t);p.world.playSound(null,p.getPosition(),net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,SoundCategory.PLAYERS,1,0.6f);
        message(p,"started");
    }
    public static void preview(EntityPlayerMP p) {
        Track t=tracks.get(p.world);
        if(t!=null && t.mode!=0){if(t.mode==2 && p.getUniqueID().equals(t.owner))returnPreview(t);return;}
        if(ExileMode.enabled(p.world) && !p.isCreative() && !AbilityStorage.isUnlocked(p,AbilityType.SLOW)){message(p,"need_unlock");return;}
        if(!sameDimension(p)){message(p,"same_dimension");return;}
        ItemStack stack=TemporalDialItem.find(p);
        if(stack.isEmpty() || TemporalDialItem.tier(stack)<2 || p.isRiding() || p.isSpectator()
                || Spector.isActive(p) || MorphIntegration.isMorphed(p) || PocketDimensionManager.isPocketDimension(p.dimension))return;
        if(!pocketmod.temporal.TemporalCorePlugin.loaded){message(p,"hooks_missing");return;}
        if(TemporalDialItem.data(stack).getInteger("rewind")<40){message(p,"empty");return;}
        if(t==null){t=new Track(p.getServerWorld());tracks.put(p.getServerWorld(),t);}
        flush(t);t.history.clear();t.changed.clear();t.changedTiles.clear();prepare(t,p,stack);t.mode=2;
        t.remaining=Math.min(200,t.dial.getInteger("rewind")); // maximum ten-second full checkpoint
        if(!capture(t)){t.mode=0;tracks.remove(t.world);sync(t);message(p,"limit");return;}
        t.checkpoint=t.history.current();sync(t);message(p,"preview_started");
    }
    static boolean capture(Track t) {
        if(t.restoring || t.invalid)return false;
        try {
            t.clock=t.world.getTotalWorldTime();
            TemporalSnapshot frame=TemporalSnapshot.capture(t.world,t);
            t.history.add(frame);t.changed.clear();t.changedTiles.clear();
            int bytes=0;for(int i=0;i<t.history.size();i++)bytes+=t.history.at(i).bytes;
            int limit=t.mode==0?101:HISTORY;
            while(t.history.size()>2 && (bytes>MAX_BYTES || t.history.size()>limit)) {
                if(t.mode==2){t.invalid=true;return false;}
                bytes-=t.history.removeOldest().bytes;
            }
            return true;
        }catch(Exception e){t.invalid=true;System.err.println("[PocketMod] Temporal history stopped: "+e);return false;}
    }
    static void flush(Track t) {if(!t.changed.isEmpty() && !t.invalid)capture(t);}
    public static boolean beforeBlock(World world,BlockPos pos) {
        if(world.isRemote)return false;Track t=tracks.get(world);
        if(t==null || t.restoring || !world.isBlockLoaded(pos))return false;
        if(t.invalid)return t.mode==2;
        if(t.changed.size()>=8192 && !t.changed.containsKey(pos)){t.invalid=true;return t.mode==2;}
        if(!t.changed.containsKey(pos)){
            t.changed.put(pos.toImmutable(),world.getBlockState(pos));
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
            if(tile!=null)t.changedTiles.put(pos.toImmutable(),tile.writeToNBT(new NBTTagCompound()));
        }
        return false;
    }
    private static void finish(Track t) {
        if(t.mode==2){returnPreview(t);return;}
        if(t.mode==3 && t.deathWait>0){deathRewind(t,5);return;}
        t.mode=0;t.remaining=0;t.deathWait=0;
        persistDial(t);List<Hit> hits=new ArrayList<Hit>(t.hits);t.hits.clear();
        t.recover=false;t.applying=true;
        try {
            for(Hit hit:hits) {
                Entity e=t.world.getEntityFromUuid(hit.target);
                if(e instanceof EntityLivingBase && !e.isDead){((EntityLivingBase)e).hurtResistantTime=0;e.attackEntityFrom(hit.source,hit.amount);}
            }
        } finally {t.applying=false;}
        if(t.recover){flush(t);t.mode=3;t.deathWait=1200;sync(t);return;}
        // Applied damage is a boundary: it may trigger boss rewards, death handlers and other mods.
        t.history.clear();t.changed.clear();t.changedTiles.clear();t.owner=null;t.checkpoint=null;t.browseUntil=0;
        sync(t);
        t.world.playSound(null,t.world.getSpawnPoint(),net.minecraft.init.SoundEvents.ENTITY_ENDERMEN_TELEPORT,SoundCategory.PLAYERS,0.5f,0.8f);
    }
    private static void returnPreview(Track t) {
        flush(t);
        t.restoring=true;
        try {
            // Pending changes also belong to the preview, even between regular two-tick snapshots.
            if(!t.changed.isEmpty()) {
                List<Map.Entry<BlockPos,IBlockState>> pending=new ArrayList<Map.Entry<BlockPos,IBlockState>>(t.changed.entrySet());
                Collections.reverse(pending);
                for(Map.Entry<BlockPos,IBlockState> c:pending)if(t.world.isBlockLoaded(c.getKey())) {
                    t.world.setBlockState(c.getKey(),c.getValue(),2);
                    NBTTagCompound tile=t.changedTiles.get(c.getKey());net.minecraft.tileentity.TileEntity live=t.world.getTileEntity(c.getKey());
                    if(tile!=null&&live!=null){live.readFromNBT(tile.copy());live.markDirty();t.world.notifyBlockUpdate(c.getKey(),c.getValue(),c.getValue(),3);}
                }
            }
            while(t.history.previous()!=null){t.history.current().blocks(t.world,true);t.history.back();}
            if(t.checkpoint!=null)t.checkpoint.restore(t.world,t);
        } finally {t.restoring=false;t.mode=0;t.hits.clear();t.history.clear();t.changed.clear();t.changedTiles.clear();t.checkpoint=null;t.owner=null;sync(t);}
    }
    public static void step(EntityPlayerMP p,int direction) {
        Track t=tracks.get(p.world);if(t==null || t.mode!=1 || !p.getUniqueID().equals(t.owner) || t.invalid)return;
        if(t.lastAction==t.world.getTotalWorldTime())return;
        ItemStack item=activeDial(t);if(item.isEmpty() || TemporalDialItem.tier(item)==0)return;
        if(t.browseUntil==0)flush(t);
        t.lastAction=t.world.getTotalWorldTime();t.browseUntil=t.elapsed+20;
        if(direction<0) {
            if(t.history.previous()==null || t.dial.getInteger("rewind")<STEP)return;
            t.dial.setInteger("rewind",t.dial.getInteger("rewind")-STEP);persistDial(t);
            t.restoring=true;
            try {t.history.current().blocks(t.world,true);t.history.back();t.history.current().restore(t.world,t);}finally{t.restoring=false;}
        } else {
            if(t.history.next()==null)return;
            t.restoring=true;
            try {t.history.forward();t.history.current().blocks(t.world,false);t.history.current().restore(t.world,t);}finally{t.restoring=false;}
        }
        persistDial(t);sync(t);
    }
    private static void deathRewind(Track t,int seconds) {
        int steps=Math.min(Math.max(1,seconds)*20/STEP,t.history.position());
        steps=Math.min(steps,t.dial.getInteger("rewind")/STEP);
        if(steps==0){t.mode=0;t.deathWait=0;finish(t);return;}
        t.dial.setInteger("rewind",t.dial.getInteger("rewind")-steps*STEP);persistDial(t);
        t.restoring=true;
        try {
            while(steps-->0){t.history.current().blocks(t.world,true);t.history.back();}
            t.history.current().restore(t.world,t);
        }finally{t.restoring=false;}
        t.hits.clear();t.mode=1;t.deathWait=0;finish(t);
    }
    public static void action(EntityPlayerMP p,int action,int value) {
        Track t=tracks.get(p.world);
        if(action==10){ItemStack dial=TemporalDialItem.find(p);if(!dial.isEmpty())use(p,dial);return;}
        if(action==0){step(p,value);return;}
        if(action==1){preview(p);return;}
        if(action==2){if(t!=null && p.getUniqueID().equals(t.owner))finish(t);return;}
        if(action==3){if(t!=null && t.mode==3 && p.getUniqueID().equals(t.owner))deathRewind(t,Math.max(1,Math.min(10,value)));return;}
        if(action==4){redirect(p);return;}
        if(t!=null && t.mode!=0)return;
        ItemStack stack=TemporalDialItem.held(p);if(stack.isEmpty())return;
        NBTTagCompound n=TemporalDialItem.data(stack);int tier=TemporalDialItem.tier(stack);
        if(action==5)n.setInteger("lapse",Math.max(1,Math.min(tier==2?60:(tier+1)*10,value)));
        if(action==6)n.setBoolean("share",!n.getBoolean("share"));
        if(action==7 && n.getInteger("charges")<4 && consume(p,Items.ENDER_PEARL,1))n.setInteger("charges",n.getInteger("charges")+1);
        if(action==8 && tier>0 && n.getInteger("rewind")<1600 && consume(p,Items.ENDER_EYE,1))n.setInteger("rewind",Math.min(1600,n.getInteger("rewind")+400));
        if(action==9 && tier<2 && consume(p,tier==0?Items.DIAMOND:Items.NETHER_STAR,tier==0?4:1))stack.setItemDamage(tier+1);
        if(t!=null && action>=7){t.history.clear();t.changed.clear();t.changedTiles.clear();}
        p.inventory.markDirty();p.inventoryContainer.detectAndSendChanges();NetworkHandler.sync(p);
    }
    private static boolean sameDimension(EntityPlayerMP p) {return p.world.playerEntities.size()==p.getServer().getPlayerList().getPlayers().size();}
    private static boolean consume(EntityPlayerMP p,net.minecraft.item.Item item,int count) {
        if(p.isCreative())return true;
        int found=0;for(ItemStack s:p.inventory.mainInventory)if(s.getItem()==item)found+=s.getCount();
        if(found<count)return false;
        for(ItemStack s:p.inventory.mainInventory)if(s.getItem()==item){int take=Math.min(count,s.getCount());s.shrink(take);count-=take;if(count==0)break;}
        return true;
    }
    public static void redirect(EntityPlayerMP p) {
        Track t=tracks.get(p.world);if(t==null || t.mode!=1 || !canMove(p) || t.browseUntil>t.elapsed)return;
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
    public static void worldTick(TickEvent.WorldTickEvent e) {
        if(e.world.isRemote || !(e.world instanceof WorldServer))return;
        WorldServer w=(WorldServer)e.world;Track t=tracks.get(w);
        if(e.phase==TickEvent.Phase.START) {
            if(t==null && retryAfter.getOrDefault(w,0L)<=w.getTotalWorldTime()) {
                for(EntityPlayer p:w.playerEntities){ItemStack item=TemporalDialItem.find(p);if(!item.isEmpty()&&TemporalDialItem.tier(item)>0 && p instanceof EntityPlayerMP && sameDimension((EntityPlayerMP)p)){t=new Track(w);tracks.put(w,t);capture(t);break;}}
            }
            return;
        }
        if(t==null)return;
        Set<UUID> participants=new HashSet<UUID>();for(EntityPlayer p:w.playerEntities)participants.add(p.getUniqueID());
        if(!t.players.equals(participants)) {
            if(t.mode!=0)finish(t);t.history.clear();t.changed.clear();t.changedTiles.clear();t.players.clear();t.players.addAll(participants);
        }
        if(t.mode!=0) {
            t.elapsed++;
            if(activeDial(t).isEmpty() || w.getPlayerEntityByUUID(t.owner)==null){finish(t);return;}
            if(t.mode==2) {
                t.dial.setInteger("rewind",Math.max(0,t.dial.getInteger("rewind")-1));persistDial(t);
            }
            if(t.mode==3) {
                if(--t.deathWait<=0){deathRewind(t,5);return;}
            } else if(--t.remaining<=0) {finish(t);return;}
            if(t.mode==1 && t.history.current()!=null)w.setWorldTime(t.history.current().day);
            if(t.browseUntil>0 && t.elapsed>=t.browseUntil){t.history.branch();t.browseUntil=0;}
        }
        if(t.invalid) {
            if(t.owner!=null){EntityPlayer p=w.getPlayerEntityByUUID(t.owner);if(p!=null)message(p,"limit");}
            finish(t);tracks.remove(w);retryAfter.put(w,w.getTotalWorldTime()+1200);return;
        }
        if(w.getTotalWorldTime()%STEP==0 && t.mode!=3 && t.browseUntil==0)capture(t);
        if(t.mode!=0 && t.elapsed%4==0)sync(t);
        if(t.mode==0 && w.getTotalWorldTime()%20==0) {
            boolean keep=false;for(EntityPlayer p:w.playerEntities){ItemStack stack=TemporalDialItem.find(p);if(!stack.isEmpty()&&TemporalDialItem.tier(stack)>0)keep=true;}
            if(!keep)tracks.remove(w);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void damage(LivingAttackEvent e) {
        Track t=tracks.get(e.getEntityLiving().world);if(t==null || t.restoring || t.applying || !frozen(t.world))return;
        if(t.mode==3){e.setCanceled(true);return;}
        EntityLivingBase target=e.getEntityLiving();
        if(!(target instanceof EntityPlayer) || !canMove((EntityPlayer)target) || e.getSource().getTrueSource()==null) {
            if(t.hits.size()<4096)t.hits.add(new Hit(target,e.getSource(),e.getAmount()));
            else t.invalid=true;
            e.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void death(LivingDeathEvent e) {
        if(!(e.getEntityLiving() instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();
        if(PocketDimensionManager.isPocketDimension(p.dimension) || p.isRiding() || Spector.isActive(p) || MorphIntegration.isMorphed(p))return;
        if(ExileMode.enabled(p.world) && !p.isCreative() && !AbilityStorage.isUnlocked(p,AbilityType.SLOW))return;
        Track t=tracks.get(p.world);
        if(t==null || t.restoring || t.invalid)return;
        if(t.applying){
            if(p.getUniqueID().equals(t.owner) && t.history.position()>=10 && t.dial.getInteger("rewind")>=20){e.setCanceled(true);p.setHealth(1);t.recover=true;}
            return;
        }
        if(t.mode==2 && p.getUniqueID().equals(t.owner)){e.setCanceled(true);p.setHealth(1);returnPreview(t);return;}
        ItemStack stack=TemporalDialItem.find(p);
        if(stack.isEmpty() || TemporalDialItem.tier(stack)==0 || TemporalDialItem.data(stack).getInteger("rewind")<20
                || t.history.position()<10 || (t.mode!=0 && !p.getUniqueID().equals(t.owner)))return;
        e.setCanceled(true);p.setHealth(1);flush(t);prepare(t,p,stack);t.mode=3;t.deathWait=1200;sync(t);message(p,"death_choice");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void toss(ItemTossEvent e) {
        if(e.getPlayer().world.isRemote)return;
        Track t=tracks.get(e.getPlayer().world);
        if(t!=null && t.mode!=0 && matches(e.getEntityItem().getItem(),t)) {
            e.setCanceled(true);e.getPlayer().inventory.addItemStackToInventory(e.getEntityItem().getItem());
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent e) {
        if(!canMove(e.getEntityPlayer())){e.setCanceled(true);return;}
        if(e.getWorld().isRemote)return;
        Track t=tracks.get(e.getWorld());
        if(t!=null && e instanceof PlayerInteractEvent.RightClickBlock)t.awake.put(e.getPos().toImmutable(),t.elapsed+40);
        if(t!=null && t.browseUntil>t.elapsed)t.browseUntil=t.elapsed; // branch on first world interaction
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent e) {if(!canMove(e.getEntityPlayer()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent e) {if(!canMove(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void spawn(LivingSpawnEvent.CheckSpawn e) {if(frozen(e.getWorld()))e.setResult(Event.Result.DENY);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void explosion(ExplosionEvent.Start e) {
        if(!frozen(e.getWorld()))return;
        // Primed TNT and fireballs do not tick; manually initiated explosions are rejected during stasis.
        e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void sleep(PlayerSleepInBedEvent e) {if(busy(e.getEntityPlayer()))e.setResult(EntityPlayer.SleepResult.OTHER_PROBLEM);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.player.world.isRemote || e.phase!=TickEvent.Phase.END)return;
        Track t=tracks.get(e.player.world);if(t==null || t.mode==0)return;
        EntityPlayer p=e.player;
        // The active dial is pinned by the click packet gate; ordinary containers remain usable.
        if(!canMove(p) && t.history.current()!=null) {
            NBTTagCompound n=t.history.current().players.get(p.getUniqueID());
            if(n!=null && p instanceof EntityPlayerMP)((EntityPlayerMP)p).connection.setPlayerLocation(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"),n.getFloat("yaw"),n.getFloat("pitch"));
            p.motionX=p.motionY=p.motionZ=0;
        }
        if(t.mode==2 && t.checkpoint!=null && p.getUniqueID().equals(t.owner)) {
            NBTTagCompound start=t.checkpoint.players.get(p.getUniqueID());
            if(start!=null && p.getDistanceSq(start.getDouble("x"),start.getDouble("y"),start.getDouble("z"))>48*48)returnPreview(t);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void chunkLoad(ChunkEvent.Load e) {
        boundary(e.getWorld());
    }
    private static void boundary(World world) {
        if(world.isRemote)return;Track t=tracks.get(world);if(t==null||t.restoring)return;
        if(t.mode!=0)finish(t);t.history.clear();t.changed.clear();t.changedTiles.clear();
    }
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload e) {
        if(e.getWorld().isRemote)return;
        Track t=tracks.get(e.getWorld());if(t==null||t.restoring)return;
        if(t.mode!=0)finish(t);t.history.clear();t.changed.clear();t.changedTiles.clear();
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload e) {
        if(e.getWorld().isRemote){clientFrozen=false;clientCanMove=true;clientDimension=Integer.MIN_VALUE;return;}
        retryAfter.remove(e.getWorld());
        Track t=tracks.get(e.getWorld());if(t!=null && t.mode!=0)finish(t);
        tracks.remove(e.getWorld());
    }
    @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e) {
        Track t=tracks.get(e.player.world);if(t!=null){if(t.mode!=0)finish(t);tracks.remove(e.player.world);}
    }
    @SubscribeEvent public static void dimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e) {
        for(Track t:new ArrayList<Track>(tracks.values()))if(t.players.contains(e.player.getUniqueID())){if(t.mode!=0)finish(t);tracks.remove(t.world);}
    }
    public static boolean pinned(EntityPlayer p,ItemStack stack) {Track t=tracks.get(p.world);return t!=null && t.mode!=0 && matches(stack,t);}
    public static boolean liveItem(Entity entity) {
        if(!(entity instanceof EntityItem))return false;
        if(entity.world.isRemote)return clientLive.contains(entity.getEntityId());
        Track t=tracks.get(entity.world);return t!=null && t.mode!=0 && t.sessionId.equals(entity.getEntityData().getString("pocket_temporal_live"));
    }
    public static boolean tileAwake(net.minecraft.tileentity.TileEntity tile) {if(tile.getWorld().isRemote)return clientAwake.contains(tile.getPos());Track t=tracks.get(tile.getWorld());return t!=null && t.mode==1 && t.awake.getOrDefault(tile.getPos(),0)>t.elapsed;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void join(EntityJoinWorldEvent e) {
        if(e.getWorld().isRemote)return;
        Track t=tracks.get(e.getWorld());
        if(t!=null && t.restoring && !t.restoringSpawn){e.setCanceled(true);return;}
        if(t!=null && !t.restoring && t.mode!=0 && e.getEntity() instanceof EntityItem)e.getEntity().getEntityData().setString("pocket_temporal_live",t.sessionId);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void travel(net.minecraftforge.event.entity.EntityTravelToDimensionEvent e) {
        if(e.getEntity().world.isRemote)return;
        Track t=tracks.get(e.getEntity().world);if(t==null)return;
        if(t.mode!=0){e.setCanceled(true);return;}
        for(Track history:tracks.values())if(history.mode==0){history.history.clear();history.changed.clear();history.changedTiles.clear();}
    }
    public static void emergency(World world) {Track t=tracks.get(world);if(t!=null)finish(t);}
}
