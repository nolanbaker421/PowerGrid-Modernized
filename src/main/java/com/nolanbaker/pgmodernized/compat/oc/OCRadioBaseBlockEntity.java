package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.helm.HelmBlockEntity;
import com.nolanbaker.pgmodernized.device.helm.RadioBaseBlockEntity;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/**
 * Radio base as OpenComputers component "powergrid_radio", with the helm's methods. Signals:
 * 'radio_key' (name, pressed) for every key change, 'radio_taken' (player) and 'radio_released'.
 */
public class OCRadioBaseBlockEntity extends RadioBaseBlockEntity implements Environment, HelmBlockEntity.Listener {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_radio");

    public OCRadioBaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        addListener(this);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        OCNodeSupport.load(tag, registries, ocNode, clientPacket);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    @Override
    public void key(String name, boolean down) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "radio_key", name, down);
    }

    @Override
    public void manned(@Nullable String helmsman) {
        if(ocNode.network() == null)
            return;
        if(helmsman != null)
            ocNode.sendToReachable("computer.signal", "radio_taken", helmsman);
        else
            ocNode.sendToReachable("computer.signal", "radio_released");
    }

    @Callback(direct = true, doc = "function():boolean -- Whether someone holds a remote on this base.")
    public Object[] isManned(Context context, Arguments args) {
        return result(isManned());
    }

    @Callback(direct = true, doc = "function():string -- Name of the player on the remote, or an empty string.")
    public Object[] getHelmsman(Context context, Arguments args) {
        return result(helmsmanName());
    }

    @Callback(direct = true, doc = "function(key:string):boolean -- Whether that key is held: w, space, left.shift, mouse.left, as the controls screen names them.")
    public Object[] isDown(Context context, Arguments args) {
        return result(isDown(args.checkString(0)));
    }

    @Callback(direct = true, doc = "function():table -- Every key held right now, in the order they went down.")
    public Object[] getPressed(Context context, Arguments args) {
        return result((Object) pressed().toArray(new String[0]));
    }
}
