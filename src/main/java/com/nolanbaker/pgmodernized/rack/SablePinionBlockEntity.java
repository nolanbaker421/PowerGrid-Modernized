package com.nolanbaker.pgmodernized.rack;

import com.nolanbaker.pgmodernized.PgmConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

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
    /** Extra speed, in m/s, leaned in while the body keeps falling short of the rim speed between steps. */
    private double bias;
    private int lastSign;

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
        double rimSpeed = getSpeed() * Math.PI * 2 / 60 * radius * (PgmConfig.PINION_INVERT.get() != inverted ? -1 : 1);

        lastPhysics = level.getGameTime();
        boolean found = false;
        int why = REASON_NO_RACK;
        for(var side : Direction.values()) {
            if(side.getAxis() == axis)
                continue;
            var sideDir = Vec3.atLowerCornerOf(side.getNormal());
            // The rack is a block of the world, or of another body, in the cell the rim faces.
            var cellWorld = pose.transformPosition(Vec3.atCenterOf(worldPosition.relative(side)));
            var rackState = rackAt(level, subLevel, cellWorld);
            if(rackState == null)
                continue;
            if(dominantDirection(pose.transformNormal(sideDir)) != rackState.getValue(RackBlock.FACING).getOpposite()) {
                why = Math.max(why, REASON_FACING);
                continue;
            }
            // Rolling direction in the body frame: the rim surface at the contact moves along axis x side,
            // so the body rolls the other way. Its world direction must follow the rack's bar.
            var travelLocal = axisDir.cross(sideDir).scale(-Math.signum(rimSpeed == 0 ? 1 : rimSpeed));
            var travelWorld = pose.transformNormal(travelLocal);
            if(dominantAxis(travelWorld) != rackState.getValue(RackBlock.AXIS)) {
                why = Math.max(why, REASON_AXIS);
                continue;
            }
            found = true;

            var contactLocal = Vec3.atCenterOf(worldPosition).add(sideDir.scale(radius));
            var along = travelLocal.normalize();
            double want = Math.abs(rimSpeed);
            double have;
            if(PgmConfig.PINION_LOCK.get()) {
                // Teeth in a rack: the body moves at the rim speed and nowhere else. Set the body's own
                // velocity straight to that, which owes nothing to how heavy Sable thinks the body is,
                // then push through the centre of mass against whatever the guides take back between
                // steps, so the drive never rocks the body into them.
                var alongWorld = pose.transformNormal(along).normalize();
                var axisWorld = pose.transformNormal(axisDir).normalize();
                var linear = body.getLinearVelocity(new Vector3d());
                var velocity = new Vec3(linear.x, linear.y, linear.z);
                have = velocity.dot(alongWorld);
                double error = want - have;
                double drift = velocity.dot(axisWorld);
                // Whatever the guides take back between steps shows up as a steady shortfall at the
                // start of the next one. Lean into it, in speed units so it owes nothing to the mass
                // estimate, and never by more than half the rim speed.
                int sign = (int) Math.signum(rimSpeed);
                if(sign != lastSign || want == 0)
                    bias = 0;
                lastSign = sign;
                if(want > 0)
                    bias = clamp(bias + error * 0.3, 0.5 * want);
                var change = alongWorld.scale(error + bias).add(axisWorld.scale(-drift));
                body.addLinearAndAngularVelocity(new Vector3d(change.x, change.y, change.z), new Vector3d());
            } else {
                var currentLocal = pose.transformNormalInverse(SableCompanion.INSTANCE.getVelocity(level, subLevel, contactLocal));
                have = currentLocal.dot(along);
                double drift = currentLocal.dot(axisDir);
                double force = PgmConfig.PINION_FORCE.get();
                double gain = PgmConfig.PINION_GAIN.get() * force;
                double cap = mass * PgmConfig.PINION_MAX_ACCELERATION.get() * force * dt;
                body.applyImpulseAtPoint(contactLocal, along.scale(clamp((want - have) * mass * gain, cap)));
                body.applyImpulseAtPoint(contactLocal, axisDir.scale(clamp(-drift * mass * gain * 0.5, cap)));
            }
            travel = (float) have;
            break;
        }
        engaged = found;
        reason = why;
        if(!found) {
            travel = 0;
            bias = 0;
        }
    }

    /** The rack block at a world point: in the world, or on another body that is there; never our own body. */
    @Nullable
    private static BlockState rackAt(ServerLevel level, ServerSubLevel mine, Vec3 point) {
        var worldState = level.getBlockState(BlockPos.containing(point));
        if(RackBlock.isRack(worldState))
            return worldState;
        for(var other : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(new AABB(BlockPos.containing(point))))) {
            if(other.getUniqueId().equals(mine.getUniqueId()))
                continue;
            var plot = BlockPos.containing(other.logicalPose().transformPositionInverse(point));
            if(!level.isLoaded(plot))
                continue;
            var state = level.getBlockState(plot);
            if(RackBlock.isRack(state))
                return state;
        }
        return null;
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
