package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerLockItem;
import com.nolanbaker.pgmodernized.network.Cat6CableItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
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

    /** Plug-on breakers keyed by rated current, in the order of {@link BreakerItem#RATINGS}. */
    public static final Map<Integer, ItemEntry<BreakerItem>> BREAKERS;

    /** Lockout hasp for a breaker handle. */
    public static final ItemEntry<BreakerLockItem> BREAKER_LOCK = REGISTRATE.item("breaker_lock", BreakerLockItem::new)
            .model(NonNullBiConsumer.noop())
            .lang("Breaker Lock")
            .register();

    /** Conduit per trade size, laid like block wire. Wire stats in wire_types/conduit_<size>.json. */
    public static final Map<ConduitSize, ItemEntry<ConduitItem>> CONDUIT;

    static {
        var breakers = new LinkedHashMap<Integer, ItemEntry<BreakerItem>>();
        for(int rating : BreakerItem.RATINGS) {
            breakers.put(rating, REGISTRATE.item("breaker_" + rating + "a", p -> new BreakerItem(p, rating))
                    .model(NonNullBiConsumer.noop())
                    .lang(rating + " A Breaker")
                    .register());
        }
        breakers.put(BreakerItem.BLANK, REGISTRATE.item("breaker_blank", p -> new BreakerItem(p, BreakerItem.BLANK))
                .model(NonNullBiConsumer.noop())
                .lang("Breaker Blank")
                .register());
        BREAKERS = Collections.unmodifiableMap(breakers);

        var conduit = new EnumMap<ConduitSize, ItemEntry<ConduitItem>>(ConduitSize.class);
        for(var size : ConduitSize.values()) {
            conduit.put(size, REGISTRATE.item("conduit_" + size.id(), p -> new ConduitItem(p, size))
                    .model(NonNullBiConsumer.noop())
                    .lang(size.label() + " Conduit")
                    .register());
        }
        CONDUIT = Collections.unmodifiableMap(conduit);
    }

    /** One breaker item of the given rating, or an empty stack if no such breaker exists. */
    public static ItemStack breaker(int rating) {
        var entry = BREAKERS.get(rating);
        return entry == null ? ItemStack.EMPTY : entry.asStack();
    }

    public static void register() {}
}
