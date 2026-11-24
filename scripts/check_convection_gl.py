"""Compile production GLSL and test actual refraction pixels without Minecraft."""
from pathlib import Path
import re, json
bootstrap=(Path(__file__).with_name('check_gray_gl.py')).read_text().split('root=Path')[0]
exec(bootstrap.replace('e.EGL_WIDTH,8,e.EGL_HEIGHT,4','e.EGL_WIDTH,256,e.EGL_HEIGHT,128'))
src=(Path(__file__).resolve().parents[1]/'src/main/java/pocketmod/ExileConvectionClient.java').read_text()
def source(name):
    value=src.split('String '+name+' =',1)[1].split(';\n',1)[0]
    return ''.join(json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"',value))
program=compileProgram(compileShader(source('VERTEX'),g.GL_VERTEX_SHADER),compileShader(source('FRAGMENT'),g.GL_FRAGMENT_SHADER))
w,h=256,128
pixels=bytes(v for y in range(h) for x in range(w) for v in ((255 if x%4<2 else 0),(y*2),x,255))
texture=g.glGenTextures(1);g.glBindTexture(g.GL_TEXTURE_2D,texture)
g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MIN_FILTER,g.GL_LINEAR);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MAG_FILTER,g.GL_LINEAR)
g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_RGBA,w,h,0,g.GL_RGBA,g.GL_UNSIGNED_BYTE,pixels)
g.glViewport(0,0,w,h);g.glDisable(g.GL_DEPTH_TEST);g.glDisable(g.GL_BLEND);g.glDisable(g.GL_DITHER)
def draw(amount,time):
    g.glUseProgram(program)
    g.glUniform1i(g.glGetUniformLocation(program,'Scene'),0)
    g.glUniform1f(g.glGetUniformLocation(program,'Amount'),amount);g.glUniform1f(g.glGetUniformLocation(program,'Time'),time)
    g.glBegin(g.GL_QUADS)
    for x,y,u,v in [(-1,-1,0,0),(1,-1,1,0),(1,1,1,1),(-1,1,0,1)]:g.glTexCoord2f(u,v);g.glVertex2f(x,y)
    g.glEnd()
    return bytes(g.glReadPixels(0,0,w,h,g.GL_RGBA,g.GL_UNSIGNED_BYTE))
a=draw(0,0);b=draw(1,0);c=draw(1,2)
assert a==pixels,'no refraction when disabled'
assert b!=a and b!=c,'visible animated heat haze'
assert all(b[i]==a[i] for i in [0,1,2,3]),'screen corner stays stable'
assert g.glGetError()==0
print('ConvectionGL: production shaders compile; disabled image exact; haze animates; corners stable; no GL errors')
