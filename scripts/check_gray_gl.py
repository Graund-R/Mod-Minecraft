"""Actual terrain-shader pixels in surfaceless Mesa/EGL; no Minecraft.
Requires PyOpenGL; tests spatial separation, fog, lightmap, alpha, and clean overlays.
"""
from pathlib import Path
import os
os.environ.setdefault('PYOPENGL_PLATFORM','egl');os.environ.setdefault('EGL_PLATFORM','surfaceless')
from OpenGL import EGL as e, GL as g
from OpenGL.GL.shaders import compileShader,compileProgram

display=e.eglGetDisplay(e.EGL_DEFAULT_DISPLAY);major,minor=e.EGLint(),e.EGLint()
assert e.eglInitialize(display,major,minor) and e.eglBindAPI(e.EGL_OPENGL_API)
attrs=(e.EGLint*13)(e.EGL_SURFACE_TYPE,e.EGL_PBUFFER_BIT,e.EGL_RENDERABLE_TYPE,e.EGL_OPENGL_BIT,e.EGL_RED_SIZE,8,e.EGL_GREEN_SIZE,8,e.EGL_BLUE_SIZE,8,e.EGL_ALPHA_SIZE,8,e.EGL_NONE)
configs=(e.EGLConfig*1)();count=e.EGLint();assert e.eglChooseConfig(display,attrs,configs,1,count) and count.value
surface=e.eglCreatePbufferSurface(display,configs[0],(e.EGLint*5)(e.EGL_WIDTH,8,e.EGL_HEIGHT,4,e.EGL_NONE))
context=e.eglCreateContext(display,configs[0],e.EGL_NO_CONTEXT,None);assert context and e.eglMakeCurrent(display,surface,surface,context)
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pocketmod/shaders/program'
program=compileProgram(compileShader((root/'exile_gray.vsh').read_text(),g.GL_VERTEX_SHADER),compileShader((root/'exile_gray.fsh').read_text(),g.GL_FRAGMENT_SHADER))
textures=g.glGenTextures(2)
for unit,texture,pixel in [(0,textures[0],bytes([60,180,240,153])),(1,textures[1],bytes([128,128,128,255]))]:
    g.glActiveTexture(g.GL_TEXTURE0+unit);g.glEnable(g.GL_TEXTURE_2D);g.glBindTexture(g.GL_TEXTURE_2D,int(texture))
    g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MIN_FILTER,g.GL_NEAREST);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MAG_FILTER,g.GL_NEAREST)
    g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_RGBA,1,1,0,g.GL_RGBA,g.GL_UNSIGNED_BYTE,pixel)
    g.glMatrixMode(g.GL_TEXTURE);g.glLoadIdentity()
g.glActiveTexture(g.GL_TEXTURE0)
g.glMatrixMode(g.GL_MODELVIEW);g.glLoadIdentity();g.glMatrixMode(g.GL_PROJECTION);g.glLoadIdentity()
g.glViewport(0,0,8,4);g.glDisable(g.GL_DEPTH_TEST);g.glDisable(g.GL_BLEND);g.glDisable(g.GL_ALPHA_TEST);g.glDisable(g.GL_DITHER)
g.glFogf(g.GL_FOG_START,0);g.glFogf(g.GL_FOG_END,1);g.glFogi(g.GL_FOG_MODE,g.GL_LINEAR);g.glFogfv(g.GL_FOG_COLOR,[.1,.2,.3,1])
def quad(x0,y0,x1,y1):
    g.glBegin(g.GL_QUADS)
    for x,y in [(x0,y0),(x1,y0),(x1,y1),(x0,y1)]:
        g.glMultiTexCoord2f(g.GL_TEXTURE0,.5,.5);g.glMultiTexCoord2f(g.GL_TEXTURE1,.5,.5);g.glVertex3f(x,y,-.5)
    g.glEnd()
checks=0
for fbo_on in [False,True]:
    fbo=target=0
    if fbo_on:
        target=g.glGenTextures(1);g.glBindTexture(g.GL_TEXTURE_2D,target);g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_RGBA,8,4,0,g.GL_RGBA,g.GL_UNSIGNED_BYTE,None)
        fbo=g.glGenFramebuffers(1);g.glBindFramebuffer(g.GL_FRAMEBUFFER,fbo);g.glFramebufferTexture2D(g.GL_FRAMEBUFFER,g.GL_COLOR_ATTACHMENT0,g.GL_TEXTURE_2D,target,0)
        assert g.glCheckFramebufferStatus(g.GL_FRAMEBUFFER)==g.GL_FRAMEBUFFER_COMPLETE
        g.glBindTexture(g.GL_TEXTURE_2D,int(textures[0]))
    for fog_on in [False,True]:
        g.glUseProgram(0);g.glClearColor(.1,.2,.8,1);g.glClear(g.GL_COLOR_BUFFER_BIT)
        if fog_on:g.glEnable(g.GL_FOG)
        else:g.glDisable(g.GL_FOG)
        g.glColor4f(1,1,1,1)
        for i,(gray,black) in enumerate([(0,0),(1,0),(1,.2),(1,1)]):
            # Clean chunk uses vanilla fixed pipeline, infected chunks use the new terrain program.
            g.glUseProgram(0 if i==0 else program)
            if i:
                for name,value in [('DiffuseSampler',0),('LightmapSampler',1),('FogMode',g.GL_LINEAR if fog_on else 0)]:g.glUniform1i(g.glGetUniformLocation(program,name),value)
                g.glUniform1f(g.glGetUniformLocation(program,'GrayAmount'),gray);g.glUniform1f(g.glGetUniformLocation(program,'BlackAmount'),black)
            quad(-1+i*.5,-1,-.5+i*.5,0)
        g.glUseProgram(0);g.glDisable(g.GL_FOG)
        # A red entity in front of the gray chunk and a green HUD element over the black region.
        for unit in [g.GL_TEXTURE1,g.GL_TEXTURE0]:g.glActiveTexture(unit);g.glDisable(g.GL_TEXTURE_2D)
        g.glColor4f(1,0,0,1);quad(-.25,-1,0,-.5)
        g.glColor4f(0,1,0,1);quad(.75,.5,1,1)
        pixels=bytes(g.glReadPixels(0,0,8,4,g.GL_RGBA,g.GL_UNSIGNED_BYTE))
        for y in range(4):
            for x in range(8):
                if x==3 and y==0:expected=[255,0,0,255]
                elif x==7 and y==3:expected=[0,255,0,255]
                elif y>=2:expected=[26,51,204,255]
                else:
                    color=[c*128/255 for c in [60,180,240]]
                    if fog_on:color=[.5*c+.5*f*255 for c,f in zip(color,[.1,.2,.3])]
                    if x>=2:
                        light=sum(c*w for c,w in zip(color,[.3,.59,.11]));black=0 if x<4 else .2 if x<6 else 1
                        color=[light*(1-black)]*3
                    expected=[round(c) for c in color]+[153]
                actual=list(pixels[(y*8+x)*4:(y*8+x+1)*4]);assert all(abs(a-b)<=2 for a,b in zip(actual,expected)),(fbo_on,fog_on,x,y,actual,expected)
                checks+=1
        assert g.glGetError()==g.GL_NO_ERROR
        for unit in [g.GL_TEXTURE1,g.GL_TEXTURE0]:g.glActiveTexture(unit);g.glEnable(g.GL_TEXTURE_2D)
    if fbo_on:g.glBindFramebuffer(g.GL_FRAMEBUFFER,0);g.glDeleteFramebuffers(1,[fbo]);g.glDeleteTextures([target]);g.glBindTexture(g.GL_TEXTURE_2D,int(textures[0]))
print(f'Terrain GL: {checks} actual pixel checks passed; clean/gray/dark/black terrain, sky/entities/HUD, fog/lightmap/alpha, FBO on/off; no game launched')
g.glUseProgram(0);g.glDeleteProgram(program);g.glDeleteTextures(textures)
e.eglMakeCurrent(display,e.EGL_NO_SURFACE,e.EGL_NO_SURFACE,e.EGL_NO_CONTEXT);e.eglDestroyContext(display,context);e.eglDestroySurface(display,surface);e.eglTerminate(display)
