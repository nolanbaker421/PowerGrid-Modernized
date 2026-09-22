package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.breaker.BreakerItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlock;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * While a breaker or blank is in hand and the crosshair is on a panel, every free space is outlined
 * and the one under the crosshair is drawn bright, so it is clear where the breaker will go.
 */
public final class BreakerPlacementOutline {
    private BreakerPlacementOutline() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if(player == null || level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        if(!(player.getMainHandItem().getItem() instanceof BreakerItem))
            return;
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if(!(state.getBlock() instanceof BreakerPanelBlock) || !(level.getBlockEntity(pos) instanceof BreakerPanelBlockEntity be))
            return;

        var spec = be.spec();
        var facing = BreakerPanelBlock.facing(state);
        int hovered = PanelLayout.slotAt(spec, facing, hit.getDirection(), hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));

        var poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        for(int slot = BreakerPanelBlockEntity.MAIN; slot < spec.slots(); ++slot) {
            if(be.breaker(slot).installed())
                continue;
            var box = toWorld(PanelLayout.breakerBox(spec, slot), facing).inflate(0.002);
            boolean bright = slot == hovered;
            LevelRenderer.renderLineBox(poseStack, buffer, box, 1f, bright ? 0.95f : 0.8f, bright ? 0.3f : 0.2f, bright ? 1f : 0.35f);
        }
        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    /** North-frame pixel box to a block-local box in blocks, rotated to the panel's facing. */
    private static AABB toWorld(AABB px, Direction facing) {
        var a = PanelLayout.fromNorthFrame(new Vec3(px.minX, px.minY, px.minZ), facing).scale(1 / 16.0);
        var b = PanelLayout.fromNorthFrame(new Vec3(px.maxX, px.maxY, px.maxZ), facing).scale(1 / 16.0);
        return new AABB(a, b);
    }
}
