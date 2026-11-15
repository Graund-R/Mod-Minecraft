package pocketmod;

import java.util.Random;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

/** Лутка. Ставка списывается заранее, отказаться от результата нельзя. Целевой RTP ~87 % (см. таблицу). */
public class Loot {

    public enum Out {
        JACKPOT("jackpot", 0.02, 8.0, false),
        BIG("big", 0.06, 3.0, false),
        MEDIUM("medium", 0.15, 1.6, false),
        SMALL("small", 0.16, 0.8, false),
        BUFF("buff", 0.20, 0.5, true),
        ITEM("item", 0.10, 0.4, true),
        DISCOUNT("discount", 0.05, 0.5, true),
        DEBUFF("debuff", 0.26, 0.0, false);

        public final String id;
        public final double p;
        /** Ценность в ставках (для мана-исходов — множитель возврата). */
        public final double value;
        public final boolean special;

        Out(String id, double p, double value, boolean special) {
            this.id = id;
            this.p = p;
            this.value = value;
            this.special = special;
        }
    }

    public static double rtp() {
        double s = 0;
        for (Out o : Out.values()) {
            s += o.p * o.value;
        }
        return s;
    }

    private static final Potion[] GOOD = { MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.HASTE, MobEffects.RESISTANCE,
            MobEffects.REGENERATION, MobEffects.JUMP_BOOST, MobEffects.NIGHT_VISION, MobEffects.ABSORPTION };
    private static final Potion[] BAD = { MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.MINING_FATIGUE,
            MobEffects.NAUSEA, MobEffects.BLINDNESS, MobEffects.HUNGER, MobEffects.POISON, MobEffects.UNLUCK };

    private static final Random RND = new Random();

    public static int pity(net.minecraft.entity.player.EntityPlayer p) {
        return PlayerData.root(p).getInteger("pocketmod_loot_pity");
    }

    public static String last(net.minecraft.entity.player.EntityPlayer p) {
        return PlayerData.root(p).getString("pocketmod_loot_last");
    }

    public static void spin(EntityPlayerMP p, int tier) {
        tier = Math.max(0, Math.min(2, tier));
        double paid = PocketConfig.lootStakes[tier] * Mana.getPriceMultiplier(p);
        if (!Mana.spend(p, paid)) {
            p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(paid)), true);
            return;
        }
        NBTTagCompound d = PlayerData.root(p);
        int pity = d.getInteger("pocketmod_loot_pity");

        Out res;
        if (pity >= PocketConfig.lootPity) {
            res = Out.MEDIUM;
        } else {
            double r = RND.nextDouble();
            res = Out.DEBUFF;
            double acc = 0;
            for (Out o : Out.values()) {
                acc += o.p;
                if (r < acc) {
                    res = o;
                    break;
                }
            }
        }
        d.setInteger("pocketmod_loot_pity", (res == Out.DEBUFF || res == Out.SMALL) ? pity + 1 : 0);

        String arg = "";
        switch (res) {
            case JACKPOT:
            case BIG:
            case MEDIUM: {
                int g = (int) Math.round(paid * res.value);
                Mana.add(p, g);
                arg = String.valueOf(g);
                break;
            }
            case SMALL: {
                int g = (int) Math.round(paid * res.value);
                Mana.add(p, g);
                arg = String.valueOf(g);
                break;
            }
            case BUFF: {
                Potion pot = GOOD[RND.nextInt(GOOD.length)];
                int sec = 90 * (tier + 1);
                p.addPotionEffect(new PotionEffect(pot, sec * 20, tier, false, true));
                arg = sec + "s";
                break;
            }
            case ITEM: {
                ItemStack st;
                switch (RND.nextInt(4)) {
                    case 0: st = new ItemStack(Items.GOLDEN_APPLE, 1 + tier); break;
                    case 1: st = new ItemStack(Items.ENDER_PEARL, 2 + tier); break;
                    case 2: st = new ItemStack(Items.COOKED_BEEF, 8 + 4 * tier); break;
                    default: st = new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + 4 * tier); break;
                }
                arg = st.getDisplayName() + " x" + st.getCount();
                if (!p.addItemStackToInventory(st)) {
                    p.dropItem(st, false);
                }
                break;
            }
            case DISCOUNT: {
                double disc = 0.10 + 0.10 * tier;
                d.setDouble("pocketmod_discount", disc);
                arg = String.valueOf((int) Math.round(disc * 100));
                break;
            }
            default: {
                int n = tier == 2 ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    Potion pot = BAD[RND.nextInt(BAD.length)];
                    int sec = 60 * (tier == 0 ? 1 : tier == 1 ? 2 : 4);
                    p.addPotionEffect(new PotionEffect(pot, sec * 20, tier, false, true));
                    arg += (i > 0 ? "," : "") + I18nLite.potion(pot);
                }
                break;
            }
        }
        d.setString("pocketmod_loot_last", res.id + "|" + arg);
        p.world.playSound(null, p.posX, p.posY, p.posZ,
                res == Out.DEBUFF ? SoundEvents.ENTITY_VILLAGER_NO : SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, res == Out.JACKPOT ? 1.0F : 0.5F, res == Out.JACKPOT ? 1.0F : 1.3F);
    }

    /** Название эффекта без клиентского I18n (сервер). */
    private static class I18nLite {
        static String potion(Potion p) {
            String n = p.getName();
            return n.startsWith("effect.") ? n.substring(7) : n;
        }
    }
}
