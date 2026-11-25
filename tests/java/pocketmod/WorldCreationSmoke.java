package pocketmod;

import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import net.minecraftforge.event.world.WorldEvent;

/** Settings and saved-data checks only: no window, world generation or server startup. */
public final class WorldCreationSmoke {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void field(Object target,String name,Object value)throws Exception{
        Field f=GuiCreateWorld.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static int type(GuiCreateWorld gui)throws Exception{
        Field f=GuiCreateWorld.class.getDeclaredField("selectedIndex");f.setAccessible(true);return f.getInt(gui);
    }
    static class ServerSettings {private WorldSettings settings;ServerSettings(WorldSettings s){settings=s;}}
    static class Text extends GuiTextField {
        String text;
        Text(){super(0,null,0,0,0,0);}
        @Override public String getText(){return text;}
    }
    static GuiCreateWorld gui(WorldType terrain)throws Exception{
        GuiCreateWorld gui=(GuiCreateWorld)TrialPlacementSmoke.unsafe.allocateInstance(GuiCreateWorld.class);
        Text text=(Text)TrialPlacementSmoke.unsafe.allocateInstance(Text.class);text.text="123456";
        field(gui,"worldSeedField",text);field(gui,"selectedIndex",terrain.getId());
        field(gui,"gameMode","creative");field(gui,"hardCoreMode",false);
        field(gui,"generateStructuresEnabled",false);field(gui,"allowCheats",true);field(gui,"bonusChestEnabled",true);
        gui.chunkProviderSettingsJson="{\"mod_setting\":\"keep exactly\"}";
        return gui;
    }
    static BloodthirstSmoke.TestWorld world(WorldSettings settings)throws Exception{
        BloodthirstSmoke.TestWorld w=(BloodthirstSmoke.TestWorld)TrialPlacementSmoke.unsafe.allocateInstance(BloodthirstSmoke.TestWorld.class);
        w.storage=new MapStorage(null);w.info=new WorldInfo(settings,"fixture");
        BloodthirstSmoke.field(w,World.class,"worldInfo",w.info);
        BloodthirstSmoke.field(w,World.class,"provider",new WorldProviderSurface());return w;
    }
    public static void main(String[] args)throws Exception{
        TrialPlacementSmoke.main(args);
        Field legacyField=ExileMode.class.getDeclaredField("worldType");legacyField.setAccessible(true);
        legacyField.set(null,WorldType.parseWorldType("exile"));
        GuiCreateWorld legacyGui=gui(ExileMode.worldType());
        ExileWorldCreationClient.initialize(legacyGui);
        check(ExileWorldCreationClient.selected(legacyGui)&&type(legacyGui)==WorldType.DEFAULT.getId(),"legacy terrain selection converts to independent mode");
        check(ExileGrayClient.amount(20)>0&&ExileGrayClient.amount(20)<ExileGrayClient.amount(50)
            &&ExileGrayClient.amount(50)<ExileGrayClient.amount(80)&&ExileGrayClient.amount(80)<ExileGrayClient.amount(100),"grayscale already grows through all infection stages");
        Field locale=net.minecraft.client.resources.I18n.class.getDeclaredField("i18nLocale");locale.setAccessible(true);
        locale.set(null,new net.minecraft.client.resources.Locale());
        GuiCreateWorld cycle=gui(WorldType.DEFAULT);
        for(String name:new String[]{"btnGameMode","btnMapFeatures","btnBonusItems","btnMapType","btnAllowCommands"})
            field(cycle,name,new net.minecraft.client.gui.GuiButton(0,0,0,""));
        field(cycle,"gameMode","survival");
        ExileWorldCreationClient.cycle(cycle);check("hardcore".equals(ExileWorldCreationClient.mode(cycle))&&!ExileWorldCreationClient.selected(cycle),"mode button cycles to vanilla hardcore");
        ExileWorldCreationClient.cycle(cycle);check("creative".equals(ExileWorldCreationClient.mode(cycle)),"mode button cycles to creative");
        ExileWorldCreationClient.cycle(cycle);check(ExileWorldCreationClient.selected(cycle),"fourth mode is exile");
        ExileWorldCreationClient.cycle(cycle);check("survival".equals(ExileWorldCreationClient.mode(cycle))&&!ExileWorldCreationClient.selected(cycle),"mode button returns to ordinary survival");
        WorldType modded=new WorldType("fixture_modded");
        WorldSettings chosen=null;
        for(WorldType terrain:new WorldType[]{WorldType.DEFAULT,WorldType.FLAT,WorldType.CUSTOMIZED,modded}){
            GuiCreateWorld gui=gui(terrain);
            ExileWorldCreationClient.set(gui,true);
            check(type(gui)==terrain.getId(),"enabling exile keeps generator "+terrain.getName());
            ExileWorldCreationClient.initialize(gui);
            check(ExileWorldCreationClient.selected(gui),"GUI reinitialization keeps mode");
            field(gui,"selectedIndex",modded.getId());
            check(ExileWorldCreationClient.selected(gui),"changing generator keeps exile");
            field(gui,"selectedIndex",terrain.getId());
            WorldSettings settings=ExileWorldCreationClient.settings(gui,42);
            check(settings.getTerrainType()==terrain,"selected generator passed intact");
            check(settings.getGeneratorOptions().equals(gui.chunkProviderSettingsJson),"raw generator settings unchanged");
            check(settings.getGameType()==GameType.SURVIVAL&&settings.getHardcoreEnabled(),"exile creates a hardcore survival world");
            check(!settings.isMapFeaturesEnabled()&&settings.areCommandsAllowed()&&!settings.isBonusChestEnabled(),"structures, cheats and bonus settings retained");
            check(settings.getSeed()==123456,"seed retained");
            ExileWorldCreationClient.set(gui,false);
            check(type(gui)==terrain.getId(),"disabling mode keeps generator");
            check(!ExileWorldCreationClient.selected(gui),"mode can be disabled");
            if(terrain==modded)chosen=settings;
        }
        check(!ExileWorldCreationClient.selected(gui(WorldType.DEFAULT)),"new GUI never inherits another screen's mode");
        check(ExileWorldCreationClient.seed("",42)==42&&ExileWorldCreationClient.seed("0",42)==42,"vanilla random seed semantics");
        check(ExileWorldCreationClient.seed("abc",42)=="abc".hashCode(),"vanilla text seed semantics");
        BloodthirstSmoke.TestWorld w=world(chosen);
        ExileWorldData original=ExileWorldData.get(w);
        check(!original.enabled,"ordinary generator alone does not enable exile");
        ExileWorldCreation.select(chosen);
        WorldSettings identical=new WorldSettings(chosen.getSeed(),chosen.getGameType(),false,false,chosen.getTerrainType());
        BloodthirstSmoke.TestWorld unrelated=world(identical);
        ExileWorldCreation.spawn(new WorldEvent.CreateSpawnPosition(unrelated,identical));
        check(!ExileWorldData.get(unrelated).enabled,"same seed and generator in another creation do not inherit mode");
        WorldEvent.CreateSpawnPosition event=new WorldEvent.CreateSpawnPosition(w,chosen) {
            @Override public boolean isCancelable(){return true;}
        };event.setCanceled(true);
        ExileWorldCreation.spawn(event);
        check(original.enabled&&w.getGameRules().getBoolean(ExileWorldCreation.RULE),"mode initialized even with a modded spawn event cancelled");
        check(w.getWorldInfo().getTerrainType()==modded&&w.getWorldInfo().getGeneratorOptions().equals(chosen.getGeneratorOptions()),"activation never replaces terrain/options");
        ExileWorldData restored=new ExileWorldData(ExileWorldData.ID);
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));
        check(restored.enabled,"mode survives saved-data round trip");
        w.storage=new MapStorage(null);
        check(ExileWorldData.get(w).enabled,"saved world rule reconstructs independent mode flag");
        BloodthirstSmoke.TestWorld repeated=world(chosen);
        ExileWorldCreation.spawn(new WorldEvent.CreateSpawnPosition(repeated,chosen));
        check(!ExileWorldData.get(repeated).enabled,"creation transfer is consumed exactly once");
        WorldSettings legacy=new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.parseWorldType("exile"));
        check(ExileWorldData.get(world(legacy)).enabled,"legacy exile terrain worlds still enabled");
        WorldSettings debug=new WorldSettings(2,GameType.SPECTATOR,false,false,WorldType.DEBUG_ALL_BLOCK_STATES);
        BloodthirstSmoke.TestWorld debugWorld=world(debug);
        ExileWorldCreation.select(debug);
        ExileWorldCreation.applyRetainedSettings(new ServerSettings(identical),debugWorld);
        check(!ExileWorldData.get(debugWorld).enabled,"fallback ignores a different creation's settings");
        ExileWorldCreation.applyRetainedSettings(new ServerSettings(debug),debugWorld);
        check(ExileWorldData.get(debugWorld).enabled,"providers skipping spawn event still receive independent mode");
        check(debugWorld.getWorldInfo().getTerrainType()==WorldType.DEBUG_ALL_BLOCK_STATES,"fallback preserves terrain");
        System.out.println("WorldCreationSmoke: "+checks+" checks passed; no game launched");
    }
}
