package com.nolanbaker.pgmodernized.ponder;

import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitCover;
import com.nolanbaker.pgmodernized.device.breaker.BreakerFrame;
import com.nolanbaker.pgmodernized.device.breaker.BreakerItem;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.BreakerState;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

/**
 * The breaker panel scene: a split-phase panel on a wall gets its main, two branch breakers, a load
 * on one of them, and a cover. Scripted rather than simulated, so it plays the same from the menu
 * and in a world. One {@code .text(...)} per card, in order: the lang generator numbers them.
 */
public final class PanelScenes {
    private PanelScenes() {}

    public static void basics(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("breaker_panel", "The Breaker Panel");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        var panel = util.grid().at(2, 2, 3);
        var lamp = util.grid().at(0, 1, 1);
        var front = util.vector().blockSurface(panel, Direction.NORTH);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(70)
                .text("A breaker panel takes the service on its lugs and splits it into branch circuits, each behind its own breaker")
                .pointAt(front).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        var main = ModItems.BREAKERS.get(2).get(BreakerFrame.F200).asStack();
        scene.overlay().showControls(front.add(0, 0.3, 0), Pointing.DOWN, 30).rightClick().withItem(main);
        scene.idle(10);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.installForScene(BreakerPanelBlockEntity.MAIN, (BreakerItem) main.getItem()));
        scene.overlay().showText(70)
                .text("The main breaker goes in the space at the top. Its poles match the panel: two on a split-phase panel, three on a three-phase one")
                .pointAt(front.add(0, 0.3, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        var branch = ModItems.BREAKERS.get(1).get(BreakerFrame.F50).asStack();
        scene.overlay().showControls(front.add(-0.2, 0.05, 0), Pointing.RIGHT, 25).rightClick().withItem(branch);
        scene.idle(8);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.installForScene(0, (BreakerItem) branch.getItem()));
        scene.idle(12);
        scene.overlay().showControls(front.add(0.2, 0.05, 0), Pointing.LEFT, 25).rightClick().withItem(branch);
        scene.idle(8);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.installForScene(1, (BreakerItem) branch.getItem()));
        scene.overlay().showText(80)
                .text("Branch breakers click into the spaces below. The two spaces of a row share a leg and the rows alternate legs, so a 2-pole breaker across two rows gets both")
                .pointAt(front).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(front.add(0, 0.3, 0), Pointing.DOWN, 20).rightClick();
        scene.idle(6);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.setState(BreakerPanelBlockEntity.MAIN, BreakerState.ON));
        scene.idle(14);
        scene.overlay().showControls(front.add(-0.2, 0.05, 0), Pointing.RIGHT, 20).rightClick();
        scene.idle(6);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.setState(0, BreakerState.ON));
        scene.world().modifyBlock(lamp, state -> state.setValue(ConduitBoxBlock.COVER, ConduitCover.LIGHT_ON), false);
        scene.overlay().showText(80)
                .text("Click a breaker to flip it. The load on that branch, here a light in a conduit box, comes on. A wrench scrolls the trip rating; it trips on its curve and goggles show its heat")
                .pointAt(util.vector().topOf(lamp)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(60)
                .text("The lugs take the service: a feed of the right size through a knockout in conduit, or hanging wire straight onto them")
                .pointAt(util.vector().blockSurface(panel, Direction.UP)).placeNearTarget();
        scene.idle(70);

        scene.overlay().showControls(front, Pointing.DOWN, 25).rightClick().withItem(ModItems.PANEL_COVER.asStack());
        scene.idle(8);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.setCover(true));
        scene.overlay().showText(70)
                .text("A panel cover closes the front. Sneak-click it to swing the door open and shut; the breakers are only reachable with it open")
                .pointAt(front).placeNearTarget().attachKeyFrame();
        scene.idle(40);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.setCoverOpen(true));
        scene.idle(30);
        scene.world().modifyBlockEntity(panel, BreakerPanelBlockEntity.class, be -> be.setCoverOpen(false));
        scene.idle(20);
        scene.markAsFinished();
    }
}
