#version 120
varying vec2 texCoord;
varying vec2 lightCoord;
varying float fogDistance;
void main() {
    gl_Position = ftransform();
    gl_FrontColor = gl_Color;
    gl_BackColor = gl_Color;
    texCoord = gl_MultiTexCoord0.xy;
    lightCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    fogDistance = abs((gl_ModelViewMatrix * gl_Vertex).z);
}
