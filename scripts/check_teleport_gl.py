"""Exercise production GLSL in Mesa/EGL: spatial compression, occlusion, haze, grayscale and HUD."""
from pathlib import Path
import re,json,struct
bootstrap=Path(__file__).with_name('check_gray_gl.py').read_text().split('root=Path')[0]
exec(bootstrap.replace('e.EGL_WIDTH,8,e.EGL_HEIGHT,4','e.EGL_WIDTH,256,e.EGL_HEIGHT,128'))
src=(Path(__file__).resolve().parents[1]/'src/main/java/pocketmod/ExileConvectionClient.java').read_text()
def source(name):
    value=src.split('String '+name+' =',1)[1].split(';\n',1)[0]
    return ''.join(json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"',value))
program=compileProgram(compileShader(source('VERTEX'),g.GL_VERTEX_SHADER),compileShader(source('FRAGMENT'),g.GL_FRAGMENT_SHADER))
w,h=256,128
scene,depth=g.glGenTextures(2)
def upload_scene(gray=False):
    pixels=bytes(v for y in range(h) for x in range(w) for v in (([x,x,x] if gray else [255 if x%8<4 else 0,y*2,x])+[153]))
    g.glActiveTexture(g.GL_TEXTURE0);g.glBindTexture(g.GL_TEXTURE_2D,int(scene))
    g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MIN_FILTER,g.GL_LINEAR);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MAG_FILTER,g.GL_LINEAR)
    g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_WRAP_S,g.GL_CLAMP_TO_EDGE);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_WRAP_T,g.GL_CLAMP_TO_EDGE)
    g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_RGBA,w,h,0,g.GL_RGBA,g.GL_UNSIGNED_BYTE,pixels);return pixels

def upload_depth(value):
    g.glActiveTexture(g.GL_TEXTURE2);g.glBindTexture(g.GL_TEXTURE_2D,int(depth))
    g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MIN_FILTER,g.GL_NEAREST);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MAG_FILTER,g.GL_NEAREST)
    g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_DEPTH_COMPONENT24,w,h,0,g.GL_DEPTH_COMPONENT,g.GL_FLOAT,struct.pack('<f',value)*(w*h))
    g.glActiveTexture(g.GL_TEXTURE0)

g.glViewport(0,0,w,h);g.glDisable(g.GL_DEPTH_TEST);g.glDisable(g.GL_BLEND);g.glDisable(g.GL_ALPHA_TEST);g.glDisable(g.GL_DITHER)
def draw(heat=0,lens=None):
    g.glUseProgram(program);g.glUniform1i(g.glGetUniformLocation(program,'Scene'),0);g.glUniform1i(g.glGetUniformLocation(program,'SceneDepth'),2)
    g.glUniform1f(g.glGetUniformLocation(program,'Amount'),heat);g.glUniform1f(g.glGetUniformLocation(program,'Time'),1.2)
    g.glUniform1i(g.glGetUniformLocation(program,'LensCount'),0 if lens is None else 1)
    if lens is not None:
        g.glUniform4f(g.glGetUniformLocation(program,'LensShape[0]'),.5,.5,.22,.38)
        g.glUniform4f(g.glGetUniformLocation(program,'LensData[0]'),lens,.7,0,0)
    g.glBegin(g.GL_QUADS)
    for x,y,u,v in [(-1,-1,0,0),(1,-1,1,0),(1,1,1,1),(-1,1,0,1)]:g.glTexCoord2f(u,v);g.glVertex2f(x,y)
    g.glEnd()
    return bytes(g.glReadPixels(0,0,w,h,g.GL_RGBA,g.GL_UNSIGNED_BYTE))
base=upload_scene();upload_depth(1)
assert draw()==base,'disabled shader changes scene'
fold=draw(lens=.65);unfold=draw(lens=-.55);haze=draw(heat=1);both=draw(heat=1,lens=.65)
assert fold!=base and unfold!=base and fold!=unfold,'compression and release must be visible and distinct'
assert both!=fold and both!=haze,'both distortions must compose'
for y in range(h):
    for x in range(w):
        at=(y*w+x)*4
        if ((x+.5)/w-.5)**2/.22**2+((y+.5)/h-.5)**2/.38**2>1:
            assert fold[at:at+4]==base[at:at+4],'fold leaks outside ellipse'
            assert both[at:at+4]==haze[at:at+4],'fold overrides infection haze outside its area'
assert all(fold[i]==153 for i in range(3,len(fold),4)),'source alpha is lost'
upload_depth(.1);assert draw(lens=.65)==base,'lens leaks through a foreground wall'
upload_depth(1);upload_scene(True);gray=draw(heat=1,lens=.65)
assert all(gray[i]==gray[i+1]==gray[i+2] for i in range(0,len(gray),4)),'refraction restores colour to grayscale terrain'
# HUD comes after the world compositor and must keep its exact colour.
g.glUseProgram(0);g.glMatrixMode(g.GL_PROJECTION);g.glLoadIdentity();g.glMatrixMode(g.GL_MODELVIEW);g.glLoadIdentity()
for unit in [g.GL_TEXTURE2,g.GL_TEXTURE1,g.GL_TEXTURE0]:g.glActiveTexture(unit);g.glDisable(g.GL_TEXTURE_2D)
g.glColor4f(0,1,0,1);g.glBegin(g.GL_QUADS)
for x,y in [(.75,.75),(1,.75),(1,1),(.75,1)]:g.glVertex2f(x,y)
g.glEnd()
assert bytes(g.glReadPixels(w-1,h-1,1,1,g.GL_RGBA,g.GL_UNSIGNED_BYTE))==bytes([0,255,0,255]),'HUD colour was altered'
# The compositor's raw attribute stack must restore all texture units, including the infection field.
original=[]
for unit in [g.GL_TEXTURE0,g.GL_TEXTURE1,g.GL_TEXTURE2]:
    g.glActiveTexture(unit);original.append(int(g.glGetIntegerv(g.GL_TEXTURE_BINDING_2D)))
g.glPushAttrib(g.GL_ALL_ATTRIB_BITS)
for unit in [g.GL_TEXTURE0,g.GL_TEXTURE2]:g.glActiveTexture(unit);g.glBindTexture(g.GL_TEXTURE_2D,0)
g.glPopAttrib()
for unit,binding in zip([g.GL_TEXTURE0,g.GL_TEXTURE1,g.GL_TEXTURE2],original):
    g.glActiveTexture(unit);assert int(g.glGetIntegerv(g.GL_TEXTURE_BINDING_2D))==binding,'texture binding leaked'
assert g.glGetError()==0
print('TeleportGL: production GLSL; compression/release; depth occlusion; shared haze; grayscale/alpha/HUD; texture-state restoration passed')
