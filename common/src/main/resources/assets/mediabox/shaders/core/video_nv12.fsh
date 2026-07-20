#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float y = 1.164383 * (texture(Sampler0, texCoord0).r - 0.062745);
    vec2 uv = texture(Sampler1, texCoord0).rg - vec2(0.5);
    vec3 rgb = vec3(
        y + 1.792741 * uv.y,
        y - 0.213249 * uv.x - 0.532909 * uv.y,
        y + 2.112402 * uv.x
    );
    vec4 color = vec4(clamp(rgb, 0.0, 1.0), 1.0) * vertexColor * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
