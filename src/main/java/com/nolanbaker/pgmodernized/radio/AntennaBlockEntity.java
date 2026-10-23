package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.util.BodySpace;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;
import java.util.Map;

/** An antenna's aim and its coax port. The radio feeding it is whichever radio's run ends here; it tells the antenna so for the goggles. */
public class AntennaBlockEntity extends SmartBlockEntity implements ICoaxPort, ISettingsHost, IHaveGoggleInformation {
    private final CoaxSupport coax = new CoaxSupport(this);
    private double azimuth, elevation;
    private double beamwidth = 120;
    private boolean aimSet;
    /** The radio whose run ends here, set by that radio every second; null when none does. */
    @Nullable
    private BlockPos radio;
    private long radioSeen;
    /** What the radio worked out about where this antenna should point, for the goggles. */
    public record Aim(String targetId, double bearing, double elevation, double distance, double offAxis, boolean joined, int clients) {
        public static final Aim NONE = new Aim("", 0, 0, 0, 0, false, 0);
    }
    private Aim aim = Aim.NONE;
    private String radioId = "";
    /** The radio's reach through this antenna, as last synced; the server asks the radio directly. */
    private double reach;

    public AntennaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public AntennaKind kind() {
        return getBlockState().getBlock() instanceof AntennaBlock antenna ? antenna.kind() : AntennaKind.OMNI;
    }

    /** Where the antenna is now, on a moving body too. */
    public Vec3 livePosition() {
        return level == null ? worldPosition.getCenter() : BodySpace.live(level, worldPosition);
    }

    /** Compass azimuth, 0 north; starts out the way the block faces. */
    public double azimuth() {
        if(aimSet)
            return azimuth;
        var state = getBlockState();
        if(!state.hasProperty(AntennaBlock.FACING))
            return 0;
        return switch(state.getValue(AntennaBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Degrees above the horizon; a sector's downtilt is a negative elevation. */
    public double elevation() {
        return elevation;
    }

    /** A sector's horizontal beamwidth in degrees, 30 to 180. */
    public double beamwidth() {
        return beamwidth;
    }

    // ---- the radio on the run ----

    /** The radio claims the antenna; the claim lapses a few seconds after the radio stops renewing it. */
    public void claim(BlockPos radioPos) {
        if(level == null)
            return;
        boolean changed = !radioPos.equals(radio);
        radio = radioPos;
        radioSeen = level.getGameTime();
        if(changed)
            sendData();
    }

    @Nullable
    public BlockPos radio() {
        if(radio != null && level != null && !level.isClientSide && level.getGameTime() - radioSeen > 60)
            return null;
        return radio;
    }

    /** The radio's reading of the aim, pushed once a second; synced when it moves. */
    public void setAim(String radioId, Aim aim) {
        var radio = radioEntity();
        double r = radio == null ? 0 : radio.range();
        boolean changed = !aim.equals(this.aim) || !radioId.equals(this.radioId) || r != reach;
        this.aim = aim;
        this.radioId = radioId;
        this.reach = r;
        if(changed)
            sendData();
    }

    /** How far the radio reaches through this antenna in blocks; 0 while no radio drives it. */
    public double reach() {
        if(level != null && level.isClientSide)
            return reach;
        var radio = radioEntity();
        return radio == null ? 0 : radio.range();
    }

    public Aim aim() {
        return aim;
    }

    @Nullable
    public RadioBlockEntity radioEntity() {
        var pos = radio();
        return pos != null && level != null && level.getBlockEntity(pos) instanceof RadioBlockEntity r ? r : null;
    }

    // ---- coax ----

    @Override
    public CoaxSupport coaxSupport() {
        return coax;
    }

    @Override
    public Vec3 coaxPosition(int port) {
        return AntennaBlock.portPosition(getBlockState(), worldPosition);
    }

    @Override
    public void remove() {
        super.remove();
        coax.remove();
    }

    // ---- settings ----

    @Override
    public List<Field> settingsFields() {
        var fields = new java.util.ArrayList<Field>();
        fields.add(new Field("azimuth", "powergrid.gui.antenna.azimuth", true, format(azimuth())));
        fields.add(new Field("elevation", "powergrid.gui.antenna.elevation", true, format(elevation)));
        if(kind() == AntennaKind.SECTOR)
            fields.add(new Field("beamwidth", "powergrid.gui.antenna.beamwidth", true, format(beamwidth)));
        return fields;
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }

    private static double parse(String text, double fallback) {
        try {
            return text == null || text.isBlank() ? fallback : Double.parseDouble(text.trim());
        } catch(NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void applySettings(Map<String, String> values) {
        if(values.containsKey("beamwidth"))
            beamwidth = Math.max(30, Math.min(180, Math.round(parse(values.get("beamwidth"), beamwidth))));
        setAim(parse(values.get("azimuth"), azimuth()), parse(values.get("elevation"), elevation));
    }

    /** Points the antenna, to a tenth of a degree, and tells the radio to look again. */
    public void setAim(double azimuthDeg, double elevationDeg) {
        azimuth = RadioMath.normalize(Math.round(azimuthDeg * 10) / 10.0);
        elevation = Math.max(-90, Math.min(90, Math.round(elevationDeg * 10) / 10.0));
        aimSet = true;
        setChanged();
        sendData();
        var radio = radioEntity();
        if(radio != null && level != null && !level.isClientSide)
            radio.refresh();
    }

    // ---- persistence ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("Azimuth", azimuth);
        tag.putDouble("Elevation", elevation);
        tag.putDouble("Beamwidth", beamwidth);
        tag.putBoolean("AimSet", aimSet);
        if(clientPacket)
            tag.putDouble("Reach", reach());
        if(radio != null && clientPacket && radio() != null) {
            tag.putLong("Radio", radio.asLong());
            tag.putString("RadioId", radioId);
            var a = new CompoundTag();
            a.putString("Target", aim.targetId());
            a.putDouble("Bearing", aim.bearing());
            a.putDouble("Elev", aim.elevation());
            a.putDouble("Distance", aim.distance());
            a.putDouble("Off", aim.offAxis());
            a.putBoolean("Joined", aim.joined());
            a.putInt("Clients", aim.clients());
            tag.put("Aim", a);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        azimuth = tag.getDouble("Azimuth");
        elevation = tag.getDouble("Elevation");
        beamwidth = tag.contains("Beamwidth") ? tag.getDouble("Beamwidth") : 120;
        aimSet = tag.getBoolean("AimSet");
        if(clientPacket) {
            reach = tag.getDouble("Reach");
            radio = tag.contains("Radio") ? BlockPos.of(tag.getLong("Radio")) : null;
            radioSeen = level == null ? 0 : level.getGameTime();
            radioId = tag.getString("RadioId");
            if(tag.contains("Aim")) {
                var a = tag.getCompound("Aim");
                aim = new Aim(a.getString("Target"), a.getDouble("Bearing"), a.getDouble("Elev"), a.getDouble("Distance"), a.getDouble("Off"), a.getBoolean("Joined"), a.getInt("Clients"));
            } else {
                aim = Aim.NONE;
            }
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.antenna." + kind().id()).style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(kind().aimed()) {
            var line = Lang.builder().translate("gui.antenna.aim", String.format("%.1f", azimuth()), String.format("%.1f", elevation)).style(ChatFormatting.WHITE);
            if(kind() == AntennaKind.SECTOR)
                line.text("  ").add(Lang.builder().translate("gui.antenna.beam", String.format("%.0f", beamwidth)).style(ChatFormatting.WHITE));
            line.forGoggles(tooltip, 1);
        }
        if(radio() == null) {
            Lang.builder().translate("gui.antenna.no_radio").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            return true;
        }
        Lang.builder().translate("gui.antenna.radio", radioId.isBlank() ? "-" : radioId).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        if(kind() == AntennaKind.SECTOR)
            Lang.builder().translate("gui.antenna.clients", aim.clients()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if(kind().aimed() && !aim.targetId().isEmpty()) {
            Lang.builder().translate(aim.joined() ? "gui.antenna.partner" : "gui.antenna.candidate", aim.targetId(),
                            String.format("%.1f", aim.bearing()), String.format("%.1f", aim.elevation()), String.format("%.0f", aim.distance()))
                    .style(aim.joined() ? ChatFormatting.GREEN : ChatFormatting.GOLD).forGoggles(tooltip, 1);
            Lang.builder().translate("gui.antenna.off_axis", String.format("%.2f", aim.offAxis())).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        }
        return true;
    }
}
