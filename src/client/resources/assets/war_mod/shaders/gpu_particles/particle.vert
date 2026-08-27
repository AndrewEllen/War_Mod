#version 430 core
struct Particle {
    vec4 position_age;
    vec4 velocity_lifetime;
    vec4 colour_size;
    vec4 orientation_mode;
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

vec3 safeNormalize(vec3 value, vec3 fallback) {
    float lengthSquared = dot(value, value);
    return lengthSquared > 1.0e-8 ? value * inversesqrt(lengthSquared) : fallback;
}

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
    int orientationMode = int(round(particle.orientation_mode.w));
    vec3 right = cameraRight;
    vec3 up = cameraUp * verticalScale;
    if (orientationMode == 1) {
        vec3 normal = safeNormalize(particle.orientation_mode.xyz,
            vec3(0.0, 1.0, 0.0));
        vec3 reference = abs(normal.y) > 0.92 ? vec3(1.0, 0.0, 0.0)
            : vec3(0.0, 1.0, 0.0);
        right = safeNormalize(cross(reference, normal), cameraRight);
        up = safeNormalize(cross(normal, right), cameraUp);
    } else if (orientationMode == 2) {
        up = safeNormalize(particle.velocity_lifetime.xyz, cameraUp);
        vec3 viewDirection = safeNormalize(cameraPosition - particle.position_age.xyz,
            -cross(cameraRight, cameraUp));
        right = safeNormalize(cross(up, viewDirection), cameraRight);
    } else if (orientationMode == 3) {
        vec3 normal = safeNormalize(particle.orientation_mode.xyz, cameraUp);
        right = safeNormalize(cross(cameraUp, normal), cameraRight);
        up = safeNormalize(cross(normal, right), cameraUp);
    }
    vec3 relative = particle.position_age.xyz - cameraPosition
        + (right * corner.x + up * corner.y) * size;
    gl_Position = viewProjection * vec4(relative, 1.0);
    particleColour = vec4(particle.colour_size.rgb,
        envelope * uintBitsToFloat(particle.metadata.w));
    quadUv = corner * 0.5 + 0.5;
    particleType = type;
    particleSeed = particle.metadata.y;
    particleAge = ageFraction;
}
