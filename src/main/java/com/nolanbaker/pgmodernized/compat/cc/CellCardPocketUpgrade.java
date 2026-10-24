package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import com.nolanbaker.pgmodernized.registry.ModItems;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.AbstractPocketUpgrade;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Cell Card as a pocket computer upgrade: craft a pocket computer with the card and it carries
 * a {@code modem} on its back that talks through the carrier's towers. The card's carrier rides
 * along in the upgrade data. Declared to CC: Tweaked by
 * {@code data/powergrid_modernized/computercraft/pocket_upgrade/cell_card.json}.
 */
public class CellCardPocketUpgrade extends AbstractPocketUpgrade {
    public static final DeferredRegister<UpgradeType<? extends IPocketUpgrade>> TYPES = DeferredRegister.create(IPocketUpgrade.typeRegistry(), PowerGridModernized.MOD_ID);
    public static final DeferredHolder<UpgradeType<? extends IPocketUpgrade>, UpgradeType<CellCardPocketUpgrade>> TYPE =
            TYPES.register("cell_card", () -> UpgradeType.simple(new CellCardPocketUpgrade()));

    public CellCardPocketUpgrade() {
        super(Component.translatable("upgrade.powergrid_modernized.cell_card.adjective"), ModItems.CELL_CARD.asStack());
    }

    @Override
    public UpgradeType<? extends IPocketUpgrade> getType() {
        return TYPE.get();
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new CellModemPeripheral(access);
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        if(peripheral instanceof CellModemPeripheral modem)
            modem.tick();
    }

    /** A configured card keeps its carrier when it becomes the upgrade. */
    @Override
    public DataComponentPatch getUpgradeData(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CELL_CARD.get());
        if(tag == null)
            return DataComponentPatch.EMPTY;
        return DataComponentPatch.builder().set(ModDataComponents.CELL_CARD.get(), tag.copy()).build();
    }
}
