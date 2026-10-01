package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.rail.RailCollectorBlock;
import com.nolanbaker.pgmodernized.rail.RailCollectorBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectric;

/**
 * Draws the collector's spring arm from the box out to the rail block it found (the blockstate's
 * reach) and the four shoes at the end, spaced like the bars. Everything is placed from the
 * block's own terminals, so it follows whatever way the block faces or rides.
 */
public class RailCollectorRenderer implements BlockEntityRenderer<RailCollectorBlockEntity> {
    private static final ResourceLocation ARM = PowerGridModernized.asResource("textures/block/rail_bracket.png");
    private static final ResourceLocation SHOE = PowerGridModernized.asResource("textures/block/rail_shoe.png");
    /** Bar centres across the rail block, in blocks from its middle. */
    private static final double[] BARS = {-0.375, -0.125, 0.125, 0.375};

    public RailCollectorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RailCollectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var level = be.getLevel();
        if(level == null)
            return;
        var pos = be.getBlockPos();
        var armDirection = be.arm();
        if(armDirection == null)
            return;   // retracted: only the model's stub shows
        int reach = be.reach();
        var centre = Vec3.atCenterOf(pos);
        var d = Vec3.atLowerCornerOf(armDirection.getNormal());
        // Shoes spread along the stud row when the arm is square to it, else along any perpendicular.
        var across = IElectric.getTerminalPos(level, pos, RailCollectorBlock.N).subtract(IElectric.getTerminalPos(level, pos, RailCollectorBlock.L1));
        var s = across.subtract(d.scale(across.dot(d)));
        if(s.lengthSqr() < 1e-4)
            s = Math.abs(d.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        s = s.normalize();
        var u = d.cross(s).normalize();
        var origin = Vec3.atLowerCornerOf(pos);
        int light = LevelRenderer.getLightColor(level, pos);
        var pose = poseStack.last();
        // The shoes sit in the middle of the rail block the arm found.
        double tip = reach;
        var armStart = centre.add(d.scale(0.375));
        var armEnd = centre.add(d.scale(tip - 0.07));
        var arm = buffer.getBuffer(RenderType.entityCutoutNoCull(ARM));
        CableChainRenderer.box(pose, arm, armStart.add(armEnd).scale(0.5).subtract(origin), d, s, u,
                armStart.distanceTo(armEnd) / 2, 0.0625, 0.0625, light);
        var shoe = buffer.getBuffer(RenderType.entityCutoutNoCull(SHOE));
        for(double bar : BARS) {
            var c = centre.add(d.scale(tip)).add(s.scale(bar)).subtract(origin);
            CableChainRenderer.box(pose, shoe, c, d, s, u, 0.0625, 0.0625, 0.09, light);
        }
    }

    @Override
    public AABB getRenderBoundingBox(RailCollectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(RailCollectorBlock.MAX_REACH + 1);
    }
}
