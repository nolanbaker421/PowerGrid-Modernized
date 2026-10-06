# PowerGrid: Modernized — User Guide

An addon for **Create: Power Grid** that adds US-style electrical hardware (breaker panels, conduit,
junction boxes), computer-controlled devices, and a thin network cable for ComputerCraft and
OpenComputers. This guide covers everything in the mod. It assumes you already know the basics of
Power Grid: wires land on terminals, every wire is a single conductor, goggles show readings.

---

## 1. Installing

You need, on Minecraft 1.21.1 with NeoForge:

| Mod | Version |
| --- | --- |
| Create | 6.0.9 or newer |
| Create: Power Grid | 0.6.2 or newer (the normal release, unmodified) |
| PowerGrid: Modernized | this jar |

Optional, picked up automatically if present: **CC: Tweaked** (peripherals) and **OpenComputers**
(components). Nothing in the addon changes Power Grid itself; it is a plain addon jar.

**The AC fork.** DaRealML's powergrid-ac is a replacement for the Power Grid jar, not an addon.
This mod is one jar for both: with stock Power Grid every meter reads instantaneous values; with
the fork installed the mod notices at startup, meters read RMS and real power, and the three-phase
motor and drive, the synchroscope and the creative AC source appear in the creative tab. Swapping
Power Grid jars on an existing world keeps everything except those four blocks.

Everything is in its own creative tab, "PowerGrid: Modernized". All blocks are mined with a pickaxe.

---

### The AC build

Power Grid itself is direct current. If you run the experimental
[powergrid-ac](https://github.com/DaRealML/powergrid-ac) fork instead (alternators, three-phase,
transformer banks), use the AC build of this addon, `powergrid-modernized-ac-mc1.21.1-<version>.jar` (not the plain
`powergrid-modernized-mc1.21.1-<version>.jar`, which is the stock build and asks for Power Grid 0.6.2),
with `powergrid-mc1.21.1-0.6.1-ac.7.jar` or newer in place of the regular Power Grid jar. The AC
build will not start on the regular jar, and says so.

On the AC build every meter, breaker and analog input reads RMS, the figure a real instrument
shows, and the CT cabinet meters real power and a power factor per channel. Currents and voltages
that alternate symmetrically read positive; direct or rectified ones keep their sign. The VFD holds
its output at the RMS setpoint. On a direct-current circuit nothing reads differently from the
regular build.

## 2. What is in the box

| Item | What it is |
| --- | --- |
| Breaker Panel 200 A / 400 A / 800 A | Wall-mounted load centres with plug-on breakers |
| Breakers 1-50, 51-200, 201-400, 401-800 A | Plug-on breaker frames; the trip rating is set with a wrench once installed |
| Breaker Blank | Filler plate for an unused space |
| Breaker Lockout | Hasp that freezes a breaker handle |
| Conduit 1/2", 3/4", 1" | Empty raceway, laid along surfaces, that wire is pulled through |
| Conduit Box | Small wall box: ends and splices conduit runs; takes a blank or node cover plate |
| Conduit Socket | Tiny fitting that turns the end of a conduit into a cord socket |
| Digital Voltage Regulator (2 kV, 8 kV) | Computer-controlled voltage/current source |
| Analog I/O Module | Four analog outputs and four analog inputs for computers |
| CT Cabinet | Four-channel power meter: volts, amps, watts and energy per feeder |
| Clamp Meter | Reads the current in any wire passing through its jaw |
| Line Voltmeter | High-impedance two-probe voltmeter |
| Line Ammeter | In-line shunt ammeter |
| Cat6 Cable, Network Jack, Network Switch | Thin computer network cabling |

---

## 3. Conduit

Conduit is laid like Power Grid block wire and is empty when placed. You pull real wire through it
afterwards, so it works with any Power Grid wire, including wires from other addons, and each pulled
wire keeps its own gauge (resistance and ampacity). Nine EMT trade sizes:

| Conduit | Area (sq in) | Slots | Tube |
| --- | --- | --- | --- |
| 1/2" | 0.304 | 4 | 1.5 px |
| 3/4" | 0.533 | 8 | 2 px |
| 1" | 0.864 | 12 | 2.5 px |
| 1-1/4" | 1.496 | 12 | 3 px |
| 1-1/2" | 2.036 | 12 | 3.5 px |
| 2" | 3.356 | 12 | 4 px |
| 2-1/2" | 5.858 | 12 | 5 px |
| 3" | 8.846 | 12 | 5.5 px |
| 4" | 14.753 | 12 | 6.5 px |

### Laying a run

1. Place a fitting at each end: a **Conduit Box**, a **Conduit Socket**, a breaker panel knockout,
   or a knockout on one of the devices (section 6).
2. Hold the conduit item and click a **hub** (the small knockout nub on a fitting).
3. Click the walls, floors or ceilings the run should follow. It hugs surfaces and turns corners.
4. Finish by clicking a hub on the other fitting.

One run per hub. A run cannot tee; if you need a branch, put a box there. Every fitting has a largest
size it takes: a Conduit Box and the device knockouts stop at 1-1/2", a socket or switch at 1", a
breaker panel at 2"; switchgear and the Pull Box take anything up to 4". To continue an open run,
click its free end with conduit. The route is previewed in green (or red where it cannot go) while
a run is pending. Shift-right-click in the air cancels a pending run. Sneak and right-click a run
with wire cutters to take the whole run back up; it drops any wire inside it.

### Pulling wire

Right-click a **closed** run (a hub on both ends) with any Power Grid wire item. One conductor of that
wire goes through, consuming that wire's usual items per metre. Repeat with the same or a different
wire until the conduit is full. Pulled wires are numbered and coloured in US order:

1 black, 2 white, 3 red, 4 blue, 5 orange, 6 brown, 7 yellow, 8 purple, 9 pink, 10 tan, 11 gray, 12 green.

So a two-wire pull is a black hot and a white neutral, and a three-phase pull with neutral is black,
white, red, blue. If a run needs another neutral, or you keep your own colour code, **right-click a
wire's pin in the splice editor** to step it through the twelve colours; the colour follows the wire
everywhere it is named. A run itself takes dye like any Power Grid wire, so conduit can be painted.

**Conduit fill** is figured by the book (NEC Chapter 9, Table 1): the pulled conductors' total
cross-section may take 53% of the conduit's internal area with one wire in it, 31% with two, and
40% with three or more. A wire that would go past that is refused, and the message tells you the
fill it would have reached. Every run also has numbered slots (4 in 1/2", 8 in 3/4", 12 above)
that cap the count whatever the gauge. Conductors of one gauge per run:

| Conduit | Area (sq in) | Slots | 12 AWG | 8 AWG | 4 AWG | 1/0 | 4/0 | 500 kcmil |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1/2" | 0.304 | 4 | 4 | 3 | 1 | 0 | 0 | 0 |
| 3/4" | 0.533 | 8 | 8 | 5 | 2 | 1 | 0 | 0 |
| 1" | 0.864 | 12 | 12 | 9 | 4 | 1 | 1 | 0 |
| 1-1/4" | 1.496 | 12 | 12 | 12 | 7 | 3 | 1 | 1 |
| 1-1/2" | 2.036 | 12 | 12 | 12 | 9 | 4 | 1 | 1 |
| 2" | 3.356 | 12 | 12 | 12 | 12 | 7 | 4 | 1 |
| 2-1/2" | 5.858 | 12 | 12 | 12 | 12 | 12 | 7 | 3 |
| 3" | 8.846 | 12 | 12 | 12 | 12 | 12 | 10 | 5 |
| 4" | 14.753 | 12 | 12 | 12 | 12 | 12 | 12 | 8 |

**THHN building wire** is this mod's wire, in thirteen gauges from 14 AWG to 500 kcmil. Each is an
ordinary Power Grid wire item (it hangs between terminals like any other) with the real conductor
area, ampacity at 75 °C (the current it burns out above) and resistance per metre; hover the item to
see them. Recipes are shapeless: dried kelp with copper nuggets (14 to 10 AWG), copper ingots
(8 AWG to 4/0) or copper blocks (250 kcmil and up), eight metres a craft. Power Grid's own wires and
another addon's count for fill as the smallest gauge whose ampacity covers their rated current, so
Power Grid's 80 A copper wire takes the room of 4 AWG and its 160 A iron wire that of 2/0.

- Right-click the run with **wire cutters** to pull the last wire back out (you get it back).
- Right-click the run with an **empty hand** or the **multimeter** to see the fill and every slot:
  colour, wire, gauge and current.
- A wire driven past its ampacity burns out like any Power Grid wire and frees its slot.

### Conduit Box

An 8 x 8 pixel wall box with **three hubs on each of its four edges** (twelve runs). It is placed
open, and takes one of two cover plates:

- **Blank Cover Plate**: a closed pull box. Runs land on it and are spliced to each other inside;
  nothing is exposed. This is the fitting for turning corners and joining runs.
- **Node Cover Plate**: closes the box with **twelve colour-coded terminals** on the cover that
  ordinary wires land on, and that the splice editor offers as points.

Right-click an open box with a plate to fit it; shift-right-click the box with an empty hand to
take the plate off (a node plate will not come off while wires hang on its terminals).

Right-click the box with an empty hand to open the **splice editor**:

- The first row is the twelve cover terminals, when there is a node plate.
- Every hub that has a run is a row of that run's slots. Filled pins are pulled wires; hollow pins
  are empty slots.
- Click a pin, then another pin, to splice them. Click the same pair again to remove the splice.
- Click a hub's **label** to land every pulled wire of that run on the cover terminal of the same
  number in one go.
- Selecting or hovering a pin lights up everything it is electrically joined to.
- Hover a pin to see the wire type in it.

A splice is a near-zero-ohm connection. A conductor spliced to two others makes a three-way group.
Splices to a wire that is pulled out go away by themselves.

Goggles on a box show each run and a splice count; open the editor to see the splices.

### Conduit Socket

A 6 x 6 pixel fitting for the equipment end of a run. It has one hub and a **cord socket**: the first
two wires pulled through its run land on the socket's two poles automatically, and a Power Grid
copper cord plugs into it with one click, then splits onto the machine as usual. Click the socket
with an empty hand to unplug the cord, or, with no cord in it, to open the splice editor for its two
knockouts. Place it with the clicked face as its back; wrench its face to
turn the hub to any of the four directions. There is nothing to splice.

### Pull Box

A full-block cabinet for the big pipe: two knockouts on the top, bottom, left, right and back, each
taking conduit of any size, and a door on the front that opens the splice editor. It has no
terminals; like a blank-covered box it only joins runs to each other. Place it with the door
towards you (sneak to face it away).

**Gutter**: set a pull box directly under or directly over a breaker panel on the same wall and,
within a second, the box lays a short 4" **nipple** between one of its knockouts and one of the
panel's. Pull wire through the nipple exactly as through any run, right-click it with the wire, and
splice in the panel editor as usual. The nipple cannot be cut or picked up; it goes away when the
box or the panel does. That is how feeders too big for a 2" panel knockout get in: up the gutter.

### Terminal Cabinet

The Pull Box with twelve colour-coded lugs on its door. Any wire hangs on a lug, 500 kcmil THHN
included, and the editor offers the lugs as points, so a feeder that arrives as hanging wire is
spliced to conductors in a 4" run inside, and the other way round. Crafted from a pull box ringed
with copper nuggets.

### Conduit Switch

The same 6 x 6 pixel fitting as the socket with a toggle on top instead of a cord socket: one hub,
and the first two wires pulled through its run land on its **Line** and **Load** poles by themselves.
Right-click it with an empty hand to flip it; the toggle leans forward when on. Goggles show the
state. Like the socket it has a second knockout on the opposite edge, so it can sit in line on a run,
and sneak-clicking it opens the splice editor for both runs: put the switch in the hot and splice the
neutral straight through. Place and wrench it like the socket. It is a plain contact between the two wires, so put it in
the hot conductor of a lighting circuit and give the light its own conduit run back to a box.

---

### Flexible Conduit

A 1" whip that hangs from one knockout straight to another instead of following blocks, for the
places a rigid run cannot go: a crane boom's pivot, a drawbridge, a ship against a dock. Its two
ends may be on different physics bodies or one on the ground, and it follows both wherever they
move; it is never routed, extended or merged. Click one knockout, then the other, up to 16
blocks apart. Wire is pulled through it like any run, up to a 1" run's capacity, and the pulled
wires connect across however the two ends move. Recipe: a 1" conduit between two string, for
two.

## 4. Breaker panels

Seven wall-mounted load centres. The rating is the main breaker's job and the largest breaker any
space accepts. The lug count is how many line conductors feed the panel.

| Panel | Largest breaker | Lugs | Branch spaces |
| --- | --- | --- | --- |
| 200 A | 200 A | 1: Line + Neutral | 8 |
| 400 A | 400 A | 1 | 12 |
| 800 A | 800 A | 1 | 12 |
| 200 A Split-Phase | 200 A | 2: L1, L2 + Neutral | 12 |
| 400 A Split-Phase | 400 A | 2 | 12 |
| 400 A Three-Phase | 400 A | 3: L1, L2, L3 + Neutral | 12 |
| 800 A Three-Phase | 800 A | 3 | 12 |

The main space sits at the top. Branch spaces run in two columns, odd circuits on the left, even on
the right, top to bottom. **Without a main breaker the panel is dead.**

### Lugs, rows and multi-pole breakers

Each row of spaces sits on the next lug down the panel, both columns of a row on the same lug, and
the sequence repeats: on a split-phase panel rows alternate L1, L2, L1, L2; on a three-phase panel
they cycle L1, L2, L3. A single-pole breaker in any space gives you that row's lug against neutral.

Breakers also come as **2-pole** and **3-pole** (craft that many single-pole breakers of one frame
with an iron nugget). A multi-pole breaker takes that many poles' worth of *rows in one column*
and puts each pole on the next lug: a 2-pole in spaces 1 and 3 of a split-phase panel gives L1 and
L2, 240 V across its two circuit points. All its poles share one handle and trip together. Its name is the
spaces it spans, "Circuit 1/3", and each space is still its own circuit point in the editor.

- A 2-pole breaker fits split-phase and three-phase panels; a 3-pole only three-phase.
- The **main** must have as many poles as the panel has lugs: a 2-pole main in a split-phase
  panel, a 3-pole main in a three-phase one.
- With a breaker in hand only the spaces it would fit are outlined.

### Panel extensions

A panel is one block, six rows a column, and a 3-pole 800 A breaker alone takes nine. The **Panel
Extension** block adds rows: place it directly under the panel, on the same wall, and the panel
grows by its row count again (twelve more spaces) on the same bus, numbered on from the head's. A
second extension goes under the first; two is the limit, 36 spaces. Breakers may span down into an
extension, and are installed, flipped, pulled, locked, labelled and wrench-set on whichever block
they show on. The extension has four knockouts along its bottom and two down each side; it covers
the head's bottom knockouts, so those must be free before it goes on. Its front opens the same
splice editor. Take an extension out and the breakers in it drop, with any extension below it.

### Breakers

Breakers come as four **frames**: 1-50 A, 51-200 A, 201-400 A and 401-800 A. The frame is the
physical breaker; its **trip rating** is set once it is in the panel. Hold a **wrench**, look at the
breaker and scroll, or click it for the settings board. The 1-50 and 51-200 frames set in 1 A
steps, 201-400 in 5 A, 401-800 in 10 A. A new breaker starts at the top of its frame.

Bigger frames are bigger breakers. The two smaller frames take one row of a column per pole; the
201-400 A frame takes two rows per pole and the 401-800 A frame three. The extra rows are dead
(no circuit point, nothing to splice to); a pole always sits on the first row of its span.

- With a breaker in hand every free space it fits is outlined and the one under the crosshair is
  bright. Right-click a space to plug it in. New breakers start OFF.
- Right-click a breaker with an empty hand to flip it. A tripped breaker goes to OFF on the first
  click and ON on the next, like the real thing.
- Shift-right-click a breaker with an empty hand to pull it.
- Any frame up to the panel's rating fits any space with enough free rows below it, including the main,
  which is a single space whatever the frame.

### Trip curve

The same curve applies to every rating, scaled by how far over the rating the current is:

| Load (× rating) | Time to trip |
| --- | --- |
| at or below 1.0 | never |
| 1.05 | 58 s |
| 1.1 | 29 s |
| 1.25 | 11 s |
| 1.5 | 4.8 s |
| 2 | 2.0 s |
| 3 | 0.75 s |
| 4 | 0.4 s |
| 8 and above | instant |

Overloads add up: repeated short surges can trip a breaker that a single one would not. A breaker
that came close to tripping is fully reset after about 12 s at normal load. Tripping sparks and plays
the breaker sound.

### Blanks, locks and labels

- **Breaker Blank**: right-click a free space with it to fill the space. Pull it like a breaker.
- **Breaker Lockout**: right-click an installed breaker with it. The handle is frozen and will not flip
  by hand, but the breaker still trips on overload. Shift-right-click the breaker to take the lock off.
- **Labels**: rename a name tag in an anvil and right-click a space with it. The label shows in
  goggles, in messages and in the splice editor ("Circuit 3 (Kitchen)"). A plain name tag clears
  the label. Tags are used up.

### Wiring a panel

Panels have no exposed terminals. Wiring comes in through **conduit knockouts**: four across the top
edge, four across the bottom and two on each side. Land a run on a knockout, pull wire, then right-click the panel
front **outside the breaker spaces** with an empty hand to open the splice editor. Its points are:

- **Line**: feeds the main breaker.
- **Neutral**: a plain junction for every return conductor.
- **Circuit 1 .. N**: each fed from the bus through its own breaker.

Splice pulled wires to those points exactly as in a conduit box.

### Goggles

Goggles list every space: label, rating, handle position (ON / OFF / TRIPPED), live current, and
LOCKED where a lock is hung, followed by the knockouts in use and a splice count.

### Switchgear

A **Switchgear Section** is a floor-standing cabinet with a **2000 A three-phase bus** and a
single 3-pole breaker space in the door. Put sections in a row, all facing the same way, and their
buses join through hidden bus bars: the row is one lineup on one bus. Goggles on any section say
how many sections share it.

- Knockouts: four on top, four underneath. Points in the editor: **L1 bus, L2 bus, L3 bus,
  Neutral bus** and **Load L1, L2, L3** (the breaker's load side).
- Land the incoming feed on the bus of any section. To have a **main breaker**, land the feed on a
  section's load side instead: that breaker then sits between the feed and the bus.
- Every other section feeds one three-phase load through its breaker: splice the outgoing run to
  Load L1..L3 and the neutral to the neutral bus.
- Any 3-pole frame goes in the door; set its rating with a wrench, flip it, lock it and label it
  exactly like a panel breaker. Blanks fit too.
- The bus bars between sections carry 2000 A and burn out like any wire past that.

### Transformers

Fourteen nameplates, each a fixed high-side and low-side voltage, in four kinds of housing sized
after Create: PowerPlantGrid. A unit is one block whose model spills into the blocks around it;
those cells are filled with invisible parts that break with the unit, so place it with the room
free (a tank needs the block above, the substation units a ring around them).

| Housing | Nameplates | Wiring |
| --- | --- | --- |
| Pole can | 480 V/240 V, 1 kV/240 V, 10 kV/240 V, 35 kV/240 V | Stand it on the ground or place it against a pole to hang it. Hanging wire on the HV bushings on the lid (H1, H2) and the LV studs on the front (X1, N, X2). |
| Pad tank | 1 kV/240 V, 10 kV/240 V, 1 kV/208 V, 10 kV/208 V, 10 kV/480 V | Hanging wire on the lid bushings: tall HV at the back, short LV at the front. |
| Substation | 35 kV/480 V, 35 kV/10 kV, 100 kV/35 kV | Same, on a much bigger tank with radiators. |
| Dry-type cabinet | 480 V/240 V, 480 V/208 V | Conduit knockouts underneath and low on the front; splice inside like a panel. |

- A 240 V low side is split-phase: X1 and X2 are 120 V either side of the centre tap N. A 208 V or
  480 V low side is three-phase line-to-line: X1, X2, X3 are 120 V (or 277 V) each against X0, from
  a delta primary H1, H2, H3.
- **Taps**: two value boxes on the front of the base block, HV on the left and LV on the right.
  Scroll or click them to move that winding in 2.5% steps up to 10% either way. Changing a tap
  while that winding is live arcs and shocks you: there is no on-load tap changer.
- **Heat**: a unit heats with its copper losses and has a thermal limit sized to its rating, so a
  25 kVA can feeding a 100 kVA load cooks. A pole can's cutout **fuses** blow on sustained overload
  (a quarter over the rating, on the breaker curve); sneak-click the can to fit new ones.
- **Network jack**: low on the front of every unit. Computers see it as `powergrid_transformer`
  with the nameplate, rating, tier, both taps and their nominal voltages, every leg's voltage and
  current, the temperature, the cutout state and the HV tap target.
- **Tap tiers**. Tier 1 (pole cans, dry-types): taps by hand on the value boxes, dead only.
  Tier 2 (pad units): a **Tap Changer Drive** bolted to the tank moves the HV tap for you, but the
  unit must be dead; live, it arcs and the drive stalls. Tier 3 (substation units): an on-load tap
  changer, the same drive moves the tap live, one step every two seconds.
- **Pole cutouts**: every pole can has fused cutouts on its high-side bushings. Sneak-right-click the
  can with an empty hand to pull them open (the high side goes dead, so the taps can be worked without
  an arc) and again to close them. Goggles show whether they are open.
- Nameplates for the AC fork's generators: Pad 3.5 kV / 480 V, Pad 8 kV / 480 V and Substation
  35 kV / 8 kV (a step-up when fed from the low side).
- Three-phase units are delta on the primary, which only makes sense with an alternating feed. On
  stock DC Power Grid three DC sources on H1, H2, H3 are a dead short across the delta.
- Goggles show the taps and their voltages, the kVA rating and low-side current, and each leg's
  voltage against neutral and its current.
- On the regular (DC) Power Grid these pass DC at the ratio, as Power Grid's own transformer does.
  On the AC build they carry the phases.

---

### Tap Changer Drive

A gearbox that bolts to any cell of a tier 2 or 3 transformer: click the tank's side with it and
its shaft points away from the tank. Give the shaft rotation from any Create source and every
full turn is one attempt to move the HV tap one step towards the **target** on the drive's value
box, which a computer can also set through the transformer's jack. Goggles say what the last turn
did: stepping, at target, winding live (tier 2 must be dead first), or no drive (tier 1). It
draws a little stress while turning. A servo with a computer behind it makes a voltage regulator.

## 5. Safety

Working on live wiring hurts.

- Adding or removing a splice, or landing a run, on a terminal above **50 V** shocks you. Damage
  grows with voltage, from half a heart at 50 V to ten hearts around 800 V.
- Cutting a conduit, or pulling a wire back out, while it carries more than **1 A** shocks you,
  scaled by current.
- The edit or cut still happens. If it kills you, the death message says you forgot your lockout
  tagout. Flip the breaker first, and hang a lock on it.

---

## 6. Devices

All four devices have a built-in Cat6 jack (the cyan terminal, section 7) and **two conduit knockouts**
in addition to their ordinary terminals, so they can be wired either way. Right-click a device body
with an empty hand to open its splice editor; the points are the device's own terminals. The wrench
rotates them as usual.

### Digital Voltage Regulator

A computer-controlled DC source, in two sizes: the **2 kV** unit (this is the block that used to be
called the Variable Frequency Drive; its id, `vfd`, and its peripheral name, `powergrid_vfd`, are
unchanged so old worlds and scripts keep working) and the **8 kV** unit, crafted from a 2 kV unit
between copper coils over a transformer core. Two input terminals (+ / −) take power; two output
terminals (+ / −) deliver a commanded voltage. On the AC build the Three-Phase Drive is the real
variable frequency drive.

- Output voltage: −2000 V to +2000 V on the 2 kV unit, −8000 V to +8000 V on the 8 kV unit
  (negative reverses polarity). `getLimits()` reports the unit's ceiling.
- Goggles show a status line under the output: running, output disabled, no input voltage, input
  wired backwards, setpoint 0 V, input too low for the setpoint, holding the current limit,
  input sagging, or braking. If nothing comes out, the line says why.
- Wire the input backwards (+ and - swapped) and nothing comes out while the drive heats until it
  fails; the status line says so.
- Output current limit: 0 to 20 A. The drive backs off to hold the limit and to avoid dragging its
  input down.
- Braking. A motor coil keeps its current flowing when the output drops, and the regulator's
  converter is a transformer, which would carry that current straight back into the supply
  multiplied by the ratio: a 45 A motor dumped into a weak line put 64 kV on it. So a reverse
  current now opens the converter path and closes a braking resistor across the output, sized
  so the coil's current stays within the current limit and never more than the unit's ceiling
  voltage appears across it. The coil's energy is burnt in the regulator (watch its heat if you
  brake a big motor every few seconds); the supply sees none of it. The status line says
  "braking" and the goggles show the resistor's current and power while it lasts, which is a
  few ticks. Disabling the output closes the resistor too, so a coil never floats.
- Output can be enabled or disabled. Goggles show the setpoint and the measured input and output.

### Analog I/O Module

Four **analog outputs** (red terminals) that put out a commanded voltage of −24 to +24 V, four
**analog inputs** (green terminals) that sense a voltage, and one **Common** terminal (blue) that both
sides are referenced to.

### CT Cabinet

Sneak-right-click the cabinet with an empty hand to zero its energy counters.

A wall cabinet with four current transformers for power monitoring. It has no exposed terminals:
wiring comes in through eight knockouts (four top, four bottom) and is spliced in its editor, like
a panel. The points are:

- **Reference**: the conductor every channel's voltage is measured against (normally neutral).
- **CT n In / CT n Out** for channels 1 to 4: run a feeder in on In and out on Out. The channel
  reports the current through it (positive In to Out), the voltage of In against Reference, the
  power, and the energy accumulated since the last reset.

Right-click the cabinet with an empty hand to open it: the readings sit above the splice rows.
Goggles show the same lines. Energy is kept when the world reloads and can be zeroed from a
computer. The cabinet has a Cat6 jack for the network.

### Three-Phase Motor (AC build)

A Create generator like Power Grid's electric motor, with three terminals U, V, W on the terminal
box at the back and the shaft out of the front. Feed it three phases (from three alternators at
0°, 120°, 240°, a three-phase transformer, or the Three-Phase Drive):

- It turns at the **synchronous speed**, 60 × frequency / pole pairs, less up to 5% slip at full
  load. Set the pole pairs on the value box on the terminal-box end: one pole pair gives 270 rpm at
  4.5 Hz, the speed of the alternator feeding it.
- The **phase sequence** sets the direction: U-V-W forward, U-W-V reverse. Swap two phases, or use
  the drive's reverse, to turn it the other way. With one phase missing or on DC it does not turn.
- It needs about 12 V per hertz per phase for full excitation and stalls below a fifth of that.
- Loaded windings draw more current, as Power Grid's motor does. It heats and can burn out.
- Goggles show frequency, sequence, phase voltage and current, pole pairs and synchronous speed.

### Three-Phase Drive (AC build)

A variable frequency drive in the DC VFD's form: floor-mounted, wrench-rotated, with two conduit
knockouts and a Cat6 jack. Three-phase in on L1, L2, L3 (DC across two of them works too); three
phases out on U, V, W.

- Set the **frequency** on the value box on top (whole hertz, 0 to 120) or from a computer.
- Sneak-right-click the drive with an empty hand to type its **ratings**: the rated volts, the
  hertz they are reached at, and the ramp rate; computers set them with `setRated(volts, hz)` and
  `setRamp`, and read them with `getRated`.
- The output holds the **rated volts per hertz** (120 V at 10 Hz by default) up to the rated
  voltage, capped at what the input can supply (about 0.58 × the input line voltage).
- Changes **ramp** at 5 Hz/s by default. Disabling ramps to a stop.
- **Reverse** swaps the phase sequence, so a motor on it runs backwards.
- Whatever real power the output delivers, plus a little idle, is drawn from the input, so a
  weak source sags.

### Clamp Meter

Reads the current in any Power Grid wire that passes through its jaw, without being in the circuit.
Several wires in the jaw are summed.

### Line Voltmeter

Two probes (+ / −) with a very high input resistance, so it can hang across anything, including
transmission lines. Reads the voltage of + relative to −.

### Line Ammeter

An in-line shunt: the line runs from its IN terminal to its OUT terminal through a small resistance.
Reads the signed current.

---

### Synchroscope (AC build)

A wall meter for paralleling a machine onto a live bus. Four lugs along its bottom: **Bus** and
**Bus neutral** on the left, **Incoming** and **Incoming neutral** on the right. Feed each pair
from the two sides of the open tie (a switch, a breaker). The needle points straight up when the
incoming is in phase with the bus and turns clockwise when it leads, at the slip frequency; the
face turns green when the slip is under 0.1 Hz, the angle within 10° and the voltages within 5%,
which is the moment to close. Goggles show both frequencies and voltages, the slip and the angle.
Computers see it as `powergrid_synchroscope` with getBusFrequency, getIncomingFrequency,
getBusVoltage, getIncomingVoltage, getSlip, getPhaseAngle and isInSync.

### Creative AC Source (AC build)

A creative-only source: L1, L2 and L3 along the front of its top and a neutral at the back, at
the line-to-neutral voltage and frequency on its two value boxes (the voltage steps through the
mod's nameplate voltages, 120 V to 100 kV; the frequency runs 1 to 120 Hz). Current is unlimited.
It is three-phase by default; sneak-right-click it with an empty hand for split-phase, where L1
and L2 are half a turn apart and L3 repeats L1.

## 7. Cat6 network

The **Cat6 Cable** is a Power Grid wire that carries computer network traffic instead of current.
It is placed with two clicks like any wire and only connects **network jacks**:

- The regulators, Analog I/O Module, Line Voltmeter, Line Ammeter and every transformer have a jack built in (the cyan
  terminal). Ordinary wires refuse it and the Cat6 refuses the electrical terminals.
- The **Network Jack** block is a small wall plate for the computer end. Adjacent ComputerCraft wired
  modems and cables, and OpenComputers cables, join its network.
- The **Network Switch** has eight numbered ports and behaves like the jack otherwise. On a floor it
  faces you, on a wall it mounts as a plate, on a ceiling it hangs. A port lights up while a cable is
  plugged in.

Jack to jack hangs a cable; clicking blocks in between lays it along their surfaces. Every port takes
one cable, so fanning out means going through a switch. Cables re-link themselves once a second, so
reloaded chunks and rebuilt networks recover on their own.

---

### Network Switch modes

Sneak-right-click the switch with an empty hand to change its mode:

- **Switch mode** (default): all eight ports are one network. Computers on it see each other's
  components, exactly as if they shared one OpenComputers cable.
- **Relay mode**: each port is its own network. Components stay behind their port; only network
  messages (modem sends and broadcasts) are repeated out of the other ports, hop counted like the
  OpenComputers relay. Use it to keep component sharing off a building's backbone.

ComputerCraft traffic is always shared. Conduit and Cat6 will not land on Power Grid's own wire
connectors or nodes; they need a knockout or a jack.

## 8. Computer integration

### ComputerCraft

Each device is a wired peripheral on the Cat6 network. Its name is `<type>_<x>_<y>_<z>` with negative
coordinates prefixed by `n`, so `peripheral.find("powergrid_vfd")` or
`peripheral.wrap("powergrid_vfd_12_64_n5")` both work from any computer on the run.

**powergrid_vfd** (both Digital Voltage Regulators)

| Method | Meaning |
| --- | --- |
| `setVoltage(volts)` | Output setpoint, up to the unit's rating either way |
| `getVoltage()` | Setpoint |
| `setCurrentLimit(amps)` | 0 to 3 |
| `getCurrentLimit()` | |
| `setEnabled(bool)` / `isEnabled()` | |
| `getOutputVoltage()` / `getOutputCurrent()` | Measured output |
| `getInputVoltage()` / `getInputCurrent()` | Measured input |
| `getPower()` | Output power in W |
| `getLimits()` | Maximum voltage and current of this unit |
| `getStatus()` | Why the output is what it is: `ok`, `disabled`, `no_input`, `reversed`, `setpoint_zero`, `input_low`, `current_limit`, `input_sag`, `braking` |

**powergrid_transformer** (legs are 1 to the leg count; taps are −4 to 4)

| Method | Meaning |
| --- | --- |
| `getNameplate()` / `getRatedVa()` | "10 kV / 480 V" and the rating in VA |
| `getTier()` | 1 manual taps, 2 drive moves the HV tap dead, 3 on-load tap changer |
| `getHvTap()` / `getLvTap()` | Present taps |
| `getHvVoltage()` / `getLvVoltage()` | Nominal volts at the present taps |
| `getLegCount()` | Low-side legs metered |
| `getVoltage(leg)` / `getCurrent(leg)` | Measured on that low-side leg |
| `getTemperature()` | Winding temperature |
| `isCutoutOpen()` / `areFusesBlown()` | Pole can cutouts |
| `getHvTapTarget()` / `setHvTapTarget(tap)` | Where a Tap Changer Drive on the tank moves the HV tap |
| `getTapStatus()` | Last drive step: `ok`, `at_target`, `live`, `no_drive`, `idle` |

**powergrid_analog_io** (channels are 1 to 4)

| Method | Meaning |
| --- | --- |
| `setOutput(channel, volts)` | −24 to 24 |
| `getOutput(channel)` | Commanded voltage |
| `getOutputCurrent(channel)` | |
| `getInput(channel)` | Sensed voltage relative to Common |
| `getInputs()` / `getOutputs()` | Tables indexed 1 to 4 |
| `getRange()` | 24 |
| `getChannels()` | 4 |

**powergrid_ct_cabinet** (channels are 1 to 4, energy in watt-hours)

| Method | Meaning |
| --- | --- |
| `getChannels()` | 4 |
| `getVoltage(channel)` / `getCurrent(channel)` / `getPower(channel)` | Live readings (RMS and real power) |
| `getPowerFactor(channel)` | Real over apparent power; 1 on direct current |
| `getEnergy(channel)` | Accumulated Wh |
| `getTotalPower()` / `getTotalEnergy()` | Summed over channels |
| `getReadings()` | Table 1..4 of {voltage, current, power, powerFactor, energy} |
| `resetEnergy()` | Zero the counters |

**powergrid_three_phase_drive** (AC build)

| Method | Meaning |
| --- | --- |
| `setFrequency(hz)` / `getFrequency()` | Commanded frequency; a negative value hands control back to the value box |
| `getOutputFrequency()` | Frequency the output is running at, following the ramp |
| `setRatedVoltage(v)` / `setRatedFrequency(hz)` | The volts-per-hertz base (120 V at 10 Hz by default) |
| `setRampRate(hzPerSecond)` | Acceleration and deceleration |
| `setEnabled(bool)` / `setReversed(bool)` | Run or coast to a stop; swap the phase sequence |
| `getOutputVoltage()` / `getOutputCurrent()` / `getInputVoltage()` / `getInputCurrent()` / `getPower()` | Live readings, RMS |

**powergrid_three_phase_motor** (AC build; from an adjacent modem): `getFrequency()`, `getVoltage()`,
`getCurrent()`, `getSequence()` (1, -1 or 0), `getSpeed()`, `getSynchronousSpeed()`, `getPolePairs()`.

**powergrid_clamp_meter**: `getCurrent()`, `isClamped()`, `getWireCount()`.

**powergrid_ammeter**: `getCurrent()` (signed, IN to OUT), `getPower()` (shunt loss).

**powergrid_voltmeter**: `getVoltage()`.

### OpenComputers

The same devices are components with the same names (`powergrid_vfd`, `powergrid_analog_io`,
`powergrid_clamp_meter`, `powergrid_ammeter`, `powergrid_voltmeter`, `powergrid_ct_cabinet`, `powergrid_transformer`) reachable over the Cat6
network from an OpenComputers cable next to a jack or switch. `component.doc` on any method prints
its signature. Methods match the ComputerCraft ones, plus:

- **Analog I/O**: `getAppliedOutput(channel)` is the voltage actually applied after current
  limiting; `setChangeThreshold(volts)` makes the module push an `analog_change` signal when an input
  moves by more than that (0 disables).
- **Clamp meter and ammeter**: `setChangeThreshold(amps)` for a `current_change` signal.
- **Voltmeter**: `setChangeThreshold(volts)` for a `voltage_change` signal.
- **CT cabinet**: `setChangeThreshold(watts)` for a `power_change` signal on total power.
- **Three-phase drive** and **motor**: the same methods as their ComputerCraft peripherals, as components
  `powergrid_three_phase_drive` and `powergrid_three_phase_motor`.

When OpenComputers is present, Power Grid's own blocks also become read-only components for an
adapter placed next to them:

| Block | Component | Methods |
| --- | --- | --- |
| Voltage / current / power gauge | `powergrid_voltage_gauge`, `powergrid_current_gauge`, `powergrid_power_gauge` | `getValue()`, `getMaxRange()`, `getRangePercentage()`, `getVoltage()`, `getCurrent()` |
| Energy meter | `powergrid_energy_meter` | `getEnergy()` |
| Battery (single or multiblock) | `powergrid_battery` | `getEnergy()`, `getCapacity()`, `getChargePercentage()`, `getPower()` |
| Any other electrical block | its own name | `getTerminalCount()`, `getTerminalVoltage(index)`, `getTerminalVoltages()` |

---

## 9. Quick recipes

| Item | Recipe |
| --- | --- |
| Conduit (8) | iron nuggets / ingots over copper wire; 1" adds a second row of ingots; bigger sizes ring copper wire with iron ingots, then iron blocks |
| THHN wire (8) | shapeless: dried kelp with copper nuggets (14, 12, 10 AWG), copper ingots (8 AWG to 4/0) or copper blocks (250 kcmil and up) |
| Conduit Box (2) | ring of eight iron nuggets |
| Blank Cover Plate (2) | an iron plate |
| Node Cover Plate | a blank cover plate and pins, shapeless |
| Pull Box | ring of eight iron plates |
| Conduit Switch (2) | iron nugget, lever, iron nugget over an iron nugget |
| Conduit Socket (2) | iron nugget, copper nugget, iron nugget over an iron nugget |
| Breaker Panel 200 A | iron plates and copper plates around a conductive casing, heavy wire connector at the bottom |
| Breaker Panel 400 A / 800 A | the smaller panel surrounded by copper and brass plates / brass and iron |
| Breaker frames | a column of iron, redstone, copper: ingots for the 1-50 A frame (makes two), plates for 51-200 A, double-width plates for 201-400 A, double-width blocks for 401-800 A |
| Breaker Blank (4) | two iron nuggets |
| Breaker Lockout | an iron ingot ringed by five nuggets |
| CT Cabinet | pins, comparator, pins / iron plate, conductive casing, iron plate / copper wire, iron plate, copper wire |
| Split-phase / three-phase panels | the single-lug panel of the same rating with heavy wire connectors either side and a copper plate (two for three-phase) below |
| 2-pole / 3-pole breakers | two or three single-pole breakers of one frame and an iron nugget, shapeless |
| Copper Bus Bar (4) | three copper blocks in a row |
| Switchgear Section | bus bars either side of a 400 A three-phase panel, iron plates above and below, a bus bar top centre |
| Transformers | copper coils either side of a transformer core, bigger housings with more of each; iron plates for cans and cabinets, smooth stone under the tanks |
| Tap Changer Drive | a shaft between iron plates, a large cogwheel between two cogwheels, iron plates below |

Look the rest up in the recipe book; every recipe unlocks from its main ingredient.

## 10. Real life components

### The electrical inspector

Any right-click, placement, pull or splice on an electrical block counts as electrical work. While
you have worked in the last five minutes there is a small chance every second (one visit per ten
minutes of work on average) that the **Electrical Inspector** spawns twelve to twenty blocks away
and walks over. He goes to the last block you touched, looks it over with a "Hmmmm", then asks for
your **Electrical License**. Having it in your inventory is not enough; he has to see it:

- **Drop the card** (Q) on the ground near him, or right-click him with it. He walks over, picks it
  up, reads it for a couple of seconds, says "All in order" and tosses it back to you. Pick it up
  again.
- Nothing within twelve seconds: he pulls a knife and stabs you for twenty seconds, or until you
  drop the card, run more than thirty blocks, or kill him.
- **Bribe**: right-click him with eight emeralds while he is asking or stabbing and he pockets them
  and leaves.

He is never sent after creative or spectator players and never within ten minutes of the last
visit, and he gives up if he cannot reach you in four minutes. But an inspector who is already
about, whether he came from a spawn egg or is on his way out from someone else's inspection, asks
**anyone** who does electrical work within sixteen blocks of him, creative players included. If he
dies holding a card, it drops.

All of this is configurable in `config/powergrid_modernized-common.toml`: the spawn odds (`0`
turns visits off), the cooldown, the work window, the bribe, the notice range, the training
center's village weight, the scrap chance and whether push brooms spawn naturally.

### Electrical License

Not craftable and not tradeable. Every village generated after the mod was installed has an
**Electrical Training Center**, a stone-brick schoolhouse with yellow trim and a lightning rod on
the roof, standing a few blocks east of the village's well or meeting point (the mod wraps every
vanilla village centre so the school comes with it). More can appear among the houses. Its chest
always holds one license plus some wire, scrap, books and the odd emerald.

Villages that existed before the mod was installed, or before this version, never get one: world
generation only touches new chunks. To find one, use `/locate structure minecraft:village_plains`
(or `_desert`, `_savanna`, `_snowy`, `_taiga`) and go to one you have not visited, or generate a
fresh village where you stand with `/place structure minecraft:village_plains`. To check the
building itself loads, `/place template powergrid_modernized:village/training_center` places it
at your feet. The server log line "Electrical Training Center: beside the centre of 5 village
types" on world load confirms the pools were patched. Both can be turned off in the config:
`training_center_at_center` and `training_center_weight`.

### Copper scrap

Pulling a wire into a conduit run, or toggling or landing a splice, has a one-in-eight chance of
giving you **Copper Scrap**. Toolsmiths, armorers and weaponsmiths buy eight for an emerald from
their first trade level. The training center chest has some too.

### The push broom

The **Push Broom** is a hostile mob that spawns at night anywhere in the overworld (weight 20, in
groups of one or two). It hops toward the nearest player and sweeps for three damage. Sixteen
health; drops up to two sticks and, one time in four, a copper scrap. Both new mobs have spawn eggs
in the creative tab.

## 11. Cable chain

Create Aeronautics bodies (a gantry trolley, a crane, a vehicle) are Sable sub-levels: their blocks
live elsewhere and are drawn where the body is. Power Grid's hanging wires already understand that
and re-project their ends every tick, so a plain wire to a body follows it. The cable chain does the
same for a whole raceway.

- **Cable Chain Anchor**: a pull box with a **4" knockout** on one side, the **chain post** on the
  front, studs **L1, L2, L3, N** on the back edge and a **Cat6 jack** on the side. Mount one on the
  structure and one on the body. Run conduit to the knockout, or land wire on the studs.
- **Cable Chain** (crafted from chain and iron plates, four per craft, two metres each): click the
  structure anchor first, then the body anchor. A 4" run now hangs between the posts, and a Cat6
  pair is carried inside it between the two anchors; both jacks stay free for your own cables.
- **Pull wire** through it exactly as through conduit: click the chain with THHN. Same fill rules
  as a 4" raceway. Click near the fixed anchor; that part of the chain never moves.
- **Splice** in each anchor with an empty hand: the chain's conductors, the knockout's conductors
  and the four studs are all points in the editor, like the two hubs of a pull box.
- The run is cut two links longer than the distance when you string it; goggles on an anchor say
  how far it can stretch. Stretch it further and it snaps, dropping the links and the pulled wire.
- **Too short?** Click the chain with more Cable Chain items: one link per click, the whole stack
  when sneaking, up to the configured longest run plus the slack. The links come back when the chain
  is taken down.
- Wire cutters sneak-click takes the chain down; breaking either anchor does too. Conduit cannot
  tee into the chain.
- The longest run is `max_length` under `cable_chain` in the config (32 m by default).

Assemble the body first and string the chain afterwards: Power Grid moves or cuts wires when
blocks are assembled into a body, and the chain is no exception.

Cat6 laid along a body's blocks, conduit runs on it and the conductors pulled through them ride
with the body: the mod moves each such entity to where the body shows it and asks Sable to carry it
from then on. Only the body's position is followed, not its rotation, which suits a trolley on a
straight runway. Hanging wires and Cat6 strung between two jacks on the same body were already
handled by Power Grid.

## 12. Conductor rail

The alternative to the cable chain for long runways, modelled on crane conductor bar systems.

- **Conductor Rail**: four insulated bars on a bracket. Mounts on any face and turns four ways like
  the devices. Lay blocks end to end (or around corners, touching) along the runway; the run is
  every rail block that touches another.
- **Rail Feed Box**: put one anywhere in the run (bars run out of both sides). It is a pull box:
  studs L1, L2, L3, N are the four bars, a 4" knockout takes your conduit, and its Cat6 jack is the
  run's data channel. Splice with an empty hand. One feed per run; a run with none is dead.
- **Rail Collector** (crafted with four Collector Shoes): the same box on the body. Put it within
  three blocks of the rail in a straight line, in any direction: on top of the trolley under an
  overhead rail, or beside a wall rail. The arm swings to the first rail block it finds (it tries its
  front first, then straight away from the face it is mounted on) and the shoes sit in that block.
  The rail may be in the world or on another body: a trolley riding a crane bridge that is itself
  a body finds the rail on that bridge. It never connects to a rail on its own body. The run
  still needs a Rail Feed Box touching it, or the goggles say "off rail".
  Turn it with the wrench so its stud row runs the same way as the bars and the shoes line up.
  While they do, and that run has a feed, the collector's studs are the bars and its jack is on the
  run's data channel: plug your Cat6 into the collector's jack on the trolley and into the feed's jack
  on the structure, and the two are one network.
  Both boxes are OpenComputers nodes too, so an OC cable next to them joins; ComputerCraft computers
  reach them through a Network Jack block and a wired modem, as with every other device jack. Goggles say which feed it is on, or "shoes off the rail".
- Contact is checked every half second and carried by hidden Power Grid hanging wires, so it rides
  the body between checks. Running off the end of the rail drops the contact; running back on
  restores it. Several collectors can share one run.
- Rail blocks have no circuit of their own, so a run of any length costs nothing to simulate: the
  collector connects straight to the feed.

A setup diagram for both the rail and the chain is in `docs/rail_and_chain_setup.svg`.

**THHN on terminals.** Since 0.14.1 the THHN gauges count as Power Grid light wires, so they land
on any terminal that takes ordinary wire: the feed and collector studs, the chain anchors, panels,
transformers and Power Grid's own blocks. Before that they only went through conduit.

## 13. Rack and pinion

How a gantry crane on a Create Aeronautics body moves itself along its runway.

- **Rack**: a toothed bar, 12 px of steel with the teeth on top. Lay it in the world along the
  runway, end to end, like a rail. The teeth point away from the face you place it on. On a floor
  it runs the way you face; against a wall it runs along the wall, or up it when you sneak (a
  climbing rack for a hoist). The pinion goes in the block the teeth point into, so a cog over a
  floor rack hangs with its rim just into the teeth.
- **Pinion**: a small cogwheel for the body. It takes rotation like any Create cogwheel, from a
  shaft in line with its axle or a cog beside it. Mount it on the crane so one of its four rim
  sides faces the rack: axle across the runway, the rack in the block its rim looks into. A pinion
  under the crane frame over a floor rack, or beside a wall rack, both work.
- Turn the shaft and the body walks along the rack at the rim speed (shaft rpm x pitch radius,
  0.5 m by default: 16 rpm is about 0.8 m/s, 64 rpm about 3.4 m/s). Reverse the shaft to go back.
  A stopped pinion holds the crane where it is, and the rack keeps it from drifting sideways. If a
  rotation walks the wrong way, sneak-click the pinion with an empty hand to reverse that one
  pinion (the goggles say when it is reversed); `pinion.invert` in the config flips them all.
- Two pinions on one body must walk the same way. Mirrored mounts, one each side of a crane,
  often turn opposite ways and cancel each other out: the crane just twitches. Check the goggles
  on each (a negative "moving" speed on one of them is the giveaway) and reverse that one.
- The pinion only pushes where its rim actually faces a rack block. Gaps in the rack are gaps in
  the drive. Several pinions on one body all push, so a long crane can have one on each leg.
- The push goes through the body's centre of mass, so it never rocks a hanging trolley into its
  guides. Create: Linear Bearing's bearing, casing and moving blocks are made frictionless for
  Sable by this mod, so a trolley riding on them slides freely.
- By default the pinion is `lock`ed to the rack: the body moves at exactly the rim speed however
  heavy it is, and a stopped pinion holds it in place. The body is also kept from turning, so a
  hanging trolley cannot tilt and wedge in its guides. If the guides still drag it short of the
  rim speed, the pinion leans in by up to `force` times the rim speed to make it up; raise `force`
  if it sticks, lower it if it lurches. Solid blocks in the way still stop it. Locked, `gain` and
  `max_acceleration` are not used. Turn `lock` off for a softer drive governed by all three.
- Goggles on the pinion show the rim speed and whether it is on a rack, and if not, why: no rack
  in any of the four cells the rim faces, a rack whose teeth do not point at the cog, a rack that
  runs along the axle instead of across it, or no physics steps at all (the pinion is not on an
  assembled body). The rack may be in the world or on another body.
- Without Create Aeronautics the pinion is just a cogwheel.

**Three-Phase Motor knockouts (0.15.8).** The motor has a conduit knockout on each side of its
terminal box, like the drive. Run conduit into one, splice U, V and W with an empty hand on the
motor, and the goggles list what is spliced.

## 14. FE Inverter (AC fork)

Forge Energy from any mod's cables in, three-phase AC out.

- Feed it FE on any side: Mekanism, EnderIO, Create New Age, anything that pushes Forge Energy.
  It holds a buffer (1,000,000 FE by default) and accepts up to 100,000 FE a tick.
- The dry-type transformer's cabinet, one block wide and two tall: it needs the block above it
  free and takes that cell with it. It is wired like the dry-type, through conduit knockouts:
  four low on the front and four underneath. Run conduit into a knockout and splice its
  conductors onto L1, L2, L3 and the neutral with an empty hand on the front; hanging wire does
  not land on it. Two value boxes on the front: the left
  steps through the nameplate voltages (120, 208, 240, 277, 480, 600, 1 kV, 3.5 kV, 8 kV, 10 kV,
  35 kV, 100 kV), the right sets the frequency in hertz. Sneak-click with an empty hand to say
  whether the figure is line-to-neutral or line-to-line: pick 480 V L-L and each line gets 277 V
  to the neutral. The goggles show both figures, the power delivered and the FE it takes per tick.
- It pays for what it delivers: the real power on the lines costs FE every tick at Power Grid's
  own rate, the `forgeEnergyPerWatt` its FE Inverter and Device Connector use (10 by default: a
  watt is 10 FE a tick, 1 kW is 10,000 FE a tick, which is just what a Device Connector would give
  back for that kilowatt), over the efficiency (95 %). `inverter.fe_per_watt` prices it apart
  from Power Grid if you must; following Power Grid is what keeps that loop lossy. An empty buffer is a
  brownout: the lines go dead and stay dead until the buffer holds a second of the steady draw
  again (at least 2 % of the buffer, never more than 10 %), so a starved inverter does not
  flicker. The goggles show how far the refill has got.
- Goggles show the setting, the power being delivered, the buffer and any brownout. Computers see
  it as `powergrid_inverter`: setVoltage, getVoltage, setFrequency, getFrequency, getPower,
  getStored, getCapacity, isBrownedOut.
- Config section `inverter`: buffer, max_input, fe_per_watt, efficiency.
- **No free energy.** The Three-Phase Motor carries only as much Create stress as its
  electrical draw buys: full-load watts times its efficiency (90 %) over the watts a stress unit
  is worth, which by default is Power Grid's own figure for its motors and generators (about
  0.159 W per SU, so a kilowatt buys about 6,300 SU; `energy.watts_per_su` overrides it). More
  voltage, more stress, more FE. A loop inverter, motor, Create New Age generator and back loses
  almost everything per lap: New Age pays 0.59 FE a second for a stress unit the motor bought for
  0.159 W, which is 32 FE a second. Setting `watts_per_su` below about 0.003 (at 10 FE per watt)
  turns that loop into a source of energy.
- **It draws what it carries.** The motor's windings are retuned every few ticks so its real
  draw is the stress it is actually carrying, its share of the network's load, times the watts a
  stress unit is worth, over its efficiency. An idle motor takes 3 % of its full-load draw for
  magnetising and friction; one on a network that is overstressed carries everything it has,
  like a locked rotor, and draws full load until something gives. The goggles show the stress
  carried and the watts asked for. A supply that cannot deliver those watts sags, and the motor
  takes what the sagged voltage allows.

## 15. Laser Rangefinder

A plate with a barrel that points away from the face you mount it on, and a Cat6 jack on the
plate. Every couple of ticks it fires a ray along the barrel and keeps the distance to the first
thing it meets:

- a block of the world,
- a block of a physics body (a crane, an elevator car, a ship),
- an entity: a Create contraption, a vehicle, a mob. Items and wires are ignored.

Mounted on a body it measures from wherever the body holds it, so one on the trolley looking down
the runway reads the distance to the end stop, and one at the bottom of a shaft looking up reads
the car's height.

- **Goggles** show the distance and what it hit, or "no target within N blocks".
- **Computers**, over the jack's Cat6 or an adjacent OC cable, see `powergrid_rangefinder`:
  getDistance (blocks, -1 for nothing), getTarget (none, block, body, entity), getRange, setRange.
  OpenComputers can also setChangeThreshold(blocks) to get a `distance_change` signal whenever the
  reading moves by that much, so a crane program can wait on it instead of polling.
- **Comparator** behind it: 15 with the target at the barrel, falling to 0 at the range limit, so
  setRange also scales the redstone.
- Config section `rangefinder`: max_range (512), default_range (128), interval (2 ticks).

## 16. Cam-Lock Boxes

For portable generator sets: a box with five cam-lock receptacles, L1, L2, L3, N and G, that
take ordinary wire (THHN included; no special cam cables), and two 4" conduit knockouts, one on
each side. Mounts on any face and turns four ways like the other devices.

- Each pole passes straight through: whatever hangs on a receptacle is on the same pole as the
  conductor you splice to it from a knockout. Open the splice editor with an empty hand.
- Two ratings: **100 A** and **400 A**. Each pole goes through a contact rated for the box, so pulling
  a quarter more than the rating through the lines heats the box like any overloaded Power Grid
  device; keep going and it burns. The goggles show the current on each pole and warn in red.
- A generator set on wheels (a Create Aeronautics body) with a box on it plugs into the building's
  box with five hanging cables, cam to cam. The cables stretch with the body like any hanging wire.
- Recipes: the 100 A box is copper ingots in an iron plate frame; the 400 A box is a 100 A box
  between copper blocks in an iron plate frame.

## 17. Load Bank (AC fork)

A dummy three-phase load for testing generators, inverters and wiring: three resistors in star
from L1, L2, L3 to the neutral, in a wall cabinet (four lugs low on the door, four conduit
knockouts, two value boxes).

- Left box: the load, 0.5 kW to 5 MW, for all three phases together. Right box: the line-to-neutral
  voltage that load is rated at. Together they fix the resistance of each phase, which the goggles
  show along with what the bank really draws at the voltage it is given. Feed a 10 kW at 277 V
  bank with 120 V and it draws about 1.9 kW, like any resistor.
- For an exact figure, sneak-click the door with an empty hand: a small screen takes the load in
  kilowatts and the rated voltage as typed numbers (Enter applies). A typed figure overrides its
  box until you turn that box, or press "Use boxes".
- It never overheats; a load bank is built to burn its power off. Everything upstream still can.
- Recipe: copper coils and an encased fan in an iron plate frame.

## 18. RF Connector (AC fork)

Power Grid's Device Connector for Power Grid's AC: AC in, Forge Energy out, never the other way.
A box the size of the conduit switch, mounted on any face and turned four ways like it, with one
conduit knockout and an orange port on top.

- Run conduit into the knockout and pull two wires through: the first two land on **Line** and
  **Neutral** by themselves, so there is nothing to splice (an empty hand still opens the editor
  if you want to move them). It is a single load from line to neutral; no RF device needs three
  phases, so feed it one line and the neutral of whatever you have, 120 V or 277 V alike.
- It hands Forge Energy to every block touching it that takes FE: machines, cables, batteries,
  from any mod. Put a Mekanism machine against it and it runs. Nothing can push FE into it, and it
  takes nothing back, so a loop through an FE Inverter only loses what both lose.
- It draws only what is being taken. The FE missing from its buffer, at Power Grid's FE per watt,
  is what it asks the line for each tick; with nothing drawing, its load is as good as open. At
  1 FE per watt per tick a machine taking 1,000 FE a tick costs about 1.05 kW. The load never goes
  below 0.1 ohm, so a sagging line is not shorted.
- Goggles show the FE being supplied, the power drawn with the voltage and current, and the
  buffer. Config section `rf_connector`: buffer (100,000 FE), max_output (100,000 FE a tick,
  which also caps the draw), efficiency (95 %).
- Recipe: a Power Grid Device Connector between two iron nuggets, a nugget below.

## 19. Helm

A pedestal with a console and a wheel, and a Cat6 jack on the back of the console. It is a
keyboard for a computer that does not need a screen: right-click it with an empty hand and every
key you press goes to the computers on that jack's network, by name, while your view stays your
own and the mouse still looks around. You stand still at it. The ~ key (grave, left of 1) lets
go, and so does opening any screen (Escape included), walking more than five blocks away,
logging out or dying. Only one player can hold a
helm; the goggles say who.

- **Keys** are named as the controls screen names them: `w`, `a`, `space`, `left.shift`,
  `left.control`, `up`, `keypad.1`, `f`, and so on. Mouse buttons are `mouse.left`,
  `mouse.right` and `mouse.middle`, and the wheel sends `scroll.up` or `scroll.down` as a press
  and release together. While you hold the helm, the game does not see any of them: no inventory,
  no chat, no attacking, no walking.
- **Computers** see `powergrid_helm` over the jack's Cat6 or an adjacent cable. Poll with
  `isDown(key)` and `getPressed()`, or wait on events: OpenComputers gets the signals `helm_key`
  (name, pressed), `helm_taken` (player) and `helm_released`; CC: Tweaked gets the same as events.
  `isManned()` and `getHelmsman()` say who is there. When the helmsman lets go, every key still
  down is released first, so a drive program never sees a key stuck.
- A helm on a physics body measures its five blocks from wherever the body holds it, so you can
  stand at the wheel of a moving crane or ship.
- A ten-line OpenComputers driver for a crane: wait on `helm_key`, and on `w`/`s` set the drive
  forward or reverse, on their release stop it; `space` as a brake. The whole bridge is then one
  block, one cable and one computer, with no screen anywhere.
- Recipe: a Create cogwheel over a network jack between iron plates, a plate below.

## 19a. Radio Remote and Radio Base

The helm without the pedestal. The **Radio Base** is a small box with an antenna and a Cat6 jack
on its back; cable it to a computer or to a controls cabinet's internal jack and it is a
`powergrid_radio` component with the helm's methods (`isManned`, `getHelmsman`, `isDown`,
`getPressed`) and signals (`radio_key`, `radio_taken`, `radio_released`), and the PLC discovers it
as `radio1`. The **Radio Remote** is a handheld item: right-click a base with it to pair, then
right-click with it anywhere in that dimension and your keys go to the base as if you stood at a
helm, with no range limit, so it drives a vehicle from the ground or a crane from the floor. You
keep the base while the remote is in either hand; putting it away, or ~, lets go. The base must be
loaded, which on a vehicle it is while anyone is near it. One base takes one holder at a time;
several remotes may be paired to the same base.

## 20. Controls Cabinet

A wall cabinet with a DIN rail inside and a door of six cells outside, for the operator side of a
machine. It mounts on a wall like the CT cabinet, has twelve conduit knockouts (four top, four
bottom, two each side) and one Cat6 jack on the left side, as you face the door, that puts the whole cabinet on the
computer network as `powergrid_controls`.

- **The rail** takes six modules, fitted by right-clicking the cabinet with one: a **24 V Power
  Supply** (the bus is live while it sees at least 50 V between the cabinet's Line and Neutral
  terminals; without one nothing works), **8-Channel Input** modules, **8-Channel Output**
  modules and **2-Channel Relay** modules. Each relay contact is a pair of terminals in the splice
  editor, Relay n.k COM and NO, that the computer closes; wire a motor starter or a light circuit
  through one. Modules come off with the Remove buttons in the cabinet's screen, or with wire
  cutters while sneaking.
- **The door** takes six devices, fitted by right-clicking a cell with one: **E-Stop** (press to
  latch, press again to twist out; it stays pressed with the power off), **Toggle Button**,
  **Momentary Button** (half a second), **Up Button** and **Down Button** (momentary, with an arrow
  on the cap), **Selector Switch** (three positions, left, centre, right), **Pilot Light** (five
  colours), **Buzzer** (a horn on an output, sounding while it is on) and **Number Display** (four
  digits the computer writes). Devices come out with Remove or with cutters on the cell. Every
  device can carry a **label**, typed in the cabinet screen and drawn under it on the door, and a
  **colour** from the pilot light's five, which colours a button's cap or a toggle's rocker. A
  button can be **backlit**: pick an output channel on its second row and its cap glows while that
  output is on, dims while it is off, so a start button can show the machine running.
- **Wiring** is done in the cabinet's screen (empty hand on the door off a device): each input
  device is wired to one channel of an input module, each light to one channel of an output
  module, with the arrow buttons; a selector takes two channels, its left position on the first,
  its right on the next. A new device wires itself to the first free channel of the first fitting
  module, so a small cabinet needs no editing at all. Sneak with an empty hand for the splice
  editor, where Line, Neutral and the relay contacts meet the conduit.
- **Computers** see `powergrid_controls`, with slots, channels and cells numbered from 1:
  isPowered, getVoltage, isEStopped, getModules, getDevices, getInput(slot, ch), getInputs(slot),
  setOutput(slot, ch, on), getOutput, setRelay(slot, ch, closed), getRelay, setDisplay(cell, value),
  getDeviceState(cell), getLimits. OpenComputers signals and CC: Tweaked events: `input_change`
  (slot, channel, state), `estop` (active) and `device` (cell, type, state) when a player works
  something on the door. An unpowered bus reads every input as off.
- The power supply draws about 15 W plus 2 W per module from Line and Neutral at whatever
  voltage it is given, 120 V or 277 V alike.
- **VFD Control Module.** Runs a drive with no computer at all. Fit one on the rail, and in the
  cabinet's screen choose which drive it commands from those found over the cabinet's Cat6 (a
  Three-Phase Drive, set in hertz, or a Digital Voltage Regulator, set in volts) and set its
  minimum and maximum (the arrows step by one; hold shift for ten, control for a hundred). Its
  four channels are Start, Stop, Reverse and Speed: wire a momentary button to Start and another
  to Stop (they latch a run on their rising edge), a toggle or selector to Reverse, and a **Speed
  Dial** to Speed. The dial steps from 0 to 100 % in tens with each click and sets the drive
  between the minimum and the maximum; with no dial the module runs at the maximum. A pressed
  E-stop anywhere on the door, or a dead bus, stops every drive. One cabinet can run as many
  drives as it has VFD modules, each with its own buttons.
- **Controls Cabinet Extension.** Placed directly under a cabinet, or under its first extension,
  on the same wall, it adds six rail slots and six door cells on the same control bus, with four
  knockouts along its bottom and two down each side; up to two extensions per cabinet. The slots
  and cells number on from the head's: an extension's rail is slots 7 to 12, its door cells 7 to
  12, so a tag there reads `X7.1` or `D8`. Everything stays in the head cabinet, which draws
  the extension's devices and answers every click on it; the extension's own knockouts take
  conduit and their wires appear in the head's splice screen. The head's bottom knockouts must
  be free before an extension goes on, as it covers them. Breaking an extension drops whatever
  sat in it and takes the extensions below it with it.
- **Control Station.** The cabinet's door, four cells at a time, anywhere the Cat6 reaches: a
  small box on the wall that takes the same buttons, lights, dials and displays, with two
  jack pins underneath that share one node, so stations daisy-chain from one to the next and
  on to the cabinet's internal jack (a switch works too). Fit devices by right-clicking the
  face with them, work them with an empty hand, and right-click elsewhere or sneak to open
  the station's screen, where each cell is wired to a cabinet module channel exactly like a
  door cell: a button to input 1.3, a light to output 3.1, a dial to a VFD module's speed. A
  new device takes the first free channel by itself. The cabinet reads station inputs every
  tick, station E-stops stop everything, and station lights follow the cabinet's outputs.
  Each station has a number, set in its screen, and the PLC reads its cells directly as
  `B1.1` to `B1.4` for station 1. An elevator is a station per floor with an up and a down
  button and a lamp each, all on one chain back to the cabinet.
- **Wiring the modules.** Relay modules put a COM and an NO terminal per channel in the splice
  screen (sneak-click the cabinet). Digital In and Digital Out modules put one terminal per
  channel there too, but only for channels that no door device is wired to: wire a button to
  input 1.3 in the cabinet screen and terminal I/O 1.3 disappears from the splices, pull the
  wire off and it is back. An input channel turns on when its terminal sees at least 50 V
  against Neutral, so a contact anywhere that feeds Line to it through conduit closes the
  input. An output channel's terminal sources Line while the channel is on, enough to drive a
  lamp, a contactor coil or another cabinet's input. Terminals exist only while the module is
  in the slot.
- **Analog Input Module.** Four channels. Each has a terminal in the splices that reads its
  volts against Neutral, or takes a Speed Dial wired to it in the cabinet screen, which then
  reads 0 to 100. The PLC sees them as `AI1.1` and so on, a computer through `getAnalog`.
  Scale blocks turn volts into whatever the program wants.
- **Analog Output Module.** Four values the PLC writes as `AO1.1` and a computer with
  `setAnalog`. A Number Display wired to a channel shows it. The values are read back with
  `AO1.1` and `getAnalogOut`; there is no electrical output on this module.
- **PLC Module.** A block-diagram PLC, drawn in the cabinet's screen the way a Q-SYS or
  function-block program is: blocks from a palette, wires from output pins to input pins, and
  the live value of every pin shown while it runs. The program scans every tick while the bus
  is live. No computer is needed anywhere. Blocks:

  - **Tag / Set tag / Constant.** A Tag block reads any cabinet name: `X1.1` an input (slot 1,
    channel 1), `Y1.1` an output, `R1.1` a relay, `V1.run` / `V1.start` / `V1.stop` /
    `V1.reverse` / `V1.speed` a VFD module, `D3` the device in door cell 3, `E` the E-stop,
    `P` the power, `C1` a coil, `N1` a network bit. Set tag writes one.
  - **Logic.** And, Or (2 to 8 inputs), Not, Xor, Rising edge, Falling edge, SR latch,
    Toggle, Select.
  - **Timers.** On delay, Off delay, One-shot and Blink, in ticks.
  - **Math.** Add, Subtract, Multiply, Divide, Compare, Scale, Clamp, Counter, Smooth (a
    low-pass filter), and Throttle: Up and Down move a value by a step per tick, or per press,
    kept between min and max, resetting to a start value, and optionally springing back to it
    when neither is held, which makes a helm key into a throttle lever. Hysteresis turns on at
    or above one level and off below a lower one.
  - **Text.** A Constant with its text field filled is a text constant, for device calls that
    take a name, `helm1.isDown` with `"w"` on its argument pin, or to compare against:
    Compare with `==` or `!=` compares text when either side is text, so
    `helm1.getHelmsman == "Nolan"` is a Device call, a text Constant and a Compare. The text
    tags `S1`, `S2`, ... hold text the way `N1` holds a number: a Set tag block writes one from
    any text pin, a Tag block reads it, an HMI Value widget shows it, and a computer reads it
    with `get("S1")`.
  - **Device call.** Calls a method on any OpenComputers component the cabinet's internal Cat6
    reaches, picked from the devices the PLC discovered (a drive is `three_phase_drive1`, a
    rangefinder `rangefinder1`), with its arguments on pins and its first result on an output
    pin. It calls every scan, on the rising edge of En, or only when an argument changes.
    Pins carry numbers, so arguments are converted to what the method's documentation says it
    takes: a boolean parameter gets true for nonzero and false for zero, a string gets the
    number's text. If a method's documentation does not say and it rejects the numbers, the
    call is retried with them as booleans. "Arguments as" on the block forces numbers or true
    and false. The same applies to calls from Lua and Rungs blocks.
  - **Lua.** A script with as many input and output pins as you give it, run every scan in a
    sandbox: `In[1]`.. are the pins, `Out[1]`.. the results, `tag(name)` and `tag(name,
    value)` read and write cabinet names, `call(alias, method, ...)` calls a device,
    `devices()` lists them, `print(...)` shows under the block, `tick` counts scans. Globals
    keep their values between scans. Each scan gets 200 000 instructions.
  - **Rungs.** The text ladder from 0.20, one rung per line, for when a line of text is
    shorter than a diagram: `Y1.1 = X1.1 & !X1.2 | C1`, `C1 S= X1.3`, `T1 = TON(X1.5, 40)`,
    `when ^X1.6: drive1.setEnabled(true)`. Rung coils and network bits are the same bits the
    Tag blocks see.
  - **Note.** Text on the canvas.

  Ctrl+wheel or the - and + buttons zoom the canvas out for the whole picture and back in.
  Every block's settings start with a Name; a named block shows the name in its title bar
  instead of its type, so a canvas reads "Door interlock" rather than "And".
  Right-click a block to set it, drag it by its body, drag from an output pin to an input pin
  to wire, click a wired input to pull its wire off, Delete removes the selected block, drag
  empty canvas to pan. Apply sends the drawing to the cabinet; the first problem comes back in
  the top bar and outlines its block in red.

  **The external port.** Fitting a PLC module opens the second Cat6 jack on the right side of
  the cabinet, on a network of its own. A computer there sees only component `powergrid_plc`:
  `get(name)` and `set(name, value)` for every name above, `getRungs`, `setRungs`,
  `getError`, `getDevices`, and the signal `plc_bit` when the program moves a network bit. The
  machine runs by itself; the computer watches it and nudges it through N bits, and never sees
  the drives and meters behind the PLC. (On CC: Tweaked the two jacks share one network.)

  **Cabinets talking to cabinets.** Run a Cat6 from one cabinet's PLC port into the other
  cabinet's internal jack and the second PLC discovers the first as `plc1`. Its tags take a
  name, so read and write them from a Lua block: `Out[1] = call("plc1", "get", "N1")` and
  `call("plc1", "set", "N2", 1)`, or from a rung: `Y1.1 = plc1.get("N1")`. Cable the other
  direction too and each can read the other. Joining the two internal jacks instead shows each
  PLC the other cabinet itself as `controls1`, with its `getInput`, `setOutput` and the rest,
  and every device on both.
- Recipes: the cabinet is a network jack in iron plates; modules are iron nuggets around a copper
  coil, pins and redstone, a redstone torch, or a lever; door devices are iron nuggets under the
  obvious part (red and yellow dye, a lever, a stone button, a comparator, glowstone dust, glass
  and redstone).

## 21. HMI Panel

A flat screen one block wide, two pixels deep, with a Cat6 jack under its right corner. Run a
Cat6 from the jack to a controls cabinet, on its PLC port or its internal port, and the panel
finds the cabinet over the cable and shows that PLC's tags the way you lay them out. No
computer, no program: the panel reads and writes tags directly.

- **Bigger screens.** Panels placed flush on one wall, facing the same way, join into one
  screen the way OpenComputers screens do: a row of panels becomes one wide screen, and rows
  of the same width stacked on each other become one tall one, up to eight by eight. Each
  panel adds eight by eight cells to the grid. The bottom-left panel holds the layout; any
  panel's jack will do for the Cat6, and any panel's face works its buttons. Breaking a
  panel splits the screen back into what still forms rectangles, keeping the layout where
  the bottom-left panel survives.
- **Laying it out.** Sneak-right-click the screen to open the editor. Pick a widget kind at the
  top, click a free cell on the 8 by 8 grid to place it, click a widget to select it and drag it
  about, and set it on the right: its text, the tag it shows or writes, a range for bars and
  setpoints, decimals, width, height and colour. Right-click a widget to remove it. Apply sends
  the layout to the panel.
- **Widgets.** A *Label* is text. A *Value* shows a tag's number next to its label. A *Lamp*
  lights while its tag is nonzero. A *Button* writes its tag: momentary gives a 1 for half a
  second and then a 0, toggle flips it between 0 and 1. A *Bar* fills from min to max. A
  *Setpoint* shows a number with minus and plus that step the tag, kept between min and max.
  A *Gauge* is a needle over an arc from min to max, the arc coloured in three bands the way a
  SCADA gauge is: the first colour up to the first threshold, the second up to the second,
  the third above it, with the value and label under the needle. Two cells tall by default.
  A *Line* runs along its widget, horizontal or vertical by its shape, for drawing pipes,
  wires and flow diagrams; give it a tag and it glows while the tag is nonzero and dims
  otherwise, so a line can show a live feeder. A *Box* is an outlined rectangle with a title
  for grouping. A *Value* on a text tag such as `S1` shows the text.
- **Using it.** The screen on the block shows everything live. Right-click a button on the face
  to press it, the left or right half of a setpoint to step it, and anywhere else to open the
  panel large, where the same widgets work with the mouse.
- **Tags.** Every name the PLC knows: `X1.1`, `Y1.1`, `R1.1`, `V1.run`, `V1.start`, `D3`, `E`,
  `P`, `C1`, `N1`, `AI1.1`, `AO1.1`. A button on `V1.start` starts a drive through its VFD
  module; a setpoint on `AO1.1` or `N5` hands the PLC a number; a lamp on `V1.run` shows it
  running. Network bits are the usual handshake: the panel writes `N1`, the program reads it.
