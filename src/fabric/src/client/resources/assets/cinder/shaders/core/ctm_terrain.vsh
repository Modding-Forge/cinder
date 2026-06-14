#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform usamplerBuffer CinderCtmPayload;
uniform samplerBuffer CinderCtmSourceUv;
uniform usamplerBuffer CinderCtmOverlayMaterials;
uniform samplerBuffer CinderCtmMaterials;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 shadeColor;
out vec4 lightColor;
out vec2 texCoord0;
flat out uint cinderCtmPayload;
flat out vec4 cinderCtmSourceUv;
flat out uvec4 cinderCtmOverlayMaterials;
flat out vec4 cinderCtmMaterialUv;
flat out int cinderCtmVertexId;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    lightColor = sample_lightmap(Sampler2, UV2);
    shadeColor = Color;
    vertexColor = Color * lightColor;
    texCoord0 = UV0;
    cinderCtmVertexId = gl_VertexID;
    cinderCtmPayload = texelFetch(CinderCtmPayload, gl_VertexID).r;
    cinderCtmSourceUv = texelFetch(CinderCtmSourceUv, gl_VertexID);
    cinderCtmOverlayMaterials = texelFetch(
            CinderCtmOverlayMaterials, gl_VertexID);
    cinderCtmMaterialUv = texelFetch(CinderCtmMaterials,
            int(cinderCtmPayload & 0xFFFFu));
}
