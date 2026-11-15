package pocketmod;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Приманка-клон (body=false) или оставленное тело при Спекторе (body=true). Не сохраняется на диск. */
public class EntityDecoy extends EntityCreature {

    public UUID ownerId;
    public boolean body;
    public int life;

    public EntityDecoy(World w) {
        super(w);
        setSize(0.6F, 1.8F);
        setNoAI(true);
        enablePersistence();
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
    }

    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (world.isRemote) return false;
        if (source == DamageSource.OUT_OF_WORLD) {
            setDead();
            return false;
        }
        if (body) {
            Spector.onBodyHit(this, source, amount);
        } else {
            setDead();
        }
        return false;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) return;
        if (ownerId == null) {
            setDead();
            return;
        }
        EntityPlayer owner = world.getPlayerEntityByUUID(ownerId);
        if (owner == null) {
            setDead();
            return;
        }
        life++;
        if (body) {
            if (!Spector.isActive(owner)) {
                setDead();
                return;
            }
        } else {
            if (!Abilities.isInvisible(owner) || life > PocketConfig.decoyMaxSec * 20) {
                setDead();
                return;
            }
        }
        if (life % 20 == 0) {
            AxisAlignedBB box = getEntityBoundingBox().grow(20.0);
            List<EntityLiving> list = world.getEntitiesWithinAABB(EntityLiving.class, box);
            for (EntityLiving e : list) {
                if (e == this || !(e instanceof IMob) || e.isDead) continue;
                if (e.getAttackTarget() != this) e.setAttackTarget(this);
            }
        }
    }

    /** Убирает все приманки игрока. */
    public static void removeDecoys(EntityPlayer owner) {
        for (Object o : new java.util.ArrayList<Object>(owner.world.loadedEntityList)) {
            if (o instanceof EntityDecoy) {
                EntityDecoy d = (EntityDecoy) o;
                if (!d.body && owner.getUniqueID().equals(d.ownerId)) d.setDead();
            }
        }
    }
}
