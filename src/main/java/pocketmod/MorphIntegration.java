package pocketmod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Optional bridge to Metamorph 1.12.2.
 *
 * Metamorph remains an optional dependency, so its classes must not appear in
 * Pocket Mod's method signatures or be resolved while Metamorph is absent.
 * Calls below use the exact public API signatures verified against Metamorph
 * 1.4 for Minecraft 1.12.2, rather than scanning methods by name.
 */
public class MorphIntegration {
    public static double morphCost() { return Math.max(0, PocketConfig.morphCost); }

    public static boolean isEnabled() { return PocketConfig.morphEnabled; }

    private static final String API_CLASS = "mchorse.metamorph.api.MorphAPI";
    private static final String MORPH_CLASS = "mchorse.metamorph.api.morphs.AbstractMorph";
    private static final String CAPABILITY_CLASS = "mchorse.metamorph.capabilities.morphing.Morphing";
    private static final String CAPABILITY_INTERFACE = "mchorse.metamorph.capabilities.morphing.IMorphing";

    private static final ThreadLocal<Boolean> AUTHORIZED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private static volatile Api api;

    private static final class Api {
        final Class<?> morphType;
        final Method getCapability;
        final Method getAcquiredMorphs;
        final Method getCurrentMorph;
        final Method morph;
        final Method demorph;
        final Method acquire;

        Api() throws Exception {
            Class<?> capabilityType = Class.forName(CAPABILITY_CLASS);
            Class<?> capabilityInterface = Class.forName(CAPABILITY_INTERFACE);
            Class<?> apiType = Class.forName(API_CLASS);
            this.morphType = Class.forName(MORPH_CLASS);

            this.getCapability = capabilityType.getMethod("get", EntityPlayer.class);
            this.getAcquiredMorphs = capabilityInterface.getMethod("getAcquiredMorphs");
            this.getCurrentMorph = capabilityInterface.getMethod("getCurrentMorph");
            this.morph = apiType.getMethod("morph", EntityPlayer.class, morphType, boolean.class);
            this.demorph = apiType.getMethod("demorph", EntityPlayer.class);
            this.acquire = apiType.getMethod("acquire", EntityPlayer.class, morphType);
        }
    }

    private static Api api() {
        if (!Loader.isModLoaded("metamorph")) {
            return null;
        }

        Api value = api;
        if (value != null) {
            return value;
        }

        synchronized (MorphIntegration.class) {
            if (api == null) {
                try {
                    api = new Api();
                } catch (Throwable error) {
                    System.err.println("[PocketMod] Metamorph API is unavailable or incompatible: " + error);
                    return null;
                }
            }
            return api;
        }
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new MorphIntegration());
    }

    public static boolean isAvailable() {
        return api() != null;
    }

    private static Object capability(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        Api bridge = api();
        if (bridge == null) {
            return null;
        }

        try {
            return bridge.getCapability.invoke(null, player);
        } catch (Throwable error) {
            System.err.println("[PocketMod] Could not read Metamorph capability: " + error);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getMorphs(EntityPlayer player) {
        Object capability = capability(player);
        if (capability == null) {
            return new ArrayList<Object>();
        }

        try {
            Object value = api().getAcquiredMorphs.invoke(capability);
            if (value instanceof List) {
                return new ArrayList<Object>((List<Object>) value);
            }
        } catch (Throwable error) {
            System.err.println("[PocketMod] Could not read acquired morphs: " + error);
        }

        return new ArrayList<Object>();
    }

    public static Object getCurrentMorph(EntityPlayer player) {
        Object capability = capability(player);
        if (capability == null) {
            return null;
        }

        try {
            return api().getCurrentMorph.invoke(capability);
        } catch (Throwable error) {
            System.err.println("[PocketMod] Could not read current morph: " + error);
            return null;
        }
    }

    public static boolean isMorphed(EntityPlayer player) {
        return getCurrentMorph(player) != null;
    }

    public static String getMorphName(Object morph) {
        if (morph == null) {
            return "Unknown";
        }

        // displayName and name are public fields in AbstractMorph 1.4.
        // getDisplayName is client-only in Metamorph, so it is only a fallback.
        try {
            Field display = morph.getClass().getField("displayName");
            Object value = display.get(morph);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        } catch (Throwable ignored) {
        }

        try {
            Field name = morph.getClass().getField("name");
            Object value = name.get(morph);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        } catch (Throwable ignored) {
        }

        try {
            Method displayName = morph.getClass().getMethod("getDisplayName");
            Object value = displayName.invoke(morph);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        } catch (Throwable ignored) {
        }

        return "Unknown";
    }

    public static boolean transform(EntityPlayerMP player, int index) {
        if (player == null) {
            return false;
        }

        if (!isEnabled()) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_disabled"), true);
            return false;
        }

        if (!AbilityStorage.isUnlocked(player, AbilityType.MORPHISM)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return false;
        }

        Api bridge = api();
        if (bridge == null) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_need_metamorph"), true);
            return false;
        }

        List<Object> morphs = getMorphs(player);
        if (index < 0 || index >= morphs.size()) {
            return false;
        }

        Object morph = morphs.get(index);
        Object current = getCurrentMorph(player);

        if (current != null && current.equals(morph)) {
            demorph(player);
            return true;
        }

        double cost = morphCost() * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, cost)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                    (int) Math.ceil(cost)), true);
            return false;
        }

        boolean success = false;
        AUTHORIZED.set(Boolean.TRUE);
        try {
            Object result = bridge.morph.invoke(null, player, morph, Boolean.FALSE);
            success = Boolean.TRUE.equals(result);
        } catch (Throwable error) {
            System.err.println("[PocketMod] Metamorph transformation failed: " + error);
        } finally {
            AUTHORIZED.remove();
        }

        if (!success) {
            Mana.add(player, cost);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_failed"), true);
            return false;
        }

        suppressAbilities(player);
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_on",
                getMorphName(morph), (int) Math.ceil(cost)), true);
        return true;
    }

    public static void demorph(EntityPlayerMP player) {
        if (player == null || getCurrentMorph(player) == null) {
            return;
        }

        Api bridge = api();
        if (bridge == null) {
            return;
        }

        try {
            AUTHORIZED.set(Boolean.TRUE);
            bridge.demorph.invoke(null, player);
        } catch (Throwable error) {
            System.err.println("[PocketMod] Metamorph demorph failed: " + error);
        } finally {
            AUTHORIZED.remove();
        }
    }

    @SubscribeEvent
    public void onMetamorphEvent(Event event) {
        String eventName = event.getClass().getName();

        // Block the normal kill -> ghost path while the Morphism ability is locked.
        // Once unlocked, Metamorph keeps its native ghost/acquisition behavior.
        if ("mchorse.metamorph.api.events.SpawnGhostEvent$Pre".equals(eventName)) {
            cancelAcquisitionWhenLocked(event);
            return;
        }

        // The direct-acquire option skips ghost creation, but MorphAPI.acquire()
        // always posts AcquireMorphEvent.Pre. This second gate covers that path.
        if ("mchorse.metamorph.api.events.AcquireMorphEvent$Pre".equals(eventName)) {
            cancelAcquisitionWhenLocked(event);
            return;
        }

        // Suppress Pocket Mod's active and passive effects after every successful morph.
        if ("mchorse.metamorph.api.events.MorphEvent$Post".equals(eventName)) {
            try {
                EntityPlayer player = (EntityPlayer) event.getClass().getField("player").get(event);
                Object morph = event.getClass().getField("morph").get(event);
                if (player instanceof EntityPlayerMP && morph != null && !player.world.isRemote) {
                    suppressAbilities((EntityPlayerMP) player);
                }
            } catch (Throwable error) {
                System.err.println("[PocketMod] Could not suppress abilities after morph: " + error);
            }
            return;
        }

        // Prevent Metamorph's native morph key from bypassing Pocket Mod's one-time mana charge.
        if ("mchorse.metamorph.api.events.MorphEvent$Pre".equals(eventName) && !AUTHORIZED.get()) {
            try {
                Object morph = event.getClass().getField("morph").get(event);
                if (morph != null) event.setCanceled(true);
            } catch (Throwable error) {
                System.err.println("[PocketMod] Could not validate Metamorph morph event: " + error);
            }
        }
    }

    public static void suppressAbilities(EntityPlayerMP player) {
        if (player == null || !isMorphed(player)) return;
        Abilities.disableForMorph(player);
        Spector.forceReturnIfActive(player);
        Flight.stop(player);
        Thermal.disable(player);
        Shield.off(player);
        SoftFall.disable(player);
    }

    private static void cancelAcquisitionWhenLocked(Event event) {
        try {
            EntityPlayer player = (EntityPlayer) event.getClass().getField("player").get(event);
            if (player == null || player.world.isRemote) return;

            if (!isEnabled() || !AbilityStorage.isUnlocked(player, AbilityType.MORPHISM)) {
                event.setCanceled(true);
            }
        } catch (Throwable error) {
            System.err.println("[PocketMod] Could not validate Metamorph acquisition event: " + error);
        }
    }
}
