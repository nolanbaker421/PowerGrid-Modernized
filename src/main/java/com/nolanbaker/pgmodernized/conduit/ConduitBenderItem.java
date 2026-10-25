package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.List;

/**
 * The hand bender. Clicked on a leg of a laid run, it slides that leg sideways, away from the face
 * that was clicked, by the set step: the legs either side grow or shrink to follow, a leg that
 * shrinks to nothing goes and its corners merge, and a push across a neighbour on another axis
 * puts in a new leg and a new corner. Offsets, kicks and saddles come from a few clicks, like
 * working a stick on a real bender. The stubs into the knockouts never move; bend the next leg.
 * <p>
 * Sneak-clicking a run picks the whole run up into the bender: its sticks, and every wire pulled
 * through it with its colour and label. A loaded bender then lays conduit exactly like the conduit
 * item, spending the sticks it holds first, and when the new run closes on a knockout the wires go
 * back in. Sneak-use in the air hands a held run back as items; unloaded, it cycles the step.
 */
public class ConduitBenderItem extends Item {
    public static final int[] STEPS = {1, 2, 4, 8};
    private static final String STEP = "Step", CONDUIT = "Conduit", COUNT = "Count", CONDUCTORS = "Conductors";
    private static final String SLOT = "Slot", ITEM = "Item", COLOR = "Color", LABEL = "Label";

    public ConduitBenderItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ what it holds

    private static CompoundTag tag(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CONDUIT_BENDER.get());
        return tag == null ? new CompoundTag() : tag.copy();
    }

    private static void store(ItemStack stack, CompoundTag tag) {
        if(tag.isEmpty())
            stack.remove(ModDataComponents.CONDUIT_BENDER.get());
        else
            stack.set(ModDataComponents.CONDUIT_BENDER.get(), tag);
    }

    /** Pixels a leg moves per click. */
    public static int step(ItemStack stack) {
        var tag = tag(stack);
        int step = tag.contains(STEP) ? tag.getInt(STEP) : STEPS[0];
        return Math.max(1, Math.min(STEPS[STEPS.length - 1], step));
    }

    /** Whether a picked-up run is in the bender. */
    public static boolean loaded(ItemStack stack) {
        return stack.getItem() instanceof ConduitBenderItem && tag(stack).contains(CONDUIT);
    }

    /** The conduit item of the held run, or null when empty. */
    @Nullable
    public static Item conduitItem(ItemStack stack) {
        var tag = tag(stack);
        if(!tag.contains(CONDUIT))
            return null;
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString(CONDUIT)));
        return item instanceof ConduitItem ? item : null;
    }

    /** Sticks of the held run not laid yet. */
    public static int sticks(ItemStack stack) {
        return tag(stack).getInt(COUNT);
    }

    public static void setSticks(ItemStack stack, int count) {
        var tag = tag(stack);
        tag.putInt(COUNT, Math.max(0, count));
        store(stack, tag);
    }

    /** Wires of the held run, as they were pulled: slot, wire stack, chosen colour (or -1) and label. */
    public record Held(int slot, ItemStack wire, int color, String label) {}

    public static List<Held> conductors(ItemStack stack) {
        var list = tag(stack).getList(CONDUCTORS, Tag.TAG_COMPOUND);
        var out = new java.util.ArrayList<Held>(list.size());
        for(int i = 0; i < list.size(); ++i) {
            var c = list.getCompound(i);
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.getString(ITEM)));
            out.add(new Held(c.getInt(SLOT), new ItemStack(item, Math.max(1, c.getInt(COUNT))), c.contains(COLOR) ? c.getInt(COLOR) : -1, c.getString(LABEL)));
        }
        return out;
    }

    // ------------------------------------------------------------------ on a run

    /** A loose click on a leg: slide it one step away from the clicked face. Server side. */
    public static InteractionResult nudge(ConduitRunEntity run, Player player, ItemStack stack) {
        if(run.isNipple())
            return fail(player, "message.conduit.nipple");
        var eye = player.getEyePosition();
        var far = eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1));
        int index = -1;
        double best = Double.MAX_VALUE;
        Vec3 hit = null;
        for(int i = 0; i < run.boundingBoxes.size() && i < run.segments.size(); ++i) {
            var clip = run.boundingBoxes.get(i).clip(eye, far);
            if(clip.isEmpty())
                continue;
            double d = clip.get().distanceToSqr(eye);
            if(d < best) {
                best = d;
                index = i;
                hit = clip.get();
            }
        }
        if(index < 0)
            return InteractionResult.FAIL;
        var segment = run.segments.get(index);
        // The face that was clicked is the one the hit point leans toward, off the leg's centre line.
        var start = segment.start != null ? segment.start : run.position();
        var centre = start.add(segment.vector().scale(0.5));
        var off = hit.subtract(centre);
        var along = segment.direction.getAxis();
        Direction.Axis pushAxis = null;
        double lean = 0;
        for(var axis : Direction.Axis.values()) {
            if(axis == along)
                continue;
            double v = axis.choose(off.x, off.y, off.z);
            if(pushAxis == null || Math.abs(v) > Math.abs(lean)) {
                pushAxis = axis;
                lean = v;
            }
        }
        var push = Direction.fromAxisAndDirection(pushAxis, lean > 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        boolean fixStart = run.getEndpoint1() != null, fixEnd = run.getEndpoint2() != null;
        var result = RunReshape.slide(run.segments, index, push, step(stack), fixStart, fixEnd);
        if(result == null || result.points().isEmpty())
            return InteractionResult.FAIL;
        if(fixStart && !RunReshape.same(result.points().get(0), run.segments.get(0)))
            return fail(player, "message.conduit.bend_stub");
        if(fixEnd && !RunReshape.same(result.points().get(result.points().size() - 1), run.segments.get(run.segments.size() - 1)))
            return fail(player, "message.conduit.bend_stub");

        // The run grows or shrinks: sticks come from, or go back to, the player's pockets.
        var entry = run.getWireEntry();
        int wanted = entry == null ? run.getWireCount() : Math.max(1, (int) Math.ceil(RunReshape.totalPixels(result.points()) / 16f * entry.itemsPerMeter()));
        int delta = wanted - run.getWireCount();
        if(delta > 0 && !player.isCreative() && !PlayerUtilities.hasEnoughItems(player, run.getItem(), delta))
            return fail(player, "message.connection_missing_items");

        var pulled = run.conductors();
        run.reshape(result);
        for(var conductor : pulled)
            conductor.follow(run);
        if(!player.isCreative()) {
            if(delta > 0)
                PlayerUtilities.removeItems(player, run.getItem(), delta);
            else if(delta < 0)
                player.getInventory().placeItemBackInInventory(new ItemStack(run.getItem(), -delta));
        }
        run.incrementWireCount(delta);
        run.level().playSound(null, hit.x, hit.y, hit.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    /** Sneak-click: the run and everything in it go into the bender. Server side. */
    public static InteractionResult pickUp(ConduitRunEntity run, Player player, ItemStack stack) {
        if(loaded(stack))
            return fail(player, "message.conduit.bender_loaded");
        if(run.isNipple())
            return fail(player, "message.conduit.nipple");
        ShockDamage.shockAmps(player, run.maxAmps());
        var tag = tag(stack);
        tag.putString(CONDUIT, BuiltInRegistries.ITEM.getKey(run.getItem()).toString());
        tag.putInt(COUNT, run.getWireCount());
        var list = new ListTag();
        var conductors = run.conductors();
        for(var conductor : conductors) {
            var c = new CompoundTag();
            c.putInt(SLOT, conductor.slot());
            c.putString(ITEM, BuiltInRegistries.ITEM.getKey(conductor.getItem()).toString());
            c.putInt(COUNT, conductor.getWireCount());
            if(conductor.hasChosenColor())
                c.putInt(COLOR, conductor.colorIndex());
            c.putString(LABEL, conductor.label());
            list.add(c);
            conductor.discard();
        }
        tag.put(CONDUCTORS, list);
        run.discard();
        store(stack, tag);
        ConduitConnection.clear(stack);
        player.displayClientMessage(Lang.builder().translate("message.conduit.bender_took", run.getWireCount(), run.getItem().getDescription(), conductors.size())
                .style(ChatFormatting.GRAY).component(), true);
        run.level().playSound(null, run.getX(), run.getY(), run.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    /** A run laid from a loaded bender just closed: its wires go back in and the leftover sticks go to the player. Server side. */
    public static void onClosed(Level level, ItemStack stack, @Nullable Player player, ConduitRunEntity run) {
        if(level.isClientSide || !loaded(stack) || player == null)
            return;
        int back = 0, missed = 0;
        for(var held : conductors(stack)) {
            if(run.pullStored(player, held.slot(), held.wire(), held.color(), held.label()))
                ++back;
            else {
                ++missed;
                player.getInventory().placeItemBackInInventory(held.wire());
            }
        }
        int sticks = sticks(stack);
        if(sticks > 0)
            player.getInventory().placeItemBackInInventory(new ItemStack(run.getItem(), sticks));
        unload(stack);
        if(back > 0 || missed > 0)
            player.displayClientMessage(Lang.builder().translate(missed > 0 ? "message.conduit.rewired_short" : "message.conduit.rewired", back, missed)
                    .style(missed > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY).component(), true);
    }

    private static void unload(ItemStack stack) {
        var tag = tag(stack);
        tag.remove(CONDUIT);
        tag.remove(COUNT);
        tag.remove(CONDUCTORS);
        store(stack, tag);
    }

    // ------------------------------------------------------------------ in hand

    /** Loaded, it lays conduit: knockouts through their host, anything else along the ground. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        if(!loaded(stack))
            return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var electric = IElectric.getAt(level, pos);
        if(electric != null) {
            if(level.getBlockEntity(pos) instanceof ISpliceHost)
                return electric.onWire(state, context);
            IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return ConduitPlacement.groundClick(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!player.isShiftKeyDown())
            return InteractionResultHolder.pass(stack);
        if(ConduitConnection.has(stack)) {
            ConduitConnection.clear(stack);
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.connection_reset").style(ChatFormatting.GRAY).component(), true);
        } else if(loaded(stack)) {
            if(!level.isClientSide) {
                var conduit = conduitItem(stack);
                int sticks = sticks(stack);
                if(conduit != null && sticks > 0)
                    player.getInventory().placeItemBackInInventory(new ItemStack(conduit, sticks));
                for(var held : conductors(stack))
                    player.getInventory().placeItemBackInInventory(held.wire());
                unload(stack);
                player.displayClientMessage(Lang.translate("message.conduit.bender_returned").style(ChatFormatting.GRAY).component(), true);
            }
        } else {
            int step = step(stack), next = STEPS[0];
            for(int i = 0; i < STEPS.length; ++i) {
                if(STEPS[i] == step) {
                    next = STEPS[(i + 1) % STEPS.length];
                    break;
                }
            }
            var tag = tag(stack);
            tag.putInt(STEP, next);
            store(stack, tag);
            if(!level.isClientSide)
                player.displayClientMessage(Lang.builder().translate("message.conduit.bend_step", next).style(ChatFormatting.GRAY).component(), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("bender.step", step(stack)).style(ChatFormatting.GRAY).component());
        var conduit = conduitItem(stack);
        if(conduit != null)
            tooltip.add(Lang.builder().translate("bender.holding", sticks(stack), conduit.getDescription(), conductors(stack).size()).style(ChatFormatting.AQUA).component());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || loaded(stack) || ConduitConnection.has(stack);
    }

    private static InteractionResult fail(Player player, String key) {
        player.displayClientMessage(Lang.translate(key).style(ChatFormatting.RED).component(), true);
        return InteractionResult.FAIL;
    }
}
