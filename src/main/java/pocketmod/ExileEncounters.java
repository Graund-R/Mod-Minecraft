package pocketmod;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileEncounters {
    /** Задача с приоритетом движения удерживает обычный AI от возвращения в опасную область. */
    private static final class EscapeTask extends net.minecraft.entity.ai.EntityAIBase {
        private final EntityLiving mob;

        EscapeTask(EntityLiving mob) {
            this.mob = mob;
            setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (mob.getEntityData().getBoolean(ExileMode.PURSUER)) return false;
            BlockPos p = mob.getPosition();
            return ExileWorldData.get(mob.world)
                            .exposure(mob.dimension, p.getX() >> 4, p.getZ() >> 4)
                    >= ExileConfig.fleeSeconds;
        }

        @Override
        public void updateTask() {
            mob.setAttackTarget(null);
        }
    }

    @SubscribeEvent
    public static void join(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (e.getWorld().isRemote
                || !(e.getEntity() instanceof EntityLiving)
                || !ExileMode.enabled(e.getWorld())
                || PocketDimensionManager.isPocketDimension(e.getEntity().dimension)) return;
        EntityLiving mob = (EntityLiving) e.getEntity();
        if (mob.isNonBoss() && !(mob instanceof EntityDecoy))
            mob.tasks.addTask(0, new EscapeTask(mob));
    }

    private ExileEncounters() {}

    static int typeCap(int stage,int kind) {
        int base=Math.max(4,Math.min(ExileConfig.pursuerCap,8+Math.max(1,stage)*4)),count=0;
        for(int i=0;i<base;i++)if(type(stage,i)==kind)count++;
        return kind==2?count:(count+1)/2;
    }
    static int cap(int stage) {return typeCap(stage,0)+typeCap(stage,1)+typeCap(stage,2);}
    static boolean slotAllowed(int kind,int occurrence) {return kind==2||(occurrence&1)==0;}
    static int kind(EntityLiving mob) {return mob instanceof EntityVex?0:mob instanceof EntityWitherSkeleton?2:1;}

    static int type(int stage, int sequence) {
        if(stage<=1) return 1;
        int slot=Math.floorMod(sequence,3);
        if(stage==2) return slot==2 ? 0 : 1;
        return slot==0 ? 2 : slot==1 ? 1 : 0;
    }

    /** The current infection stage controls every pursuer's replacement delay. */
    static int respawnSeconds(int stage) {
        return stage <= 1 ? ExileConfig.respawnStage1 : stage == 2 ? ExileConfig.respawnStage2 : ExileConfig.respawnLate;
    }

    private static int currentStage(EntityPlayerMP p) {
        int stage = ExileMode.stage(ExileWorldData.get(p.world).exposure(p.dimension,
                p.getPosition().getX() >> 4, p.getPosition().getZ() >> 4));
        return stage == 0 && ExileMode.state(p).getInteger("thirst") >= ExileConfig.thirstSeconds
                ? 1 : stage;
    }

    private static boolean canReinforce(EntityPlayerMP p) {
        return ExileMode.active(p) && !p.isDead && p.getHealth() > 0
                && !PocketDimensionManager.isPocketDimension(p.dimension) && !p.isCreative()
                && (!p.isSpectator() || Spector.isActive(p));
    }

    /** Reserve the dead mob's population slot so ordinary waves cannot refill it early. */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntityLiving().world.isRemote
                || !(event.getEntityLiving() instanceof EntityLiving)) return;
        EntityLiving mob = (EntityLiving) event.getEntityLiving();
        NBTTagCompound tag = mob.getEntityData();
        if (!tag.getBoolean(ExileMode.PURSUER) || tag.hasKey("pm_exile_trial")
                || tag.getBoolean("pm_exile_debug") || tag.getBoolean("pm_respawn_recorded")
                || !tag.hasUniqueId("pm_exile_owner")) return;
        EntityPlayer owner = mob.world.getPlayerEntityByUUID(tag.getUniqueId("pm_exile_owner"));
        if (!(owner instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) owner;
        if (p.dimension != mob.dimension || !canReinforce(p)) return;
        int stage = currentStage(p);
        if (stage < 1) return;
        NBTTagList tickets = ExileMode.state(p).getTagList("encounter_respawns", 10);
        NBTTagCompound ticket = new NBTTagCompound();
        ticket.setInteger("kind", kind(mob));
        ticket.setInteger("dimension", p.dimension);
        ticket.setLong("due", p.world.getTotalWorldTime() + respawnSeconds(stage) * 20L);
        tickets.appendTag(ticket);
        ExileMode.state(p).setTag("encounter_respawns", tickets);
        tag.setBoolean("pm_respawn_recorded", true);
    }

    private static int[] reservedSlots(EntityPlayerMP p) {
        int[] reserved = new int[3];
        NBTTagList tickets = ExileMode.state(p).getTagList("encounter_respawns", 10);
        for (int i = 0; i < tickets.tagCount(); i++) {
            NBTTagCompound ticket = tickets.getCompoundTagAt(i);
            int kind = ticket.getInteger("kind");
            if (ticket.getInteger("dimension") == p.dimension && kind >= 0 && kind < 3) reserved[kind]++;
        }
        return reserved;
    }

    private static void respawnTick(EntityPlayerMP p) {
        if (!canReinforce(p) || ExileDebug.reinforcementsPaused(p)) return;
        int stage = currentStage(p);
        if (stage < 1) return;
        NBTTagList tickets = ExileMode.state(p).getTagList("encounter_respawns", 10);
        long now = p.world.getTotalWorldTime();
        int attempts = 0;
        for (int i = 0; i < tickets.tagCount() && attempts < ExileConfig.spawnBatch;) {
            NBTTagCompound ticket = tickets.getCompoundTagAt(i);
            int kind = ticket.getInteger("kind");
            if (kind < 0 || kind > 2) { tickets.removeTag(i); continue; }
            if (ticket.getInteger("dimension") != p.dimension || now < ticket.getLong("due")
                    || now < ticket.getLong("retry") || typeCap(stage, kind) == 0) { i++; continue; }
            attempts++;
            if (spawnWave(p, stage, false, kind) > 0) tickets.removeTag(i);
            else { ticket.setLong("retry", now + 20); i++; }
        }
        ExileMode.state(p).setTag("encounter_respawns", tickets);
    }

    public static void protectFromSun(EntityLiving mob) {
        if (!(mob instanceof EntitySkeleton)) return;
        net.minecraft.item.ItemStack helmet = new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_HELMET);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        helmet.setTagCompound(tag);
        mob.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD, helmet);
        mob.setDropChance(net.minecraft.inventory.EntityEquipmentSlot.HEAD, 0);
        mob.extinguish();
    }

    /** Request a wave; all actual appearances go through the five-second queue. */
    public static void spawn(EntityPlayerMP player,int stage) { spawn(player,stage,false); }
    public static void spawn(EntityPlayerMP player,int stage,boolean manual) {
        if(stage<1||stage>4 || !manual && ExileDebug.reinforcementsPaused(player)) return;
        NBTTagCompound state=ExileMode.state(player);
        NBTTagCompound queue=state.getCompoundTag("encounter_queue");
        long now=player.world.getTotalWorldTime();
        if(queue.getBoolean("pending") && queue.getInteger("dimension")==player.dimension) {
            if(queue.getBoolean("manual") && !manual) return;
            if(queue.getInteger("stage")==stage && queue.getBoolean("manual")==manual) return;
        }
        queue=new NBTTagCompound();
        queue.setBoolean("pending",true); queue.setBoolean("manual",manual);
        queue.setInteger("stage",stage); queue.setInteger("dimension",player.dimension);
        queue.setLong("due",deadline(now,state.getLong("encounter_last_attempt"),state.hasKey("encounter_last_attempt")));
        state.setTag("encounter_queue",queue);
        ExileDebug.report(player).setString("result","queued");
    }
    static long deadline(long now,long previous,boolean hasPrevious) {
        return Math.max(now+100,hasPrevious ? previous+Math.max(5,ExileConfig.spawnInterval)*20L : now+100);
    }
    public static void cancel(EntityPlayerMP player) {
        NBTTagCompound s=ExileMode.state(player);
        if(s.getCompoundTag("encounter_queue").getBoolean("pending")) ExileDebug.report(player).setString("result","cancelled");
        s.removeTag("encounter_queue");
    }
    @SubscribeEvent public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e) {
        if(e.player instanceof EntityPlayerMP) { cancel((EntityPlayerMP)e.player); ExileMode.state(e.player).removeTag("encounter_last_attempt"); }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.player instanceof EntityPlayerMP) cancel((EntityPlayerMP)e.player);
    }
    @SubscribeEvent public static void queueTick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
        if(e.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p=(EntityPlayerMP)e.player;
        respawnTick(p);
        NBTTagCompound s=ExileMode.state(p),q=s.getCompoundTag("encounter_queue");
        if(!q.getBoolean("pending")) return;
        boolean manual=q.getBoolean("manual");
        if (!manual && ExileDebug.reinforcementsPaused(p)) { cancel(p); return; }
        if(!ExileMode.active(p) || p.isDead || p.getHealth()<=0 || p.dimension!=q.getInteger("dimension")
                || PocketDimensionManager.isPocketDimension(p.dimension)
                || (manual ? !ExileDebug.enabled(p)||!ExileDebug.mayEdit(p)
                            : p.isCreative()||(p.isSpectator()&&!Spector.isActive(p)))) { cancel(p); return; }
        if(!manual) {
            int stage=ExileMode.stage(ExileWorldData.get(p.world).exposure(p.dimension,
                    p.getPosition().getX()>>4,p.getPosition().getZ()>>4));
            if(stage==0 && s.getInteger("thirst")<ExileConfig.thirstSeconds) { cancel(p); return; }
            stage=Math.max(1,stage);
            if(stage!=q.getInteger("stage")) { cancel(p); spawn(p,stage,false); return; }
        }
        long now=p.world.getTotalWorldTime();
        if(now<q.getLong("due")) return;
        cancel(p); s.setLong("encounter_last_attempt",now);
        spawnWave(p,q.getInteger("stage"),manual);
    }
    static EntityLiving createMob(WorldServer world,int kind) {
        EntityLiving mob=kind==0 ? new EntityVex(world) : kind==1 ? new EntitySkeleton(world) : new EntityWitherSkeleton(world);
        // PathNavigate uses this range when creating its search region.
        mob.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64);
        return mob;
    }
    static boolean free(WorldServer world,EntityLiving mob,BlockPos pos) {
        mob.setLocationAndAngles(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        return world.checkNoEntityCollision(mob.getEntityBoundingBox())
                && world.getCollisionBoxes(mob,mob.getEntityBoundingBox()).isEmpty()
                && !world.containsAnyLiquid(mob.getEntityBoundingBox());
    }
    static void spawnWave(EntityPlayerMP player,int stage,boolean manual) {
        spawnWave(player, stage, manual, -1);
    }
    private static int spawnWave(EntityPlayerMP player,int stage,boolean manual,int replacementKind) {
        WorldServer world=player.getServerWorld();
        NBTTagCompound report=ExileDebug.report(player);
        report.setLong("last",world.getTotalWorldTime()); report.setInteger("wave",0);
        bump(report,"attempts");
        for(String reason:new String[]{"space","floor","visible","path","unloaded","event","planned_0","planned_1","planned_2","wave_0","wave_1","wave_2"}) report.setInteger(reason,0);
        if(world.getDifficulty()==net.minecraft.world.EnumDifficulty.PEACEFUL) { report.setString("result","peaceful"); return 0; }
        java.util.List<EntityLiving> existing=world.getEntitiesWithinAABB(EntityLiving.class,player.getEntityBoundingBox().grow(128),
                mob->mob.isEntityAlive()&&mob.getEntityData().getBoolean(ExileMode.PURSUER)
                        &&!mob.getEntityData().hasKey("pm_exile_trial"));
        int nearby=existing.size();int[] population=new int[3];
        for(EntityLiving mob:existing)population[kind(mob)]++;
        if(nearby>=cap(stage)) { report.setString("result","cap"); return 0; }
        int[] reserved = replacementKind < 0 ? reservedSlots(player) : new int[3];
        int held = 0;
        for (int k = 0; k < 3; k++) held += Math.min(reserved[k], Math.max(0, typeCap(stage,k) - population[k]));
        int spawned=0,paths=0,planned=0,wanted=replacementKind >= 0 ? 1
                : Math.min(ExileConfig.spawnBatch, Math.max(0, cap(stage)-nearby-held));
        int sequence=report.getInteger("sequence");
        BlockPos origin=player.getPosition();
        // Assign each slot a fixed type. Failed skeleton slots never turn into extra Vex.
        for(int slot=0;slot<wanted;slot++) {
            int kind=replacementKind >= 0 ? replacementKind : type(stage,sequence+slot);
            int occurrence=report.getInteger("quota_"+kind);report.setInteger("quota_"+kind,occurrence+1);
            if(replacementKind < 0 && !slotAllowed(kind,occurrence))continue;
            if(population[kind]+reserved[kind]>=typeCap(stage,kind))continue;
            planned++;bump(report,"planned_"+kind);
            EntityLiving mob=createMob(world,kind);
            for(int attempt=0;attempt<16;attempt++) {
                double angle=world.rand.nextDouble()*Math.PI*2;
                int radius=12+world.rand.nextInt(17);
                int x=origin.getX()+(int)(Math.cos(angle)*radius),z=origin.getZ()+(int)(Math.sin(angle)*radius);
                BlockPos pos=new BlockPos(x,origin.getY(),z);
                if(!world.isBlockLoaded(pos)) { bump(report,"unloaded"); continue; }
                if(kind==0) {
                    pos=pos.up(world.rand.nextInt(7)-2);
                    if(!free(world,mob,pos)) { bump(report,"space"); continue; }
                } else {
                    boolean found=false;
                    for(int k=0;k<33;k++) {
                        int dy=k==0 ? 0 : k%2==1 ? (k+1)/2 : -k/2;
                        BlockPos feet=new BlockPos(x,origin.getY()+dy,z);
                        if(PocketTeleporter.standable(world,feet)&&free(world,mob,feet)) {pos=feet;found=true;break;}
                    }
                    if(!found) { bump(report,"floor"); continue; }
                }
                if(!manual&&visible(world,mob)) { bump(report,"visible"); continue; }
                // New entities have not ticked yet. Vanilla ground navigation otherwise refuses
                // every path because onGround is still false, despite the validated solid floor.
                if(kind!=0) mob.onGround=true;
                Path path=null;
                // An archer with clear line of fire need not find a melee path to the player's feet.
                boolean firingLine=kind==1&&mob.canEntityBeSeen(player);
                if(kind!=0&&!firingLine) {
                    if(++paths>12) { bump(report,"path"); break; }
                    path=mob.getNavigator().getPathToEntityLiving(player);
                    if(path==null||path.getFinalPathPoint()==null||path.getFinalPathPoint().distanceTo(
                            new net.minecraft.pathfinding.PathPoint(origin.getX(),origin.getY(),origin.getZ()))>2) {bump(report,"path");continue;}
                }
                mob.onInitialSpawn(world.getDifficultyForLocation(pos),null);
                protectFromSun(mob);
                mob.getEntityData().setBoolean(ExileMode.PURSUER,true);
                mob.getEntityData().setBoolean("pm_exile_debug",manual);
                mob.getEntityData().setUniqueId("pm_exile_owner",player.getUniqueID());
                mob.getEntityData().setLong("pm_exile_birth",ExileWorldData.get(world).seconds);
                mob.setAttackTarget(player);
                if(path!=null)mob.getNavigator().setPath(path,1.1);
                if(world.spawnEntity(mob)) {spawned++;population[kind]++;bump(report,"total");bump(report,"total_"+kind);bump(report,"wave_"+kind);}
                else bump(report,"event");
                break;
            }
        }
        if (replacementKind < 0) report.setInteger("sequence",sequence+wanted);
        report.setInteger("wave",spawned);
        report.setString("result",planned==0?"reduced":spawned==0?"blocked":spawned<planned?"partial":"spawned");
        return spawned;
    }

    private static void bump(NBTTagCompound report, String key) { report.setInteger(key, report.getInteger(key) + 1); }

    private static boolean visible(WorldServer world, EntityLiving mob) {
        for (EntityPlayer p : world.playerEntities) {
            Vec3d to =
                    mob.getPositionVector()
                            .addVector(0, mob.height * 0.5, 0)
                            .subtract(p.getPositionEyes(1))
                            .normalize();
            if (p.getLookVec().dotProduct(to) > 0.25 && p.canEntityBeSeen(mob)) return true;
        }
        return false;
    }

    static boolean strandedExpired(NBTTagCompound tag,boolean unreachable,long now) {
        if(!unreachable) {tag.removeTag("pm_exile_stranded");return false;}
        if(!tag.hasKey("pm_exile_stranded"))tag.setLong("pm_exile_stranded",now);
        return now-tag.getLong("pm_exile_stranded")>=600;
    }

    private static boolean stranded(EntityLiving mob,EntityPlayer target) {
        NBTTagCompound tag=mob.getEntityData();
        if(mob instanceof EntityVex || tag.hasKey("pm_exile_trial") || tag.getBoolean("pm_exile_debug"))return false;
        if(Math.abs(mob.posY-target.posY)<=20 || mob.canEntityBeSeen(target)) {
            strandedExpired(tag,false,mob.world.getTotalWorldTime());return false;
        }
        if(mob.ticksExisted%200!=0)return false;
        Path path=mob.getNavigator().getPathToEntityLiving(target);
        BlockPos pos=target.getPosition();
        boolean reachable=path!=null&&path.getFinalPathPoint()!=null&&path.getFinalPathPoint().distanceTo(
                new net.minecraft.pathfinding.PathPoint(pos.getX(),pos.getY(),pos.getZ()))<=3;
        return strandedExpired(tag,!reachable,mob.world.getTotalWorldTime());
    }

    @SubscribeEvent
    public static void living(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityLiving)
                || event.getEntityLiving().world.isRemote) return;
        EntityLiving mob = (EntityLiving) event.getEntityLiving();
        if (mob.getEntityData().getBoolean(ExileMode.PURSUER) && mob instanceof EntitySkeleton) {
            if (mob.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD).isEmpty()) protectFromSun(mob);
            mob.extinguish();
        }
        if (mob.ticksExisted % 40 != 0
                || !ExileMode.enabled(mob.world)
                || PocketDimensionManager.isPocketDimension(mob.dimension)) return;
        NBTTagCompound n = mob.getEntityData();
        ExileWorldData data = ExileWorldData.get(mob.world);
        if (n.getBoolean(ExileMode.PURSUER)) {
            EntityPlayer target = mob.world.getPlayerEntityByUUID(n.getUniqueId("pm_exile_owner"));
            boolean testing = n.getBoolean("pm_exile_debug") && target != null && ExileDebug.enabled(target);
            if (target == null
                    || !target.isEntityAlive()
                    || !ExileMode.active(target)
                    || (!testing && target.isCreative())
                    || (!testing && target.isSpectator() && !Spector.isActive(target))
                    || target.getDistanceSq(mob) > 16384
                    || data.seconds - n.getLong("pm_exile_birth") > 300) {
                mob.setDead();
                return;
            }
            if (n.hasKey("pm_exile_trial") && !ExileMode.state(target).getBoolean("trial_active")) {
                mob.setDead();
                return;
            }
            if(stranded(mob,target)) {mob.setDead();return;}
            mob.setAttackTarget(target);
            return;
        }
        if (!mob.isNonBoss() || mob instanceof EntityDecoy) return;
        BlockPos origin = mob.getPosition();
        int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
        double exposure = data.exposure(mob.dimension, cx, cz);
        if (exposure >= ExileConfig.deathSeconds) {
            // Загрязнение не создаёт автоматическую ферму предметов и опыта.
            mob.world.setEntityState(mob, (byte) 3);
            mob.setDead();
            return;
        }
        if (exposure < ExileConfig.fleeSeconds) return;
        mob.setAttackTarget(null);
        double cleanest = exposure;
        BlockPos destination = null;
        int pathsTried = 0;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0 || pathsTried >= 4) continue;
                BlockPos column =
                        new BlockPos(((cx + dx) << 4) + 8, origin.getY(), ((cz + dz) << 4) + 8);
                if (!mob.world.isBlockLoaded(column)) continue;
                double next = data.exposure(mob.dimension, cx + dx, cz + dz);
                if (next >= cleanest) continue;
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos feet = column.up(dy);
                    if (!PocketTeleporter.standable(mob.world, feet)) continue;
                    if (++pathsTried > 4) break;
                    Path path = mob.getNavigator().getPathToPos(feet);
                    if (path == null
                            || path.getFinalPathPoint() == null
                            || path.getFinalPathPoint()
                                            .distanceTo(
                                                    new net.minecraft.pathfinding.PathPoint(
                                                            feet.getX(), feet.getY(), feet.getZ()))
                                    > 2) continue;
                    destination = feet;
                    cleanest = next;
                    break;
                }
            }
        if (destination != null)
            mob.getNavigator()
                    .tryMoveToXYZ(
                            destination.getX() + 0.5,
                            destination.getY(),
                            destination.getZ() + 0.5,
                            1.3);
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (e.getEntityLiving().getEntityData().getBoolean(ExileMode.PURSUER)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void experience(LivingExperienceDropEvent e) {
        if (e.getEntityLiving().getEntityData().getBoolean(ExileMode.PURSUER))
            e.setDroppedExperience(0);
    }
}
