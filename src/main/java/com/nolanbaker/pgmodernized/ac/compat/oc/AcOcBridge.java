package com.nolanbaker.pgmodernized.ac.compat.oc;

import com.nolanbaker.pgmodernized.ac.AcContent;
import com.nolanbaker.pgmodernized.compat.oc.OCBridge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** OpenComputers nodes of the AC-only devices; only touched when both OC and the fork are present. */
public final class AcOcBridge {
    private AcOcBridge() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        OCBridge.expose(event, AcContent.THREE_PHASE_MOTOR_BE.get());
        OCBridge.expose(event, AcContent.THREE_PHASE_DRIVE_BE.get());
        OCBridge.expose(event, AcContent.SYNCHROSCOPE_BE.get());
        OCBridge.expose(event, AcContent.FE_INVERTER_BE.get());
    }

    /** Before any block entity exists: the AC devices become OC network nodes. */
    public static void swapFactories() {
        AcContent.MOTOR_FACTORY = OCThreePhaseMotorBlockEntity::new;
        AcContent.DRIVE_FACTORY = OCThreePhaseDriveBlockEntity::new;
        AcContent.SYNCHROSCOPE_FACTORY = OCSynchroscopeBlockEntity::new;
        AcContent.INVERTER_FACTORY = OCFeInverterBlockEntity::new;
    }
}
