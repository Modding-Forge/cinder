package com.cinder.fabric.customsky;

import com.cinder.customsky.CustomSkyLayer;
import com.cinder.customsky.CustomSkyRotation;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.EnumMap;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * FrameGraph-era renderer for OptiFine-style 3x2 custom-skybox layers.
 *
 * <p>Threading: called on the render thread. Performance: HOT PATH; the cube
 * mesh is built lazily once and reused by every layer.
 */
public final class CustomSkyRenderer {

    private static final float SIZE = 100.0F;
    private static GpuBuffer cubeBuffer;
    private static final EnumMap<com.cinder.customsky.CustomSkyBlendMode,
            RenderPipeline> PIPELINES = new EnumMap<>(
            com.cinder.customsky.CustomSkyBlendMode.class);

    private CustomSkyRenderer() {
    }

    public static void renderLayer(PoseStack poseStack,
                                   CustomSkyClientSnapshot.RuntimeLayer layer,
                                   int dayTime,
                                   float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        AbstractTexture texture = minecraft.getTextureManager()
                .getTexture(layer.texture());
        GpuBuffer buffer = cubeBuffer();
        RenderSystem.AutoStorageIndexBuffer indices =
                RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        CustomSkyRotation rotation = layer.rule().rotation();
        if (rotation.rotate()) {
            float angle = (dayTime / 24000.0F) * (float) (Math.PI * 2.0)
                    * rotation.speed();
            modelViewStack.rotate(angle, rotation.axisX(), rotation.axisY(),
                    rotation.axisZ());
        }
        Vector4f shaderColor = shaderColor(layer.rule(), alpha);
        GpuBufferSlice dynamicTransforms =
                RenderSystem.getDynamicUniforms().writeTransform(
                        new Matrix4f(modelViewStack),
                        shaderColor);
        modelViewStack.popMatrix();

        GpuTextureView colorTexture = target.getColorTextureView();
        GpuTextureView depthTexture = target.getDepthTextureView();
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Cinder custom sky",
                        colorTexture, Optional.empty(),
                        depthTexture, OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline(layer.rule()));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", texture.getTextureView(),
                    texture.getSampler());
            renderPass.setVertexBuffer(0, buffer.slice());
            renderPass.setIndexBuffer(indexBuffer, indices.type());
            renderPass.drawIndexed(36, 1, 0, 0, 0);
        }
    }

    private static Vector4f shaderColor(CustomSkyLayer layer, float alpha) {
        float clamped = Math.max(0.0F, Math.min(1.0F, alpha));
        return switch (layer.blend()) {
            case SUBTRACT, DODGE, BURN, SCREEN, OVERLAY ->
                    new Vector4f(clamped, clamped, clamped, 1.0F);
            case MULTIPLY ->
                    new Vector4f(clamped, clamped, clamped, clamped);
            case ADD, ALPHA, REPLACE ->
                    new Vector4f(1.0F, 1.0F, 1.0F, clamped);
        };
    }

    private static RenderPipeline pipeline(CustomSkyLayer layer) {
        return PIPELINES.computeIfAbsent(layer.blend(),
                CustomSkyRenderer::createPipeline);
    }

    private static RenderPipeline createPipeline(
            com.cinder.customsky.CustomSkyBlendMode blendMode) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withLocation(Identifier.fromNamespaceAndPath("cinder",
                        "pipeline/custom_sky_" + blendMode.name()
                                .toLowerCase(java.util.Locale.ROOT)))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
        BlendFunction blend = blendFunction(blendMode);
        if (blend != null) {
            builder.withColorTargetState(new ColorTargetState(blend));
        }
        return builder.build();
    }

    private static BlendFunction blendFunction(
            com.cinder.customsky.CustomSkyBlendMode blendMode) {
        return switch (blendMode) {
            case ADD -> new BlendFunction(BlendFactor.SRC_ALPHA,
                    BlendFactor.ONE);
            case ALPHA -> BlendFunction.TRANSLUCENT;
            case MULTIPLY -> new BlendFunction(BlendFactor.DST_COLOR,
                    BlendFactor.ONE_MINUS_SRC_ALPHA);
            case SCREEN -> new BlendFunction(BlendFactor.ONE,
                    BlendFactor.ONE_MINUS_SRC_COLOR);
            case SUBTRACT -> new BlendFunction(
                    BlendFactor.ONE_MINUS_DST_COLOR, BlendFactor.ZERO);
            case DODGE -> new BlendFunction(BlendFactor.ONE,
                    BlendFactor.ONE);
            case BURN -> new BlendFunction(BlendFactor.ZERO,
                    BlendFactor.ONE_MINUS_SRC_COLOR);
            case OVERLAY -> new BlendFunction(BlendFactor.DST_COLOR,
                    BlendFactor.SRC_COLOR);
            case REPLACE -> null;
        };
    }

    private static GpuBuffer cubeBuffer() {
        if (cubeBuffer == null) {
            cubeBuffer = buildCube();
        }
        return cubeBuffer;
    }

    private static GpuBuffer buildCube() {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(
                DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()
                        * 4 * 6)) {
            BufferBuilder builder = new BufferBuilder(bytes,
                    PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            // OptiFine/MCPatcher 3x2 custom-sky sheets are laid out as
            // [down][up][north] / [west][south][east].
            addFaceRotated180(builder, -SIZE, -SIZE, SIZE, -SIZE, SIZE, SIZE,
                    SIZE, SIZE, SIZE, SIZE, -SIZE, SIZE, 2, 0);
            addFaceRotated180(builder, SIZE, -SIZE, SIZE, SIZE, SIZE, SIZE,
                    SIZE, SIZE, -SIZE, SIZE, -SIZE, -SIZE, 2, 1);
            addFaceRotated180(builder, SIZE, -SIZE, -SIZE, SIZE, SIZE, -SIZE,
                    -SIZE, SIZE, -SIZE, -SIZE, -SIZE, -SIZE, 1, 1);
            addFaceRotated180(builder, -SIZE, -SIZE, -SIZE, -SIZE, SIZE, -SIZE,
                    -SIZE, SIZE, SIZE, -SIZE, -SIZE, SIZE, 0, 1);
            addFace(builder, -SIZE, -SIZE, -SIZE, -SIZE, -SIZE, SIZE,
                    SIZE, -SIZE, SIZE, SIZE, -SIZE, -SIZE, 0, 0);
            addFace(builder, -SIZE, SIZE, SIZE, -SIZE, SIZE, -SIZE,
                    SIZE, SIZE, -SIZE, SIZE, SIZE, SIZE, 1, 0);
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(
                        () -> "Cinder custom sky cube", 32,
                        mesh.vertexBuffer());
            }
        }
    }

    private static void addFace(BufferBuilder builder,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                int column,
                                int row) {
        float u0 = column / 3.0F;
        float u1 = (column + 1) / 3.0F;
        float v0 = row / 2.0F;
        float v1 = (row + 1) / 2.0F;
        int color = ARGB.white(1.0F);
        builder.addVertex(x0, y0, z0).setUv(u0, v0).setColor(color);
        builder.addVertex(x1, y1, z1).setUv(u0, v1).setColor(color);
        builder.addVertex(x2, y2, z2).setUv(u1, v1).setColor(color);
        builder.addVertex(x3, y3, z3).setUv(u1, v0).setColor(color);
    }

    private static void addFaceRotated180(BufferBuilder builder,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float x2, float y2, float z2,
                                          float x3, float y3, float z3,
                                          int column,
                                          int row) {
        float u0 = column / 3.0F;
        float u1 = (column + 1) / 3.0F;
        float v0 = row / 2.0F;
        float v1 = (row + 1) / 2.0F;
        int color = ARGB.white(1.0F);
        builder.addVertex(x0, y0, z0).setUv(u1, v1).setColor(color);
        builder.addVertex(x1, y1, z1).setUv(u1, v0).setColor(color);
        builder.addVertex(x2, y2, z2).setUv(u0, v0).setColor(color);
        builder.addVertex(x3, y3, z3).setUv(u0, v1).setColor(color);
    }
}
