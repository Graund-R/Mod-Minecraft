#version 120
uniform sampler2D DiffuseSampler;
uniform sampler2D LightmapSampler;
uniform sampler2D InfectionField;
uniform float GrayAmount;
uniform float BlackAmount;
uniform int FogMode;
uniform int Spatial;
uniform vec2 FieldSize;
varying vec2 texCoord;
varying vec2 lightCoord;
varying float fogDistance;
varying vec2 fieldPosition;
vec2 source(vec2 cell) {
    if (cell.x < 0.0 || cell.y < 0.0 || cell.x >= FieldSize.x || cell.y >= FieldSize.y) return vec2(0.0);
    return texture2D(InfectionField, (cell + vec2(0.5)) / FieldSize).rg;
}
float influence(float strength, float distance) {
    float radius = 4.0 + 18.0 * strength;
    return strength * (1.0 - smoothstep(radius * 0.1, radius, distance));
}
vec2 infection() {
    vec2 cell = floor(fieldPosition / 16.0);
    vec2 sum = vec2(0.0);
    for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
        vec2 neighbor = cell + vec2(float(x), float(z));
        vec2 amount = source(neighbor);
        float distance = length(fieldPosition - (neighbor * 16.0 + vec2(8.0)));
        sum += vec2(influence(amount.r, distance), influence(amount.g, distance));
    }
    return clamp(sum, 0.0, 1.0);
}
void main() {
    vec4 color = texture2D(DiffuseSampler, texCoord) * gl_Color;
    color.rgb *= texture2D(LightmapSampler, lightCoord).rgb;
    float fog = 1.0;
    if (FogMode == 9729) fog = clamp((gl_Fog.end - fogDistance) / max(0.001, gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    else if (FogMode == 2048) fog = clamp(exp(-gl_Fog.density * fogDistance), 0.0, 1.0);
    else if (FogMode == 2049) fog = clamp(exp(-pow(gl_Fog.density * fogDistance, 2.0)), 0.0, 1.0);
    color.rgb = mix(gl_Fog.color.rgb, color.rgb, fog);
    vec2 amount = Spatial == 0 ? vec2(GrayAmount, BlackAmount) : infection();
    float light = dot(color.rgb, vec3(0.3, 0.59, 0.11));
    color.rgb = mix(color.rgb, vec3(light), amount.r) * (1.0 - amount.g);
    gl_FragColor = color;
}
