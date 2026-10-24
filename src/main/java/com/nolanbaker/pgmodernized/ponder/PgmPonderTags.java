package com.nolanbaker.pgmodernized.ponder;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** The index pages the scenes are grouped under. Titles and descriptions are mirrored into the lang file by the generator. */
public final class PgmPonderTags {
    public static final ResourceLocation DISTRIBUTION = id("distribution");
    public static final ResourceLocation RADIO = id("radio");

    private PgmPonderTags() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PowerGridModernized.MOD_ID, path);
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(DISTRIBUTION)
                .title("Power Distribution")
                .description("Panels, switchgear and bus duct: getting power from the service to every load behind its own breaker")
                .item(ModBlocks.BREAKER_PANEL_200_2P.get(), true, false)
                .addToIndex()
                .register();
        helper.registerTag(RADIO)
                .title("Radio Links")
                .description("Dishes, sectors and omnis: Cat6 across the air, from point-to-point hops to a carrier network of towers")
                .item(ModBlocks.DISH_ANTENNA.get(), true, false)
                .addToIndex()
                .register();
    }
}
