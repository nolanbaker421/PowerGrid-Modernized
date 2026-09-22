package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.client.BreakerPanelModels;
import com.nolanbaker.pgmodernized.client.BreakerPlacementOutline;
import com.nolanbaker.pgmodernized.client.Cat6Preview;
import com.nolanbaker.pgmodernized.compat.cc.CCBridge;
import com.nolanbaker.pgmodernized.compat.oc.OCBridge;
import com.nolanbaker.pgmodernized.network.packets.ModPackets;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.registry.ModValues;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.architectury.platform.Platform;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.patryk3211.powergrid.AbstractPowerGridRegistrate;
import org.patryk3211.powergrid.forge.ElectricProperties;
import org.patryk3211.powergrid.forge.ForgePowerGridRegistrate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PowerGridModernized.MOD_ID)
public class PowerGridModernized {
    public static final String MOD_ID = "powergrid_modernized";
    public static final Logger LOGGER = LoggerFactory.getLogger("PowerGrid: Modernized");

    public static final ResourceKey<CreativeModeTab> MAIN_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, asResource("main"));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    /** Power Grid's registrate flavour so our items get the same electrical tooltips as its own blocks. */
    public static final AbstractPowerGridRegistrate REGISTRATE = ForgePowerGridRegistrate.create(MOD_ID)
            .defaultCreativeTab(MAIN_TAB)
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(ElectricProperties.create(item))));

    public PowerGridModernized(IEventBus bus, ModContainer container) {
        LOGGER.info("PowerGrid: Modernized loading");

        // Bind the bus before registering anything, as Power Grid does: Registrate parks client hooks
        // such as entity renderers until it knows the bus, and only flushes them on a later registration.
        ((ForgePowerGridRegistrate) REGISTRATE).registerEventListeners(bus);
        ModValues.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModItems.register();
        ModEntities.register();
        ModDataComponents.REGISTER.register(bus);

        if(Platform.isModLoaded("computercraft")) {
            CCBridge.init();
        }
        if(FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(Cat6Preview.class);
            NeoForge.EVENT_BUS.register(BreakerPlacementOutline.class);
            bus.register(BreakerPanelModels.class);
        }

        TABS.register("main", () -> CreativeModeTab.builder()
                .icon(() -> new ItemStack(ModBlocks.VFD))
                .title(Component.translatable("itemGroup." + MOD_ID + ".main"))
                .build());
        TABS.register(bus);

        bus.register(PowerGridModernized.class);
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        if(Platform.isModLoaded("opencomputers")) {
            // Swap in the block entity subclasses that are OpenComputers network nodes.
            event.enqueueWork(OCBridge::register);
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        ModPackets.register(event);
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if(Platform.isModLoaded("computercraft")) {
            CCBridge.registerCapabilities(event);
        }
        if(Platform.isModLoaded("opencomputers")) {
            OCBridge.registerCapabilities(event);
        }
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
