package pocketmod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Transfers only this creation's mode choice; terrain and generator options remain vanilla. */
@Mod.EventBusSubscriber(modid=PocketMod.MODID)
public final class ExileWorldCreation {
    public static final String RULE="pocketmodExile";
    private static final Map<WorldSettings,Boolean> selections=Collections.synchronizedMap(new WeakHashMap<WorldSettings,Boolean>());
    private ExileWorldCreation() {}
    static void select(WorldSettings settings) {selections.put(settings,true);}
    private static void apply(World world) {
        world.getGameRules().setOrCreateGameRule(RULE,"true");
        ExileWorldData data=ExileWorldData.get(world);
        data.enabled=true;data.markDirty();
    }
    @SubscribeEvent(receiveCanceled=true)
    public static void spawn(WorldEvent.CreateSpawnPosition event) {
        if(event.getWorld().isRemote||event.getWorld().provider.getDimension()!=0)return;
        if(Boolean.TRUE.equals(selections.remove(event.getSettings())))apply(event.getWorld());
    }
    // Some providers skip the spawn event (including the debug terrain). The integrated
    // server retains the exact WorldSettings object passed by the creation screen.
    static void applyRetainedSettings(Object server,World world) {
        if(server==null||selections.isEmpty())return;
        for(Class<?> type=server.getClass();type!=null;type=type.getSuperclass()) {
            for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                if(field.getType()!=WorldSettings.class||java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                try {
                    field.setAccessible(true);
                    if(Boolean.TRUE.equals(selections.remove((WorldSettings)field.get(server)))) {apply(world);return;}
                } catch(IllegalAccessException ex) {throw new IllegalStateException(ex);}
            }
        }
    }
    @SubscribeEvent public static void load(WorldEvent.Load event) {
        World world=event.getWorld();
        if(!world.isRemote&&world.provider.getDimension()==0)applyRetainedSettings(world.getMinecraftServer(),world);
    }
}
