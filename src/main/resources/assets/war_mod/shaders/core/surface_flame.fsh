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
    // Keep the sprite's pixel-scale detail, but remap soot-grey into a warm
    // temperature variation. This costs the same single sample as a flat mask.
    vec4 sprite = texture(Sampler0, texCoord0);
    float detail = dot(sprite.rgb, vec3(0.299, 0.587, 0.114));
    vec2 pixel = floor(texCoord0 * 16.0);
    vec3 hash = fract(vec3(pixel.xyx) * 0.1031);
    hash += dot(hash, hash.yzx + 33.33);
    float grain = fract((hash.x + hash.y) * hash.z);
    float warmth = clamp(detail * 0.72 + grain * 0.28, 0.0, 1.0);
    vec3 shading = mix(vec3(0.83, 0.56, 0.34), vec3(1.10, 1.12, 1.06), warmth);
    // Break up the thin silhouette, while retaining an opaque flame core.
    float mask = smoothstep(0.02 + grain * 0.13, 0.48, sprite.a);
    vec4 color = vec4(vertexColor.rgb * shading, vertexColor.a * mask) * ColorModulator;
    if (color.a < 0.035) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
        FogRenderDistanceEnd, FogColor);
}
