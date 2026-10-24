package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.radio.CellCardItem;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.world.item.ItemStack;

/** Puts the Cell Card in any OpenComputers card slot: cases, servers, tablets, robots and drones alike. */
public class OCCellCardDriver implements HostAware {
    @Override
    public boolean worksWith(ItemStack stack) {
        return stack.getItem() instanceof CellCardItem;
    }

    @Override
    public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        return worksWith(stack);
    }

    @Override
    public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        return new OCCellCard(stack, host);
    }

    @Override
    public String slot(ItemStack stack) {
        return Slot.Card;
    }

    @Override
    public int tier(ItemStack stack) {
        return 1;
    }
}
