package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.conduit.gang.GangDeviceItem;
import com.nolanbaker.pgmodernized.conduit.gang.GangDevice;
import com.nolanbaker.pgmodernized.radio.CellCardItem;
import com.nolanbaker.pgmodernized.device.gear.GearComponentItem;
import com.nolanbaker.pgmodernized.device.gear.GearKind;
import com.nolanbaker.pgmodernized.device.helm.RadioRemoteItem;
import com.nolanbaker.pgmodernized.rail.RailShoeItem;
import com.nolanbaker.pgmodernized.chain.CableChainItem;
import net.minecraft.world.item.Rarity;
import com.nolanbaker.pgmodernized.conduit.ConduitCover;
import com.nolanbaker.pgmodernized.conduit.ConduitCoverItem;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.BuildingWireItem;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.FlexConduitItem;
import com.nolanbaker.pgmodernized.device.controls.ControlModule;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import com.nolanbaker.pgmodernized.conduit.WireGauge;
import com.nolanbaker.pgmodernized.device.breaker.BreakerFrame;
import com.nolanbaker.pgmodernized.device.breaker.BreakerItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerLockItem;
import com.nolanbaker.pgmodernized.device.breaker.BusBarItem;
import com.nolanbaker.pgmodernized.network.Cat6CableItem;
import com.nolanbaker.pgmodernized.radio.AlignmentScopeItem;
import com.nolanbaker.pgmodernized.radio.CoaxCableItem;
import com.nolanbaker.pgmodernized.radio.MeterAntennaItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.nolanbaker.pgmodernized.PowerGridModernized.REGISTRATE;

public class ModItems {
    /** Wire stats live in data/powergrid_modernized/powergrid/wire_types/cat6_cable.json. */
    public static final ItemEntry<Cat6CableItem> CAT6_CABLE = REGISTRATE.item("cat6_cable", Cat6CableItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Cat6 Cable")
            .register();

    /** A smart meter's radio: right-click a CT cabinet with it and the cabinet reports over the air. */
    public static final ItemEntry<MeterAntennaItem> METER_ANTENNA = REGISTRATE.item("meter_antenna", MeterAntennaItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Meter Antenna")
            .register();

    /** The devices a gang box takes, by kind. */
    public static final Map<GangDevice, ItemEntry<GangDeviceItem>> GANG_DEVICES = gangDevices();

    private static Map<GangDevice, ItemEntry<GangDeviceItem>> gangDevices() {
        var map = new EnumMap<GangDevice, ItemEntry<GangDeviceItem>>(GangDevice.class);
        for(var device : GangDevice.values()) {
            map.put(device, REGISTRATE.item("gang_" + device.id(), props -> new GangDeviceItem(props, device))
                    .model(NonNullBiConsumer.noop())
                    .lang(device.label())
                    .register());
        }
        return Collections.unmodifiableMap(map);
    }

    /** One of that device, as an item. */
    public static ItemStack gangDevice(GangDevice device) {
        return GANG_DEVICES.get(device).asStack();
    }

    /** The installer's boresight scope: sights two antennas in on each other. */
    public static final ItemEntry<AlignmentScopeItem> ALIGNMENT_SCOPE = REGISTRATE.item("alignment_scope", AlignmentScopeItem::new)
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop())
            .lang("Alignment Scope")
            .register();

    /** A SIM for a computer: an OpenComputers card or a pocket computer upgrade that joins a carrier's towers. */
    public static final ItemEntry<CellCardItem> CELL_CARD = REGISTRATE.item("cell_card", CellCardItem::new)
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop())
            .lang("Cell Card")
            .register();

    /** Coax from a radio to its antenna; stats in data/powergrid_modernized/powergrid/wire_types/coax_cable.json. */
    public static final ItemEntry<CoaxCableItem> COAX_CABLE = REGISTRATE.item("coax_cable", CoaxCableItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Coax Cable")
            .register();

    /** Proof you sat the course. Only found in the Electrical Training Center of a village; the inspector asks for it. */
    public static final ItemEntry<Item> ELECTRICAL_LICENSE = REGISTRATE.item("electrical_license", Item::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
            .model(NonNullBiConsumer.noop())
            .lang("Electrical License")
            .register();

    /** Offcuts from pulling wire and splicing; village smiths buy it. */
    public static final ItemEntry<Item> COPPER_SCRAP = REGISTRATE.item("copper_scrap", Item::new)
            .model(NonNullBiConsumer.noop())
            .lang("Copper Scrap")
            .register();

    /** The cable chain: a wire item only so the chain entities can carry it; WireGuard routes its clicks. */
    public static final ItemEntry<CableChainItem> CABLE_CHAIN = REGISTRATE.item("cable_chain", CableChainItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Cable Chain")
            .register();

    /** Collector shoe: a crafting part, and the hidden wire type of the rail pickups (wire_types/rail_shoe.json). */
    public static final ItemEntry<RailShoeItem> RAIL_SHOE = REGISTRATE.item("rail_shoe", RailShoeItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Collector Shoe")
            .register();

    /** Breakers: pole count (1, 2, 3), then frame. */
    public static final Map<Integer, Map<BreakerFrame, ItemEntry<BreakerItem>>> BREAKERS;

    /** Blank filler plate for an unused space. */
    /** A single-pole 1-50 A breaker that also trips on ground-fault current. */
    public static final ItemEntry<BreakerItem> BREAKER_GFCI = REGISTRATE.item("breaker_50a_gfci", p -> new BreakerItem(p, BreakerFrame.F50, 1, true))
            .model(NonNullBiConsumer.noop())
            .lang("1-50 A GFCI Breaker")
            .register();
    public static final ItemEntry<BreakerItem> BREAKER_BLANK = REGISTRATE.item("breaker_blank", BreakerItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Breaker Blank")
            .register();

    /** Lockout hasp for a breaker handle. */
    public static final ItemEntry<BreakerLockItem> BREAKER_LOCK = REGISTRATE.item("breaker_lock", BreakerLockItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Breaker Lockout")
            .register();

    /** Copper bus bar: a crafting part, and the hidden wire type that joins switchgear sections (wire_types/bus_bar.json). */
    public static final ItemEntry<BusBarItem> BUS_BAR = REGISTRATE.item("bus_bar", BusBarItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Copper Bus Bar")
            .register();

    /** What goes into a switchgear cell besides bus bars and 3-pole breakers. Fuse sets per rating in {@link #GEAR_FUSES}. */
    public static final ItemEntry<GearComponentItem> NEUTRAL_BAR = gearComponent("neutral_bar", "Neutral Bar", GearKind.NEUTRAL_BAR, 0);
    public static final ItemEntry<GearComponentItem> GEAR_LUGS = gearComponent("gear_lugs", "Lug Block", GearKind.LUGS, 0);
    public static final ItemEntry<GearComponentItem> GEAR_SWITCH = gearComponent("gear_switch", "Switch Unit", GearKind.SWITCH, 0);
    public static final ItemEntry<GearComponentItem> GEAR_CONTACTOR = gearComponent("gear_contactor", "Contactor", GearKind.CONTACTOR, 0);
    public static final ItemEntry<GearComponentItem> GROUND_BAR = gearComponent("ground_bar", "Ground Bar", GearKind.GROUND_BAR, 0);
    public static final ItemEntry<GearComponentItem> GEAR_STARTER = gearComponent("gear_starter", "Combination Starter", GearKind.STARTER, 0);
    public static final ItemEntry<GearComponentItem> GEAR_METER = gearComponent("gear_meter", "Metering Unit", GearKind.METER, 0);
    public static final int[] FUSE_RATINGS = {100, 200, 400, 800};
    public static final Map<Integer, ItemEntry<GearComponentItem>> GEAR_FUSES;

    private static ItemEntry<GearComponentItem> gearComponent(String id, String name, GearKind kind, int rating) {
        return REGISTRATE.item(id, p -> new GearComponentItem(p, kind, rating))
                .model(NonNullBiConsumer.noop())
                .lang(name)
                .register();
    }

    /** A fuse set of that rating, or the nearest rating at or above it. */
    public static ItemStack fuse(int rating) {
        for(int r : FUSE_RATINGS) {
            if(r >= rating)
                return GEAR_FUSES.get(r).asStack();
        }
        return GEAR_FUSES.get(FUSE_RATINGS[FUSE_RATINGS.length - 1]).asStack();
    }

    /** Cover plates for the conduit box: a blank, and the node plate with twelve terminals. */
    public static final ItemEntry<ConduitCoverItem> CONDUIT_COVER_BLANK = REGISTRATE.item("conduit_cover_blank", p -> new ConduitCoverItem(p, ConduitCover.BLANK))
            .model(NonNullBiConsumer.noop())
            .lang("Blank Cover Plate")
            .register();
    public static final ItemEntry<ConduitCoverItem> CONDUIT_COVER_NODE = REGISTRATE.item("conduit_cover_node", p -> new ConduitCoverItem(p, ConduitCover.NODE))
            .model(NonNullBiConsumer.noop())
            .lang("Node Cover Plate")
            .register();
    public static final ItemEntry<ConduitCoverItem> CONDUIT_COVER_LIGHT = REGISTRATE.item("conduit_cover_light", p -> new ConduitCoverItem(p, ConduitCover.LIGHT_OFF))
            .model(NonNullBiConsumer.noop())
            .lang("Light Socket Cover")
            .register();
    public static final ItemEntry<ConduitCoverItem> CONDUIT_COVER_RECEPTACLE = REGISTRATE.item("conduit_cover_receptacle", p -> new ConduitCoverItem(p, ConduitCover.RECEPTACLE))
            .model(NonNullBiConsumer.noop())
            .lang("Receptacle Cover")
            .register();

    /** The plate item that comes off a box with that cover. */
    public static ItemStack coverPlate(ConduitCover cover) {
        return switch(cover.itemCover()) {
            case NODE -> CONDUIT_COVER_NODE.asStack();
            case LIGHT_OFF -> CONDUIT_COVER_LIGHT.asStack();
            case RECEPTACLE -> CONDUIT_COVER_RECEPTACLE.asStack();
            default -> CONDUIT_COVER_BLANK.asStack();
        };
    }

    /** A door for a breaker panel that hides its breakers. */
    public static final ItemEntry<Item> PANEL_COVER = REGISTRATE.item("panel_cover", Item::new)
            .model(NonNullBiConsumer.noop())
            .lang("Panel Cover")
            .register();

    /** Conduit per trade size, laid like block wire. Wire stats in wire_types/conduit_<size>.json. */
    public static final Map<ConduitSize, ItemEntry<ConduitItem>> CONDUIT;
    /** 1" flexible conduit: a whip from one knockout straight to another, on any two bodies. */
    public static final ItemEntry<FlexConduitItem> CONDUIT_FLEX_ONE;
    /** What clips onto a controls cabinet's rail, and what mounts in its door. */
    public static final ItemEntry<RadioRemoteItem> RADIO_REMOTE;
    public static final Map<ControlModule, ItemEntry<ControlModule.ModuleItem>> CONTROL_MODULES;
    public static final Map<PanelDevice, ItemEntry<PanelDevice.DeviceItem>> PANEL_DEVICES;

    /** THHN building wire per gauge. Wire stats in wire_types/wire_<gauge>.json. */
    public static final Map<WireGauge, ItemEntry<BuildingWireItem>> BUILDING_WIRE;

    static {
        var fuses = new LinkedHashMap<Integer, ItemEntry<GearComponentItem>>();
        for(int rating : FUSE_RATINGS)
            fuses.put(rating, gearComponent("gear_fuse_" + rating, rating + " A Fuse Set", GearKind.FUSE, rating));
        GEAR_FUSES = Collections.unmodifiableMap(fuses);
        var breakers = new LinkedHashMap<Integer, Map<BreakerFrame, ItemEntry<BreakerItem>>>();
        for(int poles = 1; poles <= 3; ++poles) {
            var byFrame = new EnumMap<BreakerFrame, ItemEntry<BreakerItem>>(BreakerFrame.class);
            for(var frame : BreakerFrame.values()) {
                final int p = poles;
                String id = "breaker_" + frame.id() + (poles == 1 ? "" : "_" + poles + "p");
                String name = frame.label() + (poles == 1 ? " Breaker" : " " + poles + "-Pole Breaker");
                byFrame.put(frame, REGISTRATE.item(id, props -> new BreakerItem(props, frame, p))
                        .model(NonNullBiConsumer.noop())
                        .lang(name)
                        .register());
            }
            breakers.put(poles, Collections.unmodifiableMap(byFrame));
        }
        BREAKERS = Collections.unmodifiableMap(breakers);

        var conduit = new EnumMap<ConduitSize, ItemEntry<ConduitItem>>(ConduitSize.class);
        for(var size : ConduitSize.values()) {
            conduit.put(size, REGISTRATE.item("conduit_" + size.id(), p -> new ConduitItem(p, size))
                    .model(NonNullBiConsumer.noop())
                    .lang(size.label() + " Conduit")
                    .register());
        }
        CONDUIT = Collections.unmodifiableMap(conduit);
        CONDUIT_FLEX_ONE = REGISTRATE.item("conduit_flex_one", p -> new FlexConduitItem(p, ConduitSize.ONE))
                .model(NonNullBiConsumer.noop())
                .lang("1\" Flexible Conduit")
                .register();
        RADIO_REMOTE = REGISTRATE.item("radio_remote", RadioRemoteItem::new)
                .model(NonNullBiConsumer.noop())
                .lang("Radio Remote")
                .register();

        var modules = new EnumMap<ControlModule, ItemEntry<ControlModule.ModuleItem>>(ControlModule.class);
        String[] moduleNames = {"24 V Power Supply Module", "8-Channel Input Module", "8-Channel Output Module", "2-Channel Relay Module", "VFD Control Module", "PLC Module", "4-Channel Analog Input Module", "4-Channel Analog Output Module"};
        for(var module : ControlModule.values()) {
            modules.put(module, REGISTRATE.item(module.id(), p -> new ControlModule.ModuleItem(p, module))
                    .model(NonNullBiConsumer.noop())
                    .lang(moduleNames[module.ordinal()])
                    .register());
        }
        CONTROL_MODULES = Collections.unmodifiableMap(modules);
        var devices = new EnumMap<PanelDevice, ItemEntry<PanelDevice.DeviceItem>>(PanelDevice.class);
        String[] deviceNames = {"E-Stop Button", "Toggle Button", "Momentary Button", "Selector Switch", "Pilot Light", "Number Display", "Speed Dial", "Up Button", "Down Button", "Buzzer"};
        for(var device : PanelDevice.values()) {
            devices.put(device, REGISTRATE.item(device.id(), p -> new PanelDevice.DeviceItem(p, device))
                    .model(NonNullBiConsumer.noop())
                    .lang(deviceNames[device.ordinal()])
                    .register());
        }
        PANEL_DEVICES = Collections.unmodifiableMap(devices);

        var wires = new EnumMap<WireGauge, ItemEntry<BuildingWireItem>>(WireGauge.class);
        for(var gauge : WireGauge.values()) {
            wires.put(gauge, REGISTRATE.item(gauge.id(), p -> new BuildingWireItem(p, gauge))
                    .model(NonNullBiConsumer.noop())
                    .lang(gauge.label() + " THHN Wire")
                    .register());
        }
        BUILDING_WIRE = Collections.unmodifiableMap(wires);
    }

    /** One breaker item of the given frame and pole count, or an empty stack if none exists. */
    public static ItemStack breaker(BreakerFrame frame, int poles) {
        var byFrame = BREAKERS.get(Math.max(1, poles));
        var entry = byFrame == null ? null : byFrame.get(frame);
        return entry == null ? ItemStack.EMPTY : entry.asStack();
    }

    public static ItemStack blank() {
        return BREAKER_BLANK.asStack();
    }

    public static void register() {}
}
