package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.WireAcceptance;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;

/**
 * The metering head end: the radio's box with a Cat6 jack, a line and neutral and a coax port, and
 * a utility ID. Through its antenna it hears every meter antenna of its utility in range and keeps
 * their last readings for the computers on its jack.
 */
public class CollectorBlock extends HorizontalElectricBlock implements IBE<CollectorBlockEntity> {
    public static final int LINE = RadioBlock.LINE, NEUTRAL = RadioBlock.NEUTRAL, JACK = RadioBlock.JACK;

    public CollectorBlock(Properties properties) {
        super(properties);
        setTerminalCollection(horizontalNorthTerminals(this, DeviceHubs.withHubs(RadioBlock.terminals(), new AABB(7.5, 7.5, 12, 8.5, 8.5, 13), RadioBlock.HUBS),
                DeviceHubs.withHubs(RadioBlock.shape(), RadioBlock.HUBS)));
    }

    public static Vec3 coaxPosition(BlockState state, BlockPos pos) {
        return Vec3.atLowerCornerOf(pos).add(AntennaBlock.turn(RadioBlock.COAX_PORT, state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return WireAcceptance.electrical(wireStack);
    }

    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return RadioBlock.coaxOrWire(this, RadioBlock.LAYOUT, JACK, state, context, super::onWire);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if(level.isClientSide) {
            if(player.isShiftKeyDown())
                ClientHooks.openSettings(pos, "powergrid.gui.collector.title");
            else
                ClientHooks.openSplices(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<CollectorBlockEntity> getBlockEntityClass() {
        return CollectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CollectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.COLLECTOR.get();
    }
}
