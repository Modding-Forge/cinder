#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

uniform sampler2D Sampler0;
uniform samplerBuffer CinderCtmOverlayTint;
uniform samplerBuffer CinderCtmMaterials;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 shadeColor;
in vec4 lightColor;
in vec2 texCoord0;
flat in uint cinderCtmPayload;
flat in vec4 cinderCtmSourceUv;
flat in uvec4 cinderCtmOverlayMaterials;
flat in vec4 cinderCtmMaterialUv;
flat in int cinderCtmVertexId;

out vec4 fragColor;

vec2 cinderNormalizeCtmUv(vec2 uv, uint payload) {
    uint face = (payload >> 29) & 0x7u;
    if (face == 0u) {
        return vec2(uv.x, 1.0 - uv.y);
    }
    if (face == 1u) {
        return uv;
    }
    if (face == 2u) {
        return vec2(1.0 - uv.x, uv.y);
    }
    if (face == 3u) {
        return uv;
    }
    if (face == 4u) {
        return uv;
    }
    if (face == 5u) {
        return vec2(1.0 - uv.x, uv.y);
    }
    return uv;
}

uint cinderOverlayMaterialId(uint rawMaterial) {
    return rawMaterial & 0xFFFFu;
}

uint cinderOverlayOrientation(uint rawMaterial) {
    return (rawMaterial >> 16) & 0x7u;
}

vec2 cinderOrientOverlayUv(vec2 uv, uint orientation) {
    if (orientation == 1u) {
        return vec2(1.0 - uv.y, uv.x);
    }
    if (orientation == 2u) {
        return vec2(1.0 - uv.x, 1.0 - uv.y);
    }
    if (orientation == 3u) {
        return vec2(uv.y, 1.0 - uv.x);
    }
    if (orientation == 4u) {
        return vec2(1.0 - uv.x, uv.y);
    }
    if (orientation == 5u) {
        return vec2(uv.x, 1.0 - uv.y);
    }
    if (orientation == 6u) {
        return vec2(uv.y, uv.x);
    }
    if (orientation == 7u) {
        return vec2(1.0 - uv.y, 1.0 - uv.x);
    }
    return uv;
}

float cinderOverlayShade(vec3 shadeRgb) {
    float hi = max(max(shadeRgb.r, shadeRgb.g), shadeRgb.b);
    float lo = min(min(shadeRgb.r, shadeRgb.g), shadeRgb.b);
    float chroma = hi - lo;
    return clamp(hi + chroma * 0.55, 0.0, 1.0);
}

vec4 sampleOverlay(sampler2D source, vec2 uv) {
    return textureLod(source, uv, 0.0);
}

float cinderOverlayAlpha(float alpha) {
    return smoothstep(0.30, 0.70, alpha);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);

    float minPixelSize = min(pixelSize.x, pixelSize.y);

    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);

    float effectiveDerivative = sqrt(minDerivative * maxDerivative);

    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    float mipLevelLow = floor(mipLevelExact);
    float mipLevelHigh = mipLevelLow + 1.0;
    float mipBlend = fract(mipLevelExact);

    const vec2 offsets[4] = vec2[](
    vec2(0.125, 0.375),
    vec2(-0.125, -0.375),
    vec2(0.375, -0.125),
    vec2(-0.375, 0.125)
    );

    vec4 rgssColorLow = vec4(0.0);
    vec4 rgssColorHigh = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }
    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;

    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    return mix(nearestColor, rgssColor, blendFactor);
}

void main() {
    vec2 sampleUv = texCoord0;
    vec2 localUv = vec2(0.0);
    bool cinderOverlay = false;
    if (cinderCtmPayload != 0u) {
        vec2 sourceSize = cinderCtmSourceUv.zw - cinderCtmSourceUv.xy;
        localUv = clamp((texCoord0 - cinderCtmSourceUv.xy)
                / max(sourceSize, vec2(0.000001)), vec2(0.0), vec2(1.0));
        localUv = cinderNormalizeCtmUv(localUv, cinderCtmPayload);
        sampleUv = mix(cinderCtmMaterialUv.xy, cinderCtmMaterialUv.zw, localUv);
        cinderOverlay = ((cinderCtmPayload >> 25) & 0x1u) != 0u;
    }
    vec4 sampled = UseRgss == 1
            ? sampleRGSS(Sampler0, sampleUv, 1.0f / TextureSize)
            : sampleNearest(Sampler0, sampleUv, 1.0f / TextureSize);
    bool cinderPrelit = false;
    if (cinderOverlay) {
        vec4 base = UseRgss == 1
                ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize)
                : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize);
        sampled = base * vertexColor;
        cinderPrelit = true;
        for (int i = 0; i < 4; ++i) {
            uint rawMaterial = cinderCtmOverlayMaterials[i];
            uint material = cinderOverlayMaterialId(rawMaterial);
            if (material == 0u) {
                continue;
            }
            vec4 materialUv = texelFetch(CinderCtmMaterials, int(material));
            vec2 overlayLocalUv = cinderOrientOverlayUv(
                    localUv, cinderOverlayOrientation(rawMaterial));
            vec2 overlayUv = mix(materialUv.xy, materialUv.zw, overlayLocalUv);
            vec4 overlaySample = sampleOverlay(Sampler0, overlayUv);
            vec4 overlayTint = texelFetch(
                    CinderCtmOverlayTint, cinderCtmVertexId * 4 + i);
            float overlayAlpha = cinderOverlayAlpha(
                    overlaySample.a * overlayTint.a);
            vec4 overlay = vec4(
                    overlaySample.rgb * overlayTint.rgb
                            * cinderOverlayShade(shadeColor.rgb)
                            * lightColor.rgb,
                    overlayAlpha);
            sampled = mix(sampled, vec4(overlay.rgb, 1.0), overlay.a);
        }
    }
    vec4 color = cinderPrelit ? sampled : sampled * vertexColor;
#ifdef CINDER_CTM_DEBUG_OVERLAY
    if (cinderCtmPayload != 0u) {
        color.rgb = mix(color.rgb, vec3(1.0, 0.25, 0.05), 0.65);
    }
#endif
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
