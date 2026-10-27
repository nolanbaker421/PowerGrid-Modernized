package com.nolanbaker.pgmodernized.client;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nolanbaker.pgmodernized.util.BodyRider;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireRenderer;

/**
 * Power Grid's block wire renderer, for runs that may ride a Sable body. A run keeps its segments
 * in the body's own frame and its entity at the body's current position for its origin, so on a
 * body the segments are drawn turned by the body's render orientation and pinned to where the
 * body's render pose puts the origin this frame, not where the entity's tick-rate position was.
 * A run on a turned body never fits its own axis-aligned culling box, so it is always drawn.
 */
public class BodyWireRenderer extends BlockWireRenderer {
    public BodyWireRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Nullable
    private static ClientSubLevelAccess bodyOf(BlockWireEntity entity) {
        if(!(entity instanceof BodyRider rider))
            return null;
        var origin = rider.ride().origin();
        return origin == null ? null : SableCompanion.INSTANCE.getContainingClient(origin);
    }

    @Override
    public void render(BlockWireEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        var body = bodyOf(entity);
        var origin = entity instanceof BodyRider rider ? rider.ride().origin() : null;
        if(body == null || origin == null) {
            draw(entity, yaw, partialTicks, poseStack, buffer, light);
            return;
        }
        var pose = body.renderPose(partialTicks);
        var where = pose.transformPosition(origin);
        var drawn = entity.getPosition(partialTicks);
        var q = pose.orientation();
        poseStack.pushPose();
        poseStack.translate(where.x - drawn.x, where.y - drawn.y, where.z - drawn.z);
        poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        draw(entity, yaw, partialTicks, poseStack, buffer, light);
        poseStack.popPose();
    }

    /** How short a sideways jog can be and still be drawn as one smooth offset instead of two elbows, in pixels. */
    private static final int OFFSET_MAX_PX = 4;
    /** A coupling every so many blocks of tube: EMT comes in ten-foot sticks. */
    private static final double COUPLING_EVERY = 3;
    /** Short legs an offset may run through: sideways in two directions and an inline step between. */
    private static final int OFFSET_MAX_LEGS = 4;
    /** A leg this short between two legs square to each other and to it is a nudged corner, not a leg: one bend absorbs it. */
    private static final int ABSORB_MAX_PX = 2;

    /**
     * Conduit is drawn here rather than by Power Grid's loop, which tiles its banded wire texture
     * along every straight and runs each one half a tube past its ends. Here a straight is an
     * octagonal tube drawn exactly from where it starts to where it stops, every corner with room
     * for it gets a swept elbow between the shortened legs, a corner without room is a plain
     * overlapped joint, and a short sideways jog between two legs the same way is drawn as one
     * smooth offset. The first and last straights start and end on the exact centre of their
     * knockouts, which Power Grid's pixel-centre snapping can miss by half a pixel. Nipples and
     * non-conduit block wires still go through Power Grid's renderer.
     */
    private void draw(BlockWireEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if(!(entity instanceof ConduitRunEntity run) || run.isNipple() || run.segments.isEmpty()) {
            super.render(entity, yaw, partialTicks, poseStack, buffer, light);
            return;
        }
        var consumer = TubeDraw.buffer(buffer);
        var pose = poseStack.last();
        var entry = run.getWireEntry();
        float t = entry != null ? entry.wireThickness() : run.size().tubeThickness();
        float h = t / 2;
        int rgb = run.getColor() & 0xFFFFFF;
        var segs = run.segments;
        int n = segs.size();
        if(run.isDirect()) {
            // One straight piece from knockout to knockout, a pixel into each, whatever the legs say.
            boolean body = bodyOf(run) != null;
            var endPos = Vec3.ZERO;
            for(var seg : segs)
                endPos = endPos.add(seg.vector());
            var from = body ? Vec3.ZERO : exactNudge(run, run.getEndpoint1(), Vec3.ZERO);
            var to = endPos.add(body ? Vec3.ZERO : exactNudge(run, run.getEndpoint2(), endPos));
            var dir = to.subtract(from);
            if(dir.lengthSqr() > 1e-8) {
                double len = dir.length();
                dir = dir.normalize();
                int light2 = brightest(run, run.position().add(from.add(to).scale(0.5)));
                TubeDraw.straight(pose, consumer, light2, rgb, from.subtract(dir.scale(1 / 16.0)), to.add(dir.scale(1 / 16.0)), t);
                couplings(pose, consumer, light2, rgb, from, dir, 0, len, t);
            }
            return;
        }
        // Bend radius at each corner: one and a half tubes, never under two pixels nor over six, nor more than half of either leg.
        float wanted = Math.max(2 / 16f, Math.min(6 / 16f, 1.5f * t));
        // Diagonals set with the bender: a straight tube from a point on one leg to a point on a
        // later leg, the legs between it not drawn at all; offsets and absorbed corners keep out.
        var diagAt = new ConduitRunEntity.Diagonal[n];
        boolean[] inDiag = new boolean[n];
        for(var d : run.diagonals()) {
            if(d.legA() < 0 || d.legB() >= n || d.legA() >= d.legB() || diagAt[d.legA()] != null)
                continue;
            boolean clear = true;
            for(int k = d.legA(); k <= d.legB(); ++k)
                clear &= !inDiag[k];
            if(!clear)
                continue;
            diagAt[d.legA()] = d;
            for(int k = d.legA(); k <= d.legB(); ++k)
                inDiag[k] = true;
        }
        float[] radii = new float[n];
        for(int i = 0; i + 1 < n; ++i) {
            var a = segs.get(i);
            var b = segs.get(i + 1);
            // A bend needs a real corner (not a reversal) and room: under a pixel it stays a sharp joint.
            if(a.direction != b.direction && a.direction != b.direction.getOpposite()) {
                float r = Math.min(wanted, Math.min(a.length() / 2, b.length() / 2));
                radii[i] = r >= 1 / 16f ? r : 0;
            }
        }
        // A bend under a pixel and a half reads as a notch; such a corner is filled with a cube instead.
        for(int i = 0; i < n; ++i)
            if(radii[i] > 0 && radii[i] < 1.5f / 16)
                radii[i] = 0;
        // Offsets: two legs the same way with a few short legs between, sideways or inline, become one
        // smooth S: a diagonal that advances two jogs for one jog across, its kinks a setback short of
        // the legs. Knockouts a few pixels apart in two or three directions come out as one offset.
        int[] offsetSpan = new int[n];
        double[] offsetCut = new double[n], startCut = new double[n];
        for(int i = 0; i + 2 < n; ++i) {
            if(inDiag[i])
                continue;
            var a = segs.get(i);
            var along = Vec3.atLowerCornerOf(a.direction.getNormal());
            int span = 0;
            var jog = Vec3.ZERO;
            boolean ok = false;
            for(int k = i + 1; k < n && span < OFFSET_MAX_LEGS; ++k) {
                var leg = segs.get(k);
                if(inDiag[k])
                    break;
                if(leg.direction == a.direction) {
                    ok = span > 0;
                    break;
                }
                if(leg.direction == a.direction.getOpposite() || leg.gridLength > OFFSET_MAX_PX)
                    break;
                jog = jog.add(leg.vector());
                ++span;
            }
            if(!ok)
                continue;
            var across = jog.subtract(along.scale(jog.dot(along)));
            double m = across.length();
            if(m < 0.5 / 16)
                continue;
            var c = segs.get(i + span + 1);
            var diagonal = along.scale(2 * m).add(jog).normalize();
            double cut = m + TubeDraw.setback(wanted, along, diagonal);
            // The legs either side must hold the cut; a bend at their far ends gives way to it,
            // down to a sharp joint, since a kink drawn as two sharp joints looks worse.
            double roomA = a.length() - cut - startCut[i], roomC = c.length() - cut;
            if(roomA < -1e-6 || roomC < -1e-6)
                continue;
            if(i > 0 && radii[i - 1] > roomA)
                radii[i - 1] = roomA >= 1.5f / 16 ? (float) roomA : 0;
            if(radii[i + span + 1] > roomC)
                radii[i + span + 1] = roomC >= 1.5f / 16 ? (float) roomC : 0;
            offsetSpan[i] = span;
            offsetCut[i] = cut;
            startCut[i + span + 1] = cut;
            i += span;
        }
        // A leg of a pixel or two between two long legs square to each other and to it is a corner
        // the grid has pushed sideways, not a leg of its own: its two corners, too short for bends,
        // would be a pair of sharp joints. It is absorbed, and the legs either side meet in one bend
        // at its middle.
        boolean[] inOffset = new boolean[n], absorbed = new boolean[n];
        for(int i = 0; i < n; ++i)
            if(offsetSpan[i] > 0)
                for(int k = 0; k <= offsetSpan[i] + 1 && i + k < n; ++k)
                    inOffset[i + k] = true;
        for(int i = 1; i + 1 < n; ++i) {
            var a = segs.get(i - 1);
            var b = segs.get(i);
            var c = segs.get(i + 1);
            if(b.gridLength > ABSORB_MAX_PX || inOffset[i - 1] || inOffset[i] || inOffset[i + 1] || absorbed[i - 1])
                continue;
            if(inDiag[i - 1] || inDiag[i] || inDiag[i + 1])
                continue;
            if(a.gridLength <= ABSORB_MAX_PX || c.gridLength <= ABSORB_MAX_PX)
                continue;
            if(a.direction.getAxis() == c.direction.getAxis() || b.direction.getAxis() == a.direction.getAxis() || b.direction.getAxis() == c.direction.getAxis())
                continue;
            absorbed[i] = true;
            float r = Math.min(wanted, Math.min(a.length() / 2, c.length() / 2));
            radii[i - 1] = r >= 1.5f / 16 ? r : 0;
            radii[i] = 0;
        }
        // What each straight gives up at each end: a bend radius, an offset's setback, or a negative cut
        // (an overlap of half a tube) where two legs just meet in a cube.
        double[] cutStart = new double[n], cutEnd = new double[n];
        boolean[] skip = new boolean[n];
        for(int i = 0; i + 1 < n; ++i) {
            if(absorbed[i]) {
                skip[i] = true;
                continue;
            }
            if(diagAt[i] != null) {
                // The legs between are not drawn; this leg's end and the far leg's start are cut
                // once the drawn corners are known. The loop goes on from the far leg.
                for(int k = i + 1; k < diagAt[i].legB(); ++k)
                    skip[k] = true;
                i = diagAt[i].legB() - 1;
                continue;
            }
            int j = nextDrawn(absorbed, i, n);
            if(offsetSpan[i] > 0) {
                cutEnd[i] = offsetCut[i];
                for(int k = 1; k <= offsetSpan[i]; ++k)
                    skip[i + k] = true;
                cutStart[i + offsetSpan[i] + 1] = offsetCut[i];
                i += offsetSpan[i];
            } else if(j >= n) {
                break;
            } else if(radii[i] > 0) {
                cutEnd[i] = radii[i];
                cutStart[j] = radii[i];
            } else {
                cutEnd[i] = -h;
                cutStart[j] = -h;
            }
        }
        boolean onBody = bodyOf(run) != null;
        // The corners as laid, on Power Grid's pixel grid.
        var raw = new Vec3[n + 1];
        raw[0] = Vec3.ZERO;
        for(int i = 0; i < n; ++i)
            raw[i + 1] = raw[i].add(segs.get(i).vector());
        // The corners as drawn. The ends sit on their knockouts' true centres, which the grid can
        // miss by half a pixel; a leg through a strut's clamp lies in its slot, which the grid can
        // miss by most of one; and each corner between is where the two legs' lines, so shifted,
        // meet, so that bends, offsets and couplings all follow the same line as the straights.
        var startNudge = onBody ? Vec3.ZERO : exactNudge(run, run.getEndpoint1(), raw[0]);
        var endNudge = onBody ? Vec3.ZERO : exactNudge(run, run.getEndpoint2(), raw[n]);
        var side = new Vec3[n];
        for(int i = 0; i < n; ++i)
            side[i] = onBody || skip[i] ? Vec3.ZERO : strutNudge(run, raw[i], raw[i + 1], segs.get(i).direction, t, i == 0 ? startNudge : Vec3.ZERO);
        // A knockout's centring moves its whole stub, not just the stub's end: a stub is four pixels
        // long and a pixel's correction over four would read as a slant, while the long leg before
        // it takes that pixel without showing it.
        if(side[0].lengthSqr() == 0)
            side[0] = square(startNudge, segs.get(0).direction);
        if(side[n - 1].lengthSqr() == 0)
            side[n - 1] = square(endNudge, segs.get(n - 1).direction);
        var v = new Vec3[n + 1];
        v[0] = raw[0].add(startNudge.lengthSqr() > 0 ? startNudge : side[0]);
        v[n] = raw[n].add(endNudge.lengthSqr() > 0 ? endNudge : side[n - 1]);
        for(int k = 1; k < n; ++k)
            v[k] = raw[k].add(cornerShift(side[k - 1], side[k], segs.get(k - 1).direction, segs.get(k).direction));
        for(int i = 1; i + 1 < n; ++i) {
            if(!absorbed[i])
                continue;
            var mid = v[i].add(v[i + 1]).scale(0.5);
            v[i] = mid;
            v[i + 1] = mid;
        }
        // Each diagonal from its drawn points: a bend out of the first leg, the straight, a bend
        // into the far leg, with as much radius as the legs leave room for, else sharp joints.
        var diagDraw = new DiagDraw[n];
        for(int a = 0; a < n; ++a) {
            var d = diagAt[a];
            if(d == null)
                continue;
            int b = d.legB();
            var alongA = Vec3.atLowerCornerOf(segs.get(a).direction.getNormal());
            var alongB = Vec3.atLowerCornerOf(segs.get(b).direction.getNormal());
            var p = v[a].add(alongA.scale(d.pxA() / 16.0));
            var q = v[b].add(alongB.scale(d.pxB() / 16.0));
            var u = q.subtract(p);
            double length = u.length();
            if(length < 0.5 / 16)
                continue;
            u = u.scale(1 / length);
            // The bend needs its setback, not its whole radius, of straight leg at each end; a bend
            // at the leg's far end gives way to it, down to a sharp joint, as for an offset.
            double roomA = d.pxA() / 16.0, roomB = (segs.get(b).gridLength - d.pxB()) / 16.0;
            float r = (float) Math.min(wanted, length / 2);
            double dA = TubeDraw.setback(r, alongA, u), dB = TubeDraw.setback(r, u, alongB);
            if(dA > roomA && dA > 1e-9)
                r = (float) Math.min(r, r * roomA / dA);
            if(dB > roomB && dB > 1e-9)
                r = (float) Math.min(r, r * roomB / dB);
            if(r < 1.5f / 16)
                r = 0;
            dA = r > 0 ? TubeDraw.setback(r, alongA, u) : 0;
            dB = r > 0 ? TubeDraw.setback(r, u, alongB) : 0;
            if(a > 0 && !inOffset[a] && cutStart[a] > roomA - dA + 1e-9) {
                float give = (float) Math.max(0, roomA - dA);
                radii[a - 1] = give >= 1.5f / 16 ? give : 0;
                cutEnd[a - 1] = radii[a - 1] > 0 ? radii[a - 1] : -h;
                cutStart[a] = cutEnd[a - 1];
            }
            if(b + 1 < n && !inOffset[b] && cutEnd[b] > roomB - dB + 1e-9) {
                float give = (float) Math.max(0, roomB - dB);
                radii[b] = give >= 1.5f / 16 ? give : 0;
                cutEnd[b] = radii[b] > 0 ? radii[b] : -h;
                cutStart[b + 1] = cutEnd[b];
            }
            cutEnd[a] = (segs.get(a).gridLength - d.pxA()) / 16.0 + dA;
            cutStart[b] = d.pxB() / 16.0 + dB;
            diagDraw[a] = new DiagDraw(p, q, u, alongA, alongB, r, dA, dB);
        }
        double travelled = 0;
        for(int i = 0; i < n; ++i) {
            var seg = segs.get(i);
            var dir = seg.direction;
            var along = Vec3.atLowerCornerOf(dir.getNormal());
            var pos = v[i];
            var end = v[i + 1];
            if(!skip[i]) {
                // The first and last reach a pixel into their knockouts.
                var from = i == 0 ? pos.relative(dir, -1 / 16.0) : pos.relative(dir, cutStart[i]);
                var to = i == n - 1 ? end.relative(dir, 1 / 16.0) : end.relative(dir, -cutEnd[i]);
                int segLight = brightest(run, run.position().add(from.add(to).scale(0.5)));
                if(to.subtract(from).dot(along) > 1e-4) {
                    TubeDraw.straight(pose, consumer, segLight, rgb, from, to, t);
                    couplings(pose, consumer, segLight, rgb, pos, along, travelled, seg.length(), t, cutStart[i], cutEnd[i]);
                }
                if(diagDraw[i] != null) {
                    var dd = diagDraw[i];
                    int diagLight = brightest(run, run.position().add(dd.p.add(dd.q).scale(0.5)));
                    if(dd.r > 0) {
                        // One turn of the octagon from leg to leg, so neither bend steps against the diagonal.
                        var flat = TubeDraw.bend(pose, consumer, diagLight, rgb, dd.p, dd.alongA, dd.u, dd.r, t, null);
                        TubeDraw.straight(pose, consumer, diagLight, rgb, dd.p.add(dd.u.scale(dd.dA)), dd.q.subtract(dd.u.scale(dd.dB)), t, flat);
                        TubeDraw.bend(pose, consumer, diagLight, rgb, dd.q, dd.u, dd.alongB, dd.r, t, flat);
                    } else {
                        TubeDraw.joint(pose, consumer, diagLight, rgb, dd.p, t);
                        TubeDraw.straight(pose, consumer, diagLight, rgb, dd.p, dd.q, t);
                        TubeDraw.joint(pose, consumer, diagLight, rgb, dd.q, t);
                    }
                } else if(offsetSpan[i] > 0 && across(v[i + offsetSpan[i] + 1].subtract(end), along).length() < 0.25 / 16) {
                    // A jog the knockout's or a slot's centring has cancelled: one straight carries
                    // through to the next leg, where the S would have been.
                    int j = i + offsetSpan[i] + 1;
                    var nextFrom = v[j].relative(segs.get(j).direction, cutStart[j]);
                    TubeDraw.straight(pose, consumer, segLight, rgb, to, nextFrom, t);
                } else if(offsetSpan[i] > 0) {
                    var c1 = end;
                    var c2 = v[i + offsetSpan[i] + 1];
                    double m = c2.subtract(c1).length();
                    var p = c1.subtract(along.scale(m));
                    var q = c2.add(along.scale(m));
                    var diagonal = q.subtract(p).normalize();
                    double d = TubeDraw.setback(wanted, along, diagonal);
                    // The S keeps one turn of the octagon from end to end: a jog in two directions
                    // at once leans the kinks' rings off the axes, and a straight drawn square to
                    // the axes between them would step against both.
                    var flat = TubeDraw.bend(pose, consumer, segLight, rgb, p, along, diagonal, wanted, t, null);
                    TubeDraw.straight(pose, consumer, segLight, rgb, p.add(diagonal.scale(d)), q.subtract(diagonal.scale(d)), t, flat);
                    TubeDraw.bend(pose, consumer, segLight, rgb, q, diagonal, along, wanted, t, flat);
                } else if(nextDrawn(absorbed, i, n) < n && radii[i] > 0) {
                    TubeDraw.bend(pose, consumer, segLight, rgb, end, along, Vec3.atLowerCornerOf(segs.get(nextDrawn(absorbed, i, n)).direction.getNormal()), radii[i], t);
                } else if(nextDrawn(absorbed, i, n) < n) {
                    TubeDraw.joint(pose, consumer, segLight, rgb, end, t);
                }
            }
            travelled += seg.length();
        }
    }

    /** The part of a shift that is square to a leg's direction: what moves the leg's line rather than its end. */
    private static Vec3 square(Vec3 shift, Direction dir) {
        return across(shift, Vec3.atLowerCornerOf(dir.getNormal()));
    }

    /** The part of a vector square to a unit direction. */
    private static Vec3 across(Vec3 vector, Vec3 along) {
        return vector.subtract(along.scale(vector.dot(along)));
    }

    /** A diagonal ready to draw: its ends, its direction, the legs' directions, the bend radius and the setbacks. */
    private record DiagDraw(Vec3 p, Vec3 q, Vec3 u, Vec3 alongA, Vec3 alongB, float r, double dA, double dB) {}

    /** The index of the leg drawn after leg {@code i}, past any absorbed ones; {@code n} when there is none. */
    private static int nextDrawn(boolean[] absorbed, int i, int n) {
        int j = i + 1;
        while(j < n && absorbed[j])
            ++j;
        return j;
    }

    /**
     * Where two legs meet once each has been shifted sideways: the leg before, shifted by {@code a}
     * square to its direction {@code da}, and the leg after, shifted by {@code b} square to
     * {@code db}. Along each leg's own direction the other leg's shift says where the corner is;
     * square to both, the legs ought to agree, and the first with an opinion wins.
     */
    private static Vec3 cornerShift(Vec3 a, Vec3 b, Direction da, Direction db) {
        var ua = Vec3.atLowerCornerOf(da.getNormal());
        var ub = Vec3.atLowerCornerOf(db.getNormal());
        if(da.getAxis() == db.getAxis())
            return a.lengthSqr() > 0 ? a : b;
        var p = ua.cross(ub);
        double shared = Math.abs(a.dot(p)) > 1e-9 ? a.dot(p) : b.dot(p);
        return ua.scale(b.dot(ua)).add(ub.scale(a.dot(ub))).add(p.scale(shared));
    }

    private static void couplings(PoseStack.Pose pose, VertexConsumer consumer, int light, int rgb, Vec3 segStart, Vec3 along, double travelled, double length, float t) {
        couplings(pose, consumer, light, rgb, segStart, along, travelled, length, t, 0, 0);
    }

    /**
     * A coupling ring every few blocks along the run, counted from its start: a ring a quarter
     * pixel proud and two pixels long, a shade darker, only where it lands on a straight well
     * clear of the bends.
     */
    private static void couplings(PoseStack.Pose pose, VertexConsumer consumer, int light, int rgb, Vec3 segStart, Vec3 along,
                                  double travelled, double length, float t, double cutStart, double cutEnd) {
        int r = (int) ((rgb >> 16 & 0xFF) * 0.93), g = (int) ((rgb >> 8 & 0xFF) * 0.93), b = (int) ((rgb & 0xFF) * 0.93);
        int shade = r << 16 | g << 8 | b;
        double margin = 6 / 16.0;
        for(double at = Math.ceil(travelled / COUPLING_EVERY) * COUPLING_EVERY; at <= travelled + length; at += COUPLING_EVERY) {
            if(at <= 0)
                continue;
            double local = at - travelled;
            if(local < cutStart + margin || local > length - cutEnd - margin)
                continue;
            var centre = segStart.add(along.scale(local));
            TubeDraw.straight(pose, consumer, light, shade, centre.subtract(along.scale(1 / 16.0)), centre.add(along.scale(1 / 16.0)), t + 0.5f / 16);
        }
    }

    /**
     * How far the run's end sits from the true centre of its knockout, when it is on one: Power
     * Grid snaps run ends to pixel centres, and a knockout centred on a whole pixel is half a pixel
     * off that. Anything bigger than a pixel and a half is not that, and is left alone.
     */
    private static Vec3 exactNudge(ConduitRunEntity run, @Nullable IWireEndpoint endpoint, Vec3 runEnd) {
        if(!(endpoint instanceof BlockWireEndpoint knockout) || !knockout.isValid(run.level()))
            return Vec3.ZERO;
        var nudge = knockout.getExactPosition(run.level()).subtract(run.position().add(runEnd));
        return nudge.lengthSqr() <= (1.5 / 16) * (1.5 / 16) ? nudge : Vec3.ZERO;
    }

    /**
     * The sideways shift that draws a leg crossing a strut's rail in the slot it was clipped to:
     * the slots sit where a panel's knockouts do, a fraction of a pixel off the run's pixel grid.
     * Zero for a leg that crosses no strut, runs along one, or is not near a slot: within a pixel
     * of the leg as laid, or within half a pixel of a first leg already drawn from its knockout's
     * true centre, so a leg is never pulled one way at the knockout and the other at the strut.
     */
    private static Vec3 strutNudge(ConduitRunEntity run, Vec3 start, Vec3 end, Direction dir, float t, Vec3 startNudge) {
        var level = run.level();
        var origin = run.position();
        var leg = end.subtract(start);
        double len = leg.length();
        if(len < 1e-6)
            return Vec3.ZERO;
        var along = Vec3.atLowerCornerOf(dir.getNormal());
        BlockPos last = null;
        for(int k = 0; k * 0.5 <= len + 0.5; ++k) {
            var world = origin.add(start.add(leg.scale(Math.min(k * 0.5, len) / len)));
            var bp = BlockPos.containing(world);
            if(bp.equals(last))
                continue;
            last = bp;
            var state = level.getBlockState(bp);
            if(!(state.getBlock() instanceof com.nolanbaker.pgmodernized.conduit.StrutBlock))
                continue;
            var facing = state.getValue(com.nolanbaker.pgmodernized.conduit.StrutBlock.FACING);
            var axis = com.nolanbaker.pgmodernized.conduit.StrutBlock.axisFor(facing, state.getValue(com.nolanbaker.pgmodernized.conduit.StrutBlock.AXIS));
            if(dir.getAxis() == axis || dir.getAxis() == facing.getAxis())
                continue;
            var local = world.subtract(bp.getX(), bp.getY(), bp.getZ());
            var nudge = com.nolanbaker.pgmodernized.conduit.StrutBlock.clamp(state, local, t * 16 / 2).subtract(local);
            nudge = nudge.subtract(along.scale(nudge.dot(along)));
            // The seat off the mount is three pixels and the tube's radius, which the grid can miss
            // by a full half pixel (3/4" sits at 4.0); that part is judged on its own, and the part
            // along the rail against the slot, relative to a knockout's own centring on a first leg.
            var mountAxis = Vec3.atLowerCornerOf(facing.getNormal());
            double seat = nudge.dot(mountAxis);
            var slot = nudge.subtract(mountAxis.scale(seat));
            boolean fromKnockout = startNudge.lengthSqr() > 0;
            double limit = (fromKnockout ? 0.5 : 0.9) / 16;
            var reference = fromKnockout ? startNudge.subtract(mountAxis.scale(startNudge.dot(mountAxis))) : Vec3.ZERO;
            if(Math.abs(seat) <= 0.6 / 16 && slot.subtract(reference).lengthSqr() <= limit * limit + 1e-9)
                return nudge;
        }
        return Vec3.ZERO;
    }

    /**
     * The light for a piece of tube at that point: the brightest of its block and the six around it,
     * as Power Grid samples, since a run along a wall has its centre line inside the wall's block.
     */
    private static int brightest(ConduitRunEntity run, Vec3 at) {
        var level = run.level();
        var centre = BlockPos.containing(at);
        int best = LevelRenderer.getLightColor(level, centre);
        for(var d : Direction.values()) {
            int light = LevelRenderer.getLightColor(level, centre.relative(d));
            if((light & 0xFFFF) > (best & 0xFFFF) || (light >> 16) > (best >> 16))
                best = Math.max(best & 0xFFFF, light & 0xFFFF) | Math.max(best >> 16 & 0xFFFF, light >> 16 & 0xFFFF) << 16;
        }
        return best;
    }

    @Override
    public boolean shouldRender(BlockWireEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return bodyOf(entity) != null || super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
