package pocketmod;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;

/** Current boss arenas use the same full-height, 64-block neighborhood as bed safety. */
final class ExileBossArenas {
    private ExileBossArenas() {}
    static String id(EntityLivingBase boss) {
        String custom=boss.getEntityData().getString("pm_exile_boss_id");
        if(!custom.isEmpty()&&custom.length()<=128)return custom;
        ResourceLocation key=net.minecraft.entity.EntityList.getKey(boss);
        return key!=null?key.toString():boss instanceof EntityRiftWalker?"pocketmod:rift_walker":boss.getClass().getName();
    }
    static boolean protects(EntityPlayerMP player) {
        if(player.world==null||PocketDimensionManager.isPocketDimension(player.dimension))return false;
        if(ExileMode.state(player).getBoolean("trial_active"))return true;
        ExileWorldData data=ExileWorldData.get(player.world);
        AxisAlignedBB area=new AxisAlignedBB(player.posX-64,0,player.posZ-64,
                player.posX+64,player.world.getActualHeight(),player.posZ+64);
        return !player.world.getEntitiesWithinAABB(EntityLivingBase.class,area,
                mob->mob.isEntityAlive()&&!mob.isNonBoss()&&!data.bossDefeats.getBoolean(id(mob))).isEmpty();
    }
}
