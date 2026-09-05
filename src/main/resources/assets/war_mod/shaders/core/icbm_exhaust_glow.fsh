#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 mask = texture(Sampler0, texCoord0);
    vec4 colour = mask * vertexColor * ColorModulator;
    if (colour.a < 0.006) discard;

    float centre = 1.0 - smoothstep(0.08, 0.42, abs(texCoord0.x - 0.5));
    float hot = centre * (1.0 - smoothstep(0.35, 0.95, texCoord0.y));
    vec3 emission = mix(colour.rgb, vec3(1.0, 0.99, 0.94), hot * 0.88);
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd);
    // A light source dims through atmospheric haze rather than taking on its
    // colour. The orange fringe retains normal world fog in its separate pass.
    float transmission = 1.0 - 0.58 * fog;
    fragColor = vec4(emission * 1.12, colour.a * transmission);
}
