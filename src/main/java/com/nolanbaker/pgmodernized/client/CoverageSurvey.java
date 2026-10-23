package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.radio.AntennaBlockEntity;
import com.nolanbaker.pgmodernized.radio.AntennaKind;
import com.nolanbaker.pgmodernized.radio.RadioMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The scope's survey: sneak-click an antenna with the Alignment Scope and its coverage is drawn in the
 * world for a minute, from wherever the player goes. A sector is a cage of lines along the edges of
 * its beam, its aim and its 45° tilt limits, with arcs at each quarter of its reach; a dish is its
 * narrow cone; an omni is a ring at its reach. The geometry is cached when the antenna is in view so
 * the drawing survives walking out to the edge, where the antenna's chunk is long unloaded.
 * Client only, on the game event bus.
 */
public final class CoverageSurvey {
    private static final int TICKS = 20 * 60;
    private static final double TILT = 45;
    private static final double STEP = 5;

    @Nullable
    private static BlockPos target;
    private static long until;
    @Nullable
    private static Vec3 origin;
    private static AntennaKind kind = AntennaKind.OMNI;
    private static double azimuth, elevation, beamwidth, reach;

    private CoverageSurvey() {}

    /** Starts, or restarts, the survey of the antenna at {@code pos}. */
    public static void start(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if(level == null)
            return;
        if(!pos.equals(target))
            origin = null;
        target = pos;
        until = level.getGameTime() + TICKS;
        refresh();
    }

    public static void clear() {
        target = null;
        origin = null;
    }

    /** Re-reads the antenna while it is loaded; keeps the last reading when it is not. */
    private static void refresh() {
        var level = Minecraft.getInstance().level;
        if(level == null || target == null)
            return;
        if(!level.isLoaded(target))
            return;
        if(!(level.getBlockEntity(target) instanceof AntennaBlockEntity antenna)) {
            clear();
            return;
        }
        origin = antenna.livePosition();
        kind = antenna.kind();
        azimuth = antenna.azimuth();
        elevation = antenna.elevation();
        beamwidth = antenna.beamwidth();
        reach = antenna.reach();
        if(reach <= 0) {
            // The radio is down or not yet heard from: draw what it will cover once it is up.
            reach = switch(kind) {
                case DISH -> PgmConfig.RADIO_DISH_RANGE.get();
                case SECTOR -> PgmConfig.RADIO_SECTOR_RANGE.get() * RadioMath.beamGain(beamwidth);
                case OMNI -> PgmConfig.RADIO_CELL_RANGE.get();
            };
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || target == null)
            return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if(level == null || level.getGameTime() > until) {
            clear();
            return;
        }
        refresh();
        if(origin == null)
            return;
        var poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        var pose = poseStack.last();
        switch(kind) {
            case SECTOR -> drawSector(buffer, pose);
            case DISH -> drawDish(buffer, pose);
            case OMNI -> drawOmni(buffer, pose);
        }
        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    // ---- shapes, all relative to the antenna ----

    private static void drawSector(VertexConsumer buffer, PoseStack.Pose pose) {
        double half = beamwidth / 2;
        double[] azimuths = {azimuth - half, azimuth, azimuth + half};
        double[] tilts = {elevation - TILT, elevation, elevation + TILT};
        // The edges of the cage: nine rays out to the reach, the aim itself bright.
        for(double az : azimuths)
            for(double el : tilts) {
                boolean aim = az == azimuth && el == elevation;
                line(buffer, pose, Vec3.ZERO, RadioMath.aim(az, el).scale(reach), aim ? 1f : 0.3f, aim ? 0.95f : 0.85f, aim ? 0.3f : 1f, aim ? 1f : 0.8f);
            }
        for(int q = 1; q <= 4; ++q) {
            double d = reach * q / 4;
            float alpha = q == 4 ? 0.9f : 0.45f;
            // Horizontal arcs across the beam at each tilt limit and at the aim.
            for(double el : tilts)
                arcAzimuth(buffer, pose, azimuth - half, azimuth + half, el, d, alpha);
            // Vertical arcs up the two beam edges.
            arcElevation(buffer, pose, azimuth - half, elevation - TILT, elevation + TILT, d, alpha);
            arcElevation(buffer, pose, azimuth + half, elevation - TILT, elevation + TILT, d, alpha);
        }
    }

    private static void drawDish(VertexConsumer buffer, PoseStack.Pose pose) {
        double beam = PgmConfig.RADIO_DISH_BEAM.get();
        var forward = RadioMath.aim(azimuth, elevation);
        var right = RadioMath.aim(azimuth + 90, 0);
        var up = right.cross(forward).normalize();
        line(buffer, pose, Vec3.ZERO, forward.scale(reach), 1f, 0.95f, 0.3f, 1f);
        int spokes = 8, segments = 24;
        double sin = Math.sin(Math.toRadians(beam)), cos = Math.cos(Math.toRadians(beam));
        for(int i = 0; i < spokes; ++i) {
            double t = 2 * Math.PI * i / spokes;
            var dir = forward.scale(cos).add(right.scale(Math.cos(t) * sin)).add(up.scale(Math.sin(t) * sin));
            line(buffer, pose, Vec3.ZERO, dir.scale(reach), 0.4f, 1f, 0.5f, 0.8f);
        }
        for(int q = 1; q <= 4; ++q) {
            double d = reach * q / 4;
            float alpha = q == 4 ? 0.9f : 0.45f;
            Vec3 last = null;
            for(int i = 0; i <= segments; ++i) {
                double t = 2 * Math.PI * i / segments;
                var p = forward.scale(cos).add(right.scale(Math.cos(t) * sin)).add(up.scale(Math.sin(t) * sin)).scale(d);
                if(last != null)
                    line(buffer, pose, last, p, 0.4f, 1f, 0.5f, alpha);
                last = p;
            }
        }
    }

    private static void drawOmni(VertexConsumer buffer, PoseStack.Pose pose) {
        for(int i = 0; i < 8; ++i)
            line(buffer, pose, Vec3.ZERO, RadioMath.aim(i * 45, 0).scale(reach), 1f, 0.7f, 0.3f, 0.6f);
        arcAzimuth(buffer, pose, 0, 360, 0, reach / 2, 0.45f);
        arcAzimuth(buffer, pose, 0, 360, 0, reach, 0.9f);
    }

    private static void arcAzimuth(VertexConsumer buffer, PoseStack.Pose pose, double from, double to, double el, double d, float alpha) {
        int steps = Math.max(1, (int) Math.ceil((to - from) / STEP));
        Vec3 last = null;
        for(int i = 0; i <= steps; ++i) {
            var p = RadioMath.aim(from + (to - from) * i / steps, el).scale(d);
            if(last != null)
                line(buffer, pose, last, p, 0.3f, 0.85f, 1f, alpha);
            last = p;
        }
    }

    private static void arcElevation(VertexConsumer buffer, PoseStack.Pose pose, double az, double from, double to, double d, float alpha) {
        int steps = Math.max(1, (int) Math.ceil((to - from) / STEP));
        Vec3 last = null;
        for(int i = 0; i <= steps; ++i) {
            var p = RadioMath.aim(az, from + (to - from) * i / steps).scale(d);
            if(last != null)
                line(buffer, pose, last, p, 0.3f, 0.85f, 1f, alpha);
            last = p;
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float r, float g, float bl, float alpha) {
        var n = b.subtract(a).normalize();
        buffer.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        buffer.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
