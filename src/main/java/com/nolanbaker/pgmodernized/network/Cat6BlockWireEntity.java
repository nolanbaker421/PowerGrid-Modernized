package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWire;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.JunctionWireEndpoint;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A Cat6 cable laid along block surfaces, the network counterpart of Power Grid's block wire.
 * Path finding, segment geometry, rendering and pick-up are inherited; the network link comes from
 * {@link Cat6LinkState}. Power Grid's own attach and cut packets would turn this into an electrical
 * wire, so those interactions are handled here instead: a Cat6 run has no junctions and is picked up
 * whole when cut.
 */
public class Cat6BlockWireEntity extends BlockWireEntity implements ICat6Cable {
    private final Cat6LinkState link = new Cat6LinkState(this);

    public Cat6BlockWireEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public static Cat6BlockWireEntity create(Level world, IWireEndpoint endpoint1, ItemStack item, List<Point> segments) {
        var entity = new Cat6BlockWireEntity(ModEntities.CAT6_BLOCK_CABLE.get(), world);
        entity.setItem(item.getItem(), item.getCount());
        var pos = BlockTrace.alignPosition(endpoint1.getExactPosition(world));
        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.segments.addAll(segments);
        entity.bakeBoundingBoxes();
        entity.setEndpoint1(endpoint1);
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        entity.setColor(Cat6WireEntity.DEFAULT_COLOR);
        return entity;
    }

    @Override
    public void setEndpoint1(IWireEndpoint endpoint) {
        super.setEndpoint1(JackEndpoint.from(endpoint));
    }

    @Override
    public void setEndpoint2(IWireEndpoint endpoint) {
        super.setEndpoint2(JackEndpoint.from(endpoint));
    }

    @Override
    public float current() {
        return 0;
    }

    @Override
    public float measuredCurrent() {
        return 0;
    }

    @Override
    public void makeWire() {
        link.makeWire();
    }

    @Override
    public void dropWire() {
        link.dropWire();
    }

    @Override
    public void unloaded() {
        dropWire();
    }

    @Override
    public void tick() {
        super.tick();
        link.tick();
    }

    /** Same as Power Grid's flip, but the replacement stays a Cat6 cable. */
    @Override
    public BlockWireEntity flip() {
        var entity = new Cat6BlockWireEntity(ModEntities.CAT6_BLOCK_CABLE.get(), level());
        entity.setItem(getItem(), getWireCount());
        entity.setEndpoint1(getEndpoint2());
        entity.setEndpoint2(getEndpoint1());
        entity.getEntityData().set(TEMPERATURE, getTemperature());
        entity.setColor(getColor());
        var pos = position();
        for(var segment : segments) {
            pos = pos.add(segment.vector());
            if(segment.gridLength > 0)
                entity.segments.add(0, new Point(segment.direction.getOpposite(), segment.gridLength));
        }
        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.bakeBoundingBoxes();
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        discard();
        ((ServerLevel) level()).tryAddFreshEntityWithPassengers(entity);
        return entity;
    }

    /** Cat6 runs cannot be split into junctions. */
    @Override
    public JunctionWireEndpoint split(int segmentIndex, int segmentPoint) {
        return null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if(hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        if(Cat6CableItem.isCat6(stack)) {
            // Continue the run from one of its free ends.
            if(level().isClientSide)
                return InteractionResult.SUCCESS;
            return Cat6Placement.attach(this, player, stack);
        }
        if(stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) || stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag)) {
            // Power Grid would cut out a segment and respawn electrical wire; take the whole run instead.
            if(!level().isClientSide)
                kill();
            return InteractionResult.SUCCESS;
        }
        if(IWire.isWire(level(), stack.getItem())) {
            if(level().isClientSide)
                player.displayClientMessage(Lang.translate("message.connection_incorrect_wire_type").style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        return super.interact(player, hand);
    }

    // --- ICat6Cable ---

    @Override
    public BaseWireEntity asWireEntity() {
        return this;
    }

    @Override
    public void jackLost(BlockPos jackPos) {
        if(isRemoved() || level().isClientSide)
            return;
        // Drops the segment touching that jack and keeps the rest of the run.
        if(ICat6Cable.endOn(endpoint2, jackPos, -1))
            endpointRemoved(endpoint2);
        else if(ICat6Cable.endOn(endpoint1, jackPos, -1))
            endpointRemoved(endpoint1);
    }
}
