#version 430 core
struct Particle {
    vec4 position_age;
    vec4 velocity_lifetime;
    vec4 colour_size;
    uvec4 metadata;
};
layout(std430, binding = 0) readonly buffer ParticleBuffer { Particle particles[]; };
layout(std430, binding = 2) readonly buffer VisibleBuffer { uint visibleIds[]; };
uniform mat4 viewProjection;
uniform vec3 cameraPosition;
uniform vec3 cameraRight;
uniform vec3 cameraUp;
uniform uint visibleBase;
out vec4 particleColour;
out vec2 quadUv;
flat out uint particleType;
flat out uint particleSeed;
out float particleAge;

void main() {
    const vec2 corners[4] = vec2[4](vec2(-1.0, -1.0), vec2(1.0, -1.0),
        vec2(-1.0, 1.0), vec2(1.0, 1.0));
    Particle particle = particles[visibleIds[visibleBase + uint(gl_InstanceID)]];
    vec2 corner = corners[gl_VertexID];
    float life = max(0.001, particle.velocity_lifetime.w);
    float ageFraction = clamp(particle.position_age.w / life, 0.0, 1.0);
    float envelope = smoothstep(0.0, 0.08, ageFraction)
        * (1.0 - smoothstep(0.72, 1.0, ageFraction));
    uint type = particle.metadata.x;
    float growth = type == 1u || type == 4u || type == 6u
        ? mix(0.58, 1.62, ageFraction) : mix(0.72, 1.22, ageFraction);
    float size = particle.colour_size.w * growth;
    float verticalScale = type == 0u || type == 3u ? 1.65 : 1.0;
    vec3 relative = particle.position_age.xyz - cameraPosition
        + (cameraRight * corner.x + cameraUp * corner.y * verticalScale) * size;
    gl_Position = viewProjection * vec4(relative, 1.0);
    particleColour = vec4(particle.colour_size.rgb,
        envelope * uintBitsToFloat(particle.metadata.w));
    quadUv = corner * 0.5 + 0.5;
    particleType = type;
    particleSeed = particle.metadata.y;
    particleAge = ageFraction;
}
