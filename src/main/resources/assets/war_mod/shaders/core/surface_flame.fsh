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
    // Vary the silhouette within the existing card, never its cell footprint.
    // One sample retains the batching/overdraw cost of the existing near pass.
    vec2 uv = texCoord0;
    float phase = dot(vertexColor.rgb, vec3(17.0, 31.0, 43.0));
    uv.x += sin(uv.y * 15.0 + phase) * 0.045 * sin(uv.y * 3.14159);
    uv.y += sin(uv.x * 11.0 + phase * 0.71) * 0.024;
    vec4 sprite = texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0)));
    float detail = dot(sprite.rgb, vec3(0.299, 0.587, 0.114));
    vec2 pixel = floor(texCoord0 * 16.0);
    vec3 hash = fract(vec3(pixel.xyx) * 0.1031);
    hash += dot(hash, hash.yzx + 33.33);
    float grain = fract((hash.x + hash.y) * hash.z);
    float warmth = clamp(detail * 0.72 + grain * 0.28, 0.0, 1.0);
    vec3 shading = mix(vec3(0.83, 0.56, 0.34), vec3(1.10, 1.12, 1.06), warmth);
    // Preserve the sprite's soft falloff instead of thresholding its grey edge
    // into a solid cutout. Overlapping cores supply density independently.
    // Vanilla smoke has a largely binary pixel boundary. Feather inside that
    // boundary analytically so enlarged close-up cards do not read as flat PNGs.
    vec2 local = (uv - vec2(0.5)) * vec2(2.0, 1.85);
    float radius = length(local);
    float edge = 1.0 - smoothstep(0.40 + grain * 0.10, 0.98, radius);
    float mask = sprite.a * smoothstep(0.0, 0.65, sprite.a) * edge;
    vec4 color = vec4(vertexColor.rgb * shading, vertexColor.a * mask) * ColorModulator;
    if (color.a < 0.012) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
        FogRenderDistanceEnd, FogColor);
}
