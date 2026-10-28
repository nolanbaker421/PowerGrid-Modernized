package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.inspector.CopperScrap;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Client edits a splice host's splices.
 * @param action {@link #TOGGLE} joins or separates terminals {@code a} and {@code b};
 *               {@link #LAND} lands every pulled conductor of hub {@code a} on the points;
 *               {@link #RECOLOR} gives the pulled conductor on terminal {@code a} colour {@code b}
 *               (negative: back to its slot's colour).
 */
public record SplicePayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {
    public static final int TOGGLE = 0, LAND = 1, RECOLOR = 2;

    public static final Type<SplicePayload> TYPE = new Type<>(asResource("splice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SplicePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SplicePayload::pos,
            ByteBufCodecs.VAR_INT, SplicePayload::action,
            ByteBufCodecs.VAR_INT, SplicePayload::a,
            ByteBufCodecs.VAR_INT, SplicePayload::b,
            SplicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static int[] landTerminals(ISpliceHost host, int hub) {
        var run = host.hubRun(hub);
        if(run == null)
            return new int[0];
        var points = host.points();
        int count = Math.min(run.capacity(), points.size());
        var terminals = new int[count * 2];
        for(int k = 0; k < count; ++k) {
            terminals[k * 2] = points.get(k).terminal();
            terminals[k * 2 + 1] = host.conductorTerminal(hub, k);
        }
        return terminals;
    }

    public static void handle(SplicePayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(!(level.getBlockEntity(payload.pos) instanceof ISpliceHost host) || host.splices() == null)
            return;
        // A sealed cabinet or a padlocked gang keeps its wiring, even from an editor opened before the lock went on.
        boolean locked = switch(payload.action) {
            case TOGGLE -> host.spliceLocked(payload.a) || host.spliceLocked(payload.b);
            case LAND -> java.util.Arrays.stream(landTerminals(host, payload.a)).anyMatch(host::spliceLocked);
            default -> host.spliceLocked(payload.a);
        };
        if(locked)
            return;
        if(payload.action == RECOLOR) {
            // Tape on a wire's end, and on every wire spliced straight through to it: no electrical change, nothing live is touched.
            var conductor = com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.conductorAt(host, payload.a);
            if(conductor != null)
                com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.colorChain(level, conductor, payload.b);
            return;
        }
        // Working a live terminal hurts; the edit still happens, as it would in real life.
        if(level.getBlockEntity(payload.pos) instanceof ElectricBlockEntity be) {
            int[] touched = switch(payload.action) {
                case TOGGLE -> new int[] {payload.a, payload.b};
                case LAND -> landTerminals(host, payload.a);
                default -> new int[0];
            };
            ShockDamage.shockVolts(player, ShockDamage.maxVolts(be, touched));
        }
        switch(payload.action) {
            case TOGGLE -> host.splices().toggle(payload.a, payload.b);
            case LAND -> host.splices().land(payload.a);
            default -> {}
        }
        if(payload.action == TOGGLE || payload.action == LAND)
            CopperScrap.roll(player, payload.pos);
        // Two pulled wires just joined: the name and tape either carries go on through.
        if(payload.action == TOGGLE && host.hubOf(payload.a) >= 0 && host.hubOf(payload.b) >= 0) {
            int lo = Math.min(payload.a, payload.b), hi = Math.max(payload.a, payload.b);
            if(host.splices().list().stream().anyMatch(s -> s[0] == lo && s[1] == hi)) {
                var ca = com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.conductorAt(host, payload.a);
                var cb = com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.conductorAt(host, payload.b);
                if(ca != null && cb != null)
                    com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.carryOver(level, ca, cb);
            }
        }
    }
}
