package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Щит из маны: урон превращается в расход маны. Не хватает маны — щит выключается, умереть от щита нельзя. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public class Shield {

    private static final String KEY = "pm_shield";

    public static boolean isOn(EntityPlayer p) {
        return p.getEntityData().getBoolean(KEY);
    }

    public static void toggle(EntityPlayerMP p) {
        if (MorphIntegration.isMorphed(p)) return;
        if (!AbilityStorage.isUnlocked(p, AbilityType.SHIELD)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return;
        }
        boolean now = !isOn(p);
        p.getEntityData().setBoolean(KEY, now);
        p.sendStatusMessage(new TextComponentTranslation(now ? "pocketmod.msg.shield_on" : "pocketmod.msg.shield_off"), true);
        WorldServer ws = (WorldServer) p.world;
        ws.playSound(null, p.posX, p.posY, p.posZ,
                now ? SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_ON : SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_OFF,
                SoundCategory.PLAYERS, 0.5F, 1.4F);
    }

    public static void off(EntityPlayerMP p) {
        p.getEntityData().setBoolean(KEY, false);
    }

    public static void tick(EntityPlayerMP p) {
        if (!isOn(p) || p.ticksExisted % 4 != 0) return;
        WorldServer ws = (WorldServer) p.world;
        double a = p.ticksExisted * 0.15;
        for (int i = 0; i < 3; i++) {
            double ang = a + i * 2.094;
            double h = 0.3 + 1.4 * ((p.ticksExisted / 4 + i * 5) % 10) / 10.0;
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, p.posX + Math.cos(ang) * 1.0, p.posY + h,
                    p.posZ + Math.sin(ang) * 1.0, 0, 0.3, 0.7, 1.0, 1.0);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (MorphIntegration.isMorphed(p)) {
            off(p);
            return;
        }
        if (!isOn(p)) return;
        DamageSource s = e.getSource();
        if (s.canHarmInCreative()) return;
        float dmg = e.getAmount();
        if (dmg <= 0f) return;
        double per = PocketConfig.shieldManaPerDamage;
        if (AbilityStorage.getLevel(p, AbilityType.SHIELD) >= 2) per *= PocketConfig.shieldL2Factor;
        double cost = dmg * per * Mana.getPriceMultiplier(p);
        WorldServer ws = (WorldServer) p.world;
        if (Mana.spend(p, cost)) {
            e.setCanceled(true);
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, p.posX, p.posY + 1.0, p.posZ, 25, 0.5, 0.7, 0.5, 0.3);
            ws.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.PLAYERS, 0.8F, 0.6F);
        } else {
            off(p);
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.shield_broke"), true);
        }
    }
}
