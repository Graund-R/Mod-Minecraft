package pocketmod;

import net.minecraft.entity.*;
import net.minecraft.network.datasync.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.*;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.*;
import net.minecraft.world.storage.loot.LootTableList;

/** A deliberate teleport predator: marked ground is the warning, never the player. */
public class EntityRiftWalker extends EntityMob {
    private final BossInfoServer bar = new BossInfoServer(new TextComponentTranslation("entity.pocketmod.rift_walker.name"), BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS);
    private static final DataParameter<Integer> WINDUP = EntityDataManager.createKey(EntityRiftWalker.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> WINDUP_LENGTH = EntityDataManager.createKey(EntityRiftWalker.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> PULSE = EntityDataManager.createKey(EntityRiftWalker.class, DataSerializers.BOOLEAN);
    private int visualImpact, visualPhase, visualMelee;
    private boolean previousPhase;
    @Override protected void entityInit() {
        super.entityInit();
        dataManager.register(WINDUP, 0);
        dataManager.register(WINDUP_LENGTH, 30);
        dataManager.register(PULSE, false);
    }
    public boolean preparing() { return dataManager.get(WINDUP) > 0; }
    public boolean preparingPulse() { return preparing() && dataManager.get(PULSE); }
    public float windup(float partial) {
        return preparing() ? MathHelper.clamp(1F - (dataManager.get(WINDUP) - partial) / dataManager.get(WINDUP_LENGTH), 0F, 1F) : 0;
    }
    public float impact(float partial) { return Math.max(0, visualImpact-partial)/10F; }
    public float meleeSwing(float partial) {
        return visualMelee > 0 ? MathHelper.sin((1-Math.max(0,visualMelee-partial)/8F)*(float)Math.PI) : 0;
    }
    @Override public boolean attackEntityAsMob(Entity target) {
        boolean hit = super.attackEntityAsMob(target);
        if (hit && !world.isRemote) world.setEntityState(this, (byte)61);
        return hit;
    }
    public float phaseFlash(float partial) { return Math.max(0, visualPhase-partial)/30F; }
    @Override public void handleStatusUpdate(byte id) {
        if (id == 61) { visualMelee = 8; return; }
        if (id == 60) { visualImpact = 10; return; }
        super.handleStatusUpdate(id);
    }
    private int cooldown = 65, charge, pattern;
    private double markX, markY, markZ;
    public EntityRiftWalker(World world) {
        super(world);
        setSize(0.75F, 2.9F);
        experienceValue = 50;
        enablePersistence();
        isImmuneToFire = true;
    }
    @Override protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0, false));
        tasks.addTask(6, new EntityAIWanderAvoidWater(this, 0.7));
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 24));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<EntityPlayer>(this, EntityPlayer.class, true));
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(120);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.27);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(40);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.75);
    }
    public static int warningTicks(boolean enraged) { return enraged ? 22 : 30; }
    public static int recoveryTicks(boolean enraged) { return enraged ? 55 : 85; }
    public static boolean inStrike(double dx, double dy, double dz, double radius) {
        return Math.abs(dy) <= 2.5 && dx * dx + dz * dz <= radius * radius;
    }
    public boolean enraged() { return getHealth() <= getMaxHealth() * 0.5F; }
    @Override public boolean isAIDisabled() { return charge > 0 || super.isAIDisabled(); }
    @Override public boolean isNonBoss() { return false; }
    @Override protected boolean canDespawn() { return false; }
    @Override protected ResourceLocation getLootTable() { return LootTableList.EMPTY; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_ENDERMEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_ENDERMEN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_ENDERMEN_DEATH; }
    @Override public void addTrackingPlayer(EntityPlayerMP p) { super.addTrackingPlayer(p); bar.addPlayer(p); }
    @Override public void removeTrackingPlayer(EntityPlayerMP p) { super.removeTrackingPlayer(p); bar.removePlayer(p); }
    @Override public void setDead() { super.setDead(); if (bar != null) bar.setVisible(false); }
    @Override public void onLivingUpdate() {
        if (charge > 0) { motionX = motionZ = 0; moveForward = moveStrafing = 0; }
        super.onLivingUpdate();
        if (world.isRemote) {
            if (visualMelee > 0) visualMelee--;
            if (visualImpact > 0) visualImpact--;
            if (visualPhase > 0) visualPhase--;
            boolean phase = enraged();
            if (phase && !previousPhase) visualPhase = 30;
            previousPhase = phase;
            return;
        }
        if (!isEntityAlive()) return;
        bar.setPercent(getHealth()/getMaxHealth());
        bar.setColor(enraged() ? BossInfo.Color.RED : BossInfo.Color.PURPLE);
        EntityLivingBase target = getAttackTarget();
        if (target == null || !target.isEntityAlive()) { charge = 0; dataManager.set(WINDUP, 0); return; }
        if (charge > 0) {
            getNavigator().clearPath(); motionX = motionZ = 0;
            ring(markX, markY + 0.12, markZ, pattern % 3 == 0 ? 4.5 : 2.8);
            dataManager.set(WINDUP, --charge);
            if (charge == 0) {
                // A failed landing cancels the strike: no damage through walls or inside blocks.
                Vec3d departure=new Vec3d(posX,posY+height*.5,posZ);
                boolean landed = pattern % 3 == 0 || attemptTeleport(markX, markY, markZ);
                if(landed&&pattern%3!=0){TeleportEffects.depart(this,(WorldServer)world,departure);TeleportEffects.arrive(this,(WorldServer)world,new Vec3d(posX,posY+height*.5,posZ));}
                if (landed) {
                    world.setEntityState(this, (byte)60);
                    double x = pattern % 3 == 0 ? posX : markX;
                    double y = pattern % 3 == 0 ? posY : markY;
                    double z = pattern % 3 == 0 ? posZ : markZ;
                    double radius = pattern % 3 == 0 ? 4.5 : 2.8;
                    if(pattern%3==0)world.playSound(null, x,y,z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.2F, 0.7F);
                    ((WorldServer)world).spawnParticle(EnumParticleTypes.DRAGON_BREATH,x,y+1,z,65,radius/2,0.6,radius/2,0.04);
                    for (EntityPlayer p : world.playerEntities) {
                        if (p.isCreative() || p.isSpectator() || !p.isEntityAlive()) continue;
                        if (inStrike(p.posX-x,p.posY-y,p.posZ-z,radius) && canEntityBeSeen(p)) {
                            p.attackEntityFrom(DamageSource.causeMobDamage(this), enraged() ? 8 : 6);
                            p.knockBack(this,0.65F,x-p.posX,z-p.posZ);
                        }
                    }
                }
                cooldown = recoveryTicks(enraged());
            }
        } else if (--cooldown <= 0 && getDistanceSq(target) < 28*28 && canEntityBeSeen(target)) {
            pattern++;
            boolean pulse = pattern % 3 == 0;
            markX = pulse ? posX : target.posX; markY = pulse ? posY : target.posY; markZ = pulse ? posZ : target.posZ;
            charge = warningTicks(enraged());
            dataManager.set(WINDUP_LENGTH, charge);
            dataManager.set(PULSE, pulse);
            dataManager.set(WINDUP, charge);
            world.playSound(null,posX,posY,posZ,SoundEvents.BLOCK_PORTAL_TRIGGER,SoundCategory.HOSTILE,0.7F,1.6F);
        }
    }
    private void ring(double x,double y,double z,double radius) {
        WorldServer server = (WorldServer)world;
        for (int i=0;i<28;i++) {
            double a=i*Math.PI*2/28;
            server.spawnParticle(EnumParticleTypes.SPELL_WITCH,x+Math.cos(a)*radius,y,z+Math.sin(a)*radius,1,0.0,0.03,0.0,0.0);
        }
    }
    @Override protected void dropLoot(boolean wasRecentlyHit,int looting,DamageSource source) {
        super.dropLoot(wasRecentlyHit,looting,source);
        if (wasRecentlyHit) entityDropItem(AbilityDiscoveryItem.create("teleport"),0.5F);
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) { super.writeEntityToNBT(n); n.setBoolean("NoAI", super.isAIDisabled()); n.setInteger("RiftPattern",pattern); }
    @Override public void readEntityFromNBT(NBTTagCompound n) { super.readEntityFromNBT(n); pattern=n.getInteger("RiftPattern"); }
}
