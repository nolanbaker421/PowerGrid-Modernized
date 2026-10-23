package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * Eight clips, two ports each. Coax passes straight through a clip; a radio following its run
 * steps in one side and out the other. Cat6 does the same by way of the jack: the two ports of a
 * clip share one network node, so a Cat6 in and a Cat6 out are one cable as far as the computers
 * can tell (on CC: Tweaked every clip of a hanger is one node, as the switch's ports are).
 */
public class CoaxHangerBlockEntity extends SmartBlockEntity implements ICoaxPort, INetworkJack, IHaveGoggleInformation {
    private final CoaxSupport coax = new CoaxSupport(this);
    private final JackSupport jack = new JackSupport(this, false);

    public CoaxHangerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void tick() {
        super.tick();
        jack.tick();
    }

    @Override
    public void remove() {
        super.remove();
        coax.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    // ---- coax ----

    @Override
    public CoaxSupport coaxSupport() {
        return coax;
    }

    @Override
    public int coaxPortCount() {
        return CoaxHangerBlock.PORTS;
    }

    @Override
    public int coaxPortAt(Vec3 localHit) {
        return CoaxHangerBlock.portAt(getBlockState(), localHit);
    }

    @Override
    public Vec3 coaxPosition(int port) {
        return CoaxHangerBlock.portPosition(getBlockState(), worldPosition, port);
    }

    @Override
    public int coaxPassThrough(int port) {
        return port ^ 1;
    }

    // ---- Cat6 ----

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public int portCount() {
        return CoaxHangerBlock.PORTS;
    }

    @Override
    public int portAt(Vec3 localHit) {
        return CoaxHangerBlock.portAt(getBlockState(), localHit);
    }

    @Override
    public Vec3 jackPosition(int port) {
        return coaxPosition(port);
    }

    /** Whether either side of a clip holds any cable. */
    public boolean clipUsed(int clip) {
        for(int side = 0; side < 2; ++side) {
            int port = clip * 2 + side;
            if(coax.isPortUsed(port) || jack.isPortUsed(port))
                return true;
        }
        return false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.coax_hanger.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        int used = 0;
        for(int clip = 0; clip < CoaxHangerBlock.CLIPS; ++clip) {
            if(!clipUsed(clip))
                continue;
            ++used;
            String kind = coax.isPortUsed(clip * 2) || coax.isPortUsed(clip * 2 + 1) ? "coax" : "cat6";
            Lang.builder().translate("gui.coax_hanger.clip", clip + 1).text(" ").add(Lang.builder().translate("gui.coax_hanger." + kind)).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        }
        if(used == 0)
            Lang.builder().translate("gui.coax_hanger.empty").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
