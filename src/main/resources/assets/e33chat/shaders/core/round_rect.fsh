#version 330

// SDF rounded rectangle (Inigo Quilez sdRoundedBox), same math as the
// official 1.21.1 renderer. fwidth gives a physical-pixel anti-alias width,
// so the corner curve is smooth at any GUI scale.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 localPos;
in vec2 halfSize;
in float cornerRadius;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 q = abs(localPos) - halfSize + cornerRadius;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;

    float aa = fwidth(dist);
    float alpha = 1.0 - smoothstep(-aa, 0.0, dist);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
