#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 sphereNormal;
in vec3 spherePosition;

out vec4 fragColor;

mat3 cotangentFrame(vec3 normal, vec3 position, vec2 uv) {
    vec3 positionDx = dFdx(position);
    vec3 positionDy = dFdy(position);
    vec2 uvDx = dFdx(uv);
    vec2 uvDy = dFdy(uv);
    float determinant = uvDx.x * uvDy.y - uvDx.y * uvDy.x;

    if (abs(determinant) < 1.0e-8) {
        vec3 referenceAxis = abs(normal.y) < 0.999
                ? vec3(0.0, 1.0, 0.0)
                : vec3(1.0, 0.0, 0.0);
        vec3 tangent = normalize(cross(referenceAxis, normal));
        return mat3(tangent, normalize(cross(normal, tangent)), normal);
    }

    vec3 tangent = (positionDx * uvDy.y - positionDy * uvDx.y) / determinant;
    vec3 bitangent = (positionDy * uvDx.x - positionDx * uvDy.x) / determinant;
    tangent = normalize(tangent - normal * dot(normal, tangent));
    float handedness = dot(cross(normal, tangent), bitangent) < 0.0 ? -1.0 : 1.0;
    bitangent = normalize(cross(normal, tangent)) * handedness;
    return mat3(tangent, bitangent, normal);
}

void main() {
    vec4 baseColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    vec3 normal = normalize(sphereNormal);
    vec3 mappedNormal = texture(Sampler1, texCoord0).rgb * 2.0 - 1.0;
    vec3 bumpedNormal = normalize(cotangentFrame(normal, spherePosition, texCoord0) * mappedNormal);

    vec3 lightDirection = normalize(vec3(-0.45, 0.75, 0.55));
    float diffuse = max(dot(bumpedNormal, lightDirection), 0.0);
    float rim = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), 2.0) * 0.15;
    float light = 0.32 + diffuse * 0.78 + rim;

    vec4 color = vec4(baseColor.rgb * light, baseColor.a);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
