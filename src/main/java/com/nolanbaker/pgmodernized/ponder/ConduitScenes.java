package com.nolanbaker.pgmodernized.ponder;

import com.nolanbaker.pgmodernized.conduit.ConduitBoxGeometry;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.WireGauge;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.List;

/**
 * The conduit scene: two pairs of boxes on a wall. A straight run is laid between the upper pair
 * and wire pulled through it; the lower pair gets a run with an offset, to show the bender. The
 * runs are the real entities so they draw as they do in a world. One {@code .text(...)} per card.
 */
public final class ConduitScenes {
    private ConduitScenes() {}

    /** A box facing north: its middle knockout on the east edge, and on the west edge. */
    private static final int EAST_HUB = ConduitBoxGeometry.hubTerminal(10), WEST_HUB = ConduitBoxGeometry.hubTerminal(4);

    public static void basics(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("conduit", "Conduit");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        var boxA = util.grid().at(1, 2, 3);
        var boxB = util.grid().at(3, 2, 3);
        var lowA = util.grid().at(1, 1, 3);
        var lowB = util.grid().at(3, 1, 3);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(10);

        var frontA = util.vector().blockSurface(boxA, Direction.NORTH);
        scene.overlay().showText(70)
                .text("Conduit boxes on a wall. Every edge carries knockouts, flat marks in the colour of the biggest trade size they take; the hubs light up while conduit is in hand")
                .pointAt(frontA).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        var conduit = ModItems.CONDUIT.get(ConduitSize.ONE).asStack();
        var hubA = hub(boxA, 12.5);
        var hubB = hub(boxB, 3.5);
        scene.overlay().showControls(hubA, Pointing.DOWN, 20).rightClick().withItem(conduit);
        scene.idle(25);
        scene.overlay().showControls(hubB, Pointing.DOWN, 20).rightClick().withItem(conduit);
        scene.idle(10);
        run(scene, boxA, boxB, List.of(new Point(Direction.EAST, 23)));
        scene.overlay().showText(80)
                .text("Click a hub, click the surfaces the run should follow, and finish on another hub. A run leaves each knockout square, in a short stub, before it goes anywhere, and the preview shows the route it will take")
                .pointAt(hubA.add(hubB).scale(0.5)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        var wire = ModItems.BUILDING_WIRE.get(WireGauge.AWG_12).asStack();
        scene.overlay().showControls(hubA.add(hubB).scale(0.5), Pointing.DOWN, 25).rightClick().withItem(wire);
        scene.overlay().showText(80)
                .text("Right-click a closed run with building wire to pull a conductor through it. A run takes as many as its trade size allows, filled by the book, and the wires land on the boxes' hidden terminals for splicing")
                .pointAt(hubA.add(hubB).scale(0.5)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        run(scene, lowA, lowB, List.of(new Point(Direction.EAST, 8), new Point(Direction.NORTH, 2), new Point(Direction.EAST, 7),
                new Point(Direction.SOUTH, 2), new Point(Direction.EAST, 8)));
        var lowMid = hub(lowA, 12.5).add(hub(lowB, 3.5)).scale(0.5).add(0, 0, -2 / 16.0);
        scene.overlay().showControls(lowMid, Pointing.DOWN, 25).rightClick().withItem(ModItems.CONDUIT_BENDER.asStack());
        scene.overlay().showText(90)
                .text("The Conduit Bender slides the leg under the crosshair sideways, one step a click, away from the face you click. The legs beside it follow and corners come and go, so offsets, kicks and saddles take a few clicks")
                .pointAt(lowMid).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Sneak-click a run with the bender to pick the whole run up, sticks and wires together, and lay it again somewhere else; the wires go back in when it closes")
                .pointAt(lowMid).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** The centre of a box's middle knockout on the given edge, in pixels across the block. */
    private static Vec3 hub(BlockPos box, double x) {
        return new Vec3(box.getX() + x / 16, box.getY() + 8 / 16.0, box.getZ() + 14.5 / 16);
    }

    /** A real conduit run from the east knockout of one box to the west knockout of the other. */
    private static void run(SceneBuilder scene, BlockPos from, BlockPos to, List<Point> points) {
        scene.world().createEntity(level -> {
            var entity = ConduitRunEntity.create(level, new BlockWireEndpoint(from, EAST_HUB), ModItems.CONDUIT.get(ConduitSize.ONE).asStack(2), points);
            entity.setEndpoint2(new BlockWireEndpoint(to, WEST_HUB));
            return entity;
        });
    }
}
