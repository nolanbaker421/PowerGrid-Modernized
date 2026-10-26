package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConduitBenderItem;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/**
 * With an empty bender in hand, the leg of the run under the crosshair gets an outline, so it is
 * plain which leg a click will move before it moves.
 */
public final class BenderOverlay {
    private BenderOverlay() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS)
            return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if(player == null)
            return;
        var stack = player.getMainHandItem();
        if(!(stack.getItem() instanceof ConduitBenderItem) || ConduitBenderItem.loaded(stack))
            return;
        if(!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ConduitRunEntity run))
            return;
        var eye = player.getEyePosition();
        var far = eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1));
        var leg = run.segmentAt(eye, far);
        if(leg == null)
            return;
        var box = run.boundingBoxes.get(leg.index());
        var camera = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var buffer = mc.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.lines()), box, 1f, 0.85f, 0.2f, 1f);
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
