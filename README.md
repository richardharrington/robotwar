# RobotWar

A reverse-engineered version (in Clojure/ClojureScript) of Silas Warner's 1981 Apple II game, RobotWar.

In RobotWar, players write programs in a Forth-like language created specifically for the game. Those programs are compiled to virtual-machine code and used as AI brains for robots battling in an arena.

Resources:
- Original manual: ftp://ftp.apple.asimov.net/pub/apple_II/documentation/games/misc/Robotwar.pdf
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

Note: There are known gameplay gaps from the original implementation (for example, incomplete damage/radar behavior).