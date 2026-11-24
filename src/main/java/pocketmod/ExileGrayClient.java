package pocketmod;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Terrain-only coloration. Sky, entities, hands and HUD never pass through this shader. */
@SideOnly(Side.CLIENT)
public final class ExileGrayClient implements IResourceManagerReloadListener {
    private static final Field CONTAINER=ReflectionHelper.findField(RenderGlobal.class,new String[]{"renderContainer","field_174996_N"});
    private final Map<Long,Integer> styles=new HashMap<>();
    private NBTTagCompound snapshot;
    private Object world;
    private int program,previousProgram,atlasUniform,lightUniform,grayUniform,blackUniform,fogUniform,currentFog;
    private boolean failed,hasMode;
    public static String status="off";
    public ExileGrayClient() {
        if(Minecraft.getMinecraft().getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager)
            ((net.minecraft.client.resources.IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
    }
    static float amount(int percent) {return Math.max(0,Math.min(1,(percent-10)/90f));}
    static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    @Override public void onResourceManagerReload(IResourceManager manager) {
        if(program!=0)GL20.glDeleteProgram(program);program=0;failed=false;
    }
    private static int compile(IResourceManager resources,int type,String file)throws Exception {
        String source;
        try(InputStream in=resources.getResource(new ResourceLocation("pocketmod","shaders/program/"+file)).getInputStream()) {
            source=new String(com.google.common.io.ByteStreams.toByteArray(in),StandardCharsets.UTF_8);
        }
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE) {
            String log=GL20.glGetShaderInfoLog(shader,4096);GL20.glDeleteShader(shader);throw new IllegalStateException(log);
        }
        return shader;
    }
    private void initialize()throws Exception {
        int vertex=0,fragment=0;
        try {
            IResourceManager resources=Minecraft.getMinecraft().getResourceManager();
            vertex=compile(resources,GL20.GL_VERTEX_SHADER,"exile_gray.vsh");
            fragment=compile(resources,GL20.GL_FRAGMENT_SHADER,"exile_gray.fsh");
            program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)throw new IllegalStateException(GL20.glGetProgramInfoLog(program,4096));
            atlasUniform=GL20.glGetUniformLocation(program,"DiffuseSampler");lightUniform=GL20.glGetUniformLocation(program,"LightmapSampler");
            grayUniform=GL20.glGetUniformLocation(program,"GrayAmount");blackUniform=GL20.glGetUniformLocation(program,"BlackAmount");fogUniform=GL20.glGetUniformLocation(program,"FogMode");
            if(atlasUniform<0||lightUniform<0||grayUniform<0||blackUniform<0||fogUniform<0)throw new IllegalStateException("Incomplete terrain shader uniforms");
        } finally {
            if(vertex!=0)GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);
        }
    }
    private int style(RenderChunk chunk) {
        return styles.getOrDefault(key(chunk.getPosition().getX()>>4,chunk.getPosition().getZ()>>4),0);
    }
    private final TerrainPass pass=new TerrainPass() {
        @Override public boolean begin() {
            if(styles.isEmpty()||!ExileConfig.visuals)return false;
            if(!GLContext.getCapabilities().OpenGL20){status="unsupported";return false;}
            previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            // Do not replace a shader pack's active terrain program.
            if(previousProgram!=0){status="shaderpack";return false;}
            if(failed){status="failed";return false;}
            try {if(program==0)initialize();currentFog=GL11.glIsEnabled(GL11.GL_FOG)?GL11.glGetInteger(GL11.GL_FOG_MODE):0;return true;}
            catch(Exception ex) {
                failed=true;status="failed";
                if(program!=0)GL20.glDeleteProgram(program);program=0;
                System.err.println("[PocketMod] Infection terrain shader failed: "+ex);return false;
            }
        }
        @Override public void color(int style) {
            if(style==0){GL20.glUseProgram(previousProgram);return;}
            GL20.glUseProgram(program);
            GL20.glUniform1i(atlasUniform,0);
            GL20.glUniform1i(lightUniform,1);
            GL20.glUniform1f(grayUniform,((style>>8)&255)/100f);
            GL20.glUniform1f(blackUniform,(style&255)/100f);
            GL20.glUniform1i(fogUniform,currentFog);
            status="active";
        }
        @Override public void end(){GL20.glUseProgram(previousProgram);}
    };
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;
        Minecraft mc=Minecraft.getMinecraft();status="off";
        if(mc.world!=world){world=mc.world;snapshot=null;styles.clear();hasMode=false;}
        if(mc.world==null||mc.player==null)return;
        if(ExileProgression.inMode(mc.player))hasMode=true;
        if(!hasMode||PocketDimensionManager.isPocketDimension(mc.player.dimension))return;
        NBTTagCompound next=ExileMode.state(mc.player).getCompoundTag("terrain_snapshot");
        if(next!=snapshot&&validSnapshot(next,mc.player.dimension)) {
            snapshot=next;styles.clear();
            if(next.getInteger("dimension")==mc.player.dimension) {
                NBTTagList chunks=next.getTagList("chunks",10);
                for(int i=0;i<chunks.tagCount();i++) {
                    NBTTagCompound c=chunks.getCompoundTagAt(i);
                    styles.put(key(c.getInteger("x"),c.getInteger("z")),(Math.max(0,Math.min(100,c.getInteger("gray")))<<8)|Math.max(0,Math.min(100,c.getInteger("black"))));
                }
            }
        }
        try {
            ChunkRenderContainer current=(ChunkRenderContainer)CONTAINER.get(mc.renderGlobal);
            if(!(current instanceof ShadedChunks))CONTAINER.set(mc.renderGlobal,new ShadedChunks(current,this::style,pass));
        } catch(IllegalAccessException ex){status="failed";System.err.println("[PocketMod] Cannot attach infection terrain renderer: "+ex);}
    }
    static boolean validSnapshot(NBTTagCompound snapshot,int dimension) {
        return snapshot.hasKey("chunks",9)&&snapshot.hasKey("dimension",3)&&snapshot.getInteger("dimension")==dimension;
    }
    interface TerrainPass {boolean begin();void color(int style);void end();}
    /** Keeps original per-layer order (especially transparency), batching consecutive equal styles. */
    static final class ShadedChunks extends ChunkRenderContainer {
        private final ChunkRenderContainer delegate;
        private final java.util.function.ToIntFunction<RenderChunk> styles;
        private final TerrainPass pass;
        ShadedChunks(ChunkRenderContainer delegate,java.util.function.ToIntFunction<RenderChunk> styles,TerrainPass pass) {
            this.delegate=delegate;this.styles=styles;this.pass=pass;
        }
        @Override public void initialize(double x,double y,double z){super.initialize(x,y,z);delegate.initialize(x,y,z);}
        @Override public void renderChunkLayer(BlockRenderLayer layer) {
            if(!initialized)return;
            boolean custom=pass.begin();int last=-1;boolean queued=false;
            try {
                for(RenderChunk chunk:renderChunks) {
                    int style=custom?styles.applyAsInt(chunk):0;
                    if(custom&&style!=last) {
                        if(queued){delegate.renderChunkLayer(layer);queued=false;}
                        pass.color(style);last=style;
                    }
                    delegate.addRenderChunk(chunk,layer);queued=true;
                }
                if(queued)delegate.renderChunkLayer(layer);
            } finally {
                if(custom)pass.end();renderChunks.clear();
            }
        }
    }
}
