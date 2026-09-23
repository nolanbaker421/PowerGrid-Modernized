# PowerGrid: Modernized

An addon for [Create: Power Grid](https://github.com/patryk3211/PowerGrid) (NeoForge, Minecraft 1.21.1).
It carries the computer-facing devices that used to live as local patches inside the Power Grid source tree,
so Power Grid itself can be updated from upstream releases without merging.

A player-facing guide to everything in the mod, including the computer APIs, is in
[docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Contents

| Block | Purpose | CC: Tweaked | OpenComputers |
| --- | --- | --- | --- |
| Variable Frequency Drive | Computer-controlled drive output | peripheral | network node |
| Analog I/O Module | Analog voltage in/out for computers | peripheral | network node |
| Clamp Meter | Reads current in any wire passing through its jaw | peripheral | network node |
| CT Cabinet | Four-channel power and energy meter, conduit-wired | peripheral | network node |
| Line Voltmeter | High-impedance voltage probe | peripheral | network node |
| Line Ammeter | Series shunt ammeter | peripheral | network node |

## Breaker panels

Wall-mounted load centres in the style of a US residential panel, with plug-on breakers:

| Panel | Main / largest breaker | Lugs | Branch spaces |
| --- | --- | --- | --- |
| 200 A Breaker Panel | 200 A | 1 (Line, Neutral) | 8 |
| 400 A Breaker Panel | 400 A | 1 | 12 |
| 800 A Breaker Panel | 800 A | 1 | 12 |
| 200 A Split-Phase Breaker Panel | 200 A | 2 (L1, L2, Neutral) | 12 |
| 400 A Split-Phase Breaker Panel | 400 A | 2 | 12 |
| 400 A Three-Phase Breaker Panel | 400 A | 3 (L1, L2, L3, Neutral) | 12 |
| 800 A Three-Phase Breaker Panel | 800 A | 3 | 12 |

Breakers come in four frame sizes, 1-50, 51-200, 201-400 and 401-800 A, in one-, two- and
three-pole versions. The trip rating is set once the breaker is in: look at it with a wrench and
scroll, or click for the settings board. Any frame up to the panel's rating fits, and without a
main breaker the panel is dead. The main must have as many poles as the panel has lugs.

- Each row of spaces sits on the next lug down the panel, both columns of a row on the same lug,
  as in a real load centre. A two-pole breaker takes two poles' worth of rows in one column and
  lands each pole on the next lug (240 V across L1 and L2 on a split-phase panel); a three-pole
  takes three and all three phases. All poles switch and trip together under one handle. Bigger
  frames are bigger breakers: the 201-400 A frame takes two rows per pole and the 401-800 A frame
  three, the extra rows being dead. Multi-pole breakers are crafted from that many single-pole
  breakers of the same frame and an iron nugget.
- Wiring enters through conduit knockouts, four on top, four underneath and two on each side (see
  the conduit section). In the panel's splice editor the
  points are the line lugs (**Line**, or **L1..L3**), **Neutral** (a plain junction for every return)
  and **Circuit 1..N**, each fed from its lug through its breaker. Odd circuits sit in the left
  column, even in the right, top to bottom; a two-pole breaker in spaces 1 and 3 is "Circuit 1/3".
- Right-click a space with a breaker to plug it in (it starts OFF). Right-click a breaker with an
  empty hand to flip it; shift-right-click pulls it. A tripped breaker goes to OFF on the first click
  and ON on the next, like the real thing.
- Trip curve: instant at 8x the rating, about two seconds at 2x, five at 1.5x, half a minute at
  1.1x, never at or below the rating. Tripping sparks and plays the breaker sound.
- Goggles list every space with its rating, pole count, handle position and live current.
- With a breaker in hand every space it would fit is outlined on the panel and the one under the
  crosshair is drawn bright. A **Breaker Blank** fills an unused space. A **Breaker Lockout** on an
  installed breaker freezes its handle (it can still trip); shift-click the breaker to take the lock
  off. Right-click a space with a renamed name tag to label it; the label shows in goggles, messages
  and the splice editor. A plain name tag clears it.
- Splicing a live terminal, or cutting or pulling live wire out of conduit, shocks you. Damage grows
  with the voltage or current involved, and the death message says who forgot their lockout tagout.

## Switchgear

For loads beyond what a panel carries, a **Switchgear Section** is a floor-standing cabinet with a
2000 A three-phase bus and one 3-pole breaker space in its door. Place sections side by side facing
the same way and their buses join (through hidden bus bars between the cabinets), so a row of
sections is one lineup on one bus; goggles show how many sections are in it. Each section has
eight knockouts, four on top and four underneath, and its splice points are the bus (L1, L2, L3, N)
and the breaker's load side (Load L1..L3). Land the feed on any section's bus, or on a section's
load side to make that section's breaker the main. Any 3-pole frame fits, and its rating is set
with a wrench like a panel breaker. The bus bars carry 2000 A and burn like any wire beyond it.

## Transformers

Fourteen nameplates, sized after Create: PowerPlantGrid, each one block whose model spills into the
cells around it (invisible filler blocks carry the collision there and break with the unit):

| Nameplate | Size |
| --- | --- |
| Pole 480 V / 240 V, 1 kV / 240 V, 10 kV / 240 V, 35 kV / 240 V | White cans, from under a block to a block and a half tall; stand them on the ground or hang them on a pole by placing against its side. HV bushings on the lid, LV studs on the front, all for hanging wire. |
| Pad 1 kV / 240 V, 10 kV / 240 V, 1 kV / 208 V, 10 kV / 208 V, 10 kV / 480 V | Grey tanks on a skid, 1.5 blocks wide and 1.75 tall, bushings on the lid: tall HV at the back, short LV at the front. |
| Substation 35 kV / 480 V, 35 kV / 10 kV, 100 kV / 35 kV | Two to two and a half blocks wide and two tall, radiators down both sides, bushings on the lid. |
| Dry-Type 480 V / 240 V, 480 V / 208 V | An indoor cabinet two blocks tall, wired through knockouts and spliced inside like a panel. |

- **Split-phase** nameplates (a 240 V low side) have H1, H2 and a centre-tapped X1, N, X2.
  **Three-phase** ones (a 208 or 480 V low side, line-to-line) are delta primary H1, H2, H3 and
  star secondary X1, X2, X3 with neutral X0, the standard distribution bank with its 30° shift.
- Two **tap** value boxes on the front, HV on the left and LV on the right, move each winding in
  2.5% steps up to 10% either way of its nameplate. There is no on-load tap changer: changing a tap
  on a live winding arcs and shocks you.
- Goggles show the taps, the rating and each leg's voltage and current. Feed either side; the other
  follows the ratio. On stock Power Grid these pass DC exactly as Power Grid's own transformer does.

## Conduit and conduit boxes

Conduit is laid empty, like Power Grid block wire, and wire is pulled through it afterwards:

| Conduit | Capacity | Tube |
| --- | --- | --- |
| 1/2" | 4 wires | 1.5 px |
| 3/4" | 8 wires | 2 px |
| 1" | 12 wires | 2.5 px |

The **Conduit Box** is an 8 x 8 px wall box with three hubs on each of its four edges. It is
placed open; a **Blank Cover Plate** closes it as a plain pull box where runs cross and join, and a
**Node Cover Plate** closes it with twelve colour-coded terminals for ordinary wires. Shift-click
a box with an empty hand to take its plate off. Hold a conduit item, click a hub, click along the
walls, floors or ceilings the run should follow (the route is previewed as you go), and finish on
a hub of another box or a panel knockout. Sneaking with wire cutters takes a whole run up again. A
run cannot tee; give it a box. One run per hub.

**Pulling wire**: right-click a closed run with any Power Grid wire item, from this mod, Power Grid
or another addon. One conductor of that wire goes through, consuming the wire's usual items per
metre, and keeps that wire's resistance and ampacity. Pulled wires are invisible inside the tube
and are numbered and coloured in US order (black, red, blue, white, green, orange, ...). Wire
cutters pull the last wire back out; an empty hand or a multimeter on the run lists
every slot with its wire and current, and the splice editor names the wire under each pin; taking the conduit up drops all of them. A wire that
is overloaded burns out like any Power Grid wire and frees its slot.

Right-click a box to open the splice editor. The first row is the cover terminals (only under a
node plate), every hub with a run is a row of that run's slots, filled where a wire is pulled. Click two pins to splice them, click
the pair again to undo, or click a hub's label to land all of its pulled wires on the cover terminals
of the same number. So a box at the end of a run exposes the wires on its cover, and a box in the
middle joins runs.

The **Conduit Socket** is the equipment end of a run: a 6 x 6 px fitting with one knockout and a
cord socket, placed like Power Grid's socket: wrench its face to turn the knockout. The first two wires pulled through its run land on the socket's two poles by
themselves, and a Power Grid copper cord plugs into it with one click, then splits onto the
machine as usual. A cord's split end can also land directly on two cover terminals of a Conduit Box.

The Variable Frequency Drive, Analog I/O Module, Line Voltmeter and Line Ammeter each carry two
knockouts as well as their ordinary terminals. Land conduit on a knockout, pull wire, then click the
device body with an empty hand to open its splice editor, where the points are the device's own
terminals. Ordinary wires on the terminals keep working alongside.

Breaker panels are wired the same way: they have no exposed terminals, only four knockouts along the
top and four along the bottom. Right-click the panel front outside the breaker spaces to open its
splice editor, where the points are Line, Neutral and every circuit.

When OpenComputers is present the mod also registers a block driver that exposes Power Grid's own gauges,
energy meter and batteries as read-only OpenComputers components.

## Cat6 cable and network jacks

The **Cat6 Cable** is a Power Grid hanging wire that carries computer network traffic instead of current.
It renders and sags like any other wire, is placed with two clicks, and is picked up or cut the same way,
but it only connects **network jacks**:

- The VFD, Analog I/O Module, Line Voltmeter and Line Ammeter have a jack built in (the cyan terminal).
  Ordinary wires refuse that terminal and the Cat6 refuses the electrical ones.
- The **Network Jack** block is a small wall plate for the computer end. Adjacent ComputerCraft wired
  modems and cables (and OpenComputers cables) join its network, so a computer plugs in through a
  wired modem next to a jack exactly as it would with stock cable.
- The **Network Switch** has eight numbered ports and behaves like the jack otherwise: one network
  node, adjacent modems and cables join it. On a floor it faces you; on a wall it mounts as a vertical
  plate with the ports facing out; on a ceiling it hangs. Click nearest the port you want; a port
  lights up while a cable is plugged into it.

Every port takes exactly one cable, so fanning out from a device means going through a switch. The
cable item shows a live preview of the pending connection: green when it can be made, red when not.

On ComputerCraft each jack is a node in the wired network and a device jack publishes the device's
peripheral, named `<block>_<x>_<y>_<z>` (negative coordinates are prefixed with `n`), so
`peripheral.find("vfd")` or `peripheral.wrap("vfd_12_64_n5")` works from any computer on the run.
On OpenComputers the cable links the two jacks' nodes into one network. One cable carries both.

Placement works like Power Grid wires: click a jack, then click the second jack to hang the cable, or
click plain blocks in between to lay it along their surfaces and finish on the second jack. Click a
free end of a laid run with the cable to continue it, shift-right-click in the air to cancel. Runs
cannot branch; wire cutters pick up a whole run.

Cables re-link themselves once a second, so jacks that unload and reload, or a rebuilt network, recover
without any manual action. Breaking a jack drops a hanging cable and trims the last segment of a laid run.

## Building

1. Drop a Power Grid NeoForge release jar into `libs/` and point `powergrid_jar` in `gradle.properties` at it.
2. Run `./gradlew build`. The mod jar lands in `build/libs/`.
3. Run `./gradlew test` for the circuit-simulation unit tests.

Only Power Grid's public API is used: the `ElectricBlockEntity` family, the `ResistanceValues` and
`ThermalValues` provider hooks, and public fields on the wire entities. No upstream classes are patched.

## Notes for maintainers

- Blockstates, models, loot tables, recipes and tags are hand-shipped JSON under `src/main/resources`,
  copied from the datagen output the blocks had upstream. Registrate's datagen hooks are deliberately no-ops.
- Resistances and thermal limits are in `ModValues`; Power Grid's config transforms reject foreign blocks.
- Breaker panel models, textures, blockstates, loot tables and recipes are generated by
  `tools/gen_breaker_panel_assets.py`; its geometry constants mirror `PanelLayout.java`.
  `tools/gen_conduit_assets.py` does the same for the conduit box and mirrors `ConduitBoxGeometry.java`.
- A conduit run is `ConduitRunEntity` (a `BlockWireEntity` without current); pulled wires are
  `ConductorEntity` (hidden `BlockWireEntity`s made of the pulled wire item) that follow the run's path
  and die with it. Splice hosts (box, panel) share `conduit/splice/SpliceSupport` and `SpliceScreen`.
- `docs/legacy-inline-changes.patch` is the exact diff the blocks had against upstream Power Grid before the split.
- Lang keys used by the device GUIs still use the `powergrid.` prefix because the code goes through
  Power Grid's `Lang` helper, which hardcodes that namespace.
