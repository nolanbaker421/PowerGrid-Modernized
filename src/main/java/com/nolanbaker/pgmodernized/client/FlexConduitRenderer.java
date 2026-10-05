package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.conduit.FlexConduitEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A sagging square tube from one end of a flexible conduit to the other, in the conduit texture. */
public class FlexConduitRenderer extends EntityRenderer<FlexConduitEntity> {
    private static final ResourceLocation TEXTURE = PowerGridModernized.asResource("textures/special/conduit.png");

    public FlexConduitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(FlexConduitEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FlexConduitEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if(!entity.placed())
            return;
        var consumer = buffer.getBuffer(RenderType.entitySolid(TEXTURE));
        // Draw from the first end's position this tick rather than the entity's interpolated one.
        var drawn = entity.getPosition(partialTicks);
        var a = entity.end1();
        poseStack.pushPose();
        poseStack.translate(a.x - drawn.x, a.y - drawn.y, a.z - drawn.z);
        var pose = poseStack.last();
        float r = entity.size().tubeThickness() / 2;
        int n = FlexConduitEntity.RENDER_SEGMENTS;
        var prev = entity.point(0);
        for(int i = 1; i <= n; ++i) {
            var next = entity.point(i / (float) n);
            var dir = next.subtract(prev);
            double len = dir.length();
            if(len < 1e-6) {
                prev = next;
                continue;
            }
            dir = dir.scale(1 / len);
            var up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            var side = dir.cross(up).normalize();
            var up2 = side.cross(dir).normalize();
            Vec3[] corners = {
                    side.scale(r).add(up2.scale(r)), side.scale(-r).add(up2.scale(r)),
                    side.scale(-r).add(up2.scale(-r)), side.scale(r).add(up2.scale(-r)),
            };
            for(int k = 0; k < 4; ++k) {
                var c0 = corners[k];
                var c1 = corners[(k + 1) % 4];
                var normal = c0.add(c1).normalize();
                vertex(pose, consumer, prev.add(c1).subtract(a), 0, 0, normal, light);
                vertex(pose, consumer, prev.add(c0).subtract(a), 0, 1, normal, light);
                vertex(pose, consumer, next.add(c0).subtract(a), 1, 1, normal, light);
                vertex(pose, consumer, next.add(c1).subtract(a), 1, 0, normal, light);
            }
            prev = next;
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 p, float u, float v, Vec3 normal, int light) {
        consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
