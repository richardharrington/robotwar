# RobotWar

A reverse-engineered version (in Clojure/ClojureScript) of Silas Warner's 1981 Apple II game, RobotWar.

In RobotWar, players write programs in a Forth-like language created specifically for the game. Those programs are compiled to virtual-machine code and used as AI brains for robots battling in an arena.

Resources:
- Original manual: ftp://ftp.apple.asimov.net/pub/apple_II/documentation/games/misc/Robotwar.pdf (also in this repo at [resources/manual.txt](resources/manual.txt))
- Overview article: http://www.filfre.net/2012/01/robot-war/

## Current project layout

- Engine code: `src/main/robotwar/*.cljc` (shared by JVM + CLJS)
- Browser app: `src/main/robotwar/*.cljs`
- JVM tests: `src/test/robotwar/*.clj`
- Static site/assets: `public/`

## Program files and manifests

Robot programs are plain text files in:

- `public/programs/*.rw`

Program discovery is manifest-driven:

- `public/programs/programs-live.json` — programs shown in browser runtime
- `public/programs/programs-test.json` — test/dev fixtures
- `public/programs/programs.json` — compatibility alias (currently mirrors live)

The browser app loads names from `programs-live.json`, then loads each selected `.rw` source file.

### Deterministic ordering

Displayed program lists should be deterministic. Keep manifest ordering stable and intentional (typically lexicographic unless deliberately curated).

## Running locally

### Prerequisites

- Java (JDK 11+ recommended)
- Clojure CLI (`clj` / `clojure`)
- Node.js + npm

### Install

```bash
git clone git@github.com:richardharrington/robotwar.git
cd robotwar
npm install
```

### Dev workflow

Terminal 1 (watch build):

```bash
npx shadow-cljs watch app
```

Terminal 2 (serve static files from `public/`):

```bash
npx serve public -l 3000
```

Open `http://localhost:3000/`.

## Tests

Unified test command:

```bash
npm test
```

This runs JVM tests and CLJS Node tests.

## Build / release

Compile app:

```bash
npx shadow-cljs compile app
```

Production build:

```bash
npx shadow-cljs release app
```

Compiled assets are written to `public/js/cljs-runtime/`.

## Deploying to Netlify

This repo includes `netlify.toml` with:

- Build command: `npx shadow-cljs release app`
- Publish directory: `public/`

In Netlify, connect the repo and deploy. The checked-in `netlify.toml` should be auto-detected.

## Architecture (engine overview)

### Assembler

Lexes, parses, and assembles RobotWar source code into VM object code made of command/argument pairs.

### Brain

Interprets and executes robot object code each tick.

### Register

Implements accumulator/register behavior, including storage and I/O-like register semantics.

### Robot

Holds robot state within the arena and coordinates per-robot ticking behavior.

### World

Ticks robots, then ticks shells in flight, advancing the combined world state.

## Example RobotWar source

```text
                           ; Note: # means !=

256 TO RANDOM              ; All random numbers will now have as their maximum
                           ; the width and height of the arena (in meters).

LOOP
    0 TO SPEEDX TO SPEEDY  ; Stop the robot (X and Y).
    RANDOM TO A            ; Store a random X-coordinate in the arena.
    RANDOM TO B            ; Store a random Y-coordinate in the arena.

MOVE
    IF A # X GOSUB MOVEX   ; If we're moving in the X direction, recalibrate SPEEDX.
    TO N                   ; N is for no-op. (needed because there's no ELSE command).
    IF B # Y GOSUB MOVEY   ; If we're moving in the Y direction, recalibrate SPEEDY.
    IF A = X GOTO LOOP     ; A = X and B = Y, so we've stopped moving, so start over.
    GOTO MOVE              ; Continue to move.

MOVEX
    A - X TO SPEEDX        ; Distance to target sets X velocity.
    ENDSUB

MOVEY
    B - Y TO SPEEDY        ; Distance to target sets Y velocity.
    ENDSUB
```

## Gaps in this implementation

This project is a partial reimplementation of the original Apple IIc game from 1982.

The [original manual](resources/manual.txt) is pretty exhaustive and _almost_ comprises a technical description of how to reimplement the game so complete that you could run robot programs written for the original and they'd have the same behavior, but there are some maddening exceptions (one example: after the cannons fire, they undergo a "cooldown period" whose duration is unspecified). So the gaps between the original and this one fall into two categories:

1. Features well-specified in the original manual that we just haven't implemented yet in this version

2. Features not well-specified in the original manual, that we'd  have to just make a guess or a decision about.

Here's a list of what's already implemented from the original manual, along with two lists of things not yet implemented, from categories 1 and 2:

### What's already implemented

**Engine core**
- 256×256 arena, robots with position/aim/velocity/damage, shells with timed-fuse trajectories
- 40 dm/s² acceleration cap (`MAX-ACCEL 4.0` m/s² × 0.1 multiplier on SPEEDX/SPEEDY)
- Smooth acceleration physics with desired-vs-current velocity (`physics.cljc`)
- Robot-on-robot collisions with momentum transfer (billiard-ball style)

**Assembler and VM**
- Full lex/parse/instruction-pair pipeline with `,`, `IF`, `TO`, `GOTO`, `GOSUB`, `ENDSUB`
- Math (`+ - * /`), comparisons (`= # < >`), label resolution
- Skip-next-instruction semantics on failed conditions
- Call stack, accumulator, instruction pointer

**Registers**
- A–W, Z storage; X, Y (read-only); AIM, SPEEDX, SPEEDY; DAMAGE (read-only)
- SHOT (writes fire a shell, read returns cooldown timer)
- INDEX/DATA indirection pair, RANDOM with stored limit
- AIM mod-360 enforcement

**Frontend**
- Browser canvas renderer, animation loop with fast-forward, manifest-driven program loading, sound on shell fire
- Five-robot battle cap (matches the manual)

### Category 1 gaps — Clearly defined in the manual but not yet implemented

These all have specific text in the manual and are mostly tagged as TODOs in the code:

1. **Shells doing damage to robots.** `world.cljc` removes exploded shells without applying damage; a comment in the file says "TODO: make this a real let-binding, that determines which robots were damaged." Manual: "A shell exploding directly on top of a robot can do 30% damage."
2. **RADAR register.** Stub TODOs in `register.cljc`. Manual specifies a directional beam, negative return on robot hit, positive on wall/miss.
3. **Wall collisions for robots.** TODO comments in `robot.cljc` ("deal with bumping into walls", "add support for collision with walls first"). Manual: walls are strong enough robots can't crash through; damage from wall hits is explicitly mentioned in the test-bench section.
4. **Robot death.** `robot.cljc` has an `(if false ...)` guard with a comment saying "replace this real damage line when we get robot death implemented correctly: `(if (<= (:damage robot) 0)`". Dead robots currently keep ticking.
5. **Collision damage scaled by angle.** Robot-on-robot collision currently just calls `(dec damage)` — a flat 1 point regardless of impact. Manual: head-on collision = 25% to both robots, scaled by angle.
6. **Battle termination and winner detection.** No "last robot standing" logic anywhere.
7. **Scoring system.** Manual: each survivor earns 1 point per destroyed opponent, cumulative across battles, reset on program change. None of this exists.
8. **Multi-battle matches.** Manual's main-menu options 7 and 8 (schedule a match, resume an interrupted match) — not present.
9. **Assembler error catalogue.** Manual specifies 8 distinct errors (NO DATA FIELD, UNKNOWN ITEM, LARGE NUMBER, PROGRAM TOO LONG, FATAL JUNK, STORE IN NUMBER, RESERVED LABEL, NO PROGRAM CODE). `assembler.cljc` only emits one generic "Invalid word or symbol". Already noted in `issues.txt`.
10. **Number range validation (–1024..+1024).** Manual specifies this as the LARGE NUMBER error; no check in the assembler.
11. **Program length cap of 256 instructions.** Not enforced.
12. **Recursion prevention.** Already noted in `issues.txt`. The manual is silent on the mechanism but the call-stack model implies no recursion (single return pointer per GOSUB site, in the spirit of the era).
13. **Test bench / simulator.** A whole chapter of the manual: step-through, register tracer, R-key to fake a radar hit, G-key to fake damage. None of this UI exists.
14. **Sound on/off control.** Audio files are wired up in `app.cljs` but the manual's "option 4" toggle is missing from the UI.
15. **AIM-aligned shell origin.** `shell.cljc` notes: "TODO: make the starting point dependent upon the robot radius". The manual implies shells emerge from the gun.

### Category 2 gaps — Things the manual doesn't pin down (estimates required)

The manual is vague on several constants. Some of these we've chosen values for; others haven't been implemented yet.

#### Category 2a — Decisions already made

Values currently baked into the code. They're reasonable picks, but not derivable from the spec, and worth revisiting if play-testing suggests otherwise.

1. **Cannon reload time.** `constants.cljc` sets `GAME-SECONDS-PER-SHOT 20.0`. Manual only says "cooling period".
2. **Shell speed.** `SHELL-SPEED 25.0` m/s. Manual says nothing about velocity — only that shells are time-fused by distance.
3. **Robot-on-robot collision physics.** Manual specifies a damage rule (25% head-on, scaled by angle) but says nothing about how velocities change after impact. The current billiard-ball "swap momentum" choice is a guess. (The damage half of this rule is still unimplemented — see Category 1.)
4. **Robot physical size.** Manual: "1.5 meter square chassis." `constants.cljc` has `ROBOT-RADIUS 7.0` m — almost 10× larger. Either the radius is wrong, or the field units don't mean meters the way the manual claims. Worth deciding deliberately, since most other constants depend on it.
5. **Instructions per game-tick.** `brain.cljc` executes exactly one obj-code instruction per world tick. Manual gives no CPU-speed-to-world-time ratio. This single number dominates strategy balance.
6. **Tick rate.** `*GAME-SECONDS-PER-TICK* 0.033` (≈30 Hz). Pure presentation choice.
7. **RANDOM range inclusivity.** Manual says "between 0 and the random number limit". `rand-int val` gives `[0, val)`, so the limit itself is not reachable.
8. **Out-of-range SPEEDX/SPEEDY writes.** Manual says –255..+255. The current code accepts whatever's written, with no clamp or wrap.
9. **Starting positions.** `world.cljc` uses pure `rand`. Manual is silent on whether positions are randomized, on a grid, or chosen by the player.

#### Category 2b — Decisions still to make

These tie to Category 1 features that haven't been built yet, so the corresponding constants and policies will need to be chosen along with the implementation.

1. **Shell explosion radius / damage falloff curve.** Manual gives only the maximum (30% direct hit) and the qualitative rule ("depends on proximity"). The shape of the curve and the outer radius are unspecified.
2. **Wall-collision damage formula.** Mentioned in passing ("DAMAGE register will also indicate damage if the simulated robot crashes into a wall") but no number or angle dependence given.
3. **Radar beam geometry.** Manual: "emits a beam in any desired direction" with distance returned. Doesn't say whether the beam is a single ray or a wedge with width — affects how easy it is to find robots.
4. **What RADAR returns when it hits a wall** (vs. nothing). Manual implies a positive number = distance to wall, but never says so explicitly.
5. **What "head-on" means quantitatively** for collision damage scaling.
6. **Whether a robot's own shells can damage it.** Manual doesn't address this case.
7. **Robot-shell physical overlap rules.** Does a shell fired by robot A explode on robot A's body at t=0 if A is moving? Edge cases unspecified.
