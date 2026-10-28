package com.nolanbaker.pgmodernized.device.lock;

/**
 * The locks: a brass padlock that hangs on a weatherproof cover's tab, and the utility's barrel
 * lock that seals a CT cabinet, the way a meter cabinet is sealed. Both carry a serial number.
 */
public enum LockKind {
    PADLOCK("padlock", "Padlock", "PL"),
    UTILITY("utility_lock", "Utility Lock", "UL");

    private final String id;
    private final String label;
    private final String prefix;

    LockKind(String id, String label, String prefix) {
        this.id = id;
        this.label = label;
        this.prefix = prefix;
    }

    public String id() {
        return id;
    }

    /** The item's English name; the generators and lang file mirror it. */
    public String label() {
        return label;
    }

    /** A fresh serial number: the kind's letters and seven digits. */
    public String newSerial(net.minecraft.util.RandomSource random) {
        return prefix + "-" + String.format("%07d", random.nextInt(10_000_000));
    }

    public static LockKind byId(String id) {
        for(var kind : values())
            if(kind.id.equals(id))
                return kind;
        return PADLOCK;
    }
}
