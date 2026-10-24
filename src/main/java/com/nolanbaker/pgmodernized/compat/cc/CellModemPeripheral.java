package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.compat.cc.network.CCJackLink;
import com.nolanbaker.pgmodernized.radio.CellCardItem;
import com.nolanbaker.pgmodernized.radio.RadioBlockEntity;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.PacketSender;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Cell Card on a CC: Tweaked pocket computer: a {@code modem} peripheral whose packets go out
 * through the tower. Once a second it finds the nearest sector of its carrier that covers the
 * holder, within the omni reach, with a seat free, and listens on that radio's wired node; what it
 * transmits goes onto that node, and from there as far as the tower's network and relays carry it.
 * Peripherals never cross, which makes it a relay by nature. Rednet treats it as a wireless modem.
 */
public class CellModemPeripheral implements IPeripheral, PacketReceiver, PacketSender {
    private static final int INTERVAL = 20;

    private final IPocketAccess access;
    private final Set<IComputerAccess> computers = new HashSet<>();
    private final Set<Integer> channels = new HashSet<>();
    private String carrier;
    @Nullable
    private RadioBlockEntity tower;
    @Nullable
    private WiredNode towerNode;
    private int timer;

    public CellModemPeripheral(IPocketAccess access) {
        this.access = access;
        var tag = access.getUpgradeData().get(ModDataComponents.CELL_CARD.get());
        carrier = tag == null || tag.isEmpty() ? "" : tag.get().getString("Carrier");
    }

    /** Called by the upgrade every tick the pocket computer is running. */
    public void tick() {
        var level = access.getLevel();
        if(level == null || ++timer < INTERVAL)
            return;
        timer = 0;
        var pos = access.getPosition();
        var best = carrier.isBlank() ? null : RadioBlockEntity.nearestSector(level, pos, carrier, PgmConfig.RADIO_CELL_RANGE.get(), this);
        if(best != tower) {
            boolean was = tower != null;
            leave();
            if(best != null)
                join(best);
            if(was != (tower != null))
                queue("cell_link", tower != null);
        } else if(tower != null) {
            tower.claimSeat(this);
        }
    }

    private void join(RadioBlockEntity radio) {
        if(!(radio.jack().link(CCJackLink.KIND) instanceof CCJackLink link))
            return;
        towerNode = link.getNode();
        towerNode.addReceiver(this);
        radio.claimSeat(this);
        tower = radio;
    }

    private void leave() {
        if(towerNode != null)
            towerNode.removeReceiver(this);
        if(tower != null)
            tower.releaseSeat(this);
        towerNode = null;
        tower = null;
    }

    private void queue(String event, Object... args) {
        for(var computer : computers) {
            var all = new Object[args.length + 1];
            all[0] = computer.getAttachmentName();
            System.arraycopy(args, 0, all, 1, args.length);
            computer.queueEvent(event, all);
        }
    }

    // ---- peripheral ----

    @Override
    public String getType() {
        return "modem";
    }

    @Override
    public Set<String> getAdditionalTypes() {
        return Set.of("cell_card");
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        if(computers.isEmpty())
            leave();
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other == this;
    }

    // ---- modem API ----

    private static int channel(int channel) throws LuaException {
        if(channel < 0 || channel > 65535)
            throw new LuaException("Expected number in range 0-65535");
        return channel;
    }

    @LuaFunction
    public final void open(int channel) throws LuaException {
        if(channels.size() >= 128)
            throw new LuaException("Too many open channels");
        channels.add(channel(channel));
    }

    @LuaFunction
    public final boolean isOpen(int channel) throws LuaException {
        return channels.contains(channel(channel));
    }

    @LuaFunction
    public final void close(int channel) throws LuaException {
        channels.remove(channel(channel));
    }

    @LuaFunction
    public final void closeAll() {
        channels.clear();
    }

    @LuaFunction
    public final boolean isWireless() {
        return true;
    }

    @LuaFunction(mainThread = true)
    public final void transmit(int channel, int replyChannel, Object payload) throws LuaException {
        channel(channel);
        channel(replyChannel);
        if(towerNode != null)
            towerNode.transmitSameDimension(new Packet(channel, replyChannel, payload, this), Double.MAX_VALUE);
    }

    // ---- the card ----

    @LuaFunction
    public final String getCarrier() {
        return carrier;
    }

    @LuaFunction(mainThread = true)
    public final void setCarrier(String id) {
        carrier = id.trim();
        leave();
        timer = INTERVAL;
        access.setUpgradeData(DataComponentPatch.builder().set(ModDataComponents.CELL_CARD.get(), CellCardItem.tag(carrier, true)).build());
    }

    @LuaFunction
    public final boolean isConnected() {
        return tower != null && !tower.isRemoved();
    }

    @LuaFunction
    @Nullable
    public final Map<String, Object> getTower() {
        if(tower == null || tower.isRemoved())
            return null;
        var pos = tower.getBlockPos();
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "distance", access.getPosition().distanceTo(pos.getCenter()), "linkId", tower.linkId());
    }

    // ---- packets ----

    @Override
    public Level getLevel() {
        return access.getLevel();
    }

    @Override
    public Vec3 getPosition() {
        return access.getPosition();
    }

    @Override
    public double getRange() {
        return Double.MAX_VALUE;
    }

    @Override
    public boolean isInterdimensional() {
        return false;
    }

    @Override
    public void receiveSameDimension(Packet packet, double distance) {
        if(packet.sender() == this || !channels.contains(packet.channel()))
            return;
        queue("modem_message", packet.channel(), packet.replyChannel(), packet.payload(), distance);
    }

    @Override
    public void receiveDifferentDimension(Packet packet) {}

    @Override
    public String getSenderID() {
        return "cell_card";
    }
}
