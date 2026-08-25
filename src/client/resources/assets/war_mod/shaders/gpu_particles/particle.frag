#version 430 core
in vec4 particleColour;
in vec2 quadUv;
out vec4 fragColor;

void main() {
    float radius = length(quadUv - vec2(0.5)) * 2.0;
    float alpha = particleColour.a * (1.0 - smoothstep(0.48, 1.0, radius));
    if (alpha < 0.008) discard;
    fragColor = vec4(particleColour.rgb, alpha);
}
