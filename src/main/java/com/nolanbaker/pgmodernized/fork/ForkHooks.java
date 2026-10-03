package com.nolanbaker.pgmodernized.fork;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;

/**
 * Everything the mod does differently when the powergrid-ac fork is the installed Power Grid.
 * The shared code only ever talks to this interface; {@link DcHooks} answers on stock Power Grid,
 * and the fork's implementation lives in the {@code ac} package, which is loaded by name only once
 * the fork's classes have been found. Nothing outside that package may name a fork-only class, and
 * CI compiles the rest of the sources against the stock jar to keep it so.
 */
public interface ForkHooks {
    /** A class only the fork has; its presence is the test. */
    String PROBE = "org.patryk3211.powergrid.electricity.sim.special.WattmeterWire";
    String IMPLEMENTATION = "com.nolanbaker.pgmodernized.ac.AcHooks";

    static ForkHooks get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        static final ForkHooks INSTANCE = load();

        private Holder() {}

        private static ForkHooks load() {
            try {
                Class.forName(PROBE, false, ForkHooks.class.getClassLoader());
            } catch(ClassNotFoundException e) {
                return new DcHooks();
            }
            try {
                return (ForkHooks) Class.forName(IMPLEMENTATION).getConstructor().newInstance();
            } catch(ReflectiveOperationException | LinkageError e) {
                PowerGridModernized.LOGGER.warn("powergrid-ac fork found but its support failed to load; running as on stock Power Grid", e);
                return new DcHooks();
            }
        }
    }

    boolean present();

    // ---- readings: RMS over the tick on the fork, the instantaneous value on stock ----

    double rmsVoltage(AbstractElectricWire wire);

    double rmsCurrent(AbstractElectricWire wire);

    /** The direct component of the current; equals the current itself on stock. */
    double meanCurrent(AbstractElectricWire wire);

    /** The settled RMS a switched wire keeps for its trip curve; the current's magnitude on stock. */
    double lastRmsCurrent(AbstractElectricWire wire);

    /** A hung wire's heating (RMS) current; the current's magnitude on stock. */
    double heatingCurrent(BaseWireEntity wire);

    /** A metering shunt between two nodes: the fork's wattmeter against a sense branch, a plain wire on stock. */
    ElectricWire shunt(IElectricEntity.CircuitBuilder builder, double resistance, AbstractElectricWire sense, IElectricNode in, IElectricNode out);

    /** Mean real power the shunt accumulated since last asked, or NaN when the shunt cannot meter it. */
    double drainRealPower(ElectricWire shunt);

    // ---- content that exists only with the fork ----

    void registerContent();

    void registerClient(IEventBus modBus);

    /** Capabilities of fork-only blocks that need no computer mod (the inverter's energy side). */
    default void registerCapabilities(RegisterCapabilitiesEvent event) {}

    void registerCC(RegisterCapabilitiesEvent event);

    /** A ComputerCraft peripheral for an AC device, or null. Typed as Object so this interface never names CC classes. */
    @Nullable
    Object ccPeripheral(BlockEntity be);

    void registerOC(RegisterCapabilitiesEvent event);

    void swapOCFactories();
}
