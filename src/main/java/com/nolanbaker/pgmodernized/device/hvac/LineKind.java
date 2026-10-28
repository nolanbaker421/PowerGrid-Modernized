package com.nolanbaker.pgmodernized.device.hvac;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import net.minecraft.network.chat.Component;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The two refrigerant lines of a split system, laid like conduit between a condenser's and an
 * air handler's ports: the liquid line, small bare copper on the high side, and the suction line,
 * bigger and insulated black on the low side. Each is drawn as the trade size nearest its tube.
 */
public enum LineKind {
    LIQUID("line_set_liquid", "Liquid Line (3/8\")", ConduitSize.HALF, 0xB87333),
    SUCTION("line_set_suction", "Suction Line (3/4\")", ConduitSize.THREE_QUARTER, 0x26282C);

    private final String id;
    private final String label;
    private final ConduitSize drawnAs;
    private final int color;

    LineKind(String id, String label, ConduitSize drawnAs, int color) {
        this.id = id;
        this.label = label;
        this.drawnAs = drawnAs;
        this.color = color;
    }

    public String id() {
        return id;
    }

    /** The item's English name; the generators and lang file mirror it. */
    public String label() {
        return label;
    }

    /** The trade size whose tube this line is drawn as, and whose port it fits. */
    public ConduitSize drawnAs() {
        return drawnAs;
    }

    /** The tube's colour as laid: bare copper, or black insulation. */
    public int color() {
        return color;
    }

    public Component title() {
        return Lang.builder().translate("hvac.line." + id).component();
    }
}
