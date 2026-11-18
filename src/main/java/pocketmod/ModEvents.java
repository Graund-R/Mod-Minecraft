package pocketmod;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
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

    private static boolean isHostile(EntityLivingBase e) {
        if (e instanceof IMob) return true;
        if (e.isCreatureType(EnumCreatureType.MONSTER, false)) return true;
        if (e instanceof EntityCreature) {
            EntityLivingBase t = ((EntityCreature) e).getAttackTarget();
            if (t instanceof EntityPlayerMP) return true;
        }
        return false;
    }

    /**
     * Кровосися: после убийства враждебного моба в ближнем бою,
     * если у игрока < 50% HP — восстановить 25% от maxHP моба.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        EntityLivingBase dead = e.getEntityLiving();
        if (dead.world.isRemote) return;
        if (!isHostile(dead)) return;
        DamageSource src = e.getSource();
        if (src == null) return;
        EntityPlayerMP killer = null;
        if (src.getTrueSource() instanceof EntityPlayerMP) {
            killer = (EntityPlayerMP) src.getTrueSource();
        } else if (src.getImmediateSource() instanceof EntityPlayerMP) {
            killer = (EntityPlayerMP) src.getImmediateSource();
        }
        if (killer == null) return;
        if (!AbilityStorage.isUnlocked(killer, AbilityType.BLOODSUCKER)) return;
        // ближний бой: нет projectile / нет explosion
        if (src.isProjectile() || src.isExplosion()) return;
        int bl = AbilityStorage.getLevel(killer, AbilityType.BLOODSUCKER);
        // L1: <50% HP, 25% maxHP моба; L2: <60%, 35%; L3: <75%, 45%
        float hpGate = bl >= 3 ? 0.75f : (bl >= 2 ? 0.60f : 0.50f);
        float healPct = bl >= 3 ? 0.45f : (bl >= 2 ? 0.35f : 0.25f);
        if (killer.getHealth() >= killer.getMaxHealth() * hpGate) return;
        float heal = dead.getMaxHealth() * healPct;
        if (heal <= 0f) return;
        killer.heal(heal);
        killer.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                "pocketmod.msg.bloodsucker", (int) Math.ceil(heal / 2f)), true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            Spector.recoverOnLogin((EntityPlayerMP) e.player);
            Totems.promptIfPending((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            Totems.promptIfPending((EntityPlayerMP) e.player);
        }
    }
}
