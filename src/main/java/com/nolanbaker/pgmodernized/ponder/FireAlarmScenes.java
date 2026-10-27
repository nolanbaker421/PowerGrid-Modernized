package com.nolanbaker.pgmodernized.ponder;

import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxGeometry;
import com.nolanbaker.pgmodernized.conduit.ConduitCover;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock;
import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.List;

/**
 * The fire alarm scene: a panel on a wall, a smoke detector box above it on the initiating loop
 * and a horn strobe box beside it on the notification loop, the runs real entities. A fire is lit
 * near the detector and the system goes to alarm. One {@code .text(...)} per card.
 */
public final class FireAlarmScenes {
    private FireAlarmScenes() {}

    public static void basics(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("fire_alarm", "Fire Alarm");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        var panel = util.grid().at(2, 1, 3);
        var smoke = util.grid().at(2, 2, 3);
        var horn = util.grid().at(0, 1, 3);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(10);

        var door = util.vector().blockSurface(panel, Direction.NORTH);
        scene.overlay().showText(80)
                .text("The fire alarm panel. Feed it 120 V through a knockout; it keeps 24 V on two loops of pulled wire, an initiating loop for detectors and a notification loop for horn strobes, spliced in its editor like any device")
                .pointAt(door).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        // The initiating loop up to the detector, straight; the notification loop down, across and up to the horn strobe.
        run(scene, panel, 0, smoke, ConduitBoxGeometry.hubTerminal(2), List.of(new Point(Direction.UP, 3)), true);
        run(scene, panel, 2, horn, ConduitBoxGeometry.hubTerminal(7),
                List.of(new Point(Direction.DOWN, 4), new Point(Direction.WEST, 26), new Point(Direction.UP, 12), new Point(Direction.WEST, 4)), false);
        scene.idle(10);
        var puck = util.vector().blockSurface(smoke, Direction.NORTH);
        scene.overlay().showText(80)
                .text("Smoke and heat detectors and pull stations are covers for the conduit box and devices for the gang box, on the initiating loop. In alarm each closes a resistor across the loop, and the panel sees the current")
                .pointAt(puck).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        var lens = util.vector().blockSurface(horn, Direction.NORTH);
        scene.overlay().showText(70)
                .text("Horn strobes sit on the notification loop, as box covers or gang devices. The loop idles at two volts, enough for the panel to see them and not enough to wake them")
                .pointAt(lens).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        var fire = util.grid().at(4, 1, 1);
        scene.world().setBlock(fire, Blocks.FIRE.defaultBlockState(), false);
        scene.idle(20);
        scene.world().modifyBlock(smoke, s -> s.setValue(ConduitBoxBlock.COVER, ConduitCover.SMOKE_ALARM), false);
        scene.world().modifyBlockEntity(panel, FireAlarmPanelBlockEntity.class, be -> be.demo(FireAlarmPanelBlockEntity.State.ALARM, false));
        scene.world().modifyBlock(horn, s -> s.setValue(ConduitBoxBlock.COVER, ConduitCover.HORN_ON), false);
        scene.overlay().showText(90)
                .text("Fire within four blocks of a smoke detector trips it. The panel goes to Alarm, drives the notification loop to 24 V, the horns sound the three-pulse pattern, the strobes flash, and the panel puts out a redstone signal")
                .pointAt(util.vector().topOf(fire)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        var silence = new Vec3(panel.getX() + 11 / 16.0, panel.getY() + 10.5 / 16, panel.getZ() + FireAlarmPanelBlock.FRONT_Z / 16);
        scene.overlay().showControls(silence, Pointing.DOWN, 25).rightClick();
        scene.world().modifyBlockEntity(panel, FireAlarmPanelBlockEntity.class, be -> be.demo(FireAlarmPanelBlockEntity.State.ALARM, true));
        scene.overlay().showText(80)
                .text("The left button silences the horns and leaves the strobes going. Put the fire out, and the right button resets the panel; it refuses while any detector or pull station is still in alarm")
                .pointAt(silence).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.world().setBlock(fire, Blocks.AIR.defaultBlockState(), false);
        scene.world().modifyBlock(smoke, s -> s.setValue(ConduitBoxBlock.COVER, ConduitCover.SMOKE), false);
        scene.world().modifyBlock(horn, s -> s.setValue(ConduitBoxBlock.COVER, ConduitCover.HORN_OFF), false);
        scene.world().modifyBlockEntity(panel, FireAlarmPanelBlockEntity.class, be -> be.demo(FireAlarmPanelBlockEntity.State.NORMAL, false));
        scene.idle(20);
        scene.markAsFinished();
    }

    /** A real conduit run from one of the panel's knockouts to a box's knockout. */
    private static void run(SceneBuilder scene, BlockPos panel, int panelHub, BlockPos box, int boxHub, List<Point> points, boolean direct) {
        scene.world().createEntity(level -> {
            var entity = ConduitRunEntity.create(level, new BlockWireEndpoint(panel, FireAlarmPanelBlock.LAYOUT.hubTerminal(panelHub)),
                    ModItems.CONDUIT.get(ConduitSize.THREE_QUARTER).asStack(3), points);
            entity.setEndpoint2(new BlockWireEndpoint(box, boxHub));
            entity.setDirect(direct);
            return entity;
        });
    }
}
