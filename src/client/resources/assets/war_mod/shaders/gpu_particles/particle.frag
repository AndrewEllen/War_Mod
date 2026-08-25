#version 430 core
in vec4 particleColour;
in vec2 quadUv;
flat in uint particleType;
flat in uint particleSeed;
in float particleAge;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 centered = quadUv * 2.0 - 1.0;
    bool fire = particleType == 0u || particleType == 3u;
    bool ember = particleType == 2u;
    float alpha;
    vec3 colour = particleColour.rgb;
    if (fire) {
        float taper = mix(0.92, 0.18, quadUv.y);
        float flame = 1.0 - smoothstep(taper * 0.54, taper,
            abs(centered.x) + abs(centered.y + 0.18) * 0.16);
        float core = 1.0 - smoothstep(0.05, 0.62,
            length(vec2(centered.x, centered.y + 0.42)));
        colour = mix(colour, vec3(1.0, 0.82, 0.24), core * 0.72);
        alpha = particleColour.a * max(flame, core * 0.62);
    } else if (ember) {
        float radius = length(centered);
        alpha = particleColour.a * (1.0 - smoothstep(0.28, 1.0, radius));
        colour = mix(colour, vec3(1.0, 0.62, 0.16), 0.64);
    } else {
        float radius = length(centered);
        float noise = hash12(floor(quadUv * 12.0)
            + vec2(float(particleSeed & 255u), particleAge * 7.0));
        float soft = 1.0 - smoothstep(0.42 + noise * 0.10, 1.0, radius);
        alpha = particleColour.a * soft * mix(0.52, 0.24, particleAge);
    }
    if (alpha < 0.008) discard;
    fragColor = vec4(colour, alpha);
}
