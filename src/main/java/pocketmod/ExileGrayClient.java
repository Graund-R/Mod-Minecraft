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
    private boolean failed,hasMode,fieldDirty=true;
    private int fieldTexture,fieldWidth=1,fieldHeight=1,fieldX,fieldZ,previousTexture;
    private int spatialUniform,fieldUniform,sizeUniform,originUniform,inverseUniform;
    private double renderX,renderZ;
    private final java.nio.FloatBuffer matrixBuffer=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private final org.lwjgl.util.vector.Matrix4f inverseView=new org.lwjgl.util.vector.Matrix4f();
    public static String status="off";
    private static ExileGrayClient instance;
    private static boolean tilePass;
    public static void beginTile(Object object) {
        if(tilePass||instance==null||!(object instanceof net.minecraft.tileentity.TileEntity))return;
        net.minecraft.tileentity.TileEntity tile=(net.minecraft.tileentity.TileEntity)object;
        if(tile.getWorld()==null||tile.getWorld()!=Minecraft.getMinecraft().world)return;
        net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher=net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance;
        instance.pass.origin(dispatcher.staticPlayerX,dispatcher.staticPlayerY,dispatcher.staticPlayerZ);
        tilePass=instance.pass.begin();
        if(tilePass)instance.pass.color(1);
    }
    public static void endTile(){if(tilePass){try{instance.pass.end();}finally{tilePass=false;}}}

    public ExileGrayClient() {
        instance=this;
        if(Minecraft.getMinecraft().getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager)
            ((net.minecraft.client.resources.IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
    }
    static float amount(int percent) {return Math.max(0,Math.min(1,(percent-10)/90f));}
    static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    @Override public void onResourceManagerReload(IResourceManager manager) {
        if(program!=0)GL20.glDeleteProgram(program);program=0;failed=false;fieldDirty=true;
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
            spatialUniform=GL20.glGetUniformLocation(program,"Spatial");
            fieldUniform=GL20.glGetUniformLocation(program,"InfectionField");
            sizeUniform=GL20.glGetUniformLocation(program,"FieldSize");
            originUniform=GL20.glGetUniformLocation(program,"CameraOffset");
            inverseUniform=GL20.glGetUniformLocation(program,"InverseView");
            if(spatialUniform<0||fieldUniform<0||sizeUniform<0||originUniform<0||inverseUniform<0)throw new IllegalStateException("Incomplete spatial shader uniforms");
            if(atlasUniform<0||lightUniform<0||grayUniform<0||blackUniform<0||fogUniform<0)throw new IllegalStateException("Incomplete terrain shader uniforms");
        } finally {
            if(vertex!=0)GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);
        }
    }
    private int style(RenderChunk chunk) {
        int x=chunk.getPosition().getX()>>4,z=chunk.getPosition().getZ()>>4;
        // Draw the soft fringe in clean neighboring chunks too.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(styles.containsKey(key(x+dx,z+dz)))return 1;
        return 0;
    }
    private void uploadField() {
        if(!fieldDirty)return;
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(long k:styles.keySet()) {
            int x=(int)(k>>32),z=(int)k;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }
        fieldX=minX;fieldZ=minZ;fieldWidth=maxX-minX+1;fieldHeight=maxZ-minZ+1;
        java.nio.ByteBuffer bytes=org.lwjgl.BufferUtils.createByteBuffer(fieldWidth*fieldHeight*4);
        for(int z=0;z<fieldHeight;z++)for(int x=0;x<fieldWidth;x++) {
            int value=styles.getOrDefault(key(fieldX+x,fieldZ+z),0);
            bytes.put((byte)Math.round(((value>>8)&255)*2.55f));
            bytes.put((byte)Math.round((value&255)*2.55f));bytes.put((byte)0);bytes.put((byte)255);
        }
        bytes.flip();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,fieldWidth,fieldHeight,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        fieldDirty=false;
    }
    private void bindField() {
        int active=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
        previousTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if(fieldTexture==0)fieldTexture=GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,fieldTexture);uploadField();
        } catch(RuntimeException ex) {GL11.glBindTexture(GL11.GL_TEXTURE_2D,previousTexture);throw ex;}
        finally {net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(active);}
    }
    private final TerrainPass pass=new TerrainPass() {
        @Override public void origin(double x,double y,double z){renderX=x;renderZ=z;}
        @Override public boolean begin() {
            if(styles.isEmpty()||!ExileConfig.visuals)return false;
            if(!GLContext.getCapabilities().OpenGL20){status="unsupported";return false;}
            previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            // Do not replace a shader pack's active terrain program.
            if(previousProgram!=0){status="shaderpack";return false;}
            if(failed){status="failed";return false;}
            try {
                if(program==0)initialize();
                matrixBuffer.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrixBuffer);
                inverseView.load(matrixBuffer);if(inverseView.invert()==null)return false;
                bindField();
                currentFog=GL11.glIsEnabled(GL11.GL_FOG)?GL11.glGetInteger(GL11.GL_FOG_MODE):0;return true;
            }
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
            GL20.glUniform1i(spatialUniform,1);GL20.glUniform1i(fieldUniform,2);
            GL20.glUniform2f(sizeUniform,fieldWidth,fieldHeight);
            GL20.glUniform2f(originUniform,(float)(renderX-fieldX*16.0),(float)(renderZ-fieldZ*16.0));
            matrixBuffer.clear();inverseView.store(matrixBuffer);matrixBuffer.flip();
            GL20.glUniformMatrix4(inverseUniform,false,matrixBuffer);
            GL20.glUniform1i(fogUniform,currentFog);
            status="active";
        }
        @Override public void end(){
            GL20.glUseProgram(previousProgram);
            int active=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,previousTexture);
            net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(active);
        }
    };
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;
        Minecraft mc=Minecraft.getMinecraft();status="off";
        if(mc.world!=world){world=mc.world;snapshot=null;styles.clear();fieldDirty=true;hasMode=false;}
        if(mc.world==null||mc.player==null)return;
        if(ExileProgression.inMode(mc.player))hasMode=true;
        if(!hasMode||PocketDimensionManager.isPocketDimension(mc.player.dimension))return;
        NBTTagCompound next=ExileMode.state(mc.player).getCompoundTag("terrain_snapshot");
        if(next!=snapshot&&validSnapshot(next,mc.player.dimension)) {
            snapshot=next;styles.clear();fieldDirty=true;
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
    interface TerrainPass {default void origin(double x,double y,double z){} boolean begin();void color(int style);void end();}
    /** Keeps original per-layer order (especially transparency), batching consecutive equal styles. */
    static final class ShadedChunks extends ChunkRenderContainer {
        private final ChunkRenderContainer delegate;
        private final java.util.function.ToIntFunction<RenderChunk> styles;
        private final TerrainPass pass;
        ShadedChunks(ChunkRenderContainer delegate,java.util.function.ToIntFunction<RenderChunk> styles,TerrainPass pass) {
            this.delegate=delegate;this.styles=styles;this.pass=pass;
        }
        @Override public void initialize(double x,double y,double z){super.initialize(x,y,z);delegate.initialize(x,y,z);pass.origin(x,y,z);}
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
