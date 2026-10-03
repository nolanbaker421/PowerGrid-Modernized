package com.nolanbaker.pgmodernized.ac.compat.cc;

import com.nolanbaker.pgmodernized.ac.source.FeInverterBlockEntity;
import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.ac.drive.ThreePhaseDriveBlockEntity;
import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlockEntity;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** ComputerCraft peripherals of the AC-only devices; only touched when both CC and the fork are present. */
public final class AcCcBridge {
    private AcCcBridge() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), AcContent.THREE_PHASE_MOTOR_BE.get(),
                (be, direction) -> new ThreePhaseMotorPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), AcContent.THREE_PHASE_DRIVE_BE.get(),
                (be, direction) -> new ThreePhaseDrivePeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), AcContent.SYNCHROSCOPE_BE.get(),
                (be, direction) -> new SynchroscopePeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), AcContent.FE_INVERTER_BE.get(),
                (be, direction) -> new FeInverterPeripheral(be));
    }

    /** The peripheral a Cat6-reachable AC device presents, or null for anything else. */
    @Nullable
    public static IPeripheral peripheral(BlockEntity be) {
        if(be instanceof ThreePhaseDriveBlockEntity drive)
            return new ThreePhaseDrivePeripheral(drive);
        if(be instanceof SynchroscopeBlockEntity scope)
            return new SynchroscopePeripheral(scope);
        if(be instanceof FeInverterBlockEntity inverter)
            return new FeInverterPeripheral(inverter);
        return null;
    }
}
