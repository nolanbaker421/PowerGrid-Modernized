package com.nolanbaker.pgmodernized.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/** Find, on the fire alarm screen: the device's block gets a yellow outline in the world for a while, through walls. */
public final class FireAlarmHighlight {
    private static final int SHOW_TICKS = 300;
    @Nullable
    private static BlockPos target;
    private static long until;

    private FireAlarmHighlight() {}

    public static void show(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        target = pos;
        until = (level == null ? 0 : level.getGameTime()) + SHOW_TICKS;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS || target == null)
            return;
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.level.getGameTime() > until) {
            target = null;
            return;
        }
        boolean blink = (mc.level.getGameTime() / 10) % 2 == 0;
        var camera = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var buffer = mc.renderBuffers().bufferSource();
        var lines = buffer.getBuffer(RenderType.lines());
        var box = new AABB(target).inflate(blink ? 0.08 : 0.02);
        LevelRenderer.renderLineBox(poseStack, lines, box, 1f, 0.85f, 0.2f, 1f);
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
