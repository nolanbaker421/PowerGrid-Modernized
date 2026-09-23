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
transformer banks), use the AC build of this addon, `powergrid-modernized-mc1.21.1-0.2.0-ac.jar`,
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
| Breakers 10, 20, 50, 60, 100, 200, 400, 800 A | Plug-on breakers |
| Breaker Blank | Filler plate for an unused space |
| Breaker Lock | Lockout hasp that freezes a breaker handle |
| Conduit 1/2", 3/4", 1" | Empty raceway, laid along surfaces, that wire is pulled through |
| Conduit Box | Small wall box: ends and splices conduit runs |
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
wire keeps its own gauge (resistance and ampacity).

| Conduit | Wires it holds | Tube thickness |
| --- | --- | --- |
| 1/2" | 4 | 1.5 px |
| 3/4" | 8 | 2 px |
| 1" | 12 | 2.5 px |

### Laying a run

1. Place a fitting at each end: a **Conduit Box**, a **Conduit Socket**, a breaker panel knockout,
   or a knockout on one of the devices (section 6).
2. Hold the conduit item and click a **hub** (the small knockout nub on a fitting).
3. Click the walls, floors or ceilings the run should follow. It hugs surfaces and turns corners.
4. Finish by clicking a hub on the other fitting.

One run per hub. A run cannot tee; if you need a branch, put a box there. To continue an open run,
click its free end with conduit. Shift-right-click in the air cancels a pending run. Wire cutters
take a whole run back up, and drop any wire inside it.

### Pulling wire

Right-click a **closed** run (a hub on both ends) with any Power Grid wire item. One conductor of that
wire goes through, consuming that wire's usual items per metre. Repeat with the same or a different
wire until the conduit is full. Pulled wires are numbered and coloured in US order:

1 black, 2 red, 3 blue, 4 white, 5 green, 6 orange, 7 brown, 8 yellow, 9 gray, 10 purple, 11 pink, 12 tan.

- Shift-right-click the run with **wire cutters** to pull the last wire back out (you get it back).
- Right-click the run with an **empty hand** or the **multimeter** to list every slot: colour, wire type
  and current.
- A wire driven past its ampacity burns out like any Power Grid wire and frees its slot.

### Conduit Box

An 8 x 8 pixel wall box. It has **three hubs on each of its four edges** (twelve runs) and **twelve
colour-coded terminals on its cover** for ordinary wires. It is both the end of a run and a junction.

Right-click the box with an empty hand to open the **splice editor**:

- The first row is the twelve cover terminals.
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

---

## 4. Breaker panels

Three wall-mounted load centres. The rating is the main breaker's job and the largest breaker any
space accepts.

| Panel | Largest breaker | Branch spaces |
| --- | --- | --- |
| 200 A | 200 A | 8 |
| 400 A | 400 A | 12 |
| 800 A | 800 A | 12 |

The main space sits at the top. Branch spaces run in two columns, odd circuits on the left, even on
the right, top to bottom. **Without a main breaker the panel is dead.**

### Breakers

- With a breaker in hand every free space is outlined and the one under the crosshair is bright.
  Right-click a space to plug it in. New breakers start OFF.
- Right-click a breaker with an empty hand to flip it. A tripped breaker goes to OFF on the first
  click and ON on the next, like the real thing.
- Shift-right-click a breaker with an empty hand to pull it.
- Any breaker up to the panel's rating fits any space, including the main.

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
- **Breaker Lock**: right-click an installed breaker with it. The handle is frozen and will not flip
  by hand, but the breaker still trips on overload. Shift-right-click the breaker to take the lock off.
- **Labels**: rename a name tag in an anvil and right-click a space with it. The label shows in
  goggles, in messages and in the splice editor ("Circuit 3 (Kitchen)"). A plain name tag clears
  the label. Tags are used up.

### Wiring a panel

Panels have no exposed terminals. Wiring comes in through **conduit knockouts**: four across the top
edge and four across the bottom. Land a run on a knockout, pull wire, then right-click the panel
front **outside the breaker spaces** with an empty hand to open the splice editor. Its points are:

- **Line**: feeds the main breaker.
- **Neutral**: a plain junction for every return conductor.
- **Circuit 1 .. N**: each fed from the bus through its own breaker.

Splice pulled wires to those points exactly as in a conduit box.

### Goggles

Goggles list every space: label, rating, handle position (ON / OFF / TRIPPED), live current, and
LOCKED where a lock is hung, followed by the knockouts in use and a splice count.

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
| Conduit (8) | iron nuggets / ingots over copper wire; 1" adds a second row of ingots |
| Conduit Box (2) | ring of eight iron nuggets |
| Conduit Socket (2) | iron nugget, copper nugget, iron nugget over an iron nugget |
| Breaker Panel 200 A | iron plates and copper plates around a conductive casing, heavy wire connector at the bottom |
| Breaker Panel 400 A / 800 A | the smaller panel surrounded by copper and brass plates / brass and iron |
| Breakers | a column of iron, redstone, copper; nuggets for 10/20 A, ingots for 50/60 A, plates for 100/200 A, blocks for 400/800 A; the double-width version is the higher rating of each pair |
| Breaker Blank (4) | two iron nuggets |
| Breaker Lock | an iron ingot ringed by five nuggets |
| CT Cabinet | pins, comparator, pins / iron plate, conductive casing, iron plate / copper wire, iron plate, copper wire |

Look the rest up in the recipe book; every recipe unlocks from its main ingredient.
