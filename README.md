# RobotWar

## Overview

A reverse-engineered version (in Clojure/ClojureScript) of Silas Warner's 1981 Apple II game, RobotWar.

In RobotWar, players write programs in a Forth-like language created specifically for the game. Those programs are compiled to virtual-machine code and used as AI brains for robots battling in an arena.

### Note on project history

I (Richard Harrington, core contributor) started this project as a way to teach myself Clojure during a batch of Hacker School (later renamed the [Recurse Center](https://recurse.com)) in the summer of 2013. It had a Clojure backend and a JavaScript frontend.

After the commit tagged `last-non-llm-assisted-commit` in May 2026, it became an experiment in vibe-coding. Two main things have happened since then: it's been refactored to be an almost entirely front-end ClojureScript app, and a few more of the features of the original 1982 game have been implemented.

### Resources

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

## Implementation gaps

This project is a partial reimplementation of the original Apple IIc game from 1982. The [original manual](resources/manual.txt) is comprehensive but leaves some constants and behaviors unspecified. Gaps fall into two categories:

1. **Features clearly defined in the manual but not yet implemented**
2. **Features the manual leaves vague** — requiring arbitrary decisions about constants and behavior

### Already implemented

- 256×256 arena, robot physics (position/aim/velocity), shell trajectories with timed fuses
- Assembler/VM: full instruction set, label resolution, call stack, accumulator semantics
- Registers: A–W/Z storage, X/Y read-only, AIM/SPEEDX/SPEEDY/DAMAGE, SHOT with cooldown, INDEX/DATA, RANDOM, RADAR (ray-cast scan)
- Combat: shell explosions with quadratic damage falloff (`30 * max(0, 1 - d/21)²` within 21m), self-damage allowed, robot death (`:alive?` flag), victory/tie detection
- Wall collisions: clamping + slide behavior with first-contact damage (`MAX-WALL-DAMAGE 15.0`, quadratic in perpendicular speed)
- Robot-robot collisions: circle-circle detection with velocity resolution and quadratic damage in approach speed (`MAX-COLLISION-DAMAGE 25.0`)
- Frontend: canvas renderer, animation loop with fast-forward, program loading, robot-status legend, restart UX (Enter / canvas click), program-name input validation
- Visual polish: per-tick visual damage (desaturation + procedural marks), per-robot body shapes (square / circle / diamond), particle-based death animation, DOM victory overlay with leaderboard + Play Again
- Audio: Web Audio API sound effects with a persistent on/off toggle (localStorage-backed)

### Category 1 — Defined in manual, not yet implemented

These have explicit manual descriptions but are not yet implemented:

1. **Scoring system** — Manual: 1 point per destroyed opponent per survivor, cumulative across battles. Not implemented.
2. **Multi-battle matches** — Manual options 7 and 8 (schedule/resume matches). Not implemented.
3. **Assembler errors** — Manual lists 8 specific errors. Current: generic "Invalid word or symbol".
4. **Number range validation** — Manual specifies –1024..+1024 as valid range (LARGE NUMBER error). No validation in assembler.
5. **Program length cap** — Manual: 256 instruction maximum. Not enforced.
6. **Recursion prevention** — Call-stack model implies no recursion; not explicitly prevented.
7. **Test bench / simulator** — Manual chapter: step-through, register tracer, fake radar/damage keys. No UI exists.

### Category 2a — Decisions made (constants in `constants.cljc`)

Arbitrary values chosen where the manual is silent. Revisit if play-testing suggests:

- **Cannon reload**: `GAME-SECONDS-PER-SHOT 20.0` (manual: "cooling period")
- **Shell speed**: `SHELL-SPEED 25.0` m/s (manual silent on velocity)
- **Damage falloff**: Quadratic, `MAX-BLAST-DAMAGE 30.0`, `BLAST-RADIUS 21.0`
- **Collision formula**: `MAX-COLLISION-DAMAGE 25.0`, `V-MAX 25.5`, scales as `(approach_speed/51)²` (manual: head-on = 25% scaled by angle)
- **Wall damage**: `MAX-WALL-DAMAGE 15.0`, scales as `(v_perp/25.5)²`, applied once on first contact; motion clamps + slides along the wall
- **Radar geometry**: single ray (not a wedge) cast along the RADAR direction
- **Radar return**: negative distance to the nearest alive robot's disc if hit, otherwise the positive distance to the arena wall the ray exits
- **Robot size**: `ROBOT-RADIUS 7.0` m (manual claims 1.5m square — discrepancy unresolved)
- **VM speed**: 1 instruction per world tick (manual gives no CPU/world-time ratio)
- **Tick rate**: `*GAME-SECONDS-PER-TICK* 0.033` (~30 Hz)
- **RANDOM range**: `[0, limit)` — limit itself unreachable per `rand-int` semantics
- **SPEEDX/SPEEDY bounds**: Manual says –255..+255; current code accepts any value
- **Starting positions**: Pure `rand`; manual silent on placement method

### Category 2b — Decisions pending (blocked on Category 1 implementation)

These require choices when implementing remaining features:

1. **Shell origin & robot-shell overlap** — Shells currently spawn at the firing robot's center (`shell.cljc` TODO: offset by `ROBOT-RADIUS` along AIM so they emerge from the muzzle). The manual specifies only the shell's explosion distance and direction, never its origin. Once the origin moves to the robot's edge, the coupled question becomes: does a shell explode at t=0 if fired into its own body while moving?

   This question has no answer in the original game because it couldn't arise there. The 1982 version ran visibly discrete and event-driven — robot states snapped forward one tick at a time, with collision and detonation sounds firing as events — so a shell was never a continuous trajectory the engine held mid-flight. There was no state in which "the shell currently overlaps the robot that fired it" existed to be resolved. Our continuous per-tick physics, a 7m robot disc, and allowed self-damage are what together manufacture the intermediate moments where the question exists; none of the three come from the manual. (Note the substrate here is still discrete — the VM and world advance in ticks of `*GAME-SECONDS-PER-TICK*`; the continuity lives only in the rendering and per-tick integration layered on top, which is exactly where this question lives too.)
