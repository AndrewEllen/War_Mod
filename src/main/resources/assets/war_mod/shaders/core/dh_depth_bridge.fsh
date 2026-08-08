#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

float depthToNdc(float depth) {
#ifdef ZERO_ONE_NDC
    return depth;
#else
    return depth * 2.0 - 1.0;
#endif
}

float ndcToDepth(float depth) {
#ifdef ZERO_ONE_NDC
    return depth;
#else
    return depth * 0.5 + 0.5;
#endif
}

void main() {
    float dhDepth = texture(Sampler0, texCoord).r;

    // Minecraft 26.2's normal renderer uses reverse-Z, where zero is the clear/far value.
    if (dhDepth <= 0.0000001) {
        discard;
    }

    vec4 dhClip = vec4(texCoord * 2.0 - 1.0, depthToNdc(dhDepth), 1.0);

    // ModelViewMat intentionally carries MC_MVP * inverse(DH_MVP).
    vec4 mcClip = ModelViewMat * dhClip;
    if (mcClip.w <= 0.000001) {
        discard;
    }

    float mappedDepth = ndcToDepth(mcClip.z / mcClip.w);
    if (mappedDepth <= 0.0 || mappedDepth > 1.0) {
        discard;
    }

    // Resolve marginal equality at the LOD surface in the near direction for reverse-Z.
    mappedDepth = min(1.0, mappedDepth + (2.0 / 16777215.0));

    gl_FragDepth = mappedDepth;
    fragColor = vec4(0.0);
}
