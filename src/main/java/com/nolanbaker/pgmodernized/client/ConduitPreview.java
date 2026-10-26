package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.util.BodySpace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.conduit.ConduitConnection;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitSupply;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.Knockouts;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireRenderer;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.PlacementOverlay;

/**
 * In-world preview of the pending conduit run, like the Cat6 and Power Grid wire previews: the
 * route the run would take from the pending end to the block, run or knockout under the
 * crosshair, green where it can be laid and red where it cannot, with the conduit needed shown
 * on the overlay. Registered on the game event bus on the client only.
 */
public final class ConduitPreview {
    private static final int OK = 0x80AAFFAA;
    private static final int BAD = 0x80FFAAAA;

    private static boolean render;
    private static WireItemEntry item;
    private static Pair<BlockTrace.TraceState, BlockTrace.TraceResult> trace;
    private static Vec3 origin;
    /** The knockout stubs placement adds before and after the traced path. */
    private static List<BlockWireEntity.Point> prefix = List.of(), suffix = List.of();

    private ConduitPreview() {}

    @Nullable
    private static ItemStack usedStack(Player player) {
        var main = player.getMainHandItem();
        if(ConduitItem.isConduit(main) && ConduitConnection.has(main))
            return main;
        var off = player.getOffhandItem();
        if(ConduitItem.isConduit(off) && ConduitConnection.has(off))
            return off;
        return null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        render = false;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if(player == null || level == null)
            return;
        var stack = usedStack(player);
        if(stack == null)
            return;
        var endpoint = ConduitConnection.get(stack);
        if(endpoint == null)
            return;
        item = WireRegistry.forItem(level, ConduitSupply.item(stack));
        if(item == null)
            return;
        var target = mc.hitResult;
        if(target == null)
            return;
        if(target.getType() == HitResult.Type.ENTITY) {
            if(!(((EntityHitResult) target).getEntity() instanceof ConduitRunEntity))
                return;
        } else if(target.getType() != HitResult.Type.BLOCK) {
            return;
        }

        var currentPos = BodySpace.local(level, endpoint);
        Direction continueDir = null;
        if(endpoint instanceof BlockWireEntityEndpoint runEnd) {
            var entity = runEnd.getEntity(level);
            if(entity != null && !entity.segments.isEmpty()) {
                continueDir = runEnd.getEnd()
                        ? entity.segments.get(entity.segments.size() - 1).direction
                        : entity.segments.get(0).direction.getOpposite();
            }
        }

        var hitPoint = target.getLocation();
        ITerminalPlacement hitTerminal = null;
        BlockWireEntity.Point endStub = null;
        if(target instanceof BlockHitResult blockHit) {
            var pos = blockHit.getBlockPos();
            var local = hitPoint.subtract(pos.getX(), pos.getY(), pos.getZ());
            var electric = IElectric.getAt(level, pos);
            boolean onHub = false;
            if(electric != null && level.getBlockEntity(pos) instanceof ISpliceHost host) {
                var state = level.getBlockState(pos);
                int index = electric.terminalIndexAt(state, local);
                if(host.hubAt(index) >= 0) {
                    // A knockout: the run ends on the terminal itself.
                    hitTerminal = electric.terminal(state, index);
                    hitPoint = IElectric.getTerminalPos(level, pos, index);
                    onHub = true;
                    var entryDir = Knockouts.exit(level, new BlockWireEndpoint(pos, index));
                    if(entryDir != null) {
                        // Placement enters a knockout square, through a stub: trace to the stub's outer end.
                        hitPoint = BlockTrace.alignPosition(hitPoint).relative(entryDir, Knockouts.STUB_PX / 16.0);
                        hitTerminal = null;
                        endStub = new BlockWireEntity.Point(entryDir.getOpposite(), Knockouts.STUB_PX);
                    }
                }
            }
            if(!onHub)
                hitPoint = hitPoint.relative(blockHit.getDirection(), 1 / 32f);
        }
        if(currentPos.distanceTo(hitPoint) > 1000)
            return;

        var aligned = endpoint instanceof BlockWireEntityEndpoint ? currentPos : BlockTrace.alignPosition(currentPos);
        origin = aligned;
        var startStubs = new ArrayList<BlockWireEntity.Point>();
        if(endpoint instanceof BlockWireEndpoint hub) {
            // Placement leaves a knockout square, through a stub, and traces from the stub's end.
            var exit = Knockouts.exit(level, hub);
            if(exit != null) {
                startStubs.add(new BlockWireEntity.Point(exit, Knockouts.STUB_PX));
                aligned = aligned.relative(exit, Knockouts.STUB_PX / 16.0);
                continueDir = exit;
            }
        }
        trace = BlockTrace.findPathWithState(level, aligned, hitPoint, hitTerminal, continueDir);
        if(trace == null)
            return;
        prefix = startStubs;
        suffix = endStub != null ? List.of(endStub) : List.of();
        render = true;
        float length = 0;
        for(var p : prefix)
            length += p.length();
        for(var p : suffix)
            length += p.length();
        var points = trace.getSecond();
        if(points != null) {
            for(var p : points.points())
                length += p.length();
        }
        if(!player.isCreative()) {
            int required = Math.max(Math.round(length * item.itemsPerMeter()), 1);
            PlacementOverlay.setItemRequirement(ConduitSupply.item(stack), required, ConduitSupply.count(stack) >= required);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS || !render || trace == null || trace.getSecond() == null)
            return;
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null)
            return;
        var buffer = DefaultSuperRenderTypeBuffer.getInstance();
        var matrixStack = event.getPoseStack();
        var cameraPos = event.getCamera().getPosition();
        matrixStack.pushPose();
        matrixStack.translate(origin.x - cameraPos.x, origin.y - cameraPos.y, origin.z - cameraPos.z);
        var consumer = buffer.getBuffer(RenderType.entityTranslucent(item.texture()));
        var points = trace.getSecond();
        int color = points.reachedTarget() ? OK : BAD;
        var currentPos = Vec3.ZERO;
        var drawn = new ArrayList<>(prefix);
        drawn.addAll(points.points());
        drawn.addAll(suffix);
        for(var p : drawn) {
            BlockWireRenderer.renderSegment(matrixStack, consumer, LightTexture.FULL_BRIGHT, color,
                    currentPos, p.direction, item.wireThickness(), p.length(), 0);
            currentPos = currentPos.add(p.vector());
        }
        matrixStack.popPose();
        buffer.draw();
    }
}
