# Agent Guide — RobotWar Codebase

## What this project is

RobotWar is a reverse-engineered Clojure/ClojureScript port of Silas Warner's 1981 Apple II game. Players write programs in a Forth-like DSL, which compile to VM bytecode and run as AI brains inside robots battling in a 256×256 meter arena.

- Original manual: `resources/manual.txt`
- Narrative overview: https://www.filfre.net/2012/01/robot-war/

## Tech stack & build system

- **Engine**: Clojure/ClojureScript (`.cljc` shared files, `.clj` JVM-only, `.cljs` browser-only)
- **Build tools**: `deps.edn` (JVM deps + test runner) + `shadow-cljs.edn` (CLJS compilation)
- **Package manager**: npm — `shadow-cljs` is the only dependency
- **No frameworks**: Plain CLJS + atoms + Canvas. No Reagent, no re-frame, no backend in production.
- **Deployment**: Netlify static site (`netlify.toml` in repo root)

### Dev commands

```bash
npm install                                    # one-time setup
npx shadow-cljs watch app                      # dev build with hot reload
npx serve public -l 3000                     # serve static files
open http://localhost:3000

# tests — both JVM and CLJS/Node
npm test
# or individually:
clj -M:test                                    # JVM tests
npx shadow-cljs compile test && node target/test/node-tests.js   # CLJS tests
```

### Source layout

```
src/main/robotwar/*.cljc    — game engine (shared JVM + CLJS)
src/main/robotwar/*.cljs  — browser app, canvas, audio, test runners
src/main/robotwar/*.clj   — retained JVM tools (terminal.clj, core.clj REPL helpers)
src/test/robotwar/*.clj   — full JVM test suites (clojure.test)
src/main/robotwar/*_test.cljs — CLJS smoke tests (cljs.test, parallel but compact)
public/                   — static site served by Netlify
  index.html
  programs/*.rw           — robot program source files
  programs/programs-live.json    — UI-visible manifest
  programs/programs-test.json    — test/dev fixture manifest
  programs/programs.json         — compatibility alias (mirrors live)
  audio/                  — sound effects (ogg + mp3 fallbacks)
  css/                    — existing stylesheets
  fonts/                  — Data_70_LET.ttf (retro terminal font)
  js/cljs-runtime/        — shadow-cljs output (gitignored)
```

## Engine architecture (8 namespaces)

| Namespace | Responsibility |
|-----------|----------------|
| `assembler` | Lex/parse/assemble RobotWar source → VM object-code (command/argument pairs) |
| `brain` | Execute one VM instruction per world tick; op dispatch, accumulator, IP, call stack |
| `register` | Register behavior (A–W, Z, X, Y, AIM, SPEEDX, SPEEDY, DAMAGE, SHOT, INDEX, DATA, RANDOM). **RADAR is stubbed but not yet implemented.** |
| `robot` | Per-robot state & ticking: brain tick, movement, collisions, wall interaction |
| `world` | Ticks robots, then shells, advances combined state, enforces 2–5 robot count |
| `shell` | Shell flight & timed-fuse explosion trajectory |
| `physics` | Float math helpers, velocity smoothing, acceleration caps |
| `constants` | All tunable numbers: arena size, speeds, blast radius, damage caps, etc. |

Key design rules:
- Engine code is **bug-for-bug compatible** with the pre-port version. Do not change semantics during refactoring.
- All engine constants are expressed in **absolute meters**, not as multiples of `ROBOT-RADIUS`, so they survive future radius retuning.

## Browser app architecture

- `app.cljs` — state atom, `requestAnimationFrame` loop (time-accumulator pattern), program loading, input wiring, audio trigger hooks (pooled `<audio>` elements, Web Audio API migration pending)
- `canvas.cljs` — all Canvas 2D drawing: robots, shells, explosions, arena scaling, victory text overlay
- `legend.cljs` — DOM-based robot health legend (color swatches, names, damage percentages)

Game loop (time-accumulator):
1. Compute `elapsed = now - last-frame`, cap at 250ms
2. `elapsed *= fast-forward` (default 15, arrow keys adjust 1–40)
3. Accumulator += `elapsed`
4. While `accumulator >= tick-duration`: run one engine tick, subtract duration
5. Draw current world

## Robot program files & manifest system

Robot programs are plain text files (`public/programs/*.rw`). The UI discovers them via JSON manifest(s), not filesystem enumeration.

- `programs-live.json` — canonical UI-visible list
- `programs-test.json` — test/dev-only fixtures
- `programs.json` — compatibility alias, currently mirrors live

**Deterministic ordering is required.** Keep manifest order stable and intentional (typically lexicographic unless deliberately curated). The UI renders names in manifest order.

## AI spec documents (what future agents should know)

These live in `resources/ai-specs/` and capture deliberate design decisions. They are the source of truth for architectural direction and gap-closing work. Read them before making changes in their domains.

### `resources/ai-specs/refactor_to_cljs/REFACTOR_SPEC.md`
The spec for the Clojure→ClojureScript refactor. Covers:
- Goals (modernize stack, move engine to browser, no per-tick network)
- Architecture decisions (static-only backend, no frontend framework, time-accumulator loop, `.cljc` engine, Netlify hosting)
- Slice-by-slice migration order (16 slices, engine-first, app works at every commit)
- Testing strategy (Midje→clojure.test, JVM + CLJS Node parity)
- Fate of every obsolete file (delete vs. keep as JVM tooling)
- Tactical decision checklist (18 items left to implementer discretion)

### `resources/ai-specs/implementation-gap-closing/tier1-plan.md`
The spec for making the game "minimally playable": radar, shell damage, robot death, wall blocking, collision damage, circle-circle collisions, victory detection, canvas restart UX.

Locked decisions:
- **Radar**: single ray, negative distance on robot hit, positive on wall hit, closest hit wins, no self-detect, no cooldown
- **Shell damage**: quadratic falloff `30 * max(0, 1 - d/21)²` within 21m blast radius; self-damage allowed; damage stacks
- **Walls**: slide behavior (zero perpendicular velocity, preserve parallel), first-contact-only damage `15 * (v_perp/25.5)²`
- **Robot collisions**: circle-circle detection, first-contact-only damage `25 * (approach_speed/51)²`, billiard-ball response along contact normal, separate to exactly `2*ROBOT-RADIUS`
- **Death**: `:alive? false`, do not remove from `:robots` vector (preserves indexing), skip in all tick/collision/render paths
- **Victory**: canvas text overlay, click/Enter to restart, reverse start-transition

Implementation sequence (7 steps, dependency-ordered) and test obligations (JVM full coverage, CLJS smoke tests, manual frontend verification).

### `resources/ai-specs/implementation-gap-closing/tier2-plan.md`
The polish spec riding on top of Tier 1. Covers:
- **Slice A**: Web Audio API migration (replace pooled `<audio>` elements)
- **Slice B**: Sound effects for shell explosion, robot collision, wall crash, robot death; sound on/off toggle with `localStorage` persistence
- **Slice C**: Visual damage (desaturation + procedural crack marks, deterministic per `(idx, damage)`)
- **Slice D**: Per-robot shape variety (`:square :circle :diamond`) by `idx mod N`
- **Slice E**: Particle-based death animation (expanding ring + sparks, separate animation state atom)
- **Slice F**: DOM-overlay victory display (terminal-vintage style, minimal leaderboard, "Play again" button)

Locked decisions and ~19 open "TO DECIDE" questions with recommendations. Key cross-cutting rule: no new dependencies.

**Implementation status:** Tier 2 features are specified but **not yet implemented** in the codebase:
- Slice A (Web Audio API): Audio still uses pooled `<audio>` elements in `app.cljs`
- Slice B (Sound effects): No explosion/collision/death sounds hooked up
- Slice C (Visual damage): No desaturation or crack marks
- Slice D (Shape variety): All robots rendered as squares
- Slice E (Particle death animation): Not implemented
- Slice F (DOM-overlay victory): Victory rendered on canvas, not DOM

## What is NOT in these specs (already implemented or out of scope)

- **Handoff documents**: `resources/ai-specs/refactor_to_cljs/handoffs/` — these are per-slice execution logs and are *not* considered authoritative specs. They are preserved for history but should not be used as design references.
- **Scoring system, multi-battle matches, test bench UI**: out of scope for both tiers
- **Assembler error catalogue, number-range validation, program-length cap, recursion prevention**: known gaps, deferred
- **AIM-aligned shell origin**: deferred

## How to work safely in this codebase

1. **Read the relevant spec before editing.** The specs above are deliberately locked; changing a design decision requires surfacing the contradiction and re-litigating, not silently overriding.
2. **Keep engine tests green on both JVM and CLJS.** Run `npm test` before committing. Add JVM tests for new engine behavior; extend CLJS smoke tests for the same surface.
3. **Preserve `.cljc` portability.** Use reader conditionals only for genuine interop differences (math functions, `extend` vs `extend-type`, integer parsing). Do not change engine behavior during porting.
4. **Do not add runtime dependencies.** No new npm packages, no new Clojure libs. Web Audio API, Canvas 2D, and native DOM APIs are sufficient.
5. **Maintain the "app works at every commit" property.** If a change would break the playable state, split it into smaller slices.
6. **Use absolute units for new constants.** Express meters/seconds/pixels directly; do not couple to `ROBOT-RADIUS`.
7. **Keep program manifest ordering deterministic.** If you modify manifest generation, sort lexicographically unless a curated order is explicitly chosen.

## Common gotchas

- `ROBOT-RADIUS` is currently **7.0 meters**, overriding the manual's "1.5 meter square chassis" claim. This was verified against original 1981 screenshot proportions. Changing it affects collision, canvas rendering, and game balance.
- The brain runs **1 VM instruction per world tick** at ~30 Hz. This dominates AI strategy balance (a full radar sweep takes ~12 seconds wall time). Do not change this ratio without deliberate play-testing.
- `SPEEDX`/`SPEEDY` max is ±255 in the manual, but the code uses a 0.1 multiplier to get ±25.5 m/s. Be careful about units when adding damage formulas.
- The `:damage` register read rounds to nearest int; internal storage is float. Do not round prematurely in tests or engine code.
- **Collision damage is NOT yet using the Tier 1 formula.** Current implementation in `robot.cljc` applies flat 1-point damage (`(dec damage)`) regardless of approach speed. The Tier 1 spec formula `25 * (approach_speed/51)²` is specified but not yet implemented.
- The `world/tick-combined-world` order of operations is load-bearing: robot tick → collision → wall → shell tick → damage apply → death check → victory check. Changing the order changes gameplay.
- **Wall collisions are NOT fully implemented.** Robots currently stop at walls without taking damage or bouncing. The Tier 1 spec (slide behavior, perpendicular velocity zeroed, first-contact damage `15 * (v_perp/25.5)²`) is specified but not yet implemented.
