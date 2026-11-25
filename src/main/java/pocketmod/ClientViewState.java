package pocketmod;

import java.io.*;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/** Only presentation preferences: no gameplay/debug values are restored from this file. */
final class ClientViewState {
    private static final Properties values=new Properties();
    private static final File file=new File(Minecraft.getMinecraft().mcDataDir,"config/pocketmod-ui.properties");
    static {
        if(file.isFile())try(InputStream in=new FileInputStream(file)){values.load(in);}
        catch(IOException e){System.err.println("[PocketMod] Cannot read window preferences: "+e);}
    }
    static int number(String key,int fallback){try{return Integer.parseInt(values.getProperty(key));}catch(Exception e){return fallback;}}
    static String text(String key,String fallback){return values.getProperty(key,fallback);}
    static void save(String prefix,Object... entries){
        for(int i=0;i<entries.length;i+=2)values.setProperty(prefix+entries[i],String.valueOf(entries[i+1]));
        file.getParentFile().mkdirs();
        try(OutputStream out=new FileOutputStream(file)){values.store(out,"Pocket Mod window preferences");}
        catch(IOException e){System.err.println("[PocketMod] Cannot save window preferences: "+e);}
    }
}
