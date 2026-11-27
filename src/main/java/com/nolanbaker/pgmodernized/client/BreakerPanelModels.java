package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.breaker.BreakerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Standalone breaker models drawn by {@link BreakerPanelRenderer}, one per handle position. Registered on the mod bus. */
public final class BreakerPanelModels {
    public static final ModelResourceLocation ON = ModelResourceLocation.standalone(asResource("block/breaker/on"));
    public static final ModelResourceLocation OFF = ModelResourceLocation.standalone(asResource("block/breaker/off"));
    public static final ModelResourceLocation TRIPPED = ModelResourceLocation.standalone(asResource("block/breaker/tripped"));
    public static final ModelResourceLocation BLANK = ModelResourceLocation.standalone(asResource("block/breaker/blank"));
    public static final ModelResourceLocation LOCK = ModelResourceLocation.standalone(asResource("block/breaker/lock"));
    /** Power Grid's lit bulb, for the fixture designer's preview. */
    public static final ModelResourceLocation BULB_ON = ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("powergrid", "block/lamps/light_bulb_on"));

    private BreakerPanelModels() {}

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(ON);
        event.register(OFF);
        event.register(TRIPPED);
        event.register(BLANK);
        event.register(LOCK);
        event.register(BULB_ON);
    }

    public static BakedModel get(ModelResourceLocation location) {
        return Minecraft.getInstance().getModelManager().getModel(location);
    }

    public static BakedModel get(BreakerState state) {
        var location = switch(state) {
            case ON -> ON;
            case OFF -> OFF;
            case TRIPPED -> TRIPPED;
        };
        return Minecraft.getInstance().getModelManager().getModel(location);
    }
}
