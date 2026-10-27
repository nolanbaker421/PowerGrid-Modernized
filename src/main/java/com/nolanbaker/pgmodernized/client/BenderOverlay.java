package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConduitBenderItem;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/**
 * With the bender in hand, the leg of the run under the crosshair gets a yellow outline, so it
 * is plain which leg a tap will grab. A grabbed leg keeps an orange outline with a marker at the
 * grab point; while the crosshair is on a block face a green outline shows where the next tap
 * would put the leg, and on another leg of the same run a green line shows the diagonal it
 * would make.
 */
public final class BenderOverlay {
    private BenderOverlay() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS)
            return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if(player == null || level == null)
            return;
        var stack = player.getMainHandItem();
        if(!(stack.getItem() instanceof ConduitBenderItem))
            return;
        AABB held = null, ghost = null, hover = null;
        net.minecraft.world.phys.Vec3 mark = null, to = null;
        var grabbed = ConduitBenderItem.grabbed(level, stack);
        if(grabbed != null) {
            int leg = ConduitBenderItem.grabbedLeg(stack);
            if(leg < grabbed.boundingBoxes.size()) {
                held = grabbed.legBox(leg);
                mark = ConduitBenderItem.pointOnLeg(grabbed, leg, ConduitBenderItem.grabbedPx(stack));
                if(mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                    var shift = ConduitBenderItem.targetShift(grabbed, leg, hit);
                    if(shift != null && !shift.equals(Vec3i.ZERO))
                        ghost = held.move(shift.getX() / 16.0, shift.getY() / 16.0, shift.getZ() / 16.0);
                }
            }
        }
        if(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof ConduitRunEntity run) {
            var eye = player.getEyePosition();
            var far = eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1));
            var leg = run.segmentAt(eye, far);
            if(leg != null && leg.index() < run.boundingBoxes.size()) {
                hover = run.legBox(leg.index());
                if(mark != null && run == grabbed && leg.index() != ConduitBenderItem.grabbedLeg(stack))
                    to = ConduitBenderItem.pointOnLeg(run, leg.index(), ConduitBenderItem.pxAlong(run, leg.index(), leg.point()));
            }
        }
        if(held == null && ghost == null && hover == null && mark == null)
            return;
        var camera = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var buffer = mc.renderBuffers().bufferSource();
        var lines = buffer.getBuffer(RenderType.lines());
        if(hover != null && !hover.equals(held))
            LevelRenderer.renderLineBox(poseStack, lines, hover, 1f, 0.85f, 0.2f, 1f);
        if(held != null)
            LevelRenderer.renderLineBox(poseStack, lines, held, 1f, 0.55f, 0.1f, 1f);
        if(ghost != null)
            LevelRenderer.renderLineBox(poseStack, lines, ghost, 0.3f, 1f, 0.4f, 1f);
        if(mark != null) {
            double m = 1.5 / 16;
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(mark.x - m, mark.y - m, mark.z - m, mark.x + m, mark.y + m, mark.z + m), 1f, 0.55f, 0.1f, 1f);
            if(to != null)
                line(poseStack, lines, mark, to, 0.3f, 1f, 0.4f);
        }
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void line(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer lines,
                             net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, float r, float g, float bl) {
        var n = b.subtract(a);
        if(n.lengthSqr() < 1e-8)
            return;
        n = n.normalize();
        var pose = poseStack.last();
        lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1f).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1f).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
