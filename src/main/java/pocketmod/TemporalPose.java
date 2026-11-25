package pocketmod;

import net.minecraft.entity.*;
import net.minecraft.nbt.NBTTagCompound;

/** Animation clocks and combat references that vanilla entity NBT omits. */
final class TemporalPose {
    static NBTTagCompound capture(Entity entity){
        NBTTagCompound n=new NBTTagCompound();n.setInteger("ticks",entity.ticksExisted);n.setInteger("invulnerable",entity.hurtResistantTime);
        if(entity instanceof EntityLivingBase){
            EntityLivingBase e=(EntityLivingBase)entity;
            n.setFloat("limb",e.limbSwing);n.setFloat("amount",e.limbSwingAmount);n.setFloat("swing",e.swingProgress);
            n.setInteger("swing_tick",e.swingProgressInt);n.setBoolean("swinging",e.isSwingInProgress);
            n.setFloat("body",e.renderYawOffset);n.setFloat("head",e.rotationYawHead);n.setInteger("hurt",e.hurtTime);n.setInteger("death",e.deathTime);
        }
        return n;
    }
    static void apply(Entity entity,NBTTagCompound n){
        if(!n.hasKey("ticks"))return;entity.ticksExisted=n.getInteger("ticks");entity.hurtResistantTime=n.getInteger("invulnerable");
        if(entity instanceof EntityLivingBase){
            EntityLivingBase e=(EntityLivingBase)entity;
            e.limbSwing=n.getFloat("limb");e.prevLimbSwingAmount=e.limbSwingAmount=n.getFloat("amount");
            e.prevSwingProgress=e.swingProgress=n.getFloat("swing");e.swingProgressInt=n.getInteger("swing_tick");e.isSwingInProgress=n.getBoolean("swinging");
            e.prevRenderYawOffset=e.renderYawOffset=n.getFloat("body");e.prevRotationYawHead=e.rotationYawHead=n.getFloat("head");
            e.hurtTime=n.getInteger("hurt");e.deathTime=n.getInteger("death");
        }
    }
}
