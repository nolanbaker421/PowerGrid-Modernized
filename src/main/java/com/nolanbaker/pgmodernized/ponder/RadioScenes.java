package com.nolanbaker.pgmodernized.ponder;

import com.nolanbaker.pgmodernized.radio.AntennaBlockEntity;
import com.nolanbaker.pgmodernized.radio.CoaxEndpoint;
import com.nolanbaker.pgmodernized.radio.CoaxWireEntity;
import com.nolanbaker.pgmodernized.radio.RadioBlockEntity;
import com.nolanbaker.pgmodernized.radio.RadioMath;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The radio scenes. Links are drawn, not simulated: the radios' link logic runs on the server and
 * a Ponder level is a client level, so the aim, the IDs and the coax are set from the script and
 * the link itself is a line. One {@code .text(...)} per card, in order.
 */
public final class RadioScenes {
    private RadioScenes() {}

    /** Two masts, two dishes, one hop. */
    public static void dishLink(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radio_dish_link", "Point-to-Point Dishes");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(5);

        var radioA = util.grid().at(1, 1, 1);
        var radioB = util.grid().at(5, 1, 1);
        var dishA = util.grid().at(1, 3, 3);
        var dishB = util.grid().at(5, 3, 3);

        scene.world().showSection(util.select().fromTo(0, 1, 2, 6, 3, 6), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(radioA), Direction.DOWN);
        scene.world().showSection(util.select().position(radioB), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(70)
                .text("A radio is the indoor unit: a line and neutral at 50 W, a Cat6 jack for the computer network, a conduit knockout, and a coax port out to its antenna")
                .pointAt(util.vector().blockSurface(radioA, Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        coax(scene, radioA, dishA);
        coax(scene, radioB, dishB);
        scene.overlay().showText(70)
                .text("Coax runs from the radio to the antenna, up to 64 blocks a span. Reach falls with the run, full to 16 blocks and half at 64, so keep it short and clip it to cable hangers up the mast")
                .pointAt(coaxMid(radioA, dishA)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        scene.world().modifyBlockEntity(radioA, RadioBlockEntity.class, be -> be.setLinkId("north-hop"));
        scene.world().modifyBlockEntity(radioB, RadioBlockEntity.class, be -> be.setLinkId("north-hop"));
        scene.overlay().showControls(util.vector().blockSurface(radioA, Direction.NORTH), Pointing.DOWN, 25).rightClick().whileSneaking();
        scene.overlay().showText(70)
                .text("A dish on the coax makes the radio one end of a point-to-point link. Sneak-click the radio and type a link ID; both ends carry the same one")
                .pointAt(util.vector().blockSurface(radioA, Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(dishA), Pointing.DOWN, 25).rightClick().withItem(ModItems.ALIGNMENT_SCOPE.asStack());
        scene.idle(10);
        scene.world().modifyBlockEntity(dishA, AntennaBlockEntity.class, be -> be.setAim(RadioMath.bearing(dishA.getCenter(), dishB.getCenter()), 0));
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(dishB), Pointing.DOWN, 25).rightClick().withItem(ModItems.ALIGNMENT_SCOPE.asStack());
        scene.idle(10);
        scene.world().modifyBlockEntity(dishB, AntennaBlockEntity.class, be -> be.setAim(RadioMath.bearing(dishB.getCenter(), dishA.getCenter()), 0));
        scene.overlay().showText(80)
                .text("Aim each dish at the other to a tenth of a degree: sneak-click a dish for its azimuth and elevation, or clip the Alignment Scope on one and then the other and both are sighted in")
                .pointAt(util.vector().topOf(dishB)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        var a = head(dishA);
        var b = head(dishB);
        scene.overlay().showBigLine(PonderPalette.GREEN, a, b, 100);
        scene.overlay().showText(90)
                .text("Within reach and inside the 1° beam, the two radios' Cat6 jacks join as if a cable ran between them: one network, up to 2000 blocks apart. Goggles on a dish show how far off axis it is")
                .pointAt(a.add(b).scale(0.5)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    /** A tower with a sector, and an omni on the ground that it serves. */
    public static void carrier(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radio_carrier", "Sectors and Cell Clients");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(5);

        var towerRadio = util.grid().at(1, 1, 3);
        var sector = util.grid().at(3, 4, 3);
        var omni = util.grid().at(5, 1, 1);
        var omniRadio = util.grid().at(6, 1, 1);

        scene.world().showSection(util.select().fromTo(0, 1, 2, 6, 4, 6), Direction.DOWN);
        scene.idle(10);
        coax(scene, towerRadio, sector);
        scene.world().modifyBlockEntity(towerRadio, RadioBlockEntity.class, be -> be.setLinkId("acme-wireless"));
        scene.world().modifyBlockEntity(sector, AntennaBlockEntity.class, be -> be.setAim(0, -10));
        scene.idle(10);

        var sectorHead = head(sector);
        scene.overlay().showText(80)
                .text("A sector antenna is a base station: an aimed panel 30° to 180° wide, 120° by default, with a little downtilt. Its radio's link ID is the carrier, shared by every tower of that carrier")
                .pointAt(sectorHead).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        beam(scene, sectorHead, 0, 120, 4);
        scene.overlay().showText(70)
                .text("The beam is where it serves. Reach is 1000 blocks for a 120° sector and grows as the beam narrows: 2000 at 30°. Sneak-click any antenna with the Alignment Scope to draw its coverage in the world")
                .pointAt(sectorHead.add(0, 0, -2)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().fromTo(4, 1, 0, 6, 1, 1), Direction.DOWN);
        scene.idle(10);
        coax(scene, omniRadio, omni);
        scene.world().modifyBlockEntity(omniRadio, RadioBlockEntity.class, be -> be.setLinkId("acme-wireless"));
        scene.overlay().showText(80)
                .text("An omni antenna is the client, 500 blocks of reach in all directions. Give its radio the carrier's ID and it joins the nearest sector whose beam covers it with a seat free, 16 to a sector")
                .pointAt(head(omni)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.overlay().showBigLine(PonderPalette.GREEN, sectorHead, head(omni), 90);
        scene.overlay().showText(90)
                .text("Omnis roam: on a vehicle or a Sable body they hand over to the next tower as they move, and the computers on both jacks stay one network the whole way")
                .pointAt(head(omni).add(sectorHead).scale(0.5)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    // ---- helpers ----

    /** A coax span from the radio's port to the antenna's, as the real entity so it hangs like one. */
    private static void coax(SceneBuilder scene, BlockPos radio, BlockPos antenna) {
        scene.world().createEntity(level -> {
            var span = (int) Math.ceil(Math.sqrt(radio.distSqr(antenna))) + 1;
            return CoaxWireEntity.create(level, new CoaxEndpoint(radio, 0), new CoaxEndpoint(antenna, 0), ModItems.COAX_CABLE.asStack(span));
        });
    }

    private static Vec3 coaxMid(BlockPos radio, BlockPos antenna) {
        return radio.getCenter().add(antenna.getCenter()).scale(0.5).add(0, 0.5, 0);
    }

    /** Where the antenna's head sits, for lines and labels. */
    private static Vec3 head(BlockPos antenna) {
        return antenna.getCenter().add(0, 0.2, 0);
    }

    /** The edges and the centre of a sector's beam, drawn out from its head. */
    private static void beam(SceneBuilder scene, Vec3 from, double azimuth, double beamwidth, double length) {
        for(double az : new double[] {azimuth - beamwidth / 2, azimuth, azimuth + beamwidth / 2}) {
            var to = from.add(RadioMath.aim(az, 0).scale(length));
            scene.overlay().showLine(az == azimuth ? PonderPalette.WHITE : PonderPalette.BLUE, from, to, 80);
        }
    }
}
