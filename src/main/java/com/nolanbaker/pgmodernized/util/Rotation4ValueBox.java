package com.nolanbaker.pgmodernized.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;

/**
 * A value box on the top of a Power Grid four-rotation block, given in the floor frame (the block
 * standing on the floor, "facing=down"): the point is put through the same steps as the block's
 * terminals, so it follows the block onto walls and ceilings, and the box faces away from
 * whatever the block stands on.
 */
public class Rotation4ValueBox extends ValueBoxTransform {
    private final Vec3 point;
    private final float scale;

    /** @param point the box's centre in the floor frame, in sixteenths */
    public Rotation4ValueBox(Vec3 point, float scale) {
        this.point = point;
        this.scale = scale;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        if(!state.hasProperty(BlockStateProperties.FACING))
            return null;
        var facing = state.getValue(BlockStateProperties.FACING);
        int rotation = state.hasProperty(Rotation4ElectricBlock.ROTATION) ? state.getValue(Rotation4ElectricBlock.ROTATION) : 0;
        var p = Rotation4Shapes.transform(facing, rotation, new double[] {point.x / 16, point.y / 16, point.z / 16});
        return new Vec3(p[0], p[1], p[2]);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        if(!state.hasProperty(BlockStateProperties.FACING))
            return;
        // The box looks out of the block's top, which is away from what it stands on.
        Direction side = state.getValue(BlockStateProperties.FACING).getOpposite();
        float yRot = AngleHelper.horizontalAngle(side) + 180;
        float xRot = side == Direction.UP ? 90 : side == Direction.DOWN ? 270 : 0;
        ms.mulPose(Axis.YP.rotationDegrees(yRot));
        ms.mulPose(Axis.XP.rotationDegrees(xRot));
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        var offset = getLocalOffset(level, pos, state);
        return offset != null && localHit.distanceTo(offset) < scale / 2;
    }

    @Override
    public float getScale() {
        return scale;
    }
}
