package com.nolanbaker.pgmodernized.network;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/** A wire entity that carries computer network traffic: either the hanging or the block-routed Cat6 cable. */
public interface ICat6Cable {
    BaseWireEntity asWireEntity();

    /** Whether one of this cable's ends sits in the given port of the jack at the given position. */
    default boolean connectsTo(BlockPos jackPos, int port) {
        return endOn(asWireEntity().getEndpoint1(), jackPos, port) || endOn(asWireEntity().getEndpoint2(), jackPos, port);
    }

    /** Whether one of this cable's ends sits on any port of the jack at the given position. */
    default boolean connectsTo(BlockPos jackPos) {
        return endOn(asWireEntity().getEndpoint1(), jackPos, -1) || endOn(asWireEntity().getEndpoint2(), jackPos, -1);
    }

    /** The jack at the given position is gone; detach from it. Server side only. */
    void jackLost(BlockPos jackPos);

    static boolean endOn(@Nullable IWireEndpoint endpoint, BlockPos jackPos, int port) {
        return endpoint instanceof JackEndpoint jack && jack.getPos().equals(jackPos) && (port < 0 || jack.getPort() == port);
    }
}
