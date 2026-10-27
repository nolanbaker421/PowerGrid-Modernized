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
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/**
 * With the bender in hand, the leg of the run under the crosshair gets a yellow outline, so it
 * is plain which leg a tap will grab. A grabbed leg keeps an orange outline, and while the
 * crosshair is on a block face a green outline shows where the next tap would put it.
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
        var grabbed = ConduitBenderItem.grabbed(level, stack);
        if(grabbed != null) {
            int leg = ConduitBenderItem.grabbedLeg(stack);
            if(leg < grabbed.boundingBoxes.size()) {
                held = grabbed.boundingBoxes.get(leg);
                if(mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                    var shift = ConduitBenderItem.targetShift(grabbed, leg, hit);
                    if(shift != null && !shift.equals(Vec3i.ZERO))
                        ghost = held.move(shift.getX() / 16.0, shift.getY() / 16.0, shift.getZ() / 16.0);
                }
            }
        }
        if(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof ConduitRunEntity run)
            hover = legBox(run, player);
        if(held == null && ghost == null && hover == null)
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
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    @Nullable
    private static AABB legBox(ConduitRunEntity run, net.minecraft.world.entity.player.Player player) {
        var eye = player.getEyePosition();
        var far = eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1));
        var leg = run.segmentAt(eye, far);
        return leg == null || leg.index() >= run.boundingBoxes.size() ? null : run.boundingBoxes.get(leg.index());
    }
}
