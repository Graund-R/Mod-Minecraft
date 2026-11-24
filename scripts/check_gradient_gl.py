"""Render the production spatial infection shader to actual EGL pixels, without Minecraft."""
from pathlib import Path
bootstrap=Path(__file__).with_name('check_gray_gl.py').read_text().split('root=Path')[0]
exec(bootstrap.replace('e.EGL_WIDTH,8,e.EGL_HEIGHT,4','e.EGL_WIDTH,256,e.EGL_HEIGHT,128'))
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pocketmod/shaders/program'
program=compileProgram(compileShader((root/'exile_gray.vsh').read_text(),g.GL_VERTEX_SHADER),compileShader((root/'exile_gray.fsh').read_text(),g.GL_FRAGMENT_SHADER))
textures=g.glGenTextures(3)
for unit,pixel in [(0,[30,120,240,255]),(1,[255,255,255,255])]:
    g.glActiveTexture(g.GL_TEXTURE0+unit);g.glBindTexture(g.GL_TEXTURE_2D,int(textures[unit]))
    g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MIN_FILTER,g.GL_NEAREST);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MAG_FILTER,g.GL_NEAREST)
    g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_RGBA,1,1,0,g.GL_RGBA,g.GL_UNSIGNED_BYTE,bytes(pixel))
    g.glMatrixMode(g.GL_TEXTURE);g.glLoadIdentity()
g.glActiveTexture(g.GL_TEXTURE2);g.glBindTexture(g.GL_TEXTURE_2D,int(textures[2]))
g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MIN_FILTER,g.GL_NEAREST);g.glTexParameteri(g.GL_TEXTURE_2D,g.GL_TEXTURE_MAG_FILTER,g.GL_NEAREST)
g.glActiveTexture(g.GL_TEXTURE0)
g.glMatrixMode(g.GL_MODELVIEW);g.glLoadIdentity();g.glMatrixMode(g.GL_PROJECTION);g.glLoadIdentity();g.glOrtho(0,80,0,80,-1,1)
g.glViewport(0,0,256,128);g.glDisable(g.GL_DEPTH_TEST);g.glDisable(g.GL_BLEND);g.glDisable(g.GL_ALPHA_TEST);g.glDisable(g.GL_DITHER)
g.glUseProgram(program);g.glColor4f(1,1,1,1)
for name,value in [('DiffuseSampler',0),('LightmapSampler',1),('InfectionField',2),('Spatial',1),('FogMode',0)]:g.glUniform1i(g.glGetUniformLocation(program,name),value)
g.glUniform2f(g.glGetUniformLocation(program,'FieldSize'),5,5);g.glUniform2f(g.glGetUniformLocation(program,'CameraOffset'),0,0)
# This inverse maps the drawn XY plane back onto the world's XZ terrain plane.
g.glUniformMatrix4fv(g.glGetUniformLocation(program,'InverseView'),1,False,[1,0,0,0,0,0,1,0,0,1,0,0,0,0,0,1])
def render(sources):
    grid=bytearray([0,0,0,255]*25)
    for x,z,gray,black in sources:
        offset=(z*5+x)*4;grid[offset]=round(gray*255);grid[offset+1]=round(black*255)
    g.glActiveTexture(g.GL_TEXTURE2);g.glTexImage2D(g.GL_TEXTURE_2D,0,g.GL_RGBA,5,5,0,g.GL_RGBA,g.GL_UNSIGNED_BYTE,bytes(grid));g.glActiveTexture(g.GL_TEXTURE0)
    g.glBegin(g.GL_QUADS)
    for x,y in [(0,0),(80,0),(80,80),(0,80)]:
        g.glMultiTexCoord2f(g.GL_TEXTURE0,.5,.5);g.glMultiTexCoord2f(g.GL_TEXTURE1,.5,.5);g.glVertex3f(x,y,0)
    g.glEnd()
    assert g.glGetError()==0
    return bytes(g.glReadPixels(0,0,256,128,g.GL_RGBA,g.GL_UNSIGNED_BYTE))
def at(image,x,z):
    offset=(int(z/80*128)*256+int(x/80*256))*4
    return image[offset:offset+3]
def saturation(rgb):return max(rgb)-min(rgb)
checks=0
def check(ok,name):
    global checks
    checks+=1
    assert ok,name
clean=render([]);one=render([(2,2,1,0)])
check(at(clean,40,40)==bytes([30,120,240]),'clean terrain retains original color')
check(saturation(at(one,40,40))<=1,'center becomes gray first')
check(0<saturation(at(one,52,40))<210,'smooth partially gray fringe outside source chunk')
check(at(one,72,40)==bytes([30,120,240]),'no distant or texture-border leakage')
check(saturation(at(one,44,40))<saturation(at(one,48,40))<saturation(at(one,52,40)),'monotonic radial falloff')
early=render([(2,2,.2,0),(3,2,.2,0)])
half=render([(2,2,.55,0),(3,2,.55,0)])
joined=render([(2,2,1,0),(3,2,1,0)])
check(saturation(at(early,48,40))>200,'young spots remain separate')
check(0<saturation(at(half,48,40))<saturation(at(early,48,40)),'growing patches create an intermediate bridge')
check(saturation(at(joined,48,40))<=1,'mature neighbors merge into a filled bridge')
for boundary in [32,48,64]:
    a=at(half,boundary-.2,40);b=at(half,boundary+.2,40)
    check(max(abs(x-y) for x,y in zip(a,b))<=5,'no hard seam at chunk boundary '+str(boundary))
black=render([(2,2,1,1),(3,2,1,1)])
check(max(at(black,48,40))==0,'mature black cores also merge')
check(0<max(at(black,48,57))<240,'dark fringe remains graded')
# Translation is relative to the snapshot's origin, so negative and far-world coordinates use identical small GPU values.
check(g.glGetError()==0,'no GL errors')
print(f'GradientGL: {checks} actual pixel checks passed; radial cores, smooth edges, growing bridges, merged blackness, clean terrain; no game launched')
