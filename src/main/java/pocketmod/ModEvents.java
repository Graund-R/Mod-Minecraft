package pocketmod;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class ModEvents {

    /** Невидимость (замечают только вплотную) и «Тишина» после телепорта. */
    @SubscribeEvent
    public static void onTarget(LivingSetAttackTargetEvent e) {
        if (!(e.getEntityLiving() instanceof EntityLiving)) return;
        EntityLivingBase t = e.getTarget();
        if (!(t instanceof EntityPlayerMP)) return;
        EntityLiving mob = (EntityLiving) e.getEntityLiving();
        EntityPlayerMP p = (EntityPlayerMP) t;
        if (Abilities.isSilent(p)) {
            mob.setAttackTarget(null);
            return;
        }
        if (Abilities.isInvisible(p) && !Abilities.isInvisBroken(p) && mob.getDistance(p) > Abilities.noticeRange(p)) {
            mob.setAttackTarget(null);
        }
    }

    /** Удар игроком сбрасывает невидимость на несколько секунд. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getSource().getTrueSource() instanceof EntityPlayerMP) {
            Abilities.breakInvisibility((EntityPlayerMP) e.getSource().getTrueSource());
        }
    }

    /**
     * Кровосися: после убийства враждебного моба в ближнем бою,
     * если у игрока < 50% HP — восстановить 25% от maxHP моба.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        EntityLivingBase dead = e.getEntityLiving();
        if (dead.world.isRemote) return;
        if (!(dead instanceof IMob)) return;
        DamageSource src = e.getSource();
        if (src == null || !(src.getTrueSource() instanceof EntityPlayerMP)) return;
        EntityPlayerMP killer = (EntityPlayerMP) src.getTrueSource();
        if (!AbilityStorage.isUnlocked(killer, AbilityType.BLOODSUCKER)) return;
        // ближний бой: нет projectile / нет explosion
        if (src.isProjectile() || src.isExplosion()) return;
        if (killer.getHealth() >= killer.getMaxHealth() * 0.5f) return;
        float heal = dead.getMaxHealth() * 0.25f;
        if (heal <= 0f) return;
        killer.heal(heal);
        killer.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                "pocketmod.msg.bloodsucker", (int) Math.ceil(heal / 2f)), true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            Spector.recoverOnLogin((EntityPlayerMP) e.player);
        }
    }
}
