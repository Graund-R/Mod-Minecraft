#version 120
uniform int Spatial;
uniform mat4 InverseView;
uniform vec2 CameraOffset;
varying vec2 texCoord;
varying vec2 lightCoord;
varying float fogDistance;
varying vec2 fieldPosition;
void main() {
    gl_Position = ftransform();
    gl_FrontColor = gl_Color;
    gl_BackColor = gl_Color;
    texCoord = gl_MultiTexCoord0.xy;
    lightCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    vec4 eye = gl_ModelViewMatrix * gl_Vertex;
    fogDistance = abs(eye.z);
    fieldPosition = Spatial == 0 ? vec2(0.0) : (InverseView * eye).xz + CameraOffset;
}
