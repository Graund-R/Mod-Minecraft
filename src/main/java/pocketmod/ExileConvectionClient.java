package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** One screen copy per world frame; only refraction, no HUD or hand distortion. */
@SideOnly(Side.CLIENT)
final class ExileConvectionClient {
    private static int program, texture, width, height, amountUniform, timeUniform;
    private static boolean failed;
    private static final String VERTEX = "#version 120\nvarying vec2 uv; void main(){gl_Position=gl_Vertex;uv=gl_MultiTexCoord0.xy;}";
    private static final String FRAGMENT = "#version 120\nuniform sampler2D Scene; uniform float Amount; uniform float Time; varying vec2 uv;\n"
        + "void main(){vec2 p=uv;float edge=sin(3.14159265*uv.y)*sin(3.14159265*uv.x);"
        + "p.x+=Amount*0.0035*edge*(sin(uv.y*43.0-Time*1.7)+0.45*sin(uv.y*91.0+Time*2.1));"
        + "p.y+=Amount*0.0013*edge*sin(uv.x*31.0+uv.y*19.0-Time*1.2);"
        + "gl_FragColor=texture2D(Scene,clamp(p,vec2(0.001),vec2(0.999)));}";
    private static int shader(int type, String source) {
        int id=GL20.glCreateShader(type);
        GL20.glShaderSource(id,source);GL20.glCompileShader(id);
        if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0) {
            String log=GL20.glGetShaderInfoLog(id,4096);GL20.glDeleteShader(id);throw new IllegalStateException(log);
        }
        return id;
    }
    private static void initialize() {
        int vertex=shader(GL20.GL_VERTEX_SHADER,VERTEX),fragment=0;
        try {
            fragment=shader(GL20.GL_FRAGMENT_SHADER,FRAGMENT);
            program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program,4096));
            amountUniform=GL20.glGetUniformLocation(program,"Amount");timeUniform=GL20.glGetUniformLocation(program,"Time");
            texture=GL11.glGenTextures();
        } finally {GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);}
    }
    static void render(float strength,float time) {
        Minecraft mc=Minecraft.getMinecraft();
        if(failed||!GLContext.getCapabilities().OpenGL20)return;
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if(previous!=0)return; // Respect an active shader pack's frame.
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if(program==0)initialize();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            if(width!=mc.displayWidth||height!=mc.displayHeight) {
                width=mc.displayWidth;height=mc.displayHeight;
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGB,0,0,width,height,0);
            } else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Scene"),0);
            GL20.glUniform1f(amountUniform,strength);GL20.glUniform1f(timeUniform,time);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0,0);GL11.glVertex2f(-1,-1);GL11.glTexCoord2f(1,0);GL11.glVertex2f(1,-1);
            GL11.glTexCoord2f(1,1);GL11.glVertex2f(1,1);GL11.glTexCoord2f(0,1);GL11.glVertex2f(-1,1);
            GL11.glEnd();
        } catch(RuntimeException ex) {
            failed=true;System.err.println("[PocketMod] Infection convection unavailable: "+ex);
        } finally {
            GL20.glUseProgram(previous);GL11.glPopAttrib();OpenGlHelper.setActiveTexture(active);
        }
    }
}
