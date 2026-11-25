package pocketmod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.*;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.*;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.event.ForgeEventFactory;

/** Heavy dash boss. All damage, target snapshots and destruction are authoritative on the server. */
public final class EntityRunningRhino extends EntityMob {
    public static final int ROAM=0, HORN=1, WINDUP=2, CHARGE=3, STUCK=4, OVERHEAT=5, REAR=6, RECOVER=7, RETREAT=8, DIG=9, BREACH=10;
    private static final DataParameter<Integer> ACTION=EntityDataManager.createKey(EntityRunningRhino.class,DataSerializers.VARINT);
    private static final DataParameter<Integer> REMAIN=EntityDataManager.createKey(EntityRunningRhino.class,DataSerializers.VARINT);
    private static final DataParameter<Integer> LENGTH=EntityDataManager.createKey(EntityRunningRhino.class,DataSerializers.VARINT);
    private final BossInfoServer bar=new BossInfoServer(new TextComponentTranslation("entity.pocketmod.running_rhino.name"),BossInfo.Color.RED,BossInfo.Overlay.PROGRESS);
    private final RhinoTactics tactics=new RhinoTactics();
    private final Set<UUID> chargeHits=new HashSet<>(),waveHits=new HashSet<>();
    private int remaining, duration, attackDelay=55, airProbe=35, circleTicks, combo, waveAge=-1;
    private double dashX,dashZ,waveX,waveY,waveZ;
    private BlockPos support, coverMark, breakMark;
    private int coverMemory, blockedTicks, coverCooldown;
    private double retreatX,retreatZ;
    public boolean angry(){return action()==RETREAT||action()==DIG||action()==BREACH;}
    private boolean mobile(){return action()==ROAM||action()==RETREAT;}
    public boolean bellyOpen(float partial){return RhinoCoverRules.bellyOpen(action(),progress(partial));}
    @Override public boolean attackEntityFrom(DamageSource source,float amount){
        if(source==DamageSource.IN_WALL&&action()==REAR)return false;
        Entity attacker=source.getTrueSource(),direct=source.getImmediateSource();
        if(attacker instanceof EntityLivingBase&&valid((EntityLivingBase)attacker)){
            double dx=attacker.posX-posX,dz=attacker.posZ-posZ,yaw=Math.toRadians(rotationYaw);
            double h=direct!=null&&direct!=attacker?direct.posY-posY:attacker.posY+attacker.getEyeHeight()-posY;
            if(bellyOpen(0)&&RhinoCoverRules.bellyHit(-dx*Math.sin(yaw)+dz*Math.cos(yaw),dx*Math.cos(yaw)+dz*Math.sin(yaw),h))amount*=1.8F;
        }
        boolean hit=super.attackEntityFrom(source,amount);
        if(hit&&!world.isRemote&&isEntityAlive()&&attacker instanceof EntityLivingBase&&valid((EntityLivingBase)attacker)){
            remember((EntityLivingBase)attacker);setAttackTarget((EntityLivingBase)attacker);
            if(RhinoCoverRules.below(attacker.posX-posX,attacker.posY-posY,attacker.posZ-posZ)&&action()!=RETREAT)startRetreat();
        }
        return hit;
    }
    private void remember(EntityLivingBase p){coverMark=new BlockPos(p.posX,p.posY,p.posZ);coverMemory=200;}
    private void startRetreat(){
        if(coverMark==null)return;
        double[] at=RhinoCoverRules.retreat(posX,posZ,coverMark.getX()+.5,coverMark.getZ()+.5,Math.toRadians(rotationYaw));
        retreatX=at[0];retreatZ=at[1];combo=0;begin(RETREAT,55);
        sound(SoundEvents.ENTITY_POLAR_BEAR_WARNING,1.5F,.65F);
    }
    public EntityRunningRhino(World world) { super(world);setSize(2.8F,2.9F);stepHeight=1;experienceValue=60;enablePersistence();isImmuneToFire=true; }
    @Override protected void entityInit(){super.entityInit();dataManager.register(ACTION,ROAM);dataManager.register(REMAIN,0);dataManager.register(LENGTH,1);}
    @Override protected void initEntityAI(){
        tasks.addTask(0,new EntityAISwimming(this));
        tasks.addTask(7,new EntityAIWatchClosest(this,EntityPlayer.class,32));
        tasks.addTask(8,new EntityAILookIdle(this));
        targetTasks.addTask(1,new EntityAIHurtByTarget(this,false));
        targetTasks.addTask(2,new EntityAINearestAttackableTarget<EntityPlayer>(this,EntityPlayer.class,true));
    }
    @Override protected void applyEntityAttributes(){super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(180);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(9);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.27);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(8);
    }
    public int action(){return dataManager.get(ACTION);}
    public float progress(float partial){return MathHelper.clamp(1F-(dataManager.get(REMAIN)-partial)/(float)Math.max(1,dataManager.get(LENGTH)),0,1);}
    public boolean enraged(){return getHealth()<=getMaxHealth()*.5F;}
    private void begin(int state,int ticks){getNavigator().clearPath();remaining=duration=ticks;dataManager.set(ACTION,state);dataManager.set(REMAIN,ticks);dataManager.set(LENGTH,ticks);}
    @Override public boolean isNonBoss(){return false;}
    @Override protected boolean canDespawn(){return false;}
    @Override protected ResourceLocation getLootTable(){return LootTableList.EMPTY;}
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.ENTITY_POLAR_BEAR_AMBIENT;}
    @Override protected SoundEvent getHurtSound(DamageSource s){return SoundEvents.ENTITY_POLAR_BEAR_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.ENTITY_POLAR_BEAR_DEATH;}
    @Override public void addTrackingPlayer(EntityPlayerMP p){super.addTrackingPlayer(p);bar.addPlayer(p);}
    @Override public void removeTrackingPlayer(EntityPlayerMP p){super.removeTrackingPlayer(p);bar.removePlayer(p);}
    @Override public void setDead(){super.setDead();if(bar!=null)bar.setVisible(false);}
    private boolean valid(EntityLivingBase p){return p!=null&&p.isEntityAlive()&&(!(p instanceof EntityPlayer)||(!((EntityPlayer)p).isCreative()&&!((EntityPlayer)p).isSpectator()));}
    @Override public void onLivingUpdate(){
        if(!mobile()){motionX=motionZ=0;moveForward=moveStrafing=0;}
        super.onLivingUpdate();
        float desiredHeight=action()==REAR?6.2F:2.9F;
        if(height!=desiredHeight)setSize(2.8F,desiredHeight);
        if(world.isRemote){if(action()==OVERHEAT&&ticksExisted%3==0)world.spawnParticle(EnumParticleTypes.CLOUD,posX,posY+2.4,posZ,.02,.13,.02);return;}
        if(!isEntityAlive())return;
        bar.setPercent(getHealth()/getMaxHealth());
        if(coverCooldown>0)coverCooldown--;
        if(coverMemory>0)coverMemory--;else coverMark=null;
        if(waveAge>=0)waveTick();
        EntityLivingBase target=getAttackTarget();
        boolean hasTarget=valid(target)&&getDistanceSq(target)<64*64;
        boolean visible=hasTarget&&canEntityBeSeen(target);
        if(visible)remember(target);
        boolean elevated=hasTarget&&target.posY>posY+2.8;
        tactics.tick(elevated,action()==ROAM);
        if(attackDelay>0)attackDelay--;
        if(!hasTarget&&action()!=ROAM){begin(ROAM,1);combo=0;support=null;}
        if(action()==ROAM){if(hasTarget)choose(target,elevated);return;}
        int state=action(),elapsed=duration-remaining;
        if(state!=RETREAT)getNavigator().clearPath();
        if(state==RETREAT){
            if(elapsed%10==0){
                net.minecraft.pathfinding.Path path=getNavigator().getPathToXYZ(retreatX,posY,retreatZ);
                if(path!=null)getNavigator().setPath(path,1.1);
                else {
                    Vec3d away=new Vec3d(retreatX-posX,0,retreatZ-posZ).normalize();
                    Vec3d across=new Vec3d(-away.z,0,away.x).scale(elapsed%20==0?4:-4);
                    getNavigator().tryMoveToXYZ(posX+away.x*3+across.x,posY,posZ+away.z*3+across.z,1.1);
                }
            }
            if(elapsed%12==0){sound(SoundEvents.ENTITY_POLAR_BEAR_AMBIENT,1.3F,.65F);particles(EnumParticleTypes.SMOKE_NORMAL,posX,posY+2,posZ,7,.7,.15,.7);}
            if(coverMark!=null)getLookHelper().setLookPosition(coverMark.getX()+.5,coverMark.getY()+1,coverMark.getZ()+.5,12,12);
        }
        if(state==DIG||state==BREACH){
            if(breakMark!=null){double dx=breakMark.getX()+.5-posX,dz=breakMark.getZ()+.5-posZ;rotationYaw=(float)(Math.atan2(-dx,dz)*180/Math.PI);renderYawOffset=rotationYaw;}
            if(elapsed==15)breakCover(state==DIG);
        }
        if(state==WINDUP){face(target,4);if(elapsed%8==0)particles(EnumParticleTypes.SMOKE_NORMAL,posX,posY+.2,posZ,6,1,.1,1);}
        if(state==HORN&&elapsed==8)horn();
        if(state==CHARGE){chargeTick();if(action()!=CHARGE)return;}
        if(state==REAR){
            if(elapsed==8&&hasTarget)support=new BlockPos(target.posX,target.getEntityBoundingBox().minY-.15,target.posZ);
            if(elapsed>=8&&elapsed%4==0&&support!=null)particles(EnumParticleTypes.SMOKE_NORMAL,support.getX()+.5,support.getY()+1,support.getZ()+.5,10,1,.1,1);
            if(elapsed==30)collapse();
        }
        if(state==OVERHEAT&&elapsed%8==0){particles(EnumParticleTypes.CLOUD,posX,posY+2.2,posZ,8,.8,.3,.8);}
        dataManager.set(REMAIN,--remaining);
        if(remaining>0)return;
        if(state==RETREAT){
            if(coverMark!=null&&RhinoCoverRules.shallow(posY,coverMark.getY())&&coverCooldown==0&&getDistanceSqToCenter(coverMark)<11*11){
                breakMark=findPitEdge();if(breakMark!=null){begin(DIG,38);return;}
            }
            begin(ROAM,1);attackDelay=40;return;
        }
        if(state==DIG||state==BREACH){coverCooldown=100;blockedTicks=0;breakMark=null;}
        if(state==WINDUP){
            double dx=target.posX-posX,dz=target.posZ-posZ,len=Math.sqrt(dx*dx+dz*dz);
            if(len<.01){dx=-Math.sin(Math.toRadians(rotationYaw));dz=Math.cos(Math.toRadians(rotationYaw));len=1;}
            dashX=dx/len;dashZ=dz/len;rotationYaw=(float)(Math.atan2(-dashX,dashZ)*180/Math.PI);renderYawOffset=rotationYaw;
            chargeHits.clear();begin(CHARGE,30);sound(SoundEvents.ENTITY_POLAR_BEAR_WARNING,1.5F,.65F);
        }else if(state==CHARGE){begin(OVERHEAT,combo>0?20:54);sound(SoundEvents.BLOCK_FIRE_EXTINGUISH,1.4F,.65F);}
        else if(state==OVERHEAT&&combo>0){combo--;begin(WINDUP,20);}
        else{begin(ROAM,1);attackDelay=state==HORN?18:50;}
    }
    private void choose(EntityLivingBase target,boolean elevated){
        face(target,5);double distance=getDistanceSq(target);
        if(coverMark!=null&&RhinoCoverRules.below(coverMark.getX()+.5-posX,coverMark.getY()-posY,coverMark.getZ()+.5-posZ)){startRetreat();return;}
        if(coverMark!=null&&coverMark.getY()<posY-4){getNavigator().clearPath();return;}
        boolean seen=canEntityBeSeen(target);
        if(coverMark!=null&&coverCooldown==0){
            boolean obstructed=!seen||getNavigator().noPath()&&distance>4.8*4.8;
            blockedTicks=obstructed?blockedTicks+1:Math.max(0,blockedTicks-2);
            if(blockedTicks>=30){
                RayTraceResult ray=world.rayTraceBlocks(new Vec3d(posX,posY+1,posZ),new Vec3d(coverMark.getX()+.5,coverMark.getY()+1,coverMark.getZ()+.5),false,true,false);
                if(ray!=null&&ray.typeOfHit==RayTraceResult.Type.BLOCK){
                    BlockPos at=ray.getBlockPos();
                    if(at.distanceSq(posX,posY+1,posZ)<=5.5*5.5){breakMark=at;begin(BREACH,34);sound(SoundEvents.ENTITY_POLAR_BEAR_WARNING,1.2F,.65F);return;}
                    if(ticksExisted%12==0)getNavigator().tryMoveToXYZ(at.getX()+.5,posY,at.getZ()+.5,1);
                    return;
                }
            }
        }
        if(!seen){if(coverMark!=null&&ticksExisted%12==0)getNavigator().tryMoveToXYZ(coverMark.getX()+.5,coverMark.getY(),coverMark.getZ()+.5,1);return;}
        if(elevated){
            if(tactics.shouldCollapse(true)){begin(REAR,44);sound(SoundEvents.ENTITY_POLAR_BEAR_WARNING,1.4F,.65F);return;}
            // Approaches, failed horn probes and circling alternate before a collapse is considered.
            if(--airProbe<=0){airProbe=65+rand.nextInt(35);tactics.attemptedHorn();
                if(distance<13*13){begin(HORN,18);sound(SoundEvents.ENTITY_POLAR_BEAR_AMBIENT,1,.7F);return;}}
            if(ticksExisted%55==0)sound(SoundEvents.ENTITY_POLAR_BEAR_AMBIENT,.9F,.7F+rand.nextFloat()*.2F);
            if(circleTicks++%35==0){
                double angle=Math.atan2(posZ-target.posZ,posX-target.posX)+(rand.nextBoolean()?1:-1)*(.6+rand.nextDouble());
                double radius=4+rand.nextDouble()*3;
                // Every third attempt tries the actual route to the elevated target.
                if(circleTicks%105==1)getNavigator().tryMoveToEntityLiving(target,1);
                else getNavigator().tryMoveToXYZ(target.posX+Math.cos(angle)*radius,posY,target.posZ+Math.sin(angle)*radius,.85);
            }
        }else{
            airProbe=35;
            if(attackDelay<=0&&distance<4.8*4.8){begin(HORN,18);return;}
            if(attackDelay<=0&&distance>=5*5&&distance<30*30&&canEntityBeSeen(target)){
                combo=enraged()?1:0;begin(WINDUP,24);sound(SoundEvents.ENTITY_POLAR_BEAR_WARNING,1.2F,.75F);return;
            }
            if(ticksExisted%12==0)getNavigator().tryMoveToEntityLiving(target,1);
        }
    }
    private void face(EntityLivingBase target,float turn){if(!valid(target))return;faceEntity(target,turn,turn);renderYawOffset=rotationYaw;rotationYawHead=rotationYaw;}
    private void horn(){
        sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,1.2F,.7F);
        double yaw=Math.toRadians(rotationYaw),fx=-Math.sin(yaw),fz=Math.cos(yaw);
        for(EntityLivingBase p:world.getEntitiesWithinAABB(EntityLivingBase.class,getEntityBoundingBox().grow(4,3,4))){
            if(p==this||!valid(p))continue;double dx=p.posX-posX,dz=p.posZ-posZ;
            if(RhinoTactics.inHornArc(dx*fx+dz*fz,dx*fz-dz*fx,p.posY-posY)&&canEntityBeSeen(p)){
                if(p.attackEntityFrom(DamageSource.causeMobDamage(this),9)){p.knockBack(this,.9F,-dx,-dz);p.motionY=Math.max(p.motionY,.4);p.velocityChanged=true;}
            }
        }
    }
    private void chargeTick(){
        float speed=enraged()?1.05F:.9F;
        AxisAlignedBB next=getEntityBoundingBox().offset(dashX*speed,0,dashZ*speed);
        boolean obstruction=false;int destroyed=0;
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(next.minX,posY+.2,next.minZ),new BlockPos(next.maxX,posY+height-.1,next.maxZ))){
            IBlockState b=world.getBlockState(at);if(b.getMaterial().blocksMovement()){
                obstruction=true;if(destroyed<80&&breakBlock(at))destroyed++;
            }
        }
        move(MoverType.SELF,dashX*speed,0,dashZ*speed);motionX=motionZ=0;
        for(EntityLivingBase p:world.getEntitiesWithinAABB(EntityLivingBase.class,getEntityBoundingBox().grow(.9,.3,.9))){
            if(p==this||!valid(p)||chargeHits.contains(p.getUniqueID()))continue;
            chargeHits.add(p.getUniqueID());if(p.attackEntityFrom(DamageSource.causeMobDamage(this),enraged()?15:12)){
                p.knockBack(this,1.8F,-dashX,-dashZ);p.motionY=Math.max(p.motionY,.45);p.velocityChanged=true;
            }
        }
        if(ticksExisted%4==0){sound(SoundEvents.BLOCK_STONE_STEP,.9F,.65F);particles(EnumParticleTypes.SMOKE_NORMAL,posX,posY+.2,posZ,8,1,.1,1);}
        if(obstruction||collidedHorizontally){combo=0;begin(STUCK,38);sound(SoundEvents.ENTITY_GENERIC_EXPLODE,1,.65F);particles(EnumParticleTypes.EXPLOSION_NORMAL,posX,posY+1,posZ,18,1,1,1);}
    }
    private BlockPos findPitEdge(){
        if(coverMark==null)return null;
        Vec3d toward=new Vec3d(coverMark.getX()+.5-posX,0,coverMark.getZ()+.5-posZ).normalize();
        // Start at the rim we can actually reach, never excavate remotely beneath the player.
        for(int i=4;i>=1;i--){
            BlockPos p=new BlockPos(posX+toward.x*i,posY-.1,posZ+toward.z*i);
            if(world.isBlockLoaded(p)&&world.getBlockState(p).getMaterial().blocksMovement())return p;
        }
        return null;
    }
    private void breakCover(boolean digging){
        if(breakMark==null)return;
        if(breakMark.distanceSq(posX,posY,posZ)>6*6)return;
        BlockPos from=breakMark.add(-1,digging?-1:-1,-1),to=breakMark.add(1,digging?0:2,1);
        for(BlockPos p:BlockPos.getAllInBox(from,to)){
            // Never remove the column currently supporting the boss itself.
            if(digging&&p.getY()<posY&&Math.abs(p.getX()+.5-posX)<1.6&&Math.abs(p.getZ()+.5-posZ)<1.6)continue;
            breakBlock(p);
        }
        sound(SoundEvents.BLOCK_STONE_BREAK,1.5F,.65F);
        particles(EnumParticleTypes.SMOKE_LARGE,breakMark.getX()+.5,breakMark.getY()+1,breakMark.getZ()+.5,24,1,.4,1);
    }
    private boolean breakBlock(BlockPos at){
        if(!world.isBlockLoaded(at)||!ForgeEventFactory.getMobGriefingEvent(world,this))return false;
        IBlockState state=world.getBlockState(at);
        if(state.getMaterial().isLiquid()||world.isAirBlock(at)||state.getBlockHardness(world,at)<0)return false;
        if(!state.getBlock().canEntityDestroy(state,world,at,this)||!ForgeEventFactory.onEntityDestroyBlock(this,at,state))return false;
        return world.destroyBlock(at,true);
    }
    private void collapse(){
        tactics.collapsed();sound(SoundEvents.ENTITY_GENERIC_EXPLODE,1.7F,.55F);
        // Locked support is always processed first, independently of the nearby-block budget.
        if(support!=null)for(BlockPos p:BlockPos.getAllInBox(support.add(-1,-2,-1),support.add(1,0,1)))breakBlock(p);
        int count=0;
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(posX-5,posY+1,posZ-5),new BlockPos(posX+5,posY+8,posZ+5))){
            if(count>=160)break;if(p.distanceSq(posX,posY+3,posZ)>6*6)continue;
            if(breakBlock(p))count++;
        }
        particles(EnumParticleTypes.EXPLOSION_NORMAL,posX,posY+.5,posZ,35,3,.2,3);
        waveX=posX;waveY=posY;waveZ=posZ;waveAge=0;waveHits.clear();support=null;
    }
    private double ground(double x,double z){
        BlockPos p=new BlockPos(x,waveY+3,z);
        if(!world.isBlockLoaded(p))return Double.NaN;
        for(int i=0;i<10;i++,p=p.down())if(world.getBlockState(p).getMaterial().blocksMovement())return p.getY()+1;
        return Double.NaN;
    }
    private void waveTick(){
        double radius=++waveAge*.6;
        for(int i=0;i<40;i++){double a=i*Math.PI/20,x=waveX+Math.cos(a)*radius,z=waveZ+Math.sin(a)*radius,y=ground(x,z);
            if(!Double.isNaN(y))particles(EnumParticleTypes.SMOKE_LARGE,x,y+.1,z,1,.05,.03,.05);}
        for(EntityLivingBase p:world.getEntitiesWithinAABB(EntityLivingBase.class,new AxisAlignedBB(waveX-16,waveY-7,waveZ-16,waveX+16,waveY+6,waveZ+16))){
            if(p==this||!valid(p)||waveHits.contains(p.getUniqueID()))continue;
            double dx=p.posX-waveX,dz=p.posZ-waveZ,d=Math.sqrt(dx*dx+dz*dz),floor=ground(p.posX,p.posZ);
            if(Math.abs(d-radius)<1.1&&!Double.isNaN(floor)&&p.getEntityBoundingBox().minY>=floor-.4&&p.getEntityBoundingBox().minY<floor+.7){
                waveHits.add(p.getUniqueID());if(p.attackEntityFrom(DamageSource.causeMobDamage(this),8)){p.knockBack(this,.6F,-dx,-dz);p.velocityChanged=true;}
            }
        }
        if(waveAge>=24)waveAge=-1;
    }
    private void particles(EnumParticleTypes type,double x,double y,double z,int n,double dx,double dy,double dz){((WorldServer)world).spawnParticle(type,x,y,z,n,dx,dy,dz,.03);}
    private void sound(SoundEvent event,float volume,float pitch){world.playSound(null,posX,posY,posZ,event,SoundCategory.HOSTILE,volume,pitch);}
    @Override protected void dropLoot(boolean recent,int looting,DamageSource source){super.dropLoot(recent,looting,source);if(recent)entityDropItem(AbilityDiscoveryItem.create("dash"),.5F);}
    @Override public void writeEntityToNBT(NBTTagCompound n){super.writeEntityToNBT(n);n.setInteger("RhinoIrritation",tactics.frustration());n.setInteger("RhinoCollapseDelay",tactics.cooldown());}
    @Override public void readEntityFromNBT(NBTTagCompound n){super.readEntityFromNBT(n);tactics.restore(n.getInteger("RhinoIrritation"),n.getInteger("RhinoCollapseDelay"));begin(ROAM,1);attackDelay=55;}
}
