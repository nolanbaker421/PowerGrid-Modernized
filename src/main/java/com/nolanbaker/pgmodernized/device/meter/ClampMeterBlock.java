package com.nolanbaker.pgmodernized.device.meter;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/** Non-contact current meter: reads the current of any wire that physically passes through its jaw. No terminals. */
public class ClampMeterBlock extends Rotation4ElectricBlock implements IBE<ClampMeterBlockEntity> {
    private static final VoxelShape SHAPE_DOWN = box(3, 0, 5, 13, 13, 11);

    public ClampMeterBlock(Properties settings) {
        super(settings);
        setTerminalCollection(rotation4DownTerminals(this, new TerminalBoundingBox[0], SHAPE_DOWN));
    }

    @Override
    public Class<ClampMeterBlockEntity> getBlockEntityClass() {
        return ClampMeterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ClampMeterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CLAMP_METER.get();
    }
}
