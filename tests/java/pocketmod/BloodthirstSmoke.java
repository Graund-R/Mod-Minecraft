package pocketmod;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;
import sun.misc.Unsafe;

/** Headless logic checks: no game client, server, world loading or rendering is started. */
public final class BloodthirstSmoke {
    static Unsafe unsafe;
    static int checks;

    static class TestWorld extends WorldServer {
        MapStorage storage;
        WorldInfo info;

        TestWorld() {
            super(null, null, null, 0, null);
        }

        @Override
        public MapStorage getMapStorage() {
            return storage;
        }

        @Override
        public WorldInfo getWorldInfo() {
            return info;
        }
    }

    static class Player extends EntityPlayerMP {
        NBTTagCompound data;
        AbstractAttributeMap attributes;
        float exhaustion;
        int damageCalls;
        boolean creative, spectator;

        Player() {
            super(null, null, null, null);
        }

        @Override
        public NBTTagCompound getEntityData() {
            return data;
        }

        @Override
        public IAttributeInstance getEntityAttribute(IAttribute type) {
            return attributes.getAttributeInstance(type);
        }

        @Override
        public boolean isCreative() {
            return creative;
        }

        @Override
        public boolean isSpectator() {
            return spectator;
        }

        @Override
        public void addExhaustion(float value) {
            exhaustion += value;
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float damage) {
            check(
                    source.isUnblockable() && source.isDamageAbsolute(),
                    "thirst damage bypasses armor and protection");
            damageCalls++;
            setHealth(getHealth() - damage);
            return true;
        }

        @Override
        public void sendStatusMessage(
                net.minecraft.util.text.ITextComponent text, boolean actionBar) {}
    }

    static void field(Object target, Class<?> type, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static void check(boolean result, String name) {
        checks++;
        if (!result) throw new AssertionError(name);
    }

    static void near(double actual, double expected, String name) {
        check(Math.abs(actual - expected) < 0.00001, name + ": " + actual);
    }

    public static void main(String[] args) throws Exception {
        ExileConfig.cyclicThirst=false;
        ExileConfig.simpleThirst=false; // Exercise the retained original model.
        Field u = Unsafe.class.getDeclaredField("theUnsafe");
        u.setAccessible(true);
        unsafe = (Unsafe) u.get(null);
        Bootstrap.register();
        new WorldType("exile");
        TestWorld w = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        w.storage = new MapStorage(null);
        w.info =
                new WorldInfo(
                        new WorldSettings(
                                1,
                                GameType.SURVIVAL,
                                true,
                                false,
                                WorldType.parseWorldType("exile")),
                        "test");
        field(w, World.class, "provider", new WorldProviderSurface());
        Player p = (Player) unsafe.allocateInstance(Player.class);
        p.world = w;
        p.data = new NBTTagCompound();
        p.capabilities = new PlayerCapabilities();
        p.attributes = new AttributeMap();
        p.attributes.registerAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20);
        net.minecraft.network.datasync.EntityDataManager manager =
                new net.minecraft.network.datasync.EntityDataManager(p);
        field(p, Entity.class, "dataManager", manager);
        Field healthKey = net.minecraft.entity.EntityLivingBase.class.getDeclaredField("HEALTH");
        healthKey.setAccessible(true);
        manager.register(
                (net.minecraft.network.datasync.DataParameter<Float>) healthKey.get(null), 20.0f);
        p.attributes.registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4);
        p.attributes.registerAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.1);
        field(p, Entity.class, "rand", new Random(1));
        near(ExileBloodthirst.fraction(-1, 480), 0, "negative state clamped");
        near(ExileBloodthirst.fraction(960, 480), 1, "legacy overflow displays full");
        near(ExileBloodthirst.symptoms(0.35), 0, "no visual symptoms in early grace period");
        near(ExileBloodthirst.symptoms(1), 1, "maximum symptoms");
        check(
                ExileBloodthirst.threatScore(20, 8, 0) > ExileBloodthirst.threatScore(20, 2, 0),
                "modded attack affects threat");
        check(
                ExileBloodthirst.threatScore(20, 2, 15) > ExileBloodthirst.threatScore(20, 2, 0),
                "modded armor affects threat");
        check(
                ExileBloodthirst.relief(30, 50, 240) < ExileBloodthirst.relief(30, 0, 240),
                "weak enemies lose value with progression");
        check(ExileBloodthirst.relief(100000, 0, 240) == 480, "boss relief has a bounded reserve");
        p.data.setBoolean("pm_shield", true);
        Mana.set(p, 1000);
        net.minecraftforge.event.entity.living.LivingDamageEvent thirstHit =
                new net.minecraftforge.event.entity.living.LivingDamageEvent(
                        p, new DamageSource("pocketmod_bloodthirst").setDamageBypassesArmor().setDamageIsAbsolute(), 1);
        Shield.onDamage(thirstHit);
        check(!thirstHit.isCanceled(), "mana shield cannot cancel thirst damage");
        near(thirstHit.getAmount(), 1, "shield leaves full thirst damage");
        near(Mana.get(p), 1000, "thirst does not spend shield mana");
        Shield.off(p);
        UUID ability = UUID.fromString("2175a3bc-d02c-4d62-9b44-090c420e9761");
        IAttributeInstance attack = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        attack.applyModifier(new AttributeModifier(ability, "test_strength_ability", 3, 0));
        UUID restriction = UUID.fromString("94702537-3663-48d2-a945-2cd37541cc51");
        attack.applyModifier(new AttributeModifier(restriction, "exile_initial_weakness", -0.5, 2));
        NBTTagCompound s = ExileMode.state(p);
        s.setInteger("thirst", ExileConfig.thirstSeconds);
        ExileBloodthirst.refreshModifiers(p);
        near(
                attack.getAttributeValue(),
                4.375,
                "frenzy composes with strength and starting weakness");
        near(
                p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue(),
                0.12,
                "frenzy movement bonus");
        ExileBloodthirst.refreshModifiers(p);
        near(attack.getAttributeValue(), 4.375, "repeated refresh does not stack frenzy");
        ExileBloodthirst.advance(p);
        check(p.damageCalls == 0, "no damage before two full seconds");
        ExileBloodthirst.advance(p);
        check(p.damageCalls == 1 && p.getHealth() == 19, "one half-heart every two seconds");
        ExileBloodthirst.advance(p);
        ExileBloodthirst.advance(p);
        check(p.damageCalls == 2 && p.getHealth() == 18, "damage cadence repeats");
        near(p.exhaustion, 0.4, "extra hunger accumulates at maximum");
        s.setInteger("thirst", 0);
        ExileBloodthirst.refreshModifiers(p);
        near(attack.getAttributeValue(), 3.5, "relief removes only frenzy");
        check(
                attack.getModifier(ability) != null && attack.getModifier(restriction) != null,
                "ability and restriction survive relief");
        check(s.getInteger("thirst_damage_seconds") == 0, "relief resets damage cadence");
        attack.removeModifier(restriction);
        s.setInteger("thirst", ExileConfig.thirstSeconds);
        ExileBloodthirst.refreshModifiers(p);
        near(attack.getAttributeValue(), 8.75, "purchased removal of weakness stays removed");
        s.setBoolean("cured", true);
        ExileBloodthirst.refreshModifiers(p);
        near(attack.getAttributeValue(), 7, "cure removes frenzy");
        s.setBoolean("cured", false);
        p.creative = true;
        ExileBloodthirst.refreshModifiers(p);
        check(attack.getModifier(ExileBloodthirst.STRENGTH) == null, "creative mode has no frenzy");
        p.creative = false;
        p.spectator = true;
        ExileBloodthirst.refreshModifiers(p);
        check(
                attack.getModifier(ExileBloodthirst.STRENGTH) == null,
                "ordinary spectator has no frenzy");
        p.data.setBoolean("pm_spec", true);
        ExileBloodthirst.refreshModifiers(p);
        check(
                attack.getModifier(ExileBloodthirst.STRENGTH) != null,
                "Spector preserves accumulated bloodthirst");
        System.out.println("PASS " + checks + " bloodthirst checks; no Minecraft process launched");
    }
}
