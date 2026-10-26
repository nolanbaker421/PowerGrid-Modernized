package com.nolanbaker.pgmodernized.ponder;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Which scene plays for which block. The schematic name is the plot under
 * {@code assets/powergrid_modernized/ponder/}; the scene's own id, set by its title, names its text keys.
 */
public final class PgmPonderScenes {
    private PgmPonderScenes() {}

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> raw) {
        PonderSceneRegistrationHelper<BlockEntry<?>> helper = raw.withKeyFunction(BlockEntry::getId);

        helper.forComponents(ModBlocks.BREAKER_PANEL_200, ModBlocks.BREAKER_PANEL_200_2P, ModBlocks.BREAKER_PANEL_200_3P,
                        ModBlocks.BREAKER_PANEL_400, ModBlocks.BREAKER_PANEL_400_2P, ModBlocks.BREAKER_PANEL_400_3P,
                        ModBlocks.BREAKER_PANEL_800, ModBlocks.BREAKER_PANEL_800_2P, ModBlocks.BREAKER_PANEL_800_3P)
                .addStoryBoard("breaker_panel", PanelScenes::basics, PgmPonderTags.DISTRIBUTION);

        helper.forComponents(ModBlocks.RADIO, ModBlocks.DISH_ANTENNA)
                .addStoryBoard("radio_dish_link", RadioScenes::dishLink, PgmPonderTags.RADIO);
        helper.forComponents(ModBlocks.RADIO, ModBlocks.SECTOR_ANTENNA, ModBlocks.OMNI_ANTENNA)
                .addStoryBoard("radio_carrier", RadioScenes::carrier, PgmPonderTags.RADIO);

        helper.forComponents(ModBlocks.CONDUIT_BOX)
                .addStoryBoard("conduit", ConduitScenes::basics, PgmPonderTags.CONDUIT);
    }
}
