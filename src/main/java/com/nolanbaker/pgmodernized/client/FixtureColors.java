package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.fixture.CustomFixtureBlockEntity;
import com.nolanbaker.pgmodernized.device.fixture.FixtureDesign;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** The custom fixture's canopy plate is a white texture tinted with the design's canopy colour. Mod bus. */
public final class FixtureColors {
    private FixtureColors() {}

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if(tint != 0)
                return -1;
            if(level != null && pos != null && level.getBlockEntity(pos) instanceof CustomFixtureBlockEntity be)
                return be.design().canopy;
            return FixtureDesign.CANOPY_DEFAULT;
        }, ModBlocks.CUSTOM_FIXTURE.get());
    }
}
