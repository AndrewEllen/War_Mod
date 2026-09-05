#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 smoke = texture(Sampler0, texCoord0);
    // Preserve the explosion sprite's irregular silhouette and internal detail,
    // but map its soot-grey values into pale rocket exhaust/steam.
    smoke.rgb = mix(vec3(0.78), vec3(1.0), smoke.rgb);
    vec4 color = smoke * vertexColor * ColorModulator;
    // Vanilla's 0.1 alpha cutoff makes faint incoming/outgoing LOD cohorts
    // pop. Keep the same smoke sprite but preserve its gradual optical fade.
    if (color.a < 0.003) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
        FogRenderDistanceEnd, FogColor);
}
