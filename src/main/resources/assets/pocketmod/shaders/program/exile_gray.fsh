#version 120
uniform sampler2D DiffuseSampler;
uniform sampler2D LightmapSampler;
uniform float GrayAmount;
uniform float BlackAmount;
uniform int FogMode;
varying vec2 texCoord;
varying vec2 lightCoord;
varying float fogDistance;
void main() {
    vec4 color = texture2D(DiffuseSampler, texCoord) * gl_Color;
    color.rgb *= texture2D(LightmapSampler, lightCoord).rgb;
    float fog = 1.0;
    if (FogMode == 9729) fog = clamp((gl_Fog.end - fogDistance) / max(0.001, gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    else if (FogMode == 2048) fog = clamp(exp(-gl_Fog.density * fogDistance), 0.0, 1.0);
    else if (FogMode == 2049) fog = clamp(exp(-pow(gl_Fog.density * fogDistance, 2.0)), 0.0, 1.0);
    color.rgb = mix(gl_Fog.color.rgb, color.rgb, fog);
    float light = dot(color.rgb, vec3(0.3, 0.59, 0.11));
    color.rgb = mix(color.rgb, vec3(light), GrayAmount) * (1.0 - BlackAmount);
    gl_FragColor = color;
}
