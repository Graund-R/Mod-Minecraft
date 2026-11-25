package pocketmod;

import java.nio.FloatBuffer;
import org.lwjgl.BufferChecks;
import org.lwjgl.BufferUtils;

/** Reproduce the exact LWJGL 2 guard from the 2.41 crash without starting Minecraft or OpenGL. */
public final class GlQueryBufferSmoke {
    public static void main(String[] args){
        boolean reproduced=false;
        try{BufferChecks.checkBuffer(BufferUtils.createFloatBuffer(4),16);}
        catch(IllegalArgumentException expected){reproduced=expected.getMessage().contains("16");}
        if(!reproduced)throw new AssertionError("old GL_CURRENT_COLOR buffer did not reproduce the reported LWJGL rejection");
        FloatBuffer fixed=DashVisuals.State.colourBuffer();
        BufferChecks.checkBuffer(fixed,16);
        if(!fixed.isDirect()||fixed.position()!=0)throw new AssertionError("OpenGL query needs a direct buffer at its beginning");
        fixed.put(0,.2F);fixed.put(1,.4F);fixed.put(2,.6F);fixed.put(3,.8F);
        if(fixed.get(0)!=.2F||fixed.get(3)!=.8F)throw new AssertionError("RGBA values cannot be restored");
        System.out.println("LWJGL 2: reported 4-value buffer crash reproduced; actual render-state buffer passes the 16-value guard");
    }
}
