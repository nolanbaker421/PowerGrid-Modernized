package com.nolanbaker.pgmodernized.conduit.fire;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.StateTerminals;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The fire alarm control panel: a small red cabinet on a wall, fed 120 V through a knockout, that
 * keeps 24 V DC on two loops of pulled wire. Detectors sit on the initiating loop and close a
 * resistor across it in alarm; the panel sees the current and goes to Alarm, and drives the
 * notification loop that the horn strobes sit on. Silence drops the notification loop to twelve
 * volts, enough for the strobes and not the horns; Reset clears an alarm once every detector is
 * quiet. A small supervisory voltage sits on each loop the rest of the time, and a loop with
 * nothing on it is Trouble. The panel is a redstone source while in alarm.
 * <p>
 * Geometry in the north frame: the body on the wall at z = 11..16, two knockouts on the top and
 * two on the bottom. The door carries three lights, power, alarm and trouble, and two buttons,
 * Silence on the viewer's left and Reset on the right.
 */
public class FireAlarmPanelBlock extends HorizontalElectricBlock implements IBE<FireAlarmPanelBlockEntity> {
    public static final int TERMINAL_LINE = 0, TERMINAL_NEUTRAL = 1, TERMINAL_GROUND = 2;
    public static final int TERMINAL_IDC_PLUS = 3, TERMINAL_IDC_MINUS = 4, TERMINAL_NAC_PLUS = 5, TERMINAL_NAC_MINUS = 6;
    public static final int BASE_COUNT = 7;
    public static final double FRONT_Z = 11;
    public static final VoxelShape BODY = box(3, 1, FRONT_Z, 13, 15, 16);
    static final AABB HIDDEN = new AABB(7.5, 7.5, 12, 8.5, 8.5, 13);
    public static final AABB[] HUBS = new AABB[4];
    private static final double[] HUB_U = {5.5, 10.5};
    public static final int LOOP_PLUS_RGB = 0xC62828, LOOP_MINUS_RGB = 0x303030;

    static {
        for(int i = 0; i < 2; ++i) {
            double x = 16 - HUB_U[i];
            HUBS[i] = new AABB(x - 1, 15, 12.5, x + 1, 16, 14.5);
            HUBS[2 + i] = new AABB(x - 1, 0, 12.5, x + 1, 1, 14.5);
        }
    }

    /**
     * Three Form C dry contacts, COM, NO and NC each, numbered after the knockouts' landings so
     * older panels keep their wiring: Alarm's NO closes in alarm, Trouble's in trouble, Silenced's
     * once the horns are silenced. The NC contacts are supervised: closed only while the panel is
     * powered and the condition is clear, so a loss of power or a broken wire reads the same as
     * the condition. They switch whatever is spliced through them: a contactor coil, a cabinet input.
     */
    public static final int CONTACT_COUNT = 3;
    public static final int CONTACT_ALARM = 0, CONTACT_TROUBLE = 1, CONTACT_SILENCED = 2;
    private static final int[] POINTS = points();
    /** The contacts' COM and NO, then the network jack on the panel's right side, then the NC contacts, after the knockouts' landings. */
    public static final DeviceHubs.Layout LAYOUT = new DeviceHubs.Layout(BASE_COUNT, HUBS.length, POINTS, CONTACT_COUNT * 3 + 1);
    public static final int JACK = LAYOUT.extraBase() + CONTACT_COUNT * 2;
    private static final AABB JACK_BOX = new AABB(13, 3, 12.5, 14, 5, 14.5);
    private static final String[] CONTACT_KEYS = {"alarm", "trouble", "silenced"};

    private static int[] points() {
        int extraBase = BASE_COUNT + HUBS.length + HUBS.length * DeviceHubs.PER_HUB;
        var points = new int[BASE_COUNT + CONTACT_COUNT * 3];
        for(int i = 0; i < BASE_COUNT; ++i)
            points[i] = i;
        for(int i = 0; i < CONTACT_COUNT * 2; ++i)
            points[BASE_COUNT + i] = extraBase + i;
        for(int c = 0; c < CONTACT_COUNT; ++c)
            points[BASE_COUNT + CONTACT_COUNT * 2 + c] = extraBase + CONTACT_COUNT * 2 + 1 + c;
        return points;
    }

    /** The COM terminal of a contact; NO is the next one. */
    public static int contactTerminal(int contact, boolean normallyOpen) {
        return LAYOUT.extraBase() + contact * 2 + (normallyOpen ? 1 : 0);
    }

    /** The supervised NC terminal of a contact, after the jack. */
    public static int contactNcTerminal(int contact) {
        return LAYOUT.extraBase() + CONTACT_COUNT * 2 + 1 + contact;
    }

    public FireAlarmPanelBlock(Properties properties) {
        super(properties);
        var contacts = new TerminalBoundingBox[CONTACT_COUNT * 2];
        for(int c = 0; c < CONTACT_COUNT; ++c) {
            contacts[c * 2] = terminal("fire_alarm.contact_" + CONTACT_KEYS[c] + "_com", ChatFormatting.GOLD, 0xC8A000);
            contacts[c * 2 + 1] = terminal("fire_alarm.contact_" + CONTACT_KEYS[c] + "_no", ChatFormatting.GOLD, 0xE0C040);
        }
        var jack = com.nolanbaker.pgmodernized.network.JackTerminals.jack(JACK_BOX.minX, JACK_BOX.minY, JACK_BOX.minZ, JACK_BOX.maxX, JACK_BOX.maxY, JACK_BOX.maxZ);
        var nc = new TerminalBoundingBox[CONTACT_COUNT];
        for(int c = 0; c < CONTACT_COUNT; ++c)
            nc[c] = terminal("fire_alarm.contact_" + CONTACT_KEYS[c] + "_nc", ChatFormatting.GOLD, 0xB08020);
        var terminals = DeviceHubs.withExtras(DeviceHubs.withExtras(DeviceHubs.withExtras(DeviceHubs.withHubs(terminals(), HIDDEN, HUBS), contacts), jack), nc);
        var shape = net.minecraft.world.phys.shapes.Shapes.or(DeviceHubs.withHubs(BODY, HUBS), box(JACK_BOX.minX, JACK_BOX.minY, JACK_BOX.minZ, JACK_BOX.maxX, JACK_BOX.maxY, JACK_BOX.maxZ));
        setTerminalCollection(StateTerminals.horizontalNorth(this, shape, state -> terminals));
    }

    private static TerminalBoundingBox[] terminals() {
        var t = new TerminalBoundingBox[BASE_COUNT];
        t[TERMINAL_LINE] = terminal("fire_alarm.line", ChatFormatting.DARK_GRAY, ConductorColors.hotRgb(0));
        t[TERMINAL_NEUTRAL] = terminal("fire_alarm.neutral", ChatFormatting.WHITE, ConductorColors.neutralRgb());
        t[TERMINAL_GROUND] = terminal("fire_alarm.ground", ChatFormatting.GREEN, ConductorColors.groundRgb());
        t[TERMINAL_IDC_PLUS] = terminal("fire_alarm.idc_plus", ChatFormatting.RED, LOOP_PLUS_RGB);
        t[TERMINAL_IDC_MINUS] = terminal("fire_alarm.idc_minus", ChatFormatting.DARK_GRAY, LOOP_MINUS_RGB);
        t[TERMINAL_NAC_PLUS] = terminal("fire_alarm.nac_plus", ChatFormatting.RED, LOOP_PLUS_RGB);
        t[TERMINAL_NAC_MINUS] = terminal("fire_alarm.nac_minus", ChatFormatting.DARK_GRAY, LOOP_MINUS_RGB);
        return t;
    }

    /** A panel terminal: spliced to in the editor, never landed on from outside, so it has no outline. */
    private static TerminalBoundingBox terminal(String key, ChatFormatting style, int rgb) {
        var px = HIDDEN;
        return new TerminalBoundingBox(Lang.builder().translate(key).style(style).component(), px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ).withColor(rgb);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        if(ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    /** A block-local point turned back into the north frame, in 16ths. */
    public static Vec3 toNorthFrame(BlockState state, Vec3 local) {
        double lx = local.x, ly = local.y, lz = local.z;
        double nx, nz;
        switch(facing(state)) {
            case SOUTH -> { nx = 1 - lx; nz = 1 - lz; }
            case EAST -> { nx = lz; nz = 1 - lx; }
            case WEST -> { nx = 1 - lz; nz = lx; }
            default -> { nx = lx; nz = lz; }
        }
        return new Vec3(nx * 16, ly * 16, nz * 16);
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return false;
    }

    /** Conduit on the knockouts, Cat6 on the jack; nothing else lands anywhere. */
    @Override
    public InteractionResult onWire(BlockState state, UseOnContext context) {
        return DeviceHubs.onWire(this, LAYOUT, state, context, (s, c) -> com.nolanbaker.pgmodernized.network.JackTerminals.onWire(this, JACK, s, c, (s2, c2) -> {
            IElectric.sendMessage(c2, Lang.translate("message.fire_alarm.conduit_only").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Below this height on the door, in 16ths, are the two buttons; above it the display, which opens the screen. */
    private static final double BUTTONS_BELOW = 7.5;

    /** An empty hand on the door: the display opens the panel's screen; Silence and Reset sit below it, left and right; sneaking, or any other face, opens the splice editor. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        if(player.isShiftKeyDown() || hit.getDirection() != facing(state)) {
            if(level.isClientSide)
                ClientHooks.openSplices(pos);
            return InteractionResult.SUCCESS;
        }
        var p = toNorthFrame(state, hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if(p.y >= BUTTONS_BELOW) {
            if(level.isClientSide)
                ClientHooks.openFireAlarm(pos);
            return InteractionResult.SUCCESS;
        }
        // The viewer sees +x on their left.
        boolean silence = p.x >= 8;
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof FireAlarmPanelBlockEntity panel) {
            if(silence)
                panel.silence(player);
            else
                panel.reset(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---- redstone: a relay contact that closes in alarm ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FireAlarmPanelBlockEntity panel && panel.inAlarm() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public Class<FireAlarmPanelBlockEntity> getBlockEntityClass() {
        return FireAlarmPanelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FireAlarmPanelBlockEntity> getBlockEntityType() {
        return ModBlockEntities.FIRE_ALARM_PANEL.get();
    }
}
