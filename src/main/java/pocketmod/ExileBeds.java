package pocketmod;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Exile rest changes daylight, not elapsed survival time or resurrection rules. */
@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class ExileBeds {
    private static final String ATTEMPT="pocketmod_sleep_attempt";
    private static final ThreadLocal<Boolean> POCKET_USE=new ThreadLocal<>();
    private static final java.lang.reflect.Field LOCAL_SLEEP=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
            net.minecraft.world.WorldServer.class,new String[]{"allPlayersSleeping","field_73068_P"});
    private ExileBeds() {}

    static boolean pocketSurfaceCheck() {return Boolean.TRUE.equals(POCKET_USE.get());}
    static boolean pocketSleepEnabled(World world) {
        return world!=null && PocketDimensionManager.isPocketDimension(world.provider.getDimension())
                && ExileMode.enabled(world);
    }
    static World clock(World world) {
        return PocketDimensionManager.isPocketDimension(world.provider.getDimension())
                ? world.getMinecraftServer().getWorld(0) : world;
    }
    static boolean night(long time) {return ExileSimpleThirst.night(time);}
    static long nextMorning(long time) {return Math.floorDiv(time,24000L)*24000L+24000L;}
    static boolean bed(World world,BlockPos pos,EntityPlayer player) {
        if(pos==null||!world.isBlockLoaded(pos))return false;
        IBlockState block=world.getBlockState(pos);
        return block.getBlock().isBed(block,world,pos,player);
    }
    /** Keep the provider non-surface everywhere except this synchronous vanilla bed interaction. */
    static void usePocketBed(PlayerInteractEvent.RightClickBlock event) {
        if(event.isCanceled())return;
        event.setCanceled(true);
        POCKET_USE.set(true);
        try {
            IBlockState block=event.getWorld().getBlockState(event.getPos());
            boolean used=block.getBlock().onBlockActivated(event.getWorld(),event.getPos(),block,
                    event.getEntityPlayer(),event.getHand(),event.getFace(),
                    (float)(event.getHitVec().x-event.getPos().getX()),
                    (float)(event.getHitVec().y-event.getPos().getY()),
                    (float)(event.getHitVec().z-event.getPos().getZ()));
            event.setCancellationResult(used?EnumActionResult.SUCCESS:EnumActionResult.FAIL);
        } finally {POCKET_USE.remove();}
    }
    static boolean belowThreshold(double value,double maximum,double percent) {
        return value*100 < Math.max(1,maximum)*percent;
    }
    private static double contamination(EntityPlayer player,BlockPos pos) {
        World world=player.world;ExileWorldData data=ExileWorldData.get(world);
        double amount=data.exposure(player.dimension,pos.getX()>>4,pos.getZ()>>4);
        BlockPos feet=player.getPosition();
        amount=Math.max(amount,data.exposure(player.dimension,feet.getX()>>4,feet.getZ()>>4));
        IBlockState block=world.getBlockState(pos);
        if(block.getBlock() instanceof BlockBed) {
            BlockPos other=pos.offset(block.getValue(BlockBed.PART)==BlockBed.EnumPartType.HEAD
                    ? block.getValue(BlockBed.FACING).getOpposite() : block.getValue(BlockBed.FACING));
            amount=Math.max(amount,data.exposure(player.dimension,other.getX()>>4,other.getZ()>>4));
        }
        return amount;
    }
    /** Empty means that vanilla can continue with distance, occupancy and monster checks. */
    static String blocker(EntityPlayer player,BlockPos pos) {
        NBTTagCompound state=ExileMode.state(player);
        if(state.getBoolean("trial_active"))return "boss";
        AxisAlignedBB arena=new AxisAlignedBB(pos.getX()-64,0,pos.getZ()-64,
                pos.getX()+65,player.world.getActualHeight(),pos.getZ()+65);
        if(!player.world.getEntitiesWithinAABB(EntityLivingBase.class,arena,
                mob->mob.isEntityAlive()&&!mob.isNonBoss()).isEmpty())return "boss";
        if(!state.getBoolean("cured")) {
            if(!belowThreshold(state.getInteger("thirst"),ExileConfig.thirstSeconds,ExileConfig.bedMaxThirst))return "thirst";
            if(!PocketDimensionManager.isPocketDimension(player.dimension)
                    &&!belowThreshold(contamination(player,pos),ExileConfig.fullSeconds,ExileConfig.bedMaxContamination))return "infection";
        }
        AxisAlignedBB near=new AxisAlignedBB(pos).grow(16,8,16);
        if(!player.world.getEntitiesWithinAABB(EntityLiving.class,near,
                mob->mob.isEntityAlive()&&mob.getEntityData().getBoolean(ExileMode.PURSUER)).isEmpty())return "pursuers";
        return "";
    }
    private static void explain(EntityPlayer player,String reason) {
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.sleep."+reason),true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void enter(PlayerSleepInBedEvent event) {
        EntityPlayer player=event.getEntityPlayer();
        if(player.world.isRemote||!ExileMode.enabled(player.world))return;
        player.getEntityData().removeTag(ATTEMPT);
        if(event.getResultStatus()!=null&&event.getResultStatus()!=EntityPlayer.SleepResult.OK)return;
        String reason=blocker(player,event.getPos());
        if(!reason.isEmpty()) {
            event.setResult(EntityPlayer.SleepResult.OTHER_PROBLEM);explain(player,reason);return;
        }
        if(!night(clock(player.world).getWorldTime())) {
            event.setResult(EntityPlayer.SleepResult.NOT_POSSIBLE_NOW);return;
        }
        NBTTagCompound attempt=new NBTTagCompound();
        attempt.setLong("night",Math.floorDiv(clock(player.world).getWorldTime(),24000L));
        player.getEntityData().setTag(ATTEMPT,attempt);
    }
    @SubscribeEvent
    public static void time(SleepingTimeCheckEvent event) {
        EntityPlayer player=event.getEntityPlayer();
        if(player.world.isRemote||!ExileMode.enabled(player.world))return;
        event.setResult(night(clock(player.world).getWorldTime())?Event.Result.ALLOW:Event.Result.DENY);
    }
    /** One health point costs one food point; leave three drumsticks available. */
    static float recover(EntityPlayer player) {
        float amount=Math.max(0,Math.min(ExileConfig.bedHealing,
                Math.min(player.getMaxHealth()-player.getHealth(),player.getFoodStats().getFoodLevel()-6)));
        if(amount<=0)return 0;
        float before=player.getHealth();
        player.heal(amount);
        amount=Math.max(0,player.getHealth()-before);
        int food=Math.max(6,player.getFoodStats().getFoodLevel()-(int)Math.ceil(amount));
        player.getFoodStats().setFoodLevel(food);
        player.getFoodStats().setFoodSaturationLevel(Math.min(food,player.getFoodStats().getSaturationLevel()));
        return amount;
    }
    @SubscribeEvent
    public static void wake(PlayerWakeUpEvent event) {
        EntityPlayer player=event.getEntityPlayer();
        if(player.world.isRemote)return;
        NBTTagCompound attempt=player.getEntityData().getCompoundTag(ATTEMPT);
        player.getEntityData().removeTag(ATTEMPT);
        if(!ExileMode.enabled(player.world)||!attempt.hasKey("night")||!player.isPlayerSleeping()
                ||event.wakeImmediately()||!event.shouldSetSpawn()||player.getSleepTimer()<100
                ||!player.world.getGameRules().getBoolean("doDaylightCycle"))return;
        World clock=clock(player.world);
        if(night(clock.getWorldTime()))return;
        NBTTagCompound state=ExileMode.state(player);
        long slept=attempt.getLong("night");
        if(state.hasKey("sleep_healed_night")&&state.getLong("sleep_healed_night")>=slept)return;
        state.setLong("sleep_healed_night",slept);
        float healed=recover(player);
        if(healed>0)explain(player,"rested");
        else if(player.getHealth()<player.getMaxHealth())explain(player,"food");
        if(player instanceof EntityPlayerMP)NetworkHandler.sync((EntityPlayerMP)player);
    }
    /** Hold vanilla's per-dimension skip; the shared night completes once all relevant players sleep. */
    static void prepareWorld(net.minecraft.world.WorldServer world) {
        if(!ExileMode.enabled(world)||(world.provider.getDimension()!=0&&!pocketSleepEnabled(world)))return;
        try {LOCAL_SLEEP.setBoolean(world,false);}
        catch(IllegalAccessException e){throw new IllegalStateException("Cannot coordinate Exile sleep",e);}
    }
    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if(event.phase==TickEvent.Phase.START&&!event.world.isRemote&&event.world instanceof net.minecraft.world.WorldServer)
            prepareWorld((net.minecraft.world.WorldServer)event.world);
    }
    static boolean finishNight(java.util.List<net.minecraft.world.WorldServer> worlds) {
        net.minecraft.world.WorldServer outside=null;
        for(net.minecraft.world.WorldServer world:worlds)if(world!=null&&world.provider.getDimension()==0)outside=world;
        if(outside==null||!ExileMode.enabled(outside)||!night(outside.getWorldTime())
                ||!outside.getGameRules().getBoolean("doDaylightCycle"))return false;
        java.util.List<EntityPlayer> sleepers=new java.util.ArrayList<>();
        for(net.minecraft.world.WorldServer world:worlds) {
            if(world==null||(world!=outside&&!pocketSleepEnabled(world)))continue;
            for(EntityPlayer player:world.playerEntities) {
                if(player.isSpectator())continue;
                if(!player.isPlayerFullyAsleep())return false;
                if(player.bedLocation==null)return false;
                String reason=blocker(player,player.bedLocation);
                if(!reason.isEmpty()){player.wakeUpPlayer(true,true,false);explain(player,reason);return false;}
                sleepers.add(player);
            }
        }
        if(sleepers.isEmpty())return false;
        long morning=nextMorning(outside.getWorldTime());
        for(net.minecraft.world.WorldServer world:worlds)
            if(world!=null&&(world==outside||pocketSleepEnabled(world)))world.setWorldTime(morning);
        for(EntityPlayer player:sleepers)player.wakeUpPlayer(false,true,true);
        if(outside.getGameRules().getBoolean("doWeatherCycle"))outside.provider.resetRainAndThunder();
        return true;
    }
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END)finishNight(java.util.Arrays.asList(net.minecraftforge.common.DimensionManager.getWorlds()));
    }
    @SubscribeEvent
    public static void spawn(PlayerSetSpawnEvent event) {
        EntityPlayer player=event.getEntityPlayer();
        if(!player.world.isRemote&&ExileMode.enabled(player.world)&&!event.isForced()
                &&bed(player.world,event.getNewSpawn(),player))event.setCanceled(true);
    }
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player=event.player;
        if(event.phase!=TickEvent.Phase.END||player.world.isRemote||!ExileMode.enabled(player.world)
                ||!player.isPlayerSleeping()||player.bedLocation==null||player.ticksExisted%20!=0)return;
        String reason=blocker(player,player.bedLocation);
        if(!reason.isEmpty()){player.wakeUpPlayer(true,true,false);explain(player,reason);}
    }
    @SubscribeEvent
    public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        event.player.getEntityData().removeTag(ATTEMPT);
    }
}
