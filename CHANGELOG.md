# Changelog

Every release of PowerGrid: Modernized, newest first. Versions follow the jar name,
`powergrid-modernized-mc1.21.1-<version>.jar`. The user guide in `docs/USER_GUIDE.md` describes
each feature in full; this file says what changed and when.

## 0.51.1 — 2026-10-24

### Fixed
- Conduit is drawn by the mod's own tube renderer now instead of Power Grid's segment call. The
  stripes along straights were Power Grid tiling its banded wire texture down each leg; straights
  are a flat-shaded tube now. The stacked and offset pieces at corners were the knockout stubs and
  wall-lift legs being cut to nothing by the bend allowance and leaving only their elbow cubes;
  a leg is now drawn exactly between its elbows, and a corner with no room for a bend gets a
  plain joint. Elbows are swept quarter circles in the tube's own section, so there are no gaps
  or steps, and each end wears a short connector collar at the knockout.

## 0.51.0 — 2026-10-24

### Added
- **Conduit Bender** (an iron plate over two iron ingots on the diagonal). Click a leg of a laid
  run to slide it one step away from the face you clicked. The legs either side grow or shrink to
  follow, a leg that shrinks to nothing goes, and a push across a leg on another axis puts in a
  new corner, so offsets, kicks and saddles take a few clicks. Sneak-use in the air cycles the
  step through 1, 2, 4 and 8 px. Sticks are taken from or handed back to your inventory as the run
  changes length, and the wires inside follow the new path. The stubs into knockouts never move.
- Sneak-click a run with the bender to pick the whole run up: sticks and wires, colours and
  labels. The loaded bender lays conduit like the conduit item, spending its own sticks first, and
  when the new run closes on a knockout the wires go back in; a wire that no longer reaches is
  handed back. Sneak-use in the air hands a held run back as items.

## 0.50.5 — 2026-10-24

### Fixed
- Corners on thick conduit still met in a sharp joint with the elbow lost inside it: Power Grid
  draws every segment half a tube longer at both ends, so the cut at a corner now takes that half
  as well as the bend radius. Bends go up to six pixels on big tube.
- Conduit sank into the wall behind a box: the run's centre line sat at the knockout's depth, a
  pixel and a half off the wall. A run now steps off the wall by what its tube needs right after
  the knockout, and steps back before the far one, on strut legs too.

## 0.50.4 — 2026-10-24

### Fixed
- Elbows on thick conduit poked out past the tube: the bend was three turned boxes whose corners
  stood proud of the straights' square section. It is a stepped arc of axis-aligned cubes the
  tube's own size now, the way a curve is drawn in blocks. Corners with under a pixel of room, or
  a reversal, stay sharp joints instead of drawing a blob.

## 0.50.3 — 2026-10-24

### Fixed
- Conduit straights were black: Power Grid's segment call takes the colour in its fourth slot and
  the entity id last, for its texture tile, and the renderer had those swapped since 0.50.0.
- Gaps between a straight and its elbow: each straight now runs half a pixel into the bend.

## 0.50.2 — 2026-10-24

### Changed
- Knockout marks stand a third of a pixel off the body instead of a twentieth, so they show as a
  coloured sliver on each edge when a wall box is seen from the front.

## 0.50.1 — 2026-10-24

### Fixed
- Conduit straights drawn black against a wall: the renderer lit each straight from its centre
  line, which sits inside the wall's block. It takes the brightest of the surrounding blocks now,
  as Power Grid does.

### Changed
- Bend radius is one and a half tubes, never under two pixels nor over four.
- New conduit is silver.

## 0.50.0 — 2026-10-24

### Changed
- Conduit corners are real elbows: each straight stops short by the bend radius, two and a half
  tubes or half the shorter leg, and a quarter-circle elbow in the tube's own thickness fills the
  corner. The corner couplings are gone; the end connectors stay.
- Knockout hitboxes are flat like the knockouts now, so the block outline no longer shows boxes
  standing off the body. Clicks on the mark still land on the knockout.

## 0.49.0 — 2026-10-24

### Added
- **Strut Channel**: a 3 px rail on any face with a clamp every four pixels. A pending conduit run
  clicked onto it goes straight to the clamp in square legs, no path finding, across the strut
  first and along it last, and stays one piece over any number of struts until it closes on a
  knockout. Parallel runs take the clamps side by side.

### Changed
- Knockouts on every block are flat marks in the trade-size colour instead of boxes standing a
  pixel off the body: conduit boxes, switches, sockets, gang boxes, pull boxes, panels, cabinets,
  gear cells, drives, transformers and the rest. The hubs' hitboxes and terminals are unchanged.

### Fixed
- The inspector asset generator wrote the pre-1.21 looting function for the push broom's loot
  table, undoing the 0.39.0 fix whenever it was run.

## 0.48.8 — 2026-10-24

### Changed
- Conduit runs leave and enter a knockout dead centre and square: a four-pixel straight stub along
  the knockout's face normal at each end, and the tracer only routes between the stubs. No more
  tubes a pixel off the knockout's centre line or stopping short of the box. Runs continued from an
  open end keep their line; the ceiling tile box's runs still leave upward.

## 0.48.7 — 2026-10-24

### Changed
- Each end of a conduit run has a connector exactly the tube's size, flush with it, where it
  enters the knockout; corners keep their slightly proud coupling.

## 0.48.6 — 2026-10-24

### Fixed
- Conduit couplings at the ends of a run were a pixel proud of the tube, which on 1-1/2" conduit
  made five-pixel cubes that hid a box's terminals. Ends get no coupling, since the knockout is the
  fitting there; corners keep one, half a pixel proud.

## 0.48.5 — 2026-10-24

### Fixed
- A power cord in a conduit box's receptacle plate ended inside the box. Power Grid sets the cord's
  end three pixels against the block's facing, which on its sockets points into the mount; the box's
  points out of it, so the socket origin now compensates and the plug sits in front of the face.
- The settings screen's label column fits its widest label; "Mode (bridge / relay)" no longer runs
  under the box.

### Changed
- A gang box placed on a wall starts with its gangs running across instead of standing on end.
  Floors and ceilings keep following the player's facing; a wrench still turns it.

## 0.48.4 — 2026-10-24

### Fixed
- The bulb in a light socket was drawn inside the box: Power Grid's bulb model stands up from its
  base, not down from the top. It now sits on the holder and points out of the socket.

## 0.48.3 — 2026-10-24

### Fixed
- Fitting a bulb in a light socket crashed: the bulb's state loader reaches for the filament wire,
  which the socket only made once a bulb was in. The socket always carries the wire now, open
  until a bulb is fitted, and bulb data read before the wire exists waits for it.

## 0.48.2 — 2026-10-24

### Fixed
- A power cord plugged into a gang box hung from the middle of the plate. The box's state now names
  the gang of its first receptacle and the cord plugs into that face; a box with no receptacle
  takes no cord.

## 0.48.1 — 2026-10-24

### Fixed
- Placing a gang box crashed: its device arrays were made by field initialisers, which run after
  Power Grid's base constructor has already asked for the circuit. They are made on first use now.

## 0.48.0 — 2026-10-24

### Added
- **Gang Boxes**, 1 to 4 gangs at four pixels a gang, taking device items: Single-Pole Switch,
  3-Way Switch, 4-Way Switch, Push Button, Duplex Receptacle and Blank Insert. Devices fit and come
  out with a click, switches flip, buttons let go, and every device's points sit in the splice
  screen by gang; nothing lands by itself. A cord in the plate reaches the first receptacle. The
  old Conduit Switch, Socket, Button and 3-Way blocks remain and craft into their devices.

## 0.47.2 — 2026-10-24

### Fixed
- Breaker panels and the controls cabinet showed the knockout outlines of extensions they did not
  have while wiring. The terminal collection is built per section count now, with the absent
  extensions' terminals as specks, the same cure as the conduit box in 0.47.1.

## 0.47.1 — 2026-10-24

### Fixed
- Placing a Conduit Button or a 3-Way Conduit Switch crashed: neither had a contact resistance.
- A conduit box showed the outlines of all twelve cover nubs and the receptacle face while
  wiring, whatever plate it wore or none. Power Grid's overlay draws every terminal in the
  block's collection, so the collection now carries, per cover, only what that cover exposes.

## 0.47.0 — 2026-10-24

### Added
- **Light socket covers take Power Grid's bulbs**: the standard bulb, the LV bulb, grow lamps and
  neon tubes, with their own filament, warm-up, burnout and light level, drawn in the socket.
  Right-click with a bulb to fit it, sneak-click to take it out. A socket without a bulb is dark;
  sockets from earlier versions keep a standard bulb.
- Conduit runs show a coupling at every corner and both ends.

## 0.46.0 — 2026-10-24

### Added
- **Conduit Button**: the conduit switch that lets go, closed for half a second per press.
- **3-Way Conduit Switch**: common and two travellers; two of them light a hall from either end.
- **PLC Time of day** block: day tick, hour and a Day bit.
- **Redstone Port input**: the redstone reaching a port is tag `RI<n>` for the PLC.

### Fixed
- Landing terminals inside boxes, switches, sockets, disconnects, pull boxes, panels and gear
  cells were one-pixel cubes that drew a phantom outline while wiring; they are specks now.
- A click within a pixel of a conduit box cover nub lands on the nearest nub, so the middle
  nubs take a wire without pixel hunting.
- A conduit started from a box on a ceiling tile leaves it upward instead of diving into the tile.

## 0.45.1 — 2026-10-24

### Fixed
- A breaker panel came back from a reload unaware of the extension under it: cover, splice screen
  and tooltip saw one section while power still flowed through the lower runs. The section count
  is now read from the block state every time, and the head checks the blocks below it every lazy
  tick and puts the state right if they disagree.
- Pulling a new wire through a run on a conduit switch or receptacle no longer re-lands line and
  load over the splices made by hand: only new, unspliced conductors land, and only on free points.
- Breaking a device at one end of a conduit run left a ghost run with a hitbox at the other device.
  The run comes down with its wires like a conduit should.
- The pole transformers' network jack sat on the neutral tap; it is below the LV taps now.

## 0.45.0 — 2026-10-24

### Added
- **Radio mode**: bridge (the default, networks merge over the link) or relay (only network
  messages cross, hop counted, components and peripherals stay home), set on the radio's screen or
  by computers with `setMode`. Either end in relay makes the link a relay. OpenComputers relays
  between the radio's component node and an air-side node; CC: Tweaked repeats modem packets
  between the two wired networks.
- **Cell Card**: a SIM for a computer. In an OpenComputers card slot it is the `cell_card`
  component, a roaming client of its carrier that joins the nearest covering sector with a seat and
  hands over as it moves; on a CC: Tweaked pocket computer it is a `modem` whose packets go through
  the towers, so rednet works anywhere in coverage. Right-click a radio with it to take the carrier;
  relay by default, sneak-click the air for bridge. Cards take sector seats like omnis.

## 0.44.1 — 2026-10-24

### Fixed
- Conduit switches, receptacle sockets and RF connectors forgot which conductors were landed on
  their points across a reload and landed the run's first conductors again, so a switch spliced
  line and load by hand came back shorting line to neutral through the default landing. The
  landing record is saved now, and a save from before this version keeps the splices it has.
- The Ponder scenes faced away from the camera; the plots are turned round.

## 0.44.0 — 2026-10-23

### Added
- **Ponder**: hold W on a breaker panel, a radio or an antenna for a scene. Three to start: the
  breaker panel (main, branches, a load, the cover), a point-to-point dish link, and a carrier of
  a sector and a cell client. Two index pages, Power Distribution and Radio Links. The plots are
  generated from `tools/gen_ponder_schematics.py` and the text keys from `tools/gen_ponder_lang.py`.

## 0.43.0 — 2026-10-23

### Changed
- Three-phase pump and motor windings take their inductance from a nameplate power factor at
  60 Hz (pump 0.85, motor 0.9) instead of Power Grid's DC motor time constant, which gave them a
  power factor of 0.26 and left the pump crawling at 120 V until kilovolts were applied. The pump's
  inductance falls with its stages like its resistance, so stages no longer slow it. Pump winding
  260 Ω: a one-stage pump at 120 V a phase draws 120 W real and sits at the speed cap. The motor's
  load-following resistance is solved with the reactance in, so it draws what its stress is worth.
- Sector reach scales with beamwidth: the configured reach is at 120°, a 30° panel reaches twice
  as far and a 180° one 0.82 of it.
- Omni (cell) antennas reach 500 blocks by default, down from 2000. A link needs both ends within
  reach, so this is the radius a tower serves. Existing config files keep their `cell_range`.

## 0.42.1 — 2026-10-23

### Fixed
- Opening a breaker panel's cover crashed the game on every frame ("Not building!") and kept the
  world from loading again while the panel was in view. The panel renderer took its breaker buffer
  before drawing the door, which closed that buffer. Present since the cover arrived in 0.38.0; a
  world hit by it loads again with this version, nothing to repair. The switchgear and gear cell
  renderers took the same fix.

## 0.42.0 — 2026-10-22

### Added
- **Coverage survey** on the Alignment Scope: sneak-click any antenna and its coverage is drawn in
  the world for a minute, wherever you go. A sector is a cage of lines along its beam edges, its aim
  and its 45° tilt limits with arcs at each quarter of its reach; a dish is its 1° cone; an omni a
  ring at its reach. Drawn at the radio's real reach through that coax run.

### Changed
- Sector antennas reach 1000 blocks by default, down from 2000. Dishes and omnis stay at 2000. A
  config file written by an earlier version keeps its own `sector_range`.

### Fixed
- Hovering the Three-Phase Pump in an inventory crashed the game ("not found in any resistance
  providers"): Power Grid's pump tooltip asked for a single resistance the three-winding pump does
  not have. The pump now shows its winding per phase, rated voltage and full-load draw.

## 0.41.0 — 2026-10-22

Radio links, wireless metering, and a round of fixes found in play.

### Added
- **Coax Cable**: a hanging cable between coax ports, radio to antenna, up to 64 blocks a span.
  Reach falls with the length of the run (full to 16 blocks, half at 64).
- **Cable Hanger**: a bracket of eight pass-through clips for coax or Cat6, so eight feeds run
  up one tower leg in a column. Mounts on any face.
- **Radio**: the indoor unit with a line and neutral (50 W), a Cat6 jack, a 1" knockout and a
  coax port. Its role comes from the antenna on its coax. Typed link ID on a sneak-click screen.
- **Dish, Sector and Omni Antennas**: passive, one coax port, floor, wall or ceiling mounted. The
  dish and panel are drawn turned to their aim. Dishes pair with the nearest same-ID dish aimed
  back at them inside a 1° beam; sector and omni radios with one ID form a carrier, an omni
  joining the nearest covering sector with a seat and roaming as it moves. Links join the two
  radios' Cat6 jacks exactly as a cable would, so OpenComputers and CC: Tweaked networks merge.
  Antennas on Sable bodies use their live position.
- **Alignment Scope**: clip it on one antenna and then the other and both are sighted in on each
  other; sneak-click a dish to sight it on its nearest partner.
- **Meter Antenna**: a module for the CT cabinet. Right-click a cabinet with it and the cabinet
  reports its four channels every ten seconds under a meter ID and a utility, nothing to wire.
- **Meter Collector**: the head end. Hears every meter of its utility within 2000 blocks of its
  antenna and hands the last reports with their age to computers as `powergrid_collector`, raising
  `meter_report` as they come in. Store and forward; no billing in the mod.
- Computers: `powergrid_radio` and `powergrid_collector` on both CC: Tweaked and OpenComputers.
- Config section `radio`: every reach, the dish beam, seats per sector, coax lengths, line of
  sight (off by default), meter range and report interval.

### Fixed
- Terminals built from a pixel box were sixteen times too big and far off the block, which broke
  the lugs and knockouts of the switchgear cell, the bus duct feed and plug-ins, the rectifier,
  the capacitor bank and the three-phase pump. All now sit where their models show them.
- The three-phase pump's lugs are the motor's size on a proper terminal box, its vertical shapes
  follow its model, and its body carries flow arrows that flip with the phase sequence.
- The three-phase pump's windings are 360 Ω a phase, so a one-stage pump draws 120 W at 120 V and
  sits at Power Grid's speed cap instead of wasting 1.8 kW.
- A Cat6 and a coax on one radio or collector no longer refuse each other as "port in use".
- The panel cover closes on a sneak-click on any empty space, not only the frame margin.

## 0.40.0 — 2026-10-22

### Added
- **Rectifier**: a six-pulse diode bridge in a wall cabinet, L1, L2, L3 in and DC + and − out,
  with a wrench-set smoothing capacitor, built from Power Grid's own diode and capacitor elements.
- **Capacitor Bank**: three capacitors in delta behind a handle, 10 to 5000 µF each, for power
  factor correction on the fork's AC.
- **Three-Phase Pump** and **Pump Stage** (AC build): Power Grid's pump on three windings, steady
  on AC, reversed by the phase sequence. Each stage adds the pump's range again at the same flow.

### Fixed
- The bus duct family and the switchgear cell were missing from the pickaxe tag.

## 0.39.0 — 2026-10-21

### Added
- **PID** block for the PLC, with anti-windup and gains per second.
- Switchgear cells: **Combination Starter**, **Metering Unit**, **Ground Bar**; a fifth ground lug
  on lug blocks; bars burn out past 2000 A; cells are `powergrid_gear_cell` to computers.
- **Bus Duct Plug-In Breaker**: the plug-in disconnect with a wrench-set 3-pole breaker handle.
- **Receptacle Cover** for conduit boxes: power cords plug into it.
- **Ground**: every breaker panel has a Ground point bonded to its neutral, goggles show ground
  fault current, and the **1-50 A GFCI Breaker** trips on 50 mA of it.
- The electrical inspector checks the code while reading the card: an unbonded panel ground or a
  lug block without a ground bar fails the visit.

### Fixed
- Push broom loot table used the pre-1.21 looting function name.

## 0.38.0 — 2026-10-19

### Added
- **Bus Duct**, **Bus Duct End Feed** and **Bus Duct Plug-In Disconnect**: L1, L2, L3 and N from
  block to block at 2000 A.
- **Switchgear Cell**: a 3 x 3 grid of slots for bus bars, neutral bars, lug blocks, 3-pole
  breakers, fuse sets, switch units and contactors; cells facing the same way are one lineup.
- **Panel Cover** for breaker panels and **Light Socket Cover** for conduit boxes.
- A 24 V control bus for the controls cabinet's digital outputs.

### Changed
- Default terminal colours follow the NEC: black, red, blue for the lines, white for the neutral,
  green for ground, the brown, orange, yellow set above 300 V.

### Fixed
- The flex conduit texture ran along the tube instead of around it.

## 0.37.1 and earlier

One feature per version, 2026-10-03 to 2026-10-10:

- 0.37.1 Disconnect (was Safety Switch); a placement crash.
- 0.37.0 PLC programs save, load, copy and paste.
- 0.36.0 Redstone Port.
- 0.35.0 Safety Switch; knockouts coloured by size.
- 0.34.0 Conduit colour bands, conduit boxes on ceiling tiles, quieter goggles.
- 0.33.0 PLC wires can be routed by hand.
- 0.32.x Drive ratings and 120 Hz, the motor's field weakening, typed device call arguments.
- 0.31.0 to 0.25.0 Controls cabinet: labels, colours and backlit buttons, up and down buttons and
  buzzer, control stations, radio remote and base, PLC throttle, hysteresis and smoothing, HMI
  panels and screens with gauges, the cabinet extension, analog modules.
- 0.24.0 to 0.19.0 Controls Cabinet with its DIN rail of modules, the block-diagram PLC with Lua
  blocks, the VFD control module and speed dial.
- 0.18.x Helm, flexible 1" conduit, Cat6 across physics bodies, the RF Connector box.
- 0.17.x Digital Voltage Regulator behaviour, the RF Connector, FE and stress rates.
- Earlier: FE Inverter, Load Bank, CT cabinet phase angle and frequency, synchroscope sync signal,
  cam-lock connector boxes, laser rangefinder, transformers, the inspector, conduit and Cat6.
