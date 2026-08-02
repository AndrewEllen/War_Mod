#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 rippleCoord;
out vec4 fragColor;

void main() {
    vec4 noise = texture(Sampler0, rippleCoord);
    vec4 color = vec4(vertexColor.rgb * (0.82 + noise.r * 0.24), vertexColor.a * noise.a);
    color *= lightMapColor * ColorModulator;
    if (color.a < 0.008) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}