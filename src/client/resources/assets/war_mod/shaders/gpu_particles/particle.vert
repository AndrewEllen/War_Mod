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
out vec4 particleColour;
out vec2 quadUv;

void main() {
    const vec2 corners[4] = vec2[4](vec2(-1.0, -1.0), vec2(1.0, -1.0),
        vec2(-1.0, 1.0), vec2(1.0, 1.0));
    Particle particle = particles[visibleIds[gl_InstanceID]];
    vec2 corner = corners[gl_VertexID];
    float life = max(0.001, particle.velocity_lifetime.w);
    float ageFraction = clamp(particle.position_age.w / life, 0.0, 1.0);
    float envelope = smoothstep(0.0, 0.08, ageFraction)
        * (1.0 - smoothstep(0.72, 1.0, ageFraction));
    float size = particle.colour_size.w * mix(0.65, 1.35, ageFraction);
    vec3 relative = particle.position_age.xyz - cameraPosition
        + (cameraRight * corner.x + cameraUp * corner.y) * size;
    gl_Position = viewProjection * vec4(relative, 1.0);
    particleColour = vec4(particle.colour_size.rgb, envelope);
    quadUv = corner * 0.5 + 0.5;
}
