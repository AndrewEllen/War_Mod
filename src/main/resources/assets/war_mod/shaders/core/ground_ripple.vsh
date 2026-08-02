#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 rippleCoord;

void main() {
    vec3 displaced = Position;
    displaced.y += UV0.x;
    gl_Position = ProjMat * ModelViewMat * vec4(displaced, 1.0);
    sphericalVertexDistance = fog_spherical_distance(displaced);
    cylindricalVertexDistance = fog_cylindrical_distance(displaced);
    vertexColor = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    rippleCoord = Position.xz * 0.075;
}