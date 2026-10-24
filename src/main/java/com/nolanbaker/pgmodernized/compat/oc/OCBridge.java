package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.rangefinder.RangefinderBlockEntity;
import com.nolanbaker.pgmodernized.device.helm.HelmBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import li.cil.oc.api.Driver;
import li.cil.oc.api.network.Environment;
import li.cil.oc.common.capabilities.Capabilities;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.device.computer.ComputerBlockEntityFactories;

public class OCBridge {
    /** OC finds network neighbours through this NeoForge block capability, not by class; expose the block entities' nodes. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        expose(event, ModBlockEntities.ANALOG_IO.get());
        expose(event, ModBlockEntities.VFD.get());
        expose(event, ModBlockEntities.CLAMP_METER.get());
        expose(event, ModBlockEntities.LINE_VOLTMETER.get());
        expose(event, ModBlockEntities.LINE_AMMETER.get());
        expose(event, ModBlockEntities.CT_CABINET.get());
        expose(event, ModBlockEntities.TRANSFORMER.get());
        ForkHooks.get().registerOC(event);
        expose(event, ModBlockEntities.RANGEFINDER.get());
        expose(event, ModBlockEntities.HELM.get());
        expose(event, ModBlockEntities.RADIO_BASE.get());
        expose(event, ModBlockEntities.CONTROLS_CABINET.get());
        expose(event, ModBlockEntities.RAIL_FEED.get());
        expose(event, ModBlockEntities.RAIL_COLLECTOR.get());
        expose(event, ModBlockEntities.CABLE_CHAIN_ANCHOR.get());
        expose(event, ModBlockEntities.NETWORK_JACK.get());
        expose(event, ModBlockEntities.NETWORK_SWITCH.get());
        expose(event, ModBlockEntities.RADIO.get());
        expose(event, ModBlockEntities.COLLECTOR.get());
        expose(event, ModBlockEntities.COAX_HANGER.get());
    }

    public static <T extends BlockEntity> void expose(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.EnvironmentCapability(), type,
                (be, side) -> be instanceof Environment env ? env : null);
    }

    public static void register() {
        // Adapter driver for read-only blocks (gauges, meters, batteries, anything electric).
        Driver.add(new PowerGridBlockDriver());
        // The Cell Card in any card slot.
        Driver.add(new OCCellCardDriver());
        // Computer-facing blocks become OpenComputers network nodes themselves, so cables attach directly.
        ComputerBlockEntityFactories.ANALOG_IO = OCAnalogIOBlockEntity::new;
        ComputerBlockEntityFactories.VFD = OCVfdBlockEntity::new;
        ComputerBlockEntityFactories.CLAMP_METER = OCClampMeterBlockEntity::new;
        ComputerBlockEntityFactories.LINE_VOLTMETER = OCLineVoltmeterBlockEntity::new;
        ComputerBlockEntityFactories.LINE_AMMETER = OCLineAmmeterBlockEntity::new;
        ComputerBlockEntityFactories.CT_CABINET = OCCtCabinetBlockEntity::new;
        ComputerBlockEntityFactories.TRANSFORMER = OCTransformerBlockEntity::new;
        ComputerBlockEntityFactories.GEAR_CELL = OCGearCellBlockEntity::new;
        ForkHooks.get().swapOCFactories();
        RangefinderBlockEntity.FACTORY = OCRangefinderBlockEntity::new;
        HelmBlockEntity.FACTORY = OCHelmBlockEntity::new;
        com.nolanbaker.pgmodernized.device.helm.RadioBaseBlockEntity.FACTORY = OCRadioBaseBlockEntity::new;
        ControlsCabinetBlockEntity.FACTORY = OCControlsCabinetBlockEntity::new;
        ComputerBlockEntityFactories.RAIL_FEED = OCRailFeedBlockEntity::new;
        ComputerBlockEntityFactories.RAIL_COLLECTOR = OCRailCollectorBlockEntity::new;
        ComputerBlockEntityFactories.CABLE_CHAIN_ANCHOR = OCCableChainAnchorBlockEntity::new;
        ComputerBlockEntityFactories.NETWORK_JACK = OCNetworkJackBlockEntity::new;
        ComputerBlockEntityFactories.NETWORK_SWITCH = OCNetworkSwitchBlockEntity::new;
        ComputerBlockEntityFactories.RADIO = OCRadioBlockEntity::new;
        ComputerBlockEntityFactories.COLLECTOR = OCCollectorBlockEntity::new;
        ComputerBlockEntityFactories.COAX_HANGER = OCCoaxHangerBlockEntity::new;
        // Cat6 cables link the OC nodes of the jacks they join.
        JackSupport.registerLinkFactory((be, jack) -> be instanceof OCNetworkSwitchBlockEntity sw ? new OCJackLink(sw::nodeForPort)
                : be instanceof OCRadioBlockEntity radio ? new OCJackLink(radio::nodeForPort)
                : be instanceof OCCoaxHangerBlockEntity hanger ? new OCJackLink(hanger::nodeForPort)
                : be instanceof OCControlsCabinetBlockEntity cabinet ? new OCJackLink(cabinet::nodeForPort)
                : be instanceof Environment env ? new OCJackLink(env) : null);
    }
}
