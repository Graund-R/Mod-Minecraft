package pocketmod;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Shared world-only refraction: one scene copy composes infection haze and endpoint folds. */
@SideOnly(Side.CLIENT)
final class ExileConvectionClient {
    private static int program,texture,depthTexture,width,height,depthWidth,depthHeight,amountUniform,timeUniform,countUniform,shapeUniform,dataUniform;
    private static boolean failed;
    private static final String VERTEX = "#version 120\nvarying vec2 uv; void main(){gl_Position=gl_Vertex;uv=gl_MultiTexCoord0.xy;}";
    private static final String FRAGMENT = "#version 120\nuniform sampler2D Scene; uniform sampler2D SceneDepth; uniform float Amount; uniform float Time; uniform int LensCount; uniform vec4 LensShape[4]; uniform vec4 LensData[4]; varying vec2 uv;\n"
        + "void main(){vec2 offset=vec2(0.0);float edge=sin(3.14159265*uv.y)*sin(3.14159265*uv.x);"
        + "offset.x+=Amount*0.0035*edge*(sin(uv.y*43.0-Time*1.7)+0.45*sin(uv.y*91.0+Time*2.1));"
        + "offset.y+=Amount*0.0013*edge*sin(uv.x*31.0+uv.y*19.0-Time*1.2);"
        + "for(int i=0;i<4;i++){if(i<LensCount){vec4 s=LensShape[i];vec4 d=LensData[i];vec2 q=(uv-s.xy)/max(s.zw,vec2(0.001));"
        + "float fall=max(0.0,1.0-dot(q,q));float visible=(d.y<0.0||texture2D(SceneDepth,uv).r+0.0006>=d.y)?1.0:0.0;"
        + "offset+=(uv-s.xy)*d.x*fall*fall*visible;}}"
        + "vec2 p=uv+clamp(offset,vec2(-0.025),vec2(0.025));gl_FragColor=texture2D(Scene,clamp(p,vec2(0.0),vec2(1.0)));}";
    private static int shader(int type,String source){
        int id=GL20.glCreateShader(type);GL20.glShaderSource(id,source);GL20.glCompileShader(id);
        if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(id,4096);GL20.glDeleteShader(id);throw new IllegalStateException(log);}return id;
    }
    private static void initialize(){
        int vertex=shader(GL20.GL_VERTEX_SHADER,VERTEX),fragment=0;
        try{
            fragment=shader(GL20.GL_FRAGMENT_SHADER,FRAGMENT);program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program,4096));
            amountUniform=GL20.glGetUniformLocation(program,"Amount");timeUniform=GL20.glGetUniformLocation(program,"Time");
            countUniform=GL20.glGetUniformLocation(program,"LensCount");shapeUniform=GL20.glGetUniformLocation(program,"LensShape[0]");dataUniform=GL20.glGetUniformLocation(program,"LensData[0]");
            texture=GL11.glGenTextures();depthTexture=GL11.glGenTextures();
        }finally{GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);}
    }
    static void reset(){if(program!=0)GL20.glDeleteProgram(program);if(texture!=0)GL11.glDeleteTextures(texture);if(depthTexture!=0)GL11.glDeleteTextures(depthTexture);program=texture=depthTexture=width=height=depthWidth=depthHeight=0;failed=false;}
    private static void parameters(){
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
    }
    static void render(float strength,float time,float[] lenses){
        Minecraft mc=Minecraft.getMinecraft();if(failed||!GLContext.getCapabilities().OpenGL20)return;
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(previous!=0)return;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            if(program==0)initialize();
            int count=Math.min(4,lenses.length/8);boolean resized=width!=mc.displayWidth||height!=mc.displayHeight;
            width=mc.displayWidth;height=mc.displayHeight;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);parameters();
            if(resized)GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,0,0,width,height,0);
            else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);
            if(count>0){
                OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthTexture);parameters();
                if(depthWidth!=width||depthHeight!=height){GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL14.GL_DEPTH_COMPONENT24,0,0,width,height,0);depthWidth=width;depthHeight=height;}
                else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            }
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Scene"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"SceneDepth"),2);
            GL20.glUniform1f(amountUniform,strength);GL20.glUniform1f(timeUniform,time);GL20.glUniform1i(countUniform,count);
            if(count>0){FloatBuffer shapes=BufferUtils.createFloatBuffer(count*4),data=BufferUtils.createFloatBuffer(count*4);for(int i=0;i<count;i++){shapes.put(lenses,i*8,4);data.put(lenses,i*8+4,4);}shapes.flip();data.flip();GL20.glUniform4(shapeUniform,shapes);GL20.glUniform4(dataUniform,data);}
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0,0);GL11.glVertex2f(-1,-1);GL11.glTexCoord2f(1,0);GL11.glVertex2f(1,-1);GL11.glTexCoord2f(1,1);GL11.glVertex2f(1,1);GL11.glTexCoord2f(0,1);GL11.glVertex2f(-1,1);GL11.glEnd();
        }catch(RuntimeException ex){failed=true;System.err.println("[PocketMod] World refraction unavailable; geometric effects remain: "+ex);}
        finally{GL20.glUseProgram(previous);GL11.glPopAttrib();OpenGlHelper.setActiveTexture(active);}
    }
}
