package com.nolanbaker.pgmodernized.compat.oc;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;

public class ElectricEnvironment<T extends ElectricBlockEntity> extends AbstractManagedEnvironment implements NamedBlock {
    protected final T blockEntity;
    private final String componentName;

    public ElectricEnvironment(T blockEntity, String componentName) {
        this.blockEntity = blockEntity;
        this.componentName = componentName;
        setNode(Network.newNode(this, Visibility.Network).withComponent(componentName).create());
    }

    @Override
    public String preferredName() {
        return componentName;
    }

    @Override
    public int priority() {
        return 5;
    }

    protected static Object[] result(Object... values) {
        return values;
    }

    private int terminalCount() {
        if(blockEntity.getBlockState().getBlock() instanceof IElectric electric)
            return electric.terminalCount();
        return blockEntity.getElectricBehaviour().getExternalNodes().size();
    }

    private double terminalVoltage(int index) {
        var terminal = blockEntity.getElectricBehaviour().getTerminal(index);
        return terminal == null ? 0 : terminal.getVoltage();
    }

    @Callback(direct = true, doc = "function():number -- Number of wire terminals on this block.")
    public Object[] getTerminalCount(Context context, Arguments args) {
        return result(terminalCount());
    }

    @Callback(direct = true, doc = "function(index:number):number -- Voltage (V) at the given terminal, 0-based.")
    public Object[] getTerminalVoltage(Context context, Arguments args) {
        int index = args.checkInteger(0);
        if(index < 0 || index >= terminalCount())
            throw new IllegalArgumentException("invalid terminal index");
        return result(terminalVoltage(index));
    }

    @Callback(direct = true, doc = "function():table -- Voltages (V) at every terminal, indexed from 1.")
    public Object[] getTerminalVoltages(Context context, Arguments args) {
        int count = terminalCount();
        var voltages = new Double[count];
        for(int i = 0; i < count; ++i)
            voltages[i] = terminalVoltage(i);
        return result((Object) voltages);
    }
}
