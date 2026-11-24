package pocketmod;

import java.io.File;
import java.io.InputStream;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.client.resources.data.PackMetadataSectionSerializer;
import net.minecraft.util.ResourceLocation;

/** Opens only the archive resource loader, without creating Minecraft or a window. */
public final class ResourcePackSmoke {
    public static void main(String[] args)throws Exception {
        FileResourcePack pack=new FileResourcePack(new File(args[0]));
        if(!pack.getResourceDomains().contains("pocketmod"))throw new AssertionError("resource domain missing");
        MetadataSerializer metadata=new MetadataSerializer();
        metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
        PackMetadataSection section=pack.getPackMetadata(metadata,"pack");
        if(section==null||section.getPackFormat()!=3)throw new AssertionError("wrong resource pack format");
        for(String code:new String[]{"ru_ru","en_us"}) {
            try(InputStream in=pack.getInputStream(new ResourceLocation("pocketmod","lang/"+code+".lang"))) {
                String text=new String(com.google.common.io.ByteStreams.toByteArray(in),java.nio.charset.StandardCharsets.UTF_8);
                if(!text.contains("pocketmod.exile.verdict_1=")||!text.contains("key.pocketmod.exile_debug="))throw new AssertionError("missing translations");
            }
        }
        if(!pack.resourceExists(new ResourceLocation("pocketmod","shaders/post/exile_gray.json")))throw new AssertionError("missing grayscale resource");
        for(String ext:new String[]{"vsh","fsh"})
            if(!pack.resourceExists(new ResourceLocation("pocketmod","shaders/program/exile_gray."+ext)))throw new AssertionError("missing grayscale GLSL "+ext);
        com.google.gson.JsonObject sounds;
        try(InputStream in=pack.getInputStream(new ResourceLocation("pocketmod","sounds.json"))) {
            sounds=new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for(String sound:new String[]{"infection_wind","infection_whisper","infection_surge"}) {
            if(!sounds.has(sound)||!sounds.getAsJsonObject(sound).has("subtitle"))throw new AssertionError("sound/subtitle missing: "+sound);
            try(InputStream in=pack.getInputStream(new ResourceLocation("pocketmod","sounds/"+sound+".ogg"))) {
                byte[] magic=new byte[4];if(in.read(magic)!=4||!"OggS".equals(new String(magic,"US-ASCII")))throw new AssertionError("invalid Ogg "+sound);
            }
        }
        pack.close();
        System.out.println("ResourcePackSmoke: 13 archive resource checks passed; no game launched");
    }
}
