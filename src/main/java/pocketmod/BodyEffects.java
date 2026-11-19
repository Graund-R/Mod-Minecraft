package pocketmod;

import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextComponentTranslation;

/** Эффекты тела: сердца, скорость, сила, огнестойкость, дыхание. Навсегда или в аренду (мана/сек). */
public class BodyEffects {

    public static final String[] IDS = { "hearts", "speed", "strength", "fire", "breath", "regen" };
    private static final UUID HEART_ID = UUID.fromString("5b7c1a52-3d2e-4e0a-9d51-7a6f0c1b9e11");

    private static NBTTagCompound d(EntityPlayer p) {
        return PlayerData.root(p);
    }

    public static int perm(EntityPlayer p, int i) {
        return d(p).getInteger("pocketmod_body_" + IDS[i]);
    }

    public static boolean rent(EntityPlayer p, int i) {
        return d(p).getBoolean("pocketmod_body_rent_" + IDS[i]);
    }

    public static int maxPerm(int i) {
        return i == 0 ? PocketConfig.heartsMaxBuys : i == 5 ? PocketConfig.regenMaxBuys : 1;
    }

    public static double rentCost(EntityPlayer p, int i) {
        return PocketConfig.bodyRentPerSec[i] * Mana.getPriceMultiplier(p);
    }

    public static double totalRentDrain(EntityPlayer p) {
        double s = 0;
        for (int i = 0; i < IDS.length; i++) if (rent(p, i)) s += rentCost(p, i);
        return s;
    }

    public static void buyPerm(EntityPlayerMP p, int i) {
        int cur = perm(p, i);
        if (cur >= maxPerm(i)) return;
        double price = Mana.shopPrice(p, PocketConfig.bodyPermCost[i]);
        if (!Mana.spend(p, price)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(price)), true);
            return;
        }
        Mana.consumeDiscount(p);
        d(p).setInteger("pocketmod_body_" + IDS[i], cur + 1);
    }

    public static void toggleRent(EntityPlayerMP p, int i) {
        boolean now = !rent(p, i);
        if (now && Mana.get(p) < rentCost(p, i)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(rentCost(p, i))), true);
            return;
        }
        d(p).setBoolean("pocketmod_body_rent_" + IDS[i], now);
    }

    /** Убирает применённые бонусы тела, пока активен облик Metamorph. */
    public static void suppressForMorph(EntityPlayerMP p) {
        IAttributeInstance attribute = p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        AttributeModifier old = attribute.getModifier(HEART_ID);
        if (old != null) attribute.removeModifier(old);
        for (PotionEffect effect : new java.util.ArrayList<PotionEffect>(p.getActivePotionEffects())) {
            Potion potion = effect.getPotion();
            if ((potion == MobEffects.SPEED || potion == MobEffects.STRENGTH
                    || potion == MobEffects.FIRE_RESISTANCE || potion == MobEffects.WATER_BREATHING
                    || potion == MobEffects.REGENERATION)
                    && effect.getIsAmbient() && !effect.doesShowParticles()) {
                p.removePotionEffect(potion);
            }
        }
    }

    public static void tick(EntityPlayerMP p) {
        if (MorphIntegration.isMorphed(p)) {
            suppressForMorph(p);
            return;
        }
        for (int i = 0; i < IDS.length; i++) {
            if (rent(p, i) && !Mana.spend(p, rentCost(p, i) / 20.0)) {
                d(p).setBoolean("pocketmod_body_rent_" + IDS[i], false);
                p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.rent_off"), true);
            }
        }
        if (p.ticksExisted % 20 != 0) return;
        apply(p, 1, MobEffects.SPEED);
        apply(p, 2, MobEffects.STRENGTH);
        apply(p, 3, MobEffects.FIRE_RESISTANCE);
        apply(p, 4, MobEffects.WATER_BREATHING);
        applyHearts(p);
        applyRegeneration(p);
    }

    private static void apply(EntityPlayerMP p, int i, Potion pot) {
        if (perm(p, i) > 0 || rent(p, i)) {
            p.addPotionEffect(new PotionEffect(pot, 60, 0, true, false));
        }
    }

    private static void applyRegeneration(EntityPlayerMP p) {
        int level = perm(p, 5);
        if (rent(p, 5)) level = Math.max(1, level);
        if (level > 0) p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, Math.min(2, level - 1), true, false));
    }

    private static void applyHearts(EntityPlayerMP p) {
        double amount = 2.0 * perm(p, 0) + (rent(p, 0) ? 2.0 * PocketConfig.heartsRent : 0.0);
        IAttributeInstance a = p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        AttributeModifier old = a.getModifier(HEART_ID);
        if (old != null && old.getAmount() == amount) return;
        if (old != null) a.removeModifier(HEART_ID);
        if (amount > 0) a.applyModifier(new AttributeModifier(HEART_ID, "pocketmod_hearts", amount, 0));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }
}
