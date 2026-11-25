package pocketmod;

import java.util.UUID;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Server-owned bloodthirst. Dedicated modifiers never remove another ability's effects. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileBloodthirst {
    static final UUID SPEED = UUID.fromString("b3d944a7-c6b2-46a2-9d32-cab3a57af941");
    static final UUID STRENGTH = UUID.fromString("b3d944a7-c6b2-46a2-9d32-cab3a57af942");
    private static final DamageSource BLOODTHIRST =
            new DamageSource("pocketmod_bloodthirst")
                    .setDamageBypassesArmor()
                    .setDamageIsAbsolute();

    private ExileBloodthirst() {}

    public static double fraction(int thirst, int limit) {
        return Math.max(0, Math.min(1, thirst / (double) Math.max(1, limit)));
    }

    public static double symptoms(double fraction) {
        return Math.max(0, Math.min(1, (fraction - 0.35) / 0.65));
    }

    static boolean eligible(EntityPlayerMP p) {
        return ExileMode.active(p)
                && !p.isDead
                && p.getHealth() > 0
                && !p.isCreative()
                && (!p.isSpectator() || Spector.isActive(p));
    }

    public static void refreshModifiers(EntityPlayerMP p) {
        ExileSimpleThirst.ensureModel(p);
        boolean frenzy =
                eligible(p) && !ExileMode.state(p).getBoolean("cycle_arena_paused")
                        && ExileMode.state(p).getInteger("thirst") >= ExileConfig.thirstSeconds;
        modifier(
                p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED),
                SPEED,
                "exile_bloodthirst_speed",
                frenzy ? ExileConfig.frenzySpeed : 0);
        modifier(
                p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE),
                STRENGTH,
                "exile_bloodthirst_strength",
                frenzy ? ExileConfig.frenzyStrength : 0);
        if (!frenzy) ExileMode.state(p).setInteger("thirst_damage_seconds", 0);
    }

    static void modifier(IAttributeInstance attribute, UUID id, String name, double amount) {
        if (attribute == null) return;
        AttributeModifier old = attribute.getModifier(id);
        if (old != null && old.getAmount() == amount) return;
        if (old != null) attribute.removeModifier(old);
        if (amount != 0)
            attribute.applyModifier(new AttributeModifier(id, name, amount, 2).setSaved(false));
    }

    public static void advance(EntityPlayerMP p) {
        NBTTagCompound s = ExileMode.state(p);
        ExileSimpleThirst.ensureModel(p);
        if (ExileSimpleThirst.paused(p)||p.isPlayerSleeping()) return;
        if(ExileCyclicThirst.active(p)&&ExileCyclicThirst.paused(p))return;
        int previous = s.getInteger("thirst");
        int thirst;
        if(ExileCyclicThirst.active(p)) {
            ExileCyclicThirst.advance(p);thirst=s.getInteger("thirst");
        } else if (ExileSimpleThirst.isSimple(p)) {
            ExileSimpleThirst.advance(p);
            thirst=s.getInteger("thirst");
        } else {
            thirst=advanceLegacy(p,s,previous);
            s.setInteger("thirst",thirst);
        }
        if (previous < ExileConfig.thirstSeconds / 2 && thirst >= ExileConfig.thirstSeconds / 2)
            ExileMode.message(p, "thirst_warning");
        if (previous < ExileConfig.thirstSeconds && thirst >= ExileConfig.thirstSeconds)
            ExileMode.message(p, "thirst_severe");
        double symptoms = symptoms(fraction(thirst, ExileConfig.thirstSeconds));
        // Direct exhaustion composes with Hunger and regeneration without sharing potion IDs.
        if (symptoms > 0) {
            if (Spector.isActive(p)) {
                // Spectator mode normally ignores exhaustion. Preserve the body's metabolic cost.
                p.getFoodStats().addExhaustion((float) (ExileConfig.thirstExhaustion * symptoms));
            } else p.addExhaustion((float) (ExileConfig.thirstExhaustion * symptoms));
        }
        refreshModifiers(p);
        int grace=Math.max(0,s.getInteger("cycle_kill_grace"));
        if(grace>0)s.setInteger("cycle_kill_grace",grace-1);
        if (thirst < ExileConfig.thirstSeconds || grace>0) return;
        int seconds = s.getInteger("thirst_damage_seconds") + 1;
        s.setInteger("thirst_damage_seconds", seconds % 2);
        if (seconds >= 2) {
            // Return to the body before damage: Spector must not grant immunity to bloodthirst.
            Spector.forceReturnIfActive(p);
            p.attackEntityFrom(BLOODTHIRST, 1.0f);
        }
    }

    private static int advanceLegacy(EntityPlayerMP p, NBTTagCompound s, int previous) {
        double power = 0;
        for (AbilityType type : AbilityType.values()) power += AbilityStorage.getLevel(p, type);
        for (int i = 0; i < BodyEffects.IDS.length; i++) power += BodyEffects.perm(p, i);
        power = Math.max(s.getDouble("power"), power + s.getDouble("progress") / 5);
        s.setDouble("power", power);
        int thirst =
                Math.min(
                        ExileConfig.thirstSeconds, previous + 1 + (int) (Math.min(40, power) / 10));
        return thirst;
    }

    static double threatScore(double health, double attack, double armor) {
        return Math.max(1, health)
                * (1 + Math.max(0, armor) / 25.0)
                * (1 + Math.min(40, Math.max(0, attack)) / 8.0);
    }

    private static double score(EntityLivingBase mob) {
        IAttributeInstance attack = mob.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        return threatScore(
                mob.getMaxHealth(),
                attack == null ? 0 : attack.getAttributeValue(),
                mob.getTotalArmorValue());
    }

    static int relief(double enemyScore, double playerPower, int base) {
        return (int)
                Math.max(
                        1,
                        Math.min(
                                base * 2.0,
                                base * enemyScore / Math.max(30, 30 + playerPower * 2)));
    }

    private static boolean threat(EntityLivingBase mob, EntityPlayerMP p) {
        if (mob instanceof EntityPlayer
                || mob instanceof EntityDecoy
                || mob.getDistanceSq(p) > 4096) return false;
        String aggression = "pm_threat_" + p.getUniqueID();
        NBTTagCompound n = mob.getEntityData();
        boolean attacked =
                n.hasKey(aggression)
                        && mob.world.getTotalWorldTime() - n.getLong(aggression) <= 600;
        boolean targeting =
                mob instanceof EntityLiving && ((EntityLiving) mob).getAttackTarget() == p;
        boolean hostile =
                mob instanceof IMob || targeting || attacked || mob.getRevengeTarget() == p;
        if (!hostile) return false;
        if (attacked) return true;
        if (!mob.canEntityBeSeen(p)) return false;
        if (mob instanceof IRangedAttackMob) return true;
        if (!(mob instanceof EntityLiving)) return !mob.isNonBoss();
        net.minecraft.pathfinding.Path path =
                ((EntityLiving) mob).getNavigator().getPathToEntityLiving(p);
        return path != null
                && path.getFinalPathPoint() != null
                && path.getFinalPathPoint()
                                .distanceTo(
                                        new net.minecraft.pathfinding.PathPoint(
                                                net.minecraft.util.math.MathHelper.floor(p.posX),
                                                net.minecraft.util.math.MathHelper.floor(p.posY),
                                                net.minecraft.util.math.MathHelper.floor(p.posZ)))
                        <= 2;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void aggression(LivingAttackEvent e) {
        // An attempted attack counts even when the player blocks or has brief invulnerability.
        if (!(e.getEntityLiving() instanceof EntityPlayerMP)
                || !(e.getSource().getTrueSource() instanceof EntityLivingBase)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (!eligible(p)) return;
        EntityLivingBase mob = (EntityLivingBase) e.getSource().getTrueSource();
        mob.getEntityData().setLong("pm_threat_" + p.getUniqueID(), mob.world.getTotalWorldTime());
        double raw=e.getAmount();
        if(Double.isFinite(raw)&&raw>0)mob.getEntityData().setDouble("pm_attack_potential",
                Math.max(mob.getEntityData().getDouble("pm_attack_potential"),raw));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent e) {
        if (e.isCanceled()
                || e.getAmount() <= 0
                || !(e.getSource().getTrueSource() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getSource().getTrueSource();
        EntityLivingBase mob = e.getEntityLiving();
        if (!eligible(p) || (!threat(mob, p) && !(ExileCyclicThirst.active(p)&&!mob.isNonBoss()))) return;
        String key = "pm_fight_" + p.getUniqueID();
        NBTTagCompound n = mob.getEntityData();
        n.setFloat(key, n.getFloat(key) + Math.min(e.getAmount(), mob.getHealth()));
        n.setLong(key + "_time", mob.world.getTotalWorldTime());
    }

    static double danger(EntityLivingBase mob) {
        IAttributeInstance attack=mob.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        double potential=attack==null?0:attack.getAttributeValue();
        if(mob instanceof IRangedAttackMob)potential=Math.max(4,potential);
        if(mob instanceof net.minecraft.entity.monster.EntityCreeper)
            potential=Math.max(potential,((net.minecraft.entity.monster.EntityCreeper)mob).getPowered()?24:12);
        if(mob instanceof net.minecraft.entity.monster.EntityGhast)potential=Math.max(6,potential);
        potential=Math.max(potential,mob.getEntityData().getDouble("pm_attack_potential"));
        return !mob.isNonBoss()?4:ExileCyclicThirst.weight(mob.getMaxHealth(),potential,mob.getTotalArmorValue());
    }

    static boolean rewardCyclic(EntityPlayerMP player,EntityLivingBase mob) {
        ExileSimpleThirst.ensureModel(player);
        NBTTagCompound state=ExileMode.state(player);
        boolean first=!mob.isNonBoss()&&ExileCyclicThirst.victory(state,ExileBossArenas.id(mob));
        if(!first)ExileCyclicThirst.kill(state,danger(mob));
        if(!mob.isNonBoss()) {
            ExileWorldData world=ExileWorldData.get(mob.world);world.bossDefeats.setBoolean(ExileBossArenas.id(mob),true);world.markDirty();
            state.setBoolean("cycle_arena_paused",ExileBossArenas.protects(player));
        }
        return first;
    }

    static boolean cyclicCredit(EntityPlayerMP player,EntityLivingBase mob,boolean direct) {
        NBTTagCompound tag=mob.getEntityData();
        String key="pm_fight_"+player.getUniqueID();
        boolean recent=mob.world.getTotalWorldTime()-tag.getLong(key+"_time")<=600;
        boolean contribution=recent&&tag.getFloat(key)>=Math.min(10,mob.getMaxHealth()*.3);
        return contribution||(direct&&(!mob.isNonBoss()||threat(mob,player)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent e) {
        EntityLivingBase mob = e.getEntityLiving();
        if (e.isCanceled()
                || mob.world.isRemote
                || mob instanceof EntityPlayer || mob instanceof EntityDecoy
                || !ExileMode.enabled(mob.world)) return;
        // The simple model credits the player's own mob kill, with no threat/gear scoring.
        if (!(mob instanceof EntityDecoy) && e.getSource().getTrueSource() instanceof EntityPlayerMP) {
            EntityPlayerMP killer=(EntityPlayerMP)e.getSource().getTrueSource();
            if (eligible(killer) && ExileSimpleThirst.isSimple(killer)) ExileSimpleThirst.kill(killer);
        }
        for (EntityPlayer player : mob.world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)
                    || !eligible((EntityPlayerMP) player)
                    || player.getDistanceSq(mob) > 4096) continue;
            EntityPlayerMP p = (EntityPlayerMP) player;
            String key = "pm_fight_" + player.getUniqueID();
            NBTTagCompound n = mob.getEntityData();
            boolean cyclic=ExileCyclicThirst.active(p);
            boolean direct=e.getSource().getTrueSource()==p;
            if(cyclic?!cyclicCredit(p,mob,direct):n.getFloat(key)<Math.min(10,mob.getMaxHealth()*.3)
                    ||mob.world.getTotalWorldTime()-n.getLong(key+"_time")>600)continue;
            String credited="pm_thirst_credited_"+p.getUniqueID();
            if(n.getBoolean(credited))continue;
            n.setBoolean(credited,true);
            NBTTagCompound s = ExileMode.state(p);
            if(cyclic) {
                boolean first=rewardCyclic(p,mob);
                if(s.getDouble("cycle_pending_relief")>0)
                    p.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("pocketmod.cycle.banked",String.format(java.util.Locale.ROOT,"%.1f",s.getDouble("cycle_pending_relief"))),true);
                else p.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(first?"pocketmod.cycle.boss_reset":"pocketmod.cycle.relief"),true);
            } else if (!ExileSimpleThirst.isSimple(p)) {
            s.setInteger(
                    "thirst",
                    Math.max(
                            0,
                            s.getInteger("thirst")
                                    - relief(
                                            score(mob),
                                            s.getDouble("power"),
                                            ExileConfig.combatRelief)));
            }
            refreshModifiers(p);
            if (!mob.isNonBoss()) {
                if(!cyclic) {
                    ExileWorldData world=ExileWorldData.get(mob.world);world.bossDefeats.setBoolean(ExileBossArenas.id(mob),true);world.markDirty();
                    ExileCyclicThirst.recordBoss(s,ExileBossArenas.id(mob));
                }
                ExileMode.progress(p, "boss_" + ExileBossArenas.id(mob), 10);
            }
            else if (s.getDouble("progress") < 10) ExileMode.progress(p, null, 0.1);
            if (mob instanceof EntityLiving) {
                if (!(mob instanceof EntityRiftWalker) && !n.hasKey("pm_exile_trial")
                        && p.getRNG().nextFloat() < ExileConfig.discoveryChance)
                    ExileProgression.discover(p, (EntityLiving) mob);
            }
            if (!cyclic&&!ExileSimpleThirst.isSimple(p)) ExileMode.message(p, "combat_relief");
            NetworkHandler.sync(p);
        }
    }
}
