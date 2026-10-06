package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.device.helm.HelmBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helm as peripheral "powergrid_helm". Events: 'helm_key' (name, pressed) for every key change,
 * 'helm_taken' (player) and 'helm_released'.
 */
public class HelmPeripheral implements IPeripheral, HelmBlockEntity.Listener {
    private final HelmBlockEntity helm;
    private final String type;
    private final Set<IComputerAccess> computers = new HashSet<>();

    public HelmPeripheral(HelmBlockEntity helm) {
        this(helm, helm instanceof com.nolanbaker.pgmodernized.device.helm.RadioBaseBlockEntity ? "powergrid_radio" : "powergrid_helm");
    }

    public HelmPeripheral(HelmBlockEntity helm, String type) {
        this.helm = helm;
        this.type = type;
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            if(computers.isEmpty())
                helm.addListener(this);
            computers.add(computer);
        }
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            computers.remove(computer);
            if(computers.isEmpty())
                helm.removeListener(this);
        }
    }

    @Override
    public void key(String name, boolean down) {
        synchronized(computers) {
            for(var computer : computers)
                computer.queueEvent("helm_key", name, down);
        }
    }

    @Override
    public void manned(@Nullable String helmsman) {
        synchronized(computers) {
            for(var computer : computers) {
                if(helmsman != null)
                    computer.queueEvent("helm_taken", helmsman);
                else
                    computer.queueEvent("helm_released");
            }
        }
    }

    /** Whether someone is at the helm. */
    @LuaFunction
    public boolean isManned() {
        return helm.isManned();
    }

    /** Name of the player at the helm, or an empty string. */
    @LuaFunction
    public String getHelmsman() {
        return helm.helmsmanName();
    }

    /** Whether that key is held: w, space, left.shift, mouse.left, as the controls screen names them. */
    @LuaFunction
    public boolean isDown(String key) {
        return helm.isDown(key);
    }

    /** Every key held right now, in the order they went down. */
    @LuaFunction
    public List<String> getPressed() {
        return new ArrayList<>(helm.pressed());
    }

    @Override
    public @NotNull String getType() {
        return type;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof HelmPeripheral that && this.helm == that.helm;
    }

    @Override
    public int hashCode() {
        return helm.hashCode();
    }
}
