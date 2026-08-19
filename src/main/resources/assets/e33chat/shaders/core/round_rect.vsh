#version 330

// SDF rounded-rect GUI shader (1.21.11 render-state pipeline, 2.4.0 sync).
// Mirrors core/gui.vsh's UBO structure; the vertex carries SDF parameters:
//   Position.xyz = (x, y, cornerRadius)   — x/y pre-transformed by the pose
//   UV0          = local coords of the corner, ±(halfWidth, halfHeight)
// The fragment shader rebuilds halfSize = abs(UV0) (constant per quad).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec2 localPos;
out vec2 halfSize;
out float cornerRadius;
out vec4 vertexColor;

void main() {
    localPos = UV0;
    halfSize = abs(UV0);
    cornerRadius = Position.z;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
}
