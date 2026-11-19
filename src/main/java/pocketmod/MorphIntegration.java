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

/** Необязательная интеграция с Metamorph 1.12.2 через reflection. */
public class MorphIntegration {
    public static final double MORPH_COST = 20.0D;
    private static final ThreadLocal<Boolean> AUTHORIZED = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() { return Boolean.FALSE; }
    };

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new MorphIntegration());
    }

    public static boolean isAvailable() {
        return Loader.isModLoaded("metamorph");
    }

    private static Object capability(EntityPlayer player) {
        if (player == null || !isAvailable()) return null;
        try {
            Class<?> type = Class.forName("mchorse.metamorph.capabilities.morphing.Morphing");
            return type.getMethod("get", EntityPlayer.class).invoke(null, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getMorphs(EntityPlayer player) {
        Object cap = capability(player);
        if (cap == null) return new ArrayList<Object>();
        try {
            Object value = cap.getClass().getMethod("getAcquiredMorphs").invoke(cap);
            if (value instanceof List) return new ArrayList<Object>((List<Object>) value);
        } catch (Throwable ignored) {
        }
        return new ArrayList<Object>();
    }

    public static Object getCurrentMorph(EntityPlayer player) {
        Object cap = capability(player);
        if (cap == null) return null;
        try {
            return cap.getClass().getMethod("getCurrentMorph").invoke(cap);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isMorphed(EntityPlayer player) {
        return getCurrentMorph(player) != null;
    }

    public static String getMorphName(Object morph) {
        if (morph == null) return "Unknown";
        try {
            Object value = morph.getClass().getMethod("getDisplayName").invoke(morph);
            if (value != null && !value.toString().isEmpty()) return value.toString();
        } catch (Throwable ignored) {
        }
        try {
            Field display = morph.getClass().getField("displayName");
            Object value = display.get(morph);
            if (value != null && !value.toString().isEmpty()) return value.toString();
        } catch (Throwable ignored) {
        }
        try {
            Field name = morph.getClass().getField("name");
            Object value = name.get(morph);
            if (value != null) return value.toString();
        } catch (Throwable ignored) {
        }
        return "Unknown";
    }

    private static Object invokeMorphApi(String methodName, Object... args) throws Exception {
        Class<?> api = Class.forName("mchorse.metamorph.api.MorphAPI");
        for (Method method : api.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterTypes().length != args.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null) {
                    if (types[i].isPrimitive()) matches = false;
                } else if (!types[i].isInstance(args[i])
                        && !(types[i] == boolean.class && args[i] instanceof Boolean)) {
                    matches = false;
                }
            }
            if (matches) return method.invoke(null, args);
        }
        throw new NoSuchMethodException("Metamorph API method: " + methodName);
    }

    public static boolean transform(EntityPlayerMP player, int index) {
        if (player == null) return false;
        if (!AbilityStorage.isUnlocked(player, AbilityType.MORPHISM)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
            return false;
        }
        if (!isAvailable()) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_need_metamorph"), true);
            return false;
        }

        List<Object> morphs = getMorphs(player);
        if (index < 0 || index >= morphs.size()) return false;
        Object morph = morphs.get(index);
        Object current = getCurrentMorph(player);

        if (current != null && current.equals(morph)) {
            demorph(player);
            return true;
        }

        double cost = MORPH_COST * Mana.getPriceMultiplier(player);
        if (!Mana.spend(player, cost)) {
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                    (int) Math.ceil(cost)), true);
            return false;
        }

        boolean success = false;
        AUTHORIZED.set(Boolean.TRUE);
        try {
            Object result = invokeMorphApi("morph", player, morph, Boolean.FALSE);
            success = Boolean.TRUE.equals(result);
        } catch (Throwable error) {
            System.err.println("[PocketMod] Metamorph transformation failed: " + error);
        } finally {
            AUTHORIZED.set(Boolean.FALSE);
        }

        if (!success) {
            Mana.add(player, cost);
            player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_failed"), true);
            return false;
        }
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.morph_on",
                getMorphName(morph), (int) Math.ceil(cost)), true);
        return true;
    }

    public static void demorph(EntityPlayerMP player) {
        if (player == null || !isAvailable() || getCurrentMorph(player) == null) return;
        try {
            AUTHORIZED.set(Boolean.TRUE);
            invokeMorphApi("demorph", player);
        } catch (Throwable error) {
            System.err.println("[PocketMod] Metamorph demorph failed: " + error);
        } finally {
            AUTHORIZED.set(Boolean.FALSE);
        }
    }

    @SubscribeEvent
    public void onMetamorphEvent(Event event) {
        String name = event.getClass().getName();
        if ("mchorse.metamorph.api.events.SpawnGhostEvent$Pre".equals(name)) {
            try {
                EntityPlayer player = (EntityPlayer) event.getClass().getField("player").get(event);
                Object morph = event.getClass().getField("morph").get(event);
                if (player != null && !player.world.isRemote && morph != null) {
                    Object acquired = invokeMorphApi("acquire", player, morph);
                    if (Boolean.TRUE.equals(acquired)) event.setCanceled(true);
                }
            } catch (Throwable error) {
                System.err.println("[PocketMod] Metamorph acquire hook failed: " + error);
            }
            return;
        }
        if ("mchorse.metamorph.api.events.MorphEvent$Pre".equals(name) && !AUTHORIZED.get()) {
            try {
                Object morph = event.getClass().getField("morph").get(event);
                if (morph != null) event.setCanceled(true);
            } catch (Throwable ignored) {
            }
        }
    }
}
