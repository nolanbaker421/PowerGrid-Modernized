package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

import java.util.Arrays;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.*;

/**
 * Terminal numbering for a controls cabinet and the extensions below it. The head keeps the
 * numbering it always had: its own terminals, its twelve knockouts and their landings, then the
 * digital and analog channel terminals. Each extension section then appends a group of its own:
 * its slots' relay contacts, digital channels and analog inputs, its eight knockouts (four along
 * the bottom, two down each side) and their landings. Everything in an extension is described in
 * the head's frame, a block lower per section; the extension block moves its own back up.
 */
public final class CabinetLayout {
    public static final int EXT_HUBS = 8;
    public static final int PER_HUB = DeviceHubs.PER_HUB;
    private static final int EXT_RELAYS = RAIL * RELAY_CHANNELS * 2;
    static final int EXT_RELAY_OFF = 0, EXT_IO_OFF = EXT_RELAYS, EXT_AI_OFF = EXT_IO_OFF + IO_COUNT, EXT_HUB_OFF = EXT_AI_OFF + AI_COUNT, EXT_LANDING_OFF = EXT_HUB_OFF + EXT_HUBS;
    public static final int EXT_SIZE = EXT_LANDING_OFF + EXT_HUBS * PER_HUB;
    /** Where the head's terminals end and the first extension's begin. */
    public static final int EXT_BASE = LAYOUT.terminalCount();
    public static final int TERMINAL_COUNT = EXT_BASE + MAX_EXT * EXT_SIZE;

    private CabinetLayout() {}

    // ---- sections ----

    private static int extStart(int section) {
        return EXT_BASE + (section - 1) * EXT_SIZE;
    }

    /** Section a terminal belongs to: 0 the head, 1.. the extensions below. */
    public static int sectionOf(int terminal) {
        return terminal < EXT_BASE ? 0 : Math.min(MAX_EXT, 1 + (terminal - EXT_BASE) / EXT_SIZE);
    }

    /** Offset of a terminal inside its extension's group, or -1 for a head terminal. */
    private static int extLocal(int terminal) {
        return terminal < EXT_BASE || terminal >= TERMINAL_COUNT ? -1 : (terminal - EXT_BASE) % EXT_SIZE;
    }

    public static int sectionOfSlot(int slot) {
        return slot / RAIL;
    }

    // ---- module terminals, by global slot ----

    public static int relayTerminal(int slot, int channel, int side) {
        int section = slot / RAIL, local = slot % RAIL;
        return section == 0 ? ControlsCabinetBlock.relayTerminal(local, channel, side)
                : extStart(section) + EXT_RELAY_OFF + (local * RELAY_CHANNELS + channel) * 2 + side;
    }

    public static int ioTerminal(int slot, int channel) {
        int section = slot / RAIL, local = slot % RAIL;
        return section == 0 ? ControlsCabinetBlock.ioTerminal(local, channel) : extStart(section) + EXT_IO_OFF + local * IO_CHANNELS + channel;
    }

    public static int aiTerminal(int slot, int channel) {
        int section = slot / RAIL, local = slot % RAIL;
        return section == 0 ? ControlsCabinetBlock.aiTerminal(local, channel) : extStart(section) + EXT_AI_OFF + local * ANALOG_CHANNELS + channel;
    }

    /** Global slot of a relay contact terminal, or -1. */
    public static int relaySlot(int terminal) {
        if(terminal >= RELAY_BASE && terminal < JACK)
            return (terminal - RELAY_BASE) / (RELAY_CHANNELS * 2);
        int local = extLocal(terminal);
        return local >= EXT_RELAY_OFF && local < EXT_IO_OFF ? sectionOf(terminal) * RAIL + (local - EXT_RELAY_OFF) / (RELAY_CHANNELS * 2) : -1;
    }

    /** Global channel index (slot * CHANNELS + channel) of a digital channel terminal, or -1. */
    public static int ioChannel(int terminal) {
        if(terminal >= IO_BASE && terminal < IO_BASE + IO_COUNT)
            return terminal - IO_BASE;
        int local = extLocal(terminal);
        return local >= EXT_IO_OFF && local < EXT_AI_OFF ? sectionOf(terminal) * RAIL * IO_CHANNELS + (local - EXT_IO_OFF) : -1;
    }

    /** Global analog index (slot * ANALOG_CHANNELS + channel) of an analog input terminal, or -1. */
    public static int aiIndex(int terminal) {
        if(terminal >= AI_BASE && terminal < AI_BASE + AI_COUNT)
            return terminal - AI_BASE;
        int local = extLocal(terminal);
        return local >= EXT_AI_OFF && local < EXT_HUB_OFF ? sectionOf(terminal) * RAIL * ANALOG_CHANNELS + (local - EXT_AI_OFF) : -1;
    }

    // ---- knockouts ----

    public static int hubCount(int sections) {
        return HUB_COUNT + EXT_HUBS * (sections - 1);
    }

    public static int sectionOfHub(int hub) {
        return hub < HUB_COUNT ? 0 : 1 + (hub - HUB_COUNT) / EXT_HUBS;
    }

    /** The head's four bottom knockouts, which an extension covers. */
    public static boolean isHeadBottomHub(int hub) {
        return hub >= 4 && hub < 8;
    }

    public static int hubTerminal(int hub) {
        return hub < HUB_COUNT ? LAYOUT.hubTerminal(hub) : extStart(sectionOfHub(hub)) + EXT_HUB_OFF + (hub - HUB_COUNT) % EXT_HUBS;
    }

    public static int hubAt(int terminal) {
        int hub = LAYOUT.hubAt(terminal);
        if(hub >= 0)
            return hub;
        int local = extLocal(terminal);
        return local >= EXT_HUB_OFF && local < EXT_LANDING_OFF ? HUB_COUNT + (sectionOf(terminal) - 1) * EXT_HUBS + local - EXT_HUB_OFF : -1;
    }

    public static int conductorTerminal(int hub, int conductor) {
        return hub < HUB_COUNT ? LAYOUT.conductorTerminal(hub, conductor)
                : extStart(sectionOfHub(hub)) + EXT_LANDING_OFF + ((hub - HUB_COUNT) % EXT_HUBS) * PER_HUB + conductor;
    }

    public static int hubOf(int terminal) {
        if(terminal < EXT_BASE)
            return LAYOUT.hubOf(terminal);
        int local = extLocal(terminal);
        return local >= EXT_LANDING_OFF ? HUB_COUNT + (sectionOf(terminal) - 1) * EXT_HUBS + (local - EXT_LANDING_OFF) / PER_HUB : -1;
    }

    public static int conductorOf(int terminal) {
        if(terminal < EXT_BASE)
            return LAYOUT.hubOf(terminal) >= 0 ? LAYOUT.conductorOf(terminal) : -1;
        int local = extLocal(terminal);
        return local >= EXT_LANDING_OFF ? (local - EXT_LANDING_OFF) % PER_HUB : -1;
    }

    public static boolean isPoint(int terminal) {
        if(terminal < EXT_BASE)
            return LAYOUT.isPoint(terminal);
        int local = extLocal(terminal);
        return local >= 0 && local < EXT_HUB_OFF;
    }

    /** Every splice point a cabinet with that many sections has: the head's, then each extension's. */
    public static int[] points(int sections) {
        var head = LAYOUT.points();
        var out = Arrays.copyOf(head, head.length + (sections - 1) * EXT_HUB_OFF);
        int at = head.length;
        for(int section = 1; section < sections; ++section)
            for(int local = 0; local < EXT_HUB_OFF; ++local)
                out[at++] = extStart(section) + local;
        return out;
    }

    // ---- geometry ----

    /** An extension's knockouts in its own frame: four along the bottom, then right lower, right upper, left lower, left upper. */
    public static AABB[] extHubs(double yOffset) {
        var hubs = new AABB[EXT_HUBS];
        for(int i = 0; i < 4; ++i) {
            var head = HUBS[4 + i];
            hubs[i] = new AABB(head.minX, head.minY + yOffset, head.minZ, head.maxX, head.maxY + yOffset, head.maxZ);
        }
        for(int i = 0; i < 4; ++i) {
            var head = HUBS[8 + i];
            hubs[4 + i] = new AABB(head.minX, head.minY + yOffset, head.minZ, head.maxX, head.maxY + yOffset, head.maxZ);
        }
        return hubs;
    }

    public static VoxelShape extensionShape() {
        return DeviceHubs.withHubs(BODY, extHubs(0));
    }

    private static TerminalBoundingBox terminal(net.minecraft.network.chat.Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    /** The head's terminal array with every extension section's group appended, in the head's frame. */
    public static TerminalBoundingBox[] terminals(TerminalBoundingBox[] head) {
        if(head.length != EXT_BASE)
            throw new IllegalStateException("cabinet head terminals: " + head.length + ", layout expects " + EXT_BASE);
        var terminals = Arrays.copyOf(head, TERMINAL_COUNT);
        for(int section = 1; section <= MAX_EXT; ++section) {
            int start = extStart(section);
            for(int local = 0; local < RAIL; ++local) {
                int slot = section * RAIL + local;
                for(int ch = 0; ch < RELAY_CHANNELS; ++ch) {
                    terminals[relayTerminal(slot, ch, 0)] = terminal(Lang.builder().translate("controls.relay_com", slot + 1, ch + 1).style(ChatFormatting.GOLD).component(), HIDDEN).withColor(0xE0A030);
                    terminals[relayTerminal(slot, ch, 1)] = terminal(Lang.builder().translate("controls.relay_no", slot + 1, ch + 1).style(ChatFormatting.YELLOW).component(), HIDDEN).withColor(0xF0E060);
                }
                for(int ch = 0; ch < IO_CHANNELS; ++ch)
                    terminals[ioTerminal(slot, ch)] = terminal(Lang.builder().translate("controls.io", slot + 1, ch + 1).style(ChatFormatting.AQUA).component(), HIDDEN).withColor(0x70C8F0);
                for(int ch = 0; ch < ANALOG_CHANNELS; ++ch)
                    terminals[aiTerminal(slot, ch)] = terminal(Lang.builder().translate("controls.ai", slot + 1, ch + 1).style(ChatFormatting.GREEN).component(), HIDDEN).withColor(0x90E0A0);
            }
            var hubs = extHubs(-16 * section);
            for(int h = 0; h < EXT_HUBS; ++h) {
                int hub = HUB_COUNT + (section - 1) * EXT_HUBS + h;
                terminals[start + EXT_HUB_OFF + h] = terminal(DeviceHubs.hubName(hub), hubs[h]).withColor(0x2FB8D6);
                for(int k = 0; k < PER_HUB; ++k) {
                    var name = Lang.builder().add(DeviceHubs.hubName(hub)).text(" ").add(ConductorColors.name(k)).component();
                    terminals[start + EXT_LANDING_OFF + h * PER_HUB + k] = terminal(name, HIDDEN).withColor(ConductorColors.rgb(k));
                }
            }
        }
        return terminals;
    }
}
