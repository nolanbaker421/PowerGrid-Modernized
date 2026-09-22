package com.nolanbaker.pgmodernized.conduit.splice;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

import java.util.Arrays;
import java.util.function.BiFunction;

/**
 * Conduit knockouts for a device that keeps its ordinary terminals. The knockouts and the hidden
 * conductor landings are appended after the device's own terminals, so existing wiring keeps its
 * indices; the device's own terminals become the splice points.
 */
public final class DeviceHubs {
    public static final int PER_HUB = ConductorColors.COUNT;

    private DeviceHubs() {}

    /**
     * @param baseCount the device's own terminal count, jack included
     * @param hubCount knockouts
     * @param points the device terminals a pulled wire can be spliced to (everything but the jack)
     */
    public record Layout(int baseCount, int hubCount, int[] points) {
        public int hubBase() {
            return baseCount;
        }

        public int conductorBase() {
            return baseCount + hubCount;
        }

        public int terminalCount() {
            return conductorBase() + hubCount * PER_HUB;
        }

        public int hubTerminal(int hub) {
            return hubBase() + hub;
        }

        /** Hub index of a hub terminal, or -1. */
        public int hubAt(int terminal) {
            int i = terminal - hubBase();
            return i >= 0 && i < hubCount ? i : -1;
        }

        public int conductorTerminal(int hub, int conductor) {
            return conductorBase() + hub * PER_HUB + conductor;
        }

        /** Hub a hidden landing belongs to, or -1. */
        public int hubOf(int terminal) {
            int i = terminal - conductorBase();
            return i >= 0 && i < hubCount * PER_HUB ? i / PER_HUB : -1;
        }

        public int conductorOf(int terminal) {
            int i = terminal - conductorBase();
            return i >= 0 ? i % PER_HUB : -1;
        }

        public boolean isPoint(int terminal) {
            return Arrays.stream(points).anyMatch(p -> p == terminal);
        }
    }

    public static Component hubName(int hub) {
        return Lang.builder().translate("gui.device.hub", hub + 1).style(ChatFormatting.AQUA).component();
    }

    /** The device's terminals followed by the knockouts and, for each, twelve hidden landings inside the body. */
    public static TerminalBoundingBox[] withHubs(TerminalBoundingBox[] base, AABB hidden, AABB... hubs) {
        var terminals = Arrays.copyOf(base, base.length + hubs.length + hubs.length * PER_HUB);
        for(int h = 0; h < hubs.length; ++h) {
            terminals[base.length + h] = terminal(hubName(h), hubs[h]).withColor(0x2FB8D6);
            for(int k = 0; k < PER_HUB; ++k) {
                var name = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[base.length + hubs.length + h * PER_HUB + k] = terminal(name, hidden).withColor(ConductorColors.rgb(k));
            }
        }
        return terminals;
    }

    public static VoxelShape withHubs(VoxelShape shape, AABB... hubs) {
        for(var hub : hubs)
            shape = Shapes.or(shape, Block.box(hub.minX, hub.minY, hub.minZ, hub.maxX, hub.maxY, hub.maxZ));
        return shape;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    /**
     * Wire-click routing for a device with knockouts: conduit lands on a knockout and nowhere else,
     * other wires never land on a knockout, and everything else goes to the given fallback.
     */
    public static InteractionResult onWire(IElectric block, Layout layout, BlockState state, UseOnContext context,
                                           BiFunction<BlockState, UseOnContext, InteractionResult> fallback) {
        var pos = context.getClickedPos();
        int terminal = block.terminalIndexAt(state, context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        boolean conduit = ConduitItem.isConduit(context.getItemInHand());
        if(layout.hubAt(terminal) >= 0) {
            if(!conduit) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.hub_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return ConduitPlacement.click(context, new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos, terminal));
        }
        if(conduit) {
            if(terminal >= 0) {
                IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if(layout.hubOf(terminal) >= 0)
            return InteractionResult.FAIL;
        return fallback.apply(state, context);
    }
}
