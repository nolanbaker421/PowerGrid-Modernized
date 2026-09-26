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
| Variable Frequency Drive (VFD) | Computer-controlled voltage/current source |
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

One run per hub. A run cannot tee; if you need a branch, put a box there. To continue an open run,
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
with an empty hand to unplug the cord. Place it with the clicked face as its back; wrench its face to
turn the hub to any of the four directions. There is nothing to splice.

### Conduit Switch

The same 6 x 6 pixel fitting as the socket with a toggle on top instead of a cord socket: one hub,
and the first two wires pulled through its run land on its **Line** and **Load** poles by themselves.
Right-click it with an empty hand to flip it; the toggle leans forward when on. Goggles show the
state. Place and wrench it like the socket. It is a plain contact between the two wires, so put it in
the hot conductor of a lighting circuit and give the light its own conduit run back to a box.

---

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

### Variable Frequency Drive

A computer-controlled source. Two input terminals (+ / −) take power; two output terminals (+ / −)
deliver a commanded voltage.

- Output voltage: −2000 V to +2000 V (negative reverses polarity).
- Output current limit: 0 to 3 A. The drive backs off to hold the limit and to avoid dragging its
  input down.
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

- Set the **frequency** on the value box on top (whole hertz, 0 to 30) or from a computer.
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

## 7. Cat6 network

The **Cat6 Cable** is a Power Grid wire that carries computer network traffic instead of current.
It is placed with two clicks like any wire and only connects **network jacks**:

- The VFD, Analog I/O Module, Line Voltmeter and Line Ammeter have a jack built in (the cyan
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

**powergrid_vfd**

| Method | Meaning |
| --- | --- |
| `setVoltage(volts)` | Output setpoint, −2000 to 2000 |
| `getVoltage()` | Setpoint |
| `setCurrentLimit(amps)` | 0 to 3 |
| `getCurrentLimit()` | |
| `setEnabled(bool)` / `isEnabled()` | |
| `getOutputVoltage()` / `getOutputCurrent()` | Measured output |
| `getInputVoltage()` / `getInputCurrent()` | Measured input |
| `getPower()` | Output power in W |

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
`powergrid_clamp_meter`, `powergrid_ammeter`, `powergrid_voltmeter`, `powergrid_ct_cabinet`) reachable over the Cat6
network from an OpenComputers cable next to a jack or switch. `component.doc` on any method prints
its signature. Methods match the ComputerCraft ones, plus:

- **VFD**: `getLimits()` returns the maximum voltage and current.
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

Look the rest up in the recipe book; every recipe unlocks from its main ingredient.
