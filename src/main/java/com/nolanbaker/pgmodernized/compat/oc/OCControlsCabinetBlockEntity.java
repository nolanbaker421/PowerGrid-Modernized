package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import com.nolanbaker.pgmodernized.device.controls.plc.DeviceBridge;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.RELAY_CHANNELS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.CHANNELS;

/**
 * Controls cabinet on OpenComputers. The internal port carries component "powergrid_controls"
 * (slots, channels and cells numbered from 1; signals 'input_change', 'estop', 'device'), and
 * with a PLC module on the rail the external port carries component "powergrid_plc" on a network
 * of its own, through which a computer reads and writes the program's names and the program
 * itself. The PLC reaches every component on the internal network through this class: it lists
 * them with their methods and invokes them for the program.
 */
public class OCControlsCabinetBlockEntity extends ControlsCabinetBlockEntity implements Environment, ControlsCabinetBlockEntity.Listener, DeviceBridge {
    private static final String PLC_TAG = "PlcNode";

    private final Node ocNode = OCNodeSupport.create(this, "powergrid_controls");
    private final PlcPort plcPort = new PlcPort();
    @Nullable
    private Node plcNode;
    @Nullable
    private CompoundTag plcNodeTag;
    private final Map<String, Double> reportedBits = new HashMap<>();

    public OCControlsCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        addListener(this);
        setDeviceBridge(this);
    }

    // ---- ports ----

    /** Port 0 is the cabinet's own node; port 1 is the PLC's, which exists only while a PLC module is fitted. */
    public @Nullable Node nodeForPort(int port) {
        if(port != 1)
            return ocNode;
        if(!hasPlc())
            return null;
        if(plcNode == null) {
            plcNode = Network.newNode(plcPort, Visibility.Network).withComponent("powergrid_plc").create();
            if(plcNodeTag != null) {
                try {
                    plcNode.loadData(plcNodeTag, level == null ? null : level.registryAccess());
                } catch(Exception ignored) {}
                plcNodeTag = null;
            }
        }
        return plcNode;
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || isRemoved())
            return;
        if(hasPlc()) {
            var node = nodeForPort(1);
            if(node != null && node.network() == null)
                Network.joinNewNetwork(node);
            // Tell the external port about network bits the program moved.
            if(node != null && node.network() != null) {
                for(var entry : plcBits().entrySet()) {
                    if(!entry.getKey().startsWith("N"))
                        continue;
                    var was = reportedBits.get(entry.getKey());
                    if(was == null || !was.equals(entry.getValue())) {
                        reportedBits.put(entry.getKey(), entry.getValue());
                        node.sendToReachable("computer.signal", "plc_bit", entry.getKey(), entry.getValue());
                    }
                }
            }
        } else if(plcNode != null) {
            OCNodeSupport.remove(plcNode);
            plcNode = null;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
        OCNodeSupport.remove(plcNode);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
        OCNodeSupport.remove(plcNode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        OCNodeSupport.load(tag, registries, ocNode, clientPacket);
        if(!clientPacket && tag.contains(PLC_TAG)) {
            if(plcNode != null) {
                try {
                    plcNode.loadData(tag.getCompound(PLC_TAG), registries);
                } catch(Exception ignored) {}
            } else {
                plcNodeTag = tag.getCompound(PLC_TAG);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
        if(!clientPacket && plcNode != null) {
            var nodeTag = new CompoundTag();
            plcNode.saveData(nodeTag, registries);
            tag.put(PLC_TAG, nodeTag);
        }
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    // ---- the PLC's view of the internal network ----

    @Override
    public List<Device> devices() {
        var out = new ArrayList<Device>();
        var network = ocNode.network();
        if(network == null)
            return out;
        var components = new ArrayList<Component>();
        for(var node : network.nodes()) {
            if(node != ocNode && node != plcNode && node instanceof Component component && component.canBeSeenFrom(ocNode))
                components.add(component);
        }
        components.sort((a, b) -> {
            int byType = a.name().compareTo(b.name());
            return byType != 0 ? byType : a.address().compareTo(b.address());
        });
        var counts = new HashMap<String, Integer>();
        for(var component : components) {
            String type = component.name();
            int n = counts.merge(type, 1, Integer::sum);
            String alias = (type.startsWith("powergrid_") ? type.substring("powergrid_".length()) : type) + n;
            out.add(new Device(alias, type, component.address(), new ArrayList<>(component.methods())));
        }
        return out;
    }

    @Override
    public @Nullable Object call(String alias, String method, List<Object> args) throws Exception {
        var network = ocNode.network();
        if(network == null)
            return null;
        for(var device : devices()) {
            if(!device.alias().equals(alias))
                continue;
            if(!(network.node(device.address()) instanceof Component component))
                return null;
            if(!component.methods().contains(method))
                throw new Exception(alias + " has no method " + method);
            var typed = typedArguments(component, method, args);
            Object[] result;
            try {
                result = component.invoke(method, new PlcContext(), typed);
            } catch(IllegalArgumentException e) {
                // The doc did not say; the method did. Try the numbers the other way round once.
                var message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
                if(message.contains("boolean"))
                    result = component.invoke(method, new PlcContext(), flipped(typed, true));
                else if(message.contains("number") || message.contains("integer"))
                    result = component.invoke(method, new PlcContext(), flipped(typed, false));
                else
                    throw e;
            }
            return result == null || result.length == 0 ? null : result[0];
        }
        return null;
    }

    /**
     * The program's pins carry numbers, but many methods want booleans. A method's doc string names
     * its parameters as {@code function(enable:boolean, speed:number)}; each argument is converted
     * to the type the doc gives it, nonzero meaning true.
     */
    private static Object[] typedArguments(Component component, String method, List<Object> args) {
        var out = args.toArray();
        String doc;
        try {
            var annotation = component.annotation(method);
            doc = annotation == null ? null : annotation.doc();
        } catch(Exception e) {
            doc = null;
        }
        if(doc == null)
            return out;
        int open = doc.indexOf('('), close = doc.indexOf(')', open + 1);
        if(open < 0 || close < 0)
            return out;
        var params = doc.substring(open + 1, close).split(",");
        for(int i = 0; i < out.length && i < params.length; ++i) {
            var spec = params[i].strip().toLowerCase(java.util.Locale.ROOT);
            int colon = spec.indexOf(':');
            if(colon < 0)
                continue;
            var type = spec.substring(colon + 1).strip();
            if(type.startsWith("boolean") && out[i] instanceof Number n)
                out[i] = n.doubleValue() != 0;
            else if((type.startsWith("number") || type.startsWith("int")) && out[i] instanceof Boolean b)
                out[i] = b ? 1 : 0;
            else if(type.startsWith("string") && out[i] instanceof Number n)
                out[i] = com.nolanbaker.pgmodernized.device.controls.plc.NodeType.format(n.doubleValue());
        }
        return out;
    }

    /** Every number as a boolean, or every boolean as a number. */
    private static Object[] flipped(Object[] args, boolean toBoolean) {
        var out = args.clone();
        for(int i = 0; i < out.length; ++i) {
            if(toBoolean && out[i] instanceof Number n)
                out[i] = n.doubleValue() != 0;
            else if(!toBoolean && out[i] instanceof Boolean b)
                out[i] = b ? 1 : 0;
        }
        return out;
    }

    /** The context a program's calls run in: no machine, nothing to pause, every call allowed. */
    private final class PlcContext implements Context {
        @Override public Node node() { return ocNode; }
        @Override public boolean canInteract(String player) { return true; }
        @Override public boolean isRunning() { return true; }
        @Override public boolean isPaused() { return false; }
        @Override public boolean start() { return false; }
        @Override public boolean pause(double seconds) { return false; }
        @Override public boolean stop() { return false; }
        @Override public void consumeCallBudget(double callCost) {}
        @Override public boolean signal(String name, Object... args) { return false; }
    }

    // ---- the external port ----

    /** Component "powergrid_plc": the program's names, read and written from outside, and the program itself. */
    public final class PlcPort implements Environment {
        @Override public Node node() { return plcNode; }
        @Override public void onConnect(Node node) {}
        @Override public void onDisconnect(Node node) {}
        @Override public void onMessage(Message message) {}

        @Callback(direct = true, doc = "function(name:string):number -- Read a name the program sees: X1.1, Y1.1, R1.1, V1.run, D3, E, P, C1, T1, N1. Booleans are 0 or 1.")
        public Object[] get(Context context, Arguments args) {
            var text = plcReadText(args.checkString(0));
            if(text != null)
                return result(text);
            double v = plcRead(args.checkString(0));
            if(Double.isNaN(v))
                return result(null, "unknown name");
            return result(v);
        }

        @Callback(doc = "function(name:string, value:number|boolean):boolean -- Write a network bit N1 to N32, a coil C1 to C32, or anything the program may write.")
        public Object[] set(Context context, Arguments args) {
            if(isTextName(args.checkString(0).toUpperCase(java.util.Locale.ROOT)))
                return result(plcWriteText(args.checkString(0), args.isString(1) ? args.checkString(1) : String.valueOf(args.checkAny(1))));
            double v = args.isBoolean(1) ? (args.checkBoolean(1) ? 1 : 0) : args.checkDouble(1);
            return result(plcWrite(args.checkString(0), v));
        }

        @Callback(direct = true, doc = "function():string -- The text of the program's first Rungs block, or an empty string.")
        public Object[] getRungs(Context context, Arguments args) {
            return result(rungsText());
        }

        @Callback(doc = "function(text:string):boolean, string -- Replace the first Rungs block's text, adding the block when the program has none; false and the message when it does not compile.")
        public Object[] setRungs(Context context, Arguments args) {
            setRungsText(args.checkString(0));
            return result(plcError().isEmpty(), plcError());
        }

        @Callback(direct = true, doc = "function():string -- The current compile or scan error, or an empty string.")
        public Object[] getError(Context context, Arguments args) {
            return result(plcError());
        }

        @Callback(direct = true, doc = "function():table -- Devices on the cabinet's internal network: alias -> {type, address, methods}.")
        public Object[] getDevices(Context context, Arguments args) {
            Map<String, Object> map = new HashMap<>();
            for(var device : devices()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("type", device.type());
                entry.put("address", device.address());
                entry.put("methods", device.methods().toArray(new String[0]));
                map.put(device.alias(), entry);
            }
            return result(map);
        }

        @Callback(direct = true, doc = "function():boolean -- Whether the cabinet's control bus is live.")
        public Object[] isPowered(Context context, Arguments args) {
            return result(OCControlsCabinetBlockEntity.this.isPowered());
        }

        @Callback(direct = true, doc = "function():boolean -- Whether an E-stop on the door is pressed.")
        public Object[] isEStopped(Context context, Arguments args) {
            return result(OCControlsCabinetBlockEntity.this.isEStopped());
        }
    }

    // ---- signals on the internal port ----

    @Override
    public void input(int slot, int channel, boolean state) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "input_change", slot + 1, channel + 1, state);
    }

    @Override
    public void estop(boolean active) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "estop", active);
        if(plcNode != null && plcNode.network() != null)
            plcNode.sendToReachable("computer.signal", "estop", active);
    }

    @Override
    public void device(int cell, PanelDevice type, int state) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "device", cell + 1, type.key(), state);
    }

    // ---- callbacks on the internal port ----

    @Callback(direct = true, doc = "function():boolean -- Whether the control bus is live: a power supply module with at least 50 V on line and neutral.")
    public Object[] isPowered(Context context, Arguments args) {
        return result(isPowered());
    }

    @Callback(direct = true, doc = "function():number -- Volts between line and neutral.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) volts());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether any E-stop on the door is pressed.")
    public Object[] isEStopped(Context context, Arguments args) {
        return result(isEStopped());
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):number -- An analog input module's channel: volts on its terminal against neutral, or a wired dial's percent.")
    public Object[] getAnalog(Context context, Arguments args) {
        return result((double) analogIn(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(doc = "function(slot:number, channel:number, value:number):boolean -- Set an analog output module's channel; false if that slot holds no analog output module.")
    public Object[] setAnalog(Context context, Arguments args) {
        return result(setAnalogOut(args.checkInteger(0) - 1, args.checkInteger(1) - 1, (float) args.checkDouble(2)));
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):number -- An analog output module's channel as last set.")
    public Object[] getAnalogOut(Context context, Arguments args) {
        return result((double) analogOut(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(direct = true, doc = "function():table -- Module in each rail slot, 1 to 6: power_supply, digital_in, digital_out, relay, vfd, plc, analog_in, analog_out, or nil.")
    public Object[] getModules(Context context, Arguments args) {
        Map<Integer, String> map = new HashMap<>();
        for(int slot = 0; slot < slots(); ++slot)
            if(module(slot) != null)
                map.put(slot + 1, module(slot).key());
        return result(map);
    }

    @Callback(direct = true, doc = "function():table -- Device in each door cell, 1 to 6 (top left to bottom right): e_stop, toggle, momentary, selector, led, display, dial, or nil.")
    public Object[] getDevices(Context context, Arguments args) {
        Map<Integer, String> map = new HashMap<>();
        for(int cell = 0; cell < cells(); ++cell)
            if(device(cell) != null)
                map.put(cell + 1, device(cell).key());
        return result(map);
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):boolean -- An input module's channel.")
    public Object[] getInput(Context context, Arguments args) {
        return result(input(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(direct = true, doc = "function(slot:number):table -- All eight inputs of a module.")
    public Object[] getInputs(Context context, Arguments args) {
        int slot = args.checkInteger(0) - 1;
        Map<Integer, Boolean> map = new HashMap<>();
        for(int ch = 0; ch < CHANNELS; ++ch)
            map.put(ch + 1, input(slot, ch));
        return result(map);
    }

    @Callback(doc = "function(slot:number, channel:number, on:boolean):boolean -- Set an output module's channel; false if that slot holds no output module.")
    public Object[] setOutput(Context context, Arguments args) {
        return result(setOutput(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkBoolean(2)));
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):boolean -- An output module's channel.")
    public Object[] getOutput(Context context, Arguments args) {
        return result(output(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(doc = "function(slot:number, channel:number, closed:boolean):boolean -- Close or open a relay module's contact (channels 1 and 2); false if that slot holds no relay module.")
    public Object[] setRelay(Context context, Arguments args) {
        return result(setRelay(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkBoolean(2)));
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):boolean -- Whether a relay contact is commanded closed.")
    public Object[] getRelay(Context context, Arguments args) {
        return result(relay(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(doc = "function(cell:number, value:number):boolean -- Write a number display on the door, -999 to 9999; false if that cell holds no display.")
    public Object[] setDisplay(Context context, Arguments args) {
        return result(setDisplay(args.checkInteger(0) - 1, args.checkInteger(1)));
    }

    @Callback(direct = true, doc = "function(cell:number):number -- What the device in a cell is doing: 0 or 1 for buttons and lights, 0 to 2 for a selector, 0 to 100 for a dial, the value of a display.")
    public Object[] getDeviceState(Context context, Arguments args) {
        int cell = args.checkInteger(0) - 1;
        return result(cell >= 0 && cell < cells() ? deviceState(cell) : 0);
    }

    @Callback(direct = true, doc = "function():number, number, number -- Rail slots, channels per input or output module, channels per relay module.")
    public Object[] getLimits(Context context, Arguments args) {
        return result(slots(), CHANNELS, RELAY_CHANNELS);
    }
}
