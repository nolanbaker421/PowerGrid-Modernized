package com.nolanbaker.pgmodernized.device.helm;

import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.network.packets.HelmStatePayload;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Knows who is at the helm and which of their keys are down. The client at the helm sends every
 * key press and release; the server keeps the set, hands each change to the computer bindings
 * through {@link Listener}, and lets go of the helmsman when they walk off, log out, die, or ask.
 * Nothing is saved: a loaded helm is free. A helm on a physics body measures distance from where
 * the body holds it.
 */
public class HelmBlockEntity extends SmartBlockEntity implements INetworkJack, IHaveGoggleInformation {
    /** Replaced by the computer bridges with their network-node subclasses. */
    public static BlockEntityFactory<HelmBlockEntity> FACTORY = HelmBlockEntity::new;
    /** How far the helmsman may stand from the helm, blocks. */
    public static final double REACH = 5;
    /** The key name the client sends to let go. */
    public static final String RELEASE = "";

    /** What the computer bindings subscribe to. */
    public interface Listener {
        void key(String name, boolean down);

        void manned(@Nullable String helmsman);
    }

    private final JackSupport jack = new JackSupport(this, true);
    private final Set<String> pressed = new LinkedHashSet<>();
    private final List<Listener> listeners = new ArrayList<>();
    @Nullable
    private UUID helmsman;
    private String helmsmanName = "";
    private int checkTimer;

    public HelmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---- the helmsman ----

    public boolean isManned() {
        return helmsman != null;
    }

    public String helmsmanName() {
        return helmsmanName;
    }

    /** Keys down right now, in the order they went down; names as the client sends them (w, space, left.shift, mouse.left). */
    public Set<String> pressed() {
        return pressed;
    }

    public boolean isDown(String key) {
        return pressed.contains(key);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Server side: this player takes the helm, unless someone else holds it. */
    public void take(ServerPlayer player) {
        if(helmsman != null && !helmsman.equals(player.getUUID())) {
            player.displayClientMessage(Lang.builder().translate("message.helm.taken", helmsmanName).style(ChatFormatting.RED).component(), true);
            return;
        }
        if(helmsman == null) {
            helmsman = player.getUUID();
            helmsmanName = player.getGameProfile().getName();
            pressed.clear();
            for(var listener : listeners)
                listener.manned(helmsmanName);
            notifyUpdate();
        }
        PacketDistributor.sendToPlayer(player, new HelmStatePayload(worldPosition, true));
    }

    /** Server side: the helmsman lets go, or is let go of. Every key still down is released first. */
    public void release() {
        if(helmsman == null)
            return;
        var player = level != null && level.getServer() != null ? level.getServer().getPlayerList().getPlayer(helmsman) : null;
        for(var key : new ArrayList<>(pressed))
            for(var listener : listeners)
                listener.key(key, false);
        pressed.clear();
        helmsman = null;
        helmsmanName = "";
        for(var listener : listeners)
            listener.manned(null);
        if(player != null)
            PacketDistributor.sendToPlayer(player, new HelmStatePayload(worldPosition, false));
        notifyUpdate();
    }

    /** Server side: a key change from the client at the helm; anyone else is ignored. */
    public void press(ServerPlayer player, String key, boolean down) {
        if(helmsman == null || !helmsman.equals(player.getUUID()))
            return;
        if(RELEASE.equals(key)) {
            release();
            return;
        }
        boolean changed = down ? pressed.add(key) : pressed.remove(key);
        if(!changed)
            return;
        for(var listener : listeners)
            listener.key(key, down);
    }

    /** The helm's place in the world, which on a physics body is wherever the body holds it. */
    private Vec3 worldCenter() {
        var center = Vec3.atCenterOf(worldPosition);
        var body = level == null ? null : SableCompanion.INSTANCE.getContaining(level, worldPosition);
        return body == null ? center : body.logicalPose().transformPosition(center);
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level == null || level.isClientSide || helmsman == null || ++checkTimer < 5)
            return;
        checkTimer = 0;
        var server = level.getServer();
        var player = server == null ? null : server.getPlayerList().getPlayer(helmsman);
        if(player == null || !player.isAlive() || player.level() != level || player.position().distanceToSqr(worldCenter()) > REACH * REACH)
            release();
    }

    // ---- jack ----

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return HelmBlock.jackPosition(getBlockState(), getBlockPos());
    }

    @Override
    public void remove() {
        release();
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    // ---- sync (the helmsman's name for the goggles; never saved) ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if(clientPacket)
            tag.putString("Helmsman", helmsmanName);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if(clientPacket)
            helmsmanName = tag.getString("Helmsman");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.helm.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(helmsmanName.isEmpty())
            Lang.builder().translate("gui.helm.free").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.helm.manned", helmsmanName).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        return true;
    }
}
