package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.network.Cat6BlockWireEntity;
import com.nolanbaker.pgmodernized.network.Cat6CableItem;
import com.nolanbaker.pgmodernized.network.Cat6Connection;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
import com.mojang.blaze3d.vertex.PoseStack;
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
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireRenderer;
import org.patryk3211.powergrid.electricity.wire.HangingWireRenderer;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.PlacementOverlay;

/**
 * In-world preview of the pending Cat6 connection, a port of Power Grid's wire preview that reads the
 * addon's own connection component. Green means the connection is possible, red means it is not.
 * Registered on the game event bus on the client only.
 */
public final class Cat6Preview {
    private static final int OK = 0x80AAFFAA;
    private static final int BAD = 0x80FFAAAA;

    private static int renderPath;
    private static WireItemEntry item;
    private static Pair<BlockTrace.TraceState, BlockTrace.TraceResult> trace;
    private static Vec3 pos1, pos2;
    private static int color;

    private Cat6Preview() {}

    @Nullable
    private static ItemStack usedStack(Player player) {
        var main = player.getMainHandItem();
        if(Cat6CableItem.isCat6(main) && Cat6Connection.has(main))
            return main;
        var off = player.getOffhandItem();
        if(Cat6CableItem.isCat6(off) && Cat6Connection.has(off))
            return off;
        return null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        renderPath = 0;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if(player == null || level == null)
            return;
        var stack = usedStack(player);
        if(stack == null)
            return;
        var endpoint = Cat6Connection.get(stack);
        if(endpoint == null)
            return;
        item = WireRegistry.forItem(level, stack.getItem());
        if(item == null)
            return;
        var target = mc.hitResult;
        if(target == null)
            return;
        if(target.getType() == HitResult.Type.ENTITY) {
            if(!(((EntityHitResult) target).getEntity() instanceof Cat6BlockWireEntity))
                return;
        } else if(target.getType() != HitResult.Type.BLOCK) {
            return;
        }

        var currentPos = endpoint.getExactPosition(level);
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
        boolean hitJack = false;
        if(target instanceof BlockHitResult blockHit) {
            var pos = blockHit.getBlockPos();
            var local = hitPoint.subtract(pos.getX(), pos.getY(), pos.getZ());
            if(level.getBlockEntity(pos) instanceof INetworkJack jack) {
                var electric = IElectric.getAt(level, pos);
                if(electric != null) {
                    // A device: only its jack terminal is a target, the electrical ones are not.
                    var state = level.getBlockState(pos);
                    var terminal = electric.terminalAt(state, local);
                    var jackTerminal = jack.jackTerminalIndex() >= 0 ? electric.terminal(state, jack.jackTerminalIndex()) : null;
                    if(terminal != null && terminal == jackTerminal) {
                        hitTerminal = terminal;
                        hitJack = true;
                        hitPoint = jack.jackPosition(0);
                    } else {
                        hitPoint = hitPoint.relative(blockHit.getDirection(), 1 / 32f);
                    }
                } else {
                    hitJack = true;
                    hitPoint = jack.jackPosition(jack.portAt(local));
                }
            } else {
                hitPoint = hitPoint.relative(blockHit.getDirection(), 1 / 32f);
            }
        }

        float length = (float) currentPos.distanceTo(hitPoint);
        if(length > 1000)
            return;

        if(endpoint instanceof JackEndpoint && hitJack) {
            color = length < item.maximumLength() ? OK : BAD;
            pos1 = currentPos;
            pos2 = hitPoint;
            renderPath = 1;
        } else {
            length = 0;
            var aligned = endpoint instanceof BlockWireEntityEndpoint ? currentPos : BlockTrace.alignPosition(currentPos);
            pos1 = aligned;
            trace = BlockTrace.findPathWithState(level, aligned, hitPoint, hitTerminal, continueDir);
            if(trace != null) {
                renderPath = 2;
                var points = trace.getSecond();
                if(points != null) {
                    for(var p : points.points())
                        length += p.length();
                }
            }
        }
        if(!player.isCreative()) {
            int required = Math.max(Math.round(length * item.itemsPerMeter()), 1);
            PlacementOverlay.setItemRequirement(stack.getItem(), required, stack.getCount() >= required);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS || renderPath == 0)
            return;
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null)
            return;
        var buffer = DefaultSuperRenderTypeBuffer.getInstance();
        render(buffer, event.getPoseStack(), event.getCamera().getPosition());
        buffer.draw();
    }

    private static void render(DefaultSuperRenderTypeBuffer buffer, PoseStack matrixStack, Vec3 cameraPos) {
        matrixStack.pushPose();
        matrixStack.translate(pos1.x - cameraPos.x, pos1.y - cameraPos.y, pos1.z - cameraPos.z);
        float thickness = item.wireThickness();
        var consumer = buffer.getBuffer(RenderType.entityTranslucent(item.texture()));
        if(renderPath == 1) {
            HangingWireRenderer.renderFromPositions(matrixStack, consumer, Vec3.ZERO, pos2.subtract(pos1),
                    1.01, 1.2, thickness, LightTexture.FULL_BRIGHT, color);
        } else if(trace != null && trace.getSecond() != null) {
            var points = trace.getSecond();
            int segmentColor = points.reachedTarget() ? OK : BAD;
            var currentPos = Vec3.ZERO;
            for(var p : points.points()) {
                BlockWireRenderer.renderSegment(matrixStack, consumer, LightTexture.FULL_BRIGHT, segmentColor,
                        currentPos, p.direction, thickness, p.length(), 0);
                currentPos = currentPos.add(p.vector());
            }
        }
        matrixStack.popPose();
    }
}
