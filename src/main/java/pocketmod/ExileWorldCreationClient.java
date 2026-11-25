package pocketmod;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Independent mode flag survives generator customization and GUI reinitialization. */
@SideOnly(Side.CLIENT)
final class ExileWorldCreationClient {
    private static final Map<GuiCreateWorld,Boolean> choices=new WeakHashMap<>();
    private static Field field(String name,String srg) {return ReflectionHelper.findField(GuiCreateWorld.class,new String[]{name,srg});}
    private static final Field TYPE=field("selectedIndex","field_146331_K");
    private static final Field MODE=field("gameMode","field_146342_r");
    private static final Field HARDCORE=field("hardCoreMode","field_146337_w");
    private static final Field STRUCTURES=field("generateStructuresEnabled","field_146341_s");
    private static final Field BONUS=field("bonusChestEnabled","field_146338_v");
    private static final Field CHEATS_SET=field("allowCheatsWasSetByUser","field_146339_u");
    private static final Field CHEATS=field("allowCheats","field_146340_t");
    private static final Field SEED=field("worldSeedField","field_146335_h");
    private static final Field NAME=field("worldNameField","field_146333_g");
    private static final Field FOLDER=field("saveDirName","field_146336_i");
    private static final Field GENERATED=field("alreadyGenerated","field_146345_x");
    private ExileWorldCreationClient() {}
    static String mode(GuiCreateWorld gui) {
        try {return (String)MODE.get(gui);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static void cycle(GuiCreateWorld gui) {
        try {
            if(selected(gui)){set(gui,false);MODE.set(gui,"survival");HARDCORE.setBoolean(gui,false);}
            else if("creative".equals(mode(gui)))set(gui,true);
            else if("survival".equals(mode(gui))){MODE.set(gui,"hardcore");HARDCORE.setBoolean(gui,true);}
            else {MODE.set(gui,"creative");HARDCORE.setBoolean(gui,false);}
            if(!CHEATS_SET.getBoolean(gui))CHEATS.setBoolean(gui,!selected(gui)&&"creative".equals(mode(gui)));
            refresh(gui);
        }catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static boolean selected(GuiCreateWorld gui) {return Boolean.TRUE.equals(choices.get(gui));}
    static void set(GuiCreateWorld gui,boolean enabled) {
        choices.put(gui,enabled);
        if(enabled)try {MODE.set(gui,"survival");HARDCORE.setBoolean(gui,false);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static void initialize(GuiCreateWorld gui) {
        if(choices.containsKey(gui))return;
        try {
            // Compatibility when recreating a world saved with the former special terrain type.
            if(WorldType.WORLD_TYPES[TYPE.getInt(gui)]==ExileMode.worldType()) {
                TYPE.setInt(gui,WorldType.DEFAULT.getId());set(gui,true);
            }
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static void refresh(GuiCreateWorld gui) {
        for(String name:new String[]{"updateDisplayState","func_146319_h"})try {
            java.lang.reflect.Method method=GuiCreateWorld.class.getDeclaredMethod(name);
            method.setAccessible(true);method.invoke(gui);return;
        } catch(NoSuchMethodException ignored) {
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        throw new IllegalStateException("Cannot refresh world creation controls");
    }
    static long seed(String text,long random) {
        if(text.isEmpty())return random;
        try {long value=Long.parseLong(text);return value==0?random:value;}
        catch(NumberFormatException e){return text.hashCode();}
    }
    static WorldSettings settings(GuiCreateWorld gui,long randomSeed) {
        try {
            WorldType terrain=WorldType.WORLD_TYPES[TYPE.getInt(gui)];
            WorldSettings settings=new WorldSettings(seed(((GuiTextField)SEED.get(gui)).getText(),randomSeed),
                    GameType.getByName((String)MODE.get(gui)),STRUCTURES.getBoolean(gui),selected(gui)||HARDCORE.getBoolean(gui),terrain);
            settings.setGeneratorOptions(gui.chunkProviderSettingsJson);
            if(BONUS.getBoolean(gui)&&!settings.getHardcoreEnabled())settings.enableBonusChest();
            if(CHEATS.getBoolean(gui)&&(selected(gui)||!settings.getHardcoreEnabled()))settings.enableCommands();
            return settings;
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static void create(GuiCreateWorld gui) {
        try {
            if(GENERATED.getBoolean(gui))return;
            GENERATED.setBoolean(gui,true);
            Minecraft.getMinecraft().displayGuiScreen(null);
            // Retain the selected generator's own pre-creation hook, before reading its options.
            WorldType.WORLD_TYPES[TYPE.getInt(gui)].onGUICreateWorldPress();
            WorldSettings settings=settings(gui,new java.util.Random().nextLong());
            ExileWorldCreation.select(settings);
            Minecraft.getMinecraft().launchIntegratedServer((String)FOLDER.get(gui),
                    ((GuiTextField)NAME.get(gui)).getText().trim(),settings);
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
}
