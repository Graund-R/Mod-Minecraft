package pocketmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Реестр непрерывных способностей: единый цикл тика, единая сумма расхода и единый список флагов для синхронизации.
 * Порядок регистрации = порядок вызова каждый тик.
 *
 * «Особые» способности (невидимость, замедление, мягкое падение, аренда эффектов тела) платят ману сами внутри tick,
 * поэтому chargedByRegistry() у них false: реестр лишь вызывает их tick и учитывает их расход в сумме.
 */
public final class ActiveAbilities {

    private static final List<ActiveAbility> ALL = new ArrayList<ActiveAbility>();

    static {
        register(new LegacyInvisibility());
        register(new LegacySlow());
        register(new TimelineAbility());
        register(new SpectorAbility());
        register(new ThermalAbility());
        register(new ShieldAbility());
        register(new FlightAbility());
        register(new LegacySoftFall());
        register(new LegacyBody());
        register(new OreScannerAbility());
        register(new DashAbility());
    }

    private ActiveAbilities() {}

    public static void register(ActiveAbility a) {
        ALL.add(a);
    }

    public static List<ActiveAbility> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Общий цикл: оплата, выключение при нуле маны, вызов tick каждой способности. */
    public static void tick(EntityPlayerMP p) {
        for (ActiveAbility a : ALL) {
            if (a.chargedByRegistry() && a.isActive(p)) {
                double d = a.drainPerSec(p);
                if (d > 0.0 && !Mana.spendAbility(p, d / 20.0)) {
                    a.onManaExhausted(p);
                    continue;
                }
            }
            a.tick(p);
        }
    }

    /** Суммарный расход маны в секунду по всем активным способностям (для магазина). */
    public static double totalDrainPerSec(EntityPlayer p) {
        double sum = 0.0;
        for (ActiveAbility a : ALL) {
            if (a.isActive(p)) sum += a.drainPerSec(p);
        }
        return sum;
    }

    /** Все флаги состояния, которые уходят клиенту (собираются из зарегистрированных способностей). */
    public static List<String> syncedFlags() {
        List<String> keys = new ArrayList<String>();
        for (ActiveAbility a : ALL) {
            for (String k : a.syncedFlags()) {
                if (!keys.contains(k)) keys.add(k);
            }
        }
        return keys;
    }

    // ----------------------------------------------------------------- адаптеры существующих способностей

    private abstract static class Base implements ActiveAbility {
        private final String id;
        private final String[] flags;

        Base(String id, String... flags) {
            this.id = id;
            this.flags = flags;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String[] syncedFlags() {
            return flags;
        }
    }

    // --- платят через реестр

    private static final class SpectorAbility extends Base {
        SpectorAbility() { super("spector", "pm_spec"); }
        @Override public boolean isActive(EntityPlayer p) { return Spector.isPaying(p); }
        @Override public double drainPerSec(EntityPlayer p) { return Spector.drainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { Spector.tick(p); }
        @Override public void onManaExhausted(EntityPlayerMP p) { Spector.onManaExhausted(p); }
    }

    private static final class ThermalAbility extends Base {
        ThermalAbility() { super("thermal", "pm_thermal"); }
        @Override public boolean isActive(EntityPlayer p) { return Thermal.isOn(p); }
        @Override public double drainPerSec(EntityPlayer p) { return Thermal.drainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { Thermal.tick(p); }
        @Override public void onManaExhausted(EntityPlayerMP p) { Thermal.onManaExhausted(p); }
    }

    private static final class ShieldAbility extends Base {
        ShieldAbility() { super("shield", "pm_shield"); }
        @Override public boolean isActive(EntityPlayer p) { return Shield.isOn(p); }
        @Override public double drainPerSec(EntityPlayer p) { return Shield.upkeepPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { Shield.tick(p); }
        @Override public void onManaExhausted(EntityPlayerMP p) { Shield.onManaExhausted(p); }
    }

    private static final class FlightAbility extends Base {
        FlightAbility() { super("flight", "pm_flight"); }
        @Override public boolean isActive(EntityPlayer p) { return Flight.paying(p); }
        @Override public double drainPerSec(EntityPlayer p) { return Flight.drainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { Flight.tick(p); }
        @Override public void onManaExhausted(EntityPlayerMP p) { Flight.onManaExhausted(p); }
    }

    private static final class OreScannerAbility extends Base {
        OreScannerAbility() { super("ore_scanner", "pm_ore_scan"); }
        @Override public boolean isActive(EntityPlayer p) { return OreScanner.isOn(p) && OreScanner.available(p); }
        @Override public double drainPerSec(EntityPlayer p) { return OreScanner.drainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { OreScanner.tick(p); }
        @Override public void onManaExhausted(EntityPlayerMP p) { OreScanner.onManaExhausted(p); }
    }

    /** Рывок: ману берёт при запуске (Dash.request), реестр только вызывает tick (удары, отражение, окончание). */
    private static final class DashAbility extends Base {
        DashAbility() { super("dash"); }
        @Override public boolean isActive(EntityPlayer p) { return false; }
        @Override public double drainPerSec(EntityPlayer p) { return 0.0; }
        @Override public void tick(EntityPlayerMP p) { Dash.tick(p); }
        @Override public boolean chargedByRegistry() { return false; }
    }

    // --- платят сами (особая логика), реестр только вызывает tick и учитывает расход

    private static final class LegacyInvisibility extends Base {
        LegacyInvisibility() { super("invisibility", "pocketmod_invis"); }
        @Override public boolean isActive(EntityPlayer p) { return Abilities.isInvisible(p); }
        @Override public double drainPerSec(EntityPlayer p) { return Abilities.invisDrainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { Abilities.tickInvisibility(p); }
        @Override public boolean chargedByRegistry() { return false; }
    }

    private static final class LegacySlow extends Base {
        LegacySlow() { super("slow", "pocketmod_slow"); }
        @Override public boolean isActive(EntityPlayer p) { return Abilities.isSlow(p); }
        @Override public double drainPerSec(EntityPlayer p) { return TimeSlow.drainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { Abilities.tickSlow(p); }
        @Override public boolean chargedByRegistry() { return false; }
    }

    private static final class TimelineAbility extends Base {
        TimelineAbility(){super("time_branches");}
        @Override public boolean isActive(EntityPlayer p){return TemporalSystem.drainPerSec(p)>0;}
        @Override public double drainPerSec(EntityPlayer p){return TemporalSystem.drainPerSec(p);}
        @Override public void tick(EntityPlayerMP p){}
        @Override public boolean chargedByRegistry(){return false;}
    }

    private static final class LegacySoftFall extends Base {
        LegacySoftFall() { super("softfall", "pm_softfall"); }
        @Override public boolean isActive(EntityPlayer p) { return SoftFall.isActive(p); }
        @Override public double drainPerSec(EntityPlayer p) { return SoftFall.drainPerSec(p); }
        @Override public void tick(EntityPlayerMP p) { SoftFall.tick(p); }
        @Override public boolean chargedByRegistry() { return false; }
    }

    private static final class LegacyBody extends Base {
        LegacyBody() { super("body"); }
        @Override public boolean isActive(EntityPlayer p) { return BodyEffects.totalRentDrain(p) > 0.0; }
        @Override public double drainPerSec(EntityPlayer p) { return BodyEffects.totalRentDrain(p); }
        @Override public void tick(EntityPlayerMP p) { BodyEffects.tick(p); }
        @Override public boolean chargedByRegistry() { return false; }
    }
}
