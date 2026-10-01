package com.nolanbaker.pgmodernized.rack;

import com.nolanbaker.pgmodernized.PgmConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The pinion on a Sable body. Every physics step it looks past each of its four rim sides for a
 * rack in the world whose bar runs the way the rim would roll, and pushes the body along that
 * bar until the body's speed at the contact point matches the rim speed. A stopped pinion holds
 * the body in place on the rack, and the rack's flanges keep the pinion from drifting sideways.
 * <p>
 * Sable takes impulses in the body's own frame: the point in plot coordinates, the impulse as a
 * body-local vector (its arrow and explosion hooks convert world vectors with the inverse pose
 * before applying them), and its point-velocity helper wants the plot point too, so everything
 * here is worked out in that frame.
 */
public class SablePinionBlockEntity extends PinionBlockEntity implements BlockEntitySubLevelActor {
    public SablePinionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle body, double dt) {
        var level = subLevel.getLevel();
        if(level == null || isRemoved() || dt <= 0)
            return;
        var state = getBlockState();
        if(!(state.getBlock() instanceof PinionBlock))
            return;
        var axis = state.getValue(PinionBlock.AXIS);
        var pose = subLevel.logicalPose();
        var massData = subLevel.getMassTracker();
        if(massData == null || massData.isInvalid())
            return;
        double mass = massData.getMass();
        double radius = pitchRadius();
        var axisDir = Vec3.atLowerCornerOf(Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal());
        double rimSpeed = getSpeed() * Math.PI * 2 / 60 * radius * (PgmConfig.PINION_INVERT.get() ? -1 : 1);

        boolean found = false;
        for(var side : Direction.values()) {
            if(side.getAxis() == axis)
                continue;
            var sideDir = Vec3.atLowerCornerOf(side.getNormal());
            // The rack has to be a world block in the cell the rim faces.
            var cellWorld = pose.transformPosition(Vec3.atCenterOf(worldPosition.relative(side)));
            var rackState = level.getBlockState(BlockPos.containing(cellWorld));
            if(!RackBlock.isRack(rackState))
                continue;
            if(dominantDirection(pose.transformNormal(sideDir)) != rackState.getValue(RackBlock.FACING).getOpposite())
                continue;
            // Rolling direction in the body frame: the rim surface at the contact moves along axis x side,
            // so the body rolls the other way. Its world direction must follow the rack's bar.
            var travelLocal = axisDir.cross(sideDir).scale(-Math.signum(rimSpeed == 0 ? 1 : rimSpeed));
            var travelWorld = pose.transformNormal(travelLocal);
            if(dominantAxis(travelWorld) != rackState.getValue(RackBlock.AXIS))
                continue;
            found = true;

            var contactLocal = Vec3.atCenterOf(worldPosition).add(sideDir.scale(radius));
            var currentLocal = pose.transformNormalInverse(SableCompanion.INSTANCE.getVelocity(level, subLevel, contactLocal));
            var along = travelLocal.normalize();
            double want = Math.abs(rimSpeed);
            double have = currentLocal.dot(along);
            double error = want - have;
            double gain = PgmConfig.PINION_GAIN.get();
            double cap = mass * PgmConfig.PINION_MAX_ACCELERATION.get() * dt;
            double push = clamp(error * mass * gain, cap);
            body.applyImpulseAtPoint(contactLocal, along.scale(push));

            // The flanges: take out any drift along the shaft axis at the contact.
            double drift = currentLocal.dot(axisDir);
            double hold = clamp(-drift * mass * gain * 0.5, cap);
            body.applyImpulseAtPoint(contactLocal, axisDir.scale(hold));

            travel = (float) have;
            break;
        }
        engaged = found;
        if(!found)
            travel = 0;
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static Direction dominantDirection(Vec3 v) {
        var axis = dominantAxis(v);
        double along = axis.choose(v.x, v.y, v.z);
        return Direction.get(along >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis);
    }

    private static Direction.Axis dominantAxis(Vec3 v) {
        double x = Math.abs(v.x), y = Math.abs(v.y), z = Math.abs(v.z);
        if(x >= y && x >= z)
            return Direction.Axis.X;
        return y >= z ? Direction.Axis.Y : Direction.Axis.Z;
    }
}
