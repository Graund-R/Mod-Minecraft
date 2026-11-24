package pocketmod;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.config.Configuration;

/** Regression checks without starting a Minecraft client or server. */
public final class ExileToolsSmoke {
    static int checks;
    static void check(boolean ok,String name) { checks++; if(!ok)throw new AssertionError(name); }
    static void translations(String code,Map<String,String> map) throws Exception {
        java.util.Properties expected=new java.util.Properties();
        try(java.io.Reader in=new java.io.InputStreamReader(ExileToolsSmoke.class.getResourceAsStream(
                "/assets/pocketmod/lang/"+code+".lang"),java.nio.charset.StandardCharsets.UTF_8)) {
            // .lang is not a Java properties file: preserve Unicode and literal backslashes.
            java.io.BufferedReader reader=new java.io.BufferedReader(in);
            String line;
            while((line=reader.readLine())!=null) if(!line.startsWith("#")&&line.contains("=")) {
                String[] parts=line.split("=",2);
                check(parts[1].equals(map.get(parts[0])),"fallback coverage "+parts[0]);
            }
        }
        for(int i=1;i<=5;i++)check(!map.get("pocketmod.exile.verdict_"+i).startsWith("pocketmod."),"readable verdict");
    }
    public static void main(String[] args)throws Exception {
        NBTTagCompound inventory = new NBTTagCompound();
        check(ExileProgression.purchasedInventorySlots(inventory)==0,"new inventory has no purchased slots");
        inventory.setInteger("inventory_level",1);
        check(ExileProgression.purchasedInventorySlots(inventory)==9,"legacy row purchase preserves nine slots");
        inventory.setInteger("inventory_level",2);
        check(ExileProgression.purchasedInventorySlots(inventory)==18,"legacy two rows preserve eighteen slots");
        inventory.setInteger("inventory_slots_bought",19);
        check(ExileProgression.purchasedInventorySlots(inventory)==19,"single slot purchase overrides legacy rows");
        inventory.setInteger("inventory_slots_bought",1);
        check(ExileProgression.purchasedInventorySlots(inventory)==1,"one purchase grants one slot");
        inventory.setInteger("inventory_slots_bought",-1);
        check(ExileProgression.purchasedInventorySlots(inventory)==0,"negative purchases clamped");
        translations("ru_ru",Lang.RU); translations("en_us",Lang.EN);
        check(ExileConfig.fullSeconds==600,"ten minute default");
        check(ExileMode.stage(119)==0&&ExileMode.stage(120)==1,"first threat at two minutes");
        check(ExileMode.stage(300)==2&&ExileMode.stage(480)==3&&ExileMode.stage(600)==4,"new stages");
        check(ExileEncounters.cap(1)==6&&ExileEncounters.cap(4)==16,"reduced type population limits");
        boolean[] kinds=new boolean[3];
        for(int i=0;i<12;i++) {
            check(ExileEncounters.type(1,i)==1,"archers are the first pursuers");
            check(ExileEncounters.type(2,i)<2,"no early wither skeletons");
            kinds[ExileEncounters.type(4,i)]=true;
        }
        check(kinds[0]&&kinds[1]&&kinds[2],"mixed late waves");
        File temp=Files.createTempDirectory("pocket-config-check").toFile();
        File file=new File(temp,"pocketmod.cfg");
        java.lang.reflect.Field home=net.minecraftforge.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true); home.set(null,temp);
        Configuration cfg=new Configuration(file);
        cfg.get(ConfigMigrations.META,"configVersion",4).set(4);
        cfg.get("изгнанник","fullSeconds",1200).set(1200);
        cfg.get("изгнанник","firstSeconds",300).set(300);
        cfg.get("изгнанник","fleeSeconds",600).set(600);
        cfg.get("изгнанник","deathSeconds",900).set(900);
        cfg.get("изгнанник","witherSeconds",1020).set(1020);
        cfg.get("изгнанник","spawnInterval",25).set(25);
        cfg.save(); ConfigMigrations.run(cfg);
        check(cfg.get("изгнанник","fullSeconds",1200).getInt()==600,"old full-time migrated");
        check(cfg.get("изгнанник","firstSeconds",300).getInt()==120,"old first-time migrated");
        check(cfg.get("изгнанник","spawnInterval",25).getInt()==8,"old reinforcements migrated");
        check(new File(temp,"pocketmod.cfg.bak-v4").isFile(),"configuration backup");
        Configuration custom=new Configuration(new File(temp,"custom.cfg"));
        custom.get(ConfigMigrations.META,"configVersion",4).set(4);
        custom.get("изгнанник","fullSeconds",1800).set(1800);
        custom.get("изгнанник","spawnInterval",18).set(18);
        ConfigMigrations.run(custom);
        check(custom.get("изгнанник","fullSeconds",0).getInt()==1800,"custom times preserved");
        check(custom.get("изгнанник","spawnInterval",0).getInt()==18,"custom frequency preserved");
        ExileWorldData data=new ExileWorldData("test");
        data.setExposure(0,-2,4,600);
        check(data.exposure(0,-2,4)==600,"set test stage");
        data.setExposure(0,-2,4,0);
        check(data.exposure(0,-2,4)==0,"debug clears accumulated peak too");
        data.setExposure(0,-2,4,9999);
        check(data.exposure(0,-2,4)==600,"debug clamp");
        data.setExposure(0,-2,4,-1);
        check(data.exposure(0,-2,4)==0,"debug negative clamp");
        check(ExileDebug.stageExposure(4)==600,"debug stage matches current configuration");
        Configuration toolOptions=new Configuration(new File(temp,"tools.cfg"));
        toolOptions.get("изгнанник","respawnStage1",15).set(23);
        toolOptions.get("изгнанник","respawnStage2",10).set(17);
        toolOptions.get("изгнанник","respawnLate",5).set(7);
        toolOptions.get("изгнанник","debugHud",true).set(false);
        toolOptions.get("изгнанник","debugBorders",true).set(false);
        toolOptions.get("изгнанник","debugAreaRadius",1).set(3);
        ExileConfig.load(toolOptions);
        check(ExileEncounters.respawnSeconds(1)==23&&ExileEncounters.respawnSeconds(2)==17
                &&ExileEncounters.respawnSeconds(3)==7&&ExileEncounters.respawnSeconds(4)==7,"custom respawn settings reach the actual scheduler");
        check(!ExileConfig.debugHud&&!ExileConfig.debugBorders&&ExileConfig.debugAreaRadius==3,"test tool preferences load without a world");
        toolOptions.get("изгнанник","respawnStage1",15).set(31);
        ExileConfig.load(toolOptions);
        check(ExileEncounters.respawnSeconds(1)==31,"saved changes apply on reload without restarting");
        toolOptions.get("изгнанник","debugAreaRadius",1).set(500);
        ExileConfig.load(toolOptions);
        check(ExileConfig.debugAreaRadius==4,"edit radius is bounded even for manually edited config");
        for(net.minecraftforge.common.config.Property property:toolOptions.getCategory("изгнанник").getOrderedValues())
            check(ConfigLang.row("изгнанник",property.getName())!=null,"readable configuration label "+property.getName());
        System.out.println("ExileToolsSmoke: "+checks+" checks passed; no game launched");
    }
}
