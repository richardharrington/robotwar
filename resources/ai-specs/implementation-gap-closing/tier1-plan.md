# Tier 1 Implementation Plan — Minimally-Fun Gameplay Loop

A specification for the first round of feature work closing the implementation gap between this RobotWar port and the original 1981 game. The goal of Tier 1 is to make the game **playable as a game**: AIs can find each other (radar), shells can kill (damage), robots die, walls block, and battles end with a declared winner.

All polish — sound effects, visual damage representation, particle death animations, shape variety, DOM overlay — is deliberately deferred to [Tier 2](./tier2-plan.md).

Design decisions captured here are the outcome of a deliberate grilling-style design conversation. Take them as fixed unless something concrete forces a revisit. Items tagged **TACTICAL** are deliberately left to the implementer.

---

## 1. Scope

### 1.1 In scope

- **Radar** (manual: RADAR register; README Category 1 #2)
- **Shells explode and damage robots** (manual: shell damage rules; README Category 1 #1)
- **Wall blocking** for robot movement (README Category 1 #3)
- **Wall-collision damage** (README Category 1 #3, second half)
- **Robot-on-robot collision damage**, angle-scaled (README Category 1 #5)
- **Circle-circle collision detection** (prerequisite for angle-scaled damage; replaces existing axis-by-axis bounding-square logic)
- **Robot death and removal from play** (README Category 1 #4)
- **Battle termination and winner detection** (README Category 1 #6)
- **5-robot upper / 2-robot lower invariant** in the engine boundary
- **Canvas-text victory overlay** and **restart UX** (reverse the start-transition to restore the input form)
- **Robot-status legend** — DOM sidebar to the right of the arena listing each robot's color square, program name, and health percentage (pulled forward from later polish because it aids development; added as Step 3.5)

### 1.2 Out of scope (deferred to Tier 2)

- Any new sound effects (#1, #4, #8) and the Web Audio API migration (#12)
- Visual damage representation (#9) — desaturation + cracks/marks
- Per-robot shape variety (alternating squares/circles)
- Particle-based robot death animation
- DOM-overlay victory display (Tier 1 ships canvas text only)

### 1.3 Not addressed (out of both tiers)

- Scoring system + multi-battle matches (README Category 1 #7, #8)
- Assembler error catalogue (README Category 1 #9), number-range validation (#10), program-length cap (#11), recursion prevention (#12)
- Test bench UI (README Category 1 #13)
- Sound on/off toggle (README Category 1 #14)
- AIM-aligned shell origin (README Category 1 #15)

---

## 2. Design decisions (all locked)

The following decisions are the outcome of the design conversation. Each is final unless concrete evidence forces a revisit.

### 2.1 Radar

- **Geometry: ray vs. robot-as-disc.** Cast a single ray from the robot's center along the direction stored in the RADAR register. The ray "hits" a robot if it passes within `ROBOT-RADIUS` of that robot's center.
- **Return value.** Negative `distance` if the closest hit is a robot; positive `distance` to the wall the ray eventually intersects otherwise. Always returns *something* (the ray always hits a wall if it doesn't hit a robot first).
- **Multiple robots on the ray.** Closest hit wins. Robots farther behind are obscured.
- **Self-detection.** A robot does not see itself.
- **Cooldown.** None. Brain may ping radar as often as it likes; cost is the brain instructions used to write to and read from RADAR.
- **Range.** Limited only by the walls.
- **Sub-decision (TACTICAL):** when computing distance to a robot-hit, use the distance from the firing robot's center to the closest point of the target's bounding disc (or to the point on the ray where it first enters the disc) — either is acceptable, both differ by at most `ROBOT-RADIUS`. Pick one and document.

### 2.2 Shell explosion damage

- **Curve: quadratic falloff.** `damage = MAX_BLAST_DAMAGE × max(0, 1 - distance / BLAST_RADIUS)²`
- **Constants:** `MAX_BLAST_DAMAGE = 30`, `BLAST_RADIUS = 21.0` (meters, absolute — not in multiples of `ROBOT-RADIUS`).
- **Distance metric.** Distance from the shell's explosion point `(dest-x, dest-y)` to each robot's `(pos-x, pos-y)` center, *after* both robots and shells have ticked in the current frame.
- **Self-damage.** A robot can be damaged by its own shells. No exception.
- **Damage representation.** Internal damage value is float. The DAMAGE register read already rounds on access (`register.cljc:42-44`); no other rounding needed.
- **Stacking.** Multiple shells exploding in the same tick stack additively on a single robot.

### 2.3 Wall handling

- **Behavior on impact: slide.** The robot's velocity component perpendicular to the wall zeros out; the parallel component is preserved. A robot moving diagonally into a vertical wall continues moving vertically.
- **Desired velocity (SPEEDX/SPEEDY) is left alone.** The brain is in charge. A robot mashing against a wall is the brain's problem to detect (via X/Y register polling) and fix.
- **Damage formula:** `damage = MAX_WALL_DAMAGE × (v_perp / V_MAX)²` where `v_perp` is the velocity component into the wall at the moment of impact. `MAX_WALL_DAMAGE = 15`, `V_MAX = 25.5` (m/s, derived from the manual's SPEEDX max of ±255 dm/s).
- **Repeat-collision policy: first contact only.** Damage applied in the tick the robot transitions from not-touching-wall to touching-wall. Subsequent ticks while still pressed against the wall do nothing. Track per-robot per-axis "was-touching-wall" state.

### 2.4 Robot-on-robot collisions

- **Detection: circle-circle.** Two robots collide when the distance between their centers is `< 2 × ROBOT-RADIUS`. Replace the existing axis-by-axis square logic in `collide-two-robots` / `collide-all-robots` in `robot.cljc` (currently ~line 103 onward; shifts as steps land).
- **Damage formula:** `damage = MAX_COLLISION_DAMAGE × (approach_speed / (2 × V_MAX))²` per robot, where `approach_speed = (v_actor - v_target) · contact_normal` and `contact_normal = (target_pos - actor_pos) / distance`. `MAX_COLLISION_DAMAGE = 25` (matches the manual's "head-on" reference).
- **First-contact only.** Same policy as walls — only apply damage on the tick of transition. Track per-robot-pair contact state.
- **Collision response.** Preserve the existing "swap momentum" billiard-ball behavior, but along the contact normal vector rather than along a picked axis. Separate the robots so they're exactly `2 × ROBOT-RADIUS` apart along the normal (eliminate overlap).

### 2.5 Robot death

- **Trigger:** `damage <= 0`.
- **Data representation:** set `:alive? false` on the robot. **Do not remove from `:robots` vec.** The register layer (`register.cljc:11`) uses `[:robots robot-idx]` paths and each robot stores its `:idx` at init time; removing entries would invalidate all indexing.
- **Tick / collision / render skip:** dead robots are skipped in `world/tick-combined-world` (no brain tick, no movement, not eligible for shell damage, not eligible as collision targets, not eligible as radar targets).
- **Visual (Tier 1):** dead robots simply stop rendering. No animation. The particle-based death animation is deferred to Tier 2.

### 2.6 Victory detection + display

- **Trigger:** after each call to `tick-combined-world`, count alive robots. If `≤ 1`, the battle is over.
- **Outcomes:** exactly 1 alive → that robot wins. 0 alive (possible if a single explosion kills the last two) → tie / "NO WINNER".
- **World result field:** add `:result` to the world map. `nil` while running; `{:winner robot-idx}` or `{:tie? true}` when over.
- **Game loop:** in `app.cljs:loop-step`, when `:result` is non-nil, stop the loop (cancel raf, set `:running? false`).
- **Visual (Tier 1):** draw winner text on the canvas itself. E.g. `"ECHO WINS"` (program name) in the winner's color, centered; or `"TIE"` in white. No DOM changes.
- **Restart UX:** clicking the canvas (or pressing Enter) after the overlay appears reverses the start-transition — collapse canvas opacity to 0, restore the instruction-box height. User can then enter a new lineup.

### 2.7 Robot-count invariant

- **Lower bound: 2 robots.** Enforce in `world/init-world` (engine boundary) — throw on `< 2`. Also enforce at the frontend input layer in `app.cljs:valid-program-names` so the error is surfaced as a UI message rather than an uncaught exception.
- **Upper bound: 5 robots.** Already gated in the UI via `max-program-count`. Add the matching engine check in `init-world` — throw on `> 5`. Removes the silent "default to white" fallback currently in `canvas.cljs:93`.

### 2.8 Tuning knobs *not* touched in Tier 1

These are deliberate balance knobs reserved for later play-testing. Do not change them in Tier 1:

- **`ROBOT-RADIUS` (currently 7m).** The manual specifies a "1.5 meter square chassis," but the original 1981 Apple II game itself rendered robots at ~6-7.5m wide (verified from a screenshot — battlefield ≈170 pixels for a 256m arena, robots ~4-5 pixels = 6-7.5m). So 7m is roughly 2× what the original game *displayed*, not 10× off from what the manual *claimed*. Changing it ripples into collision detection (`robot.cljc:94`), canvas rendering (`canvas.cljs:21,28,30-33`), and game balance (smaller robots = harder to hit at the same shell speed). May shrink toward 4-5m after Tier 1 play-testing, but as a deliberate balance pass. **New gameplay constants added in Tier 1 (blast radius, wall damage thresholds) must be expressed in absolute meters, not multiples of `ROBOT-RADIUS`, so they stay valid if the radius is later tuned.**
- **Brain-tick ratio (currently 1 obj-code instruction per world tick at 30Hz).** A full 360° radar sweep at 5° steps takes ~12 seconds of wall time (~5 instructions per scan-and-check cycle × 72 iterations), making scanning AIs much weaker than fixed-direction shooters. The README already lists "Instructions per game-tick" as a Category 2a unknown that "dominates strategy balance." May bump to 2-4:1 in a later pass if scanning AIs feel uncompetitive after Tier 1 — but not as part of this slice.

---

## 3. Implementation sequence

Each step below is a logical checkpoint. The implementer should run `npm test` after each step and verify the game still loads + runs (visually) at the end of every step. Steps are dependency-ordered; do not re-order without thought.

### Step 1 — Plumbing (no behavior change)

**Files:** `world.cljc`, `robot.cljc`, `constants.cljc`

- Add `:alive? true` to `robot/init-robot` output map.
- Add `world/init-world` invariant: throw on `(count programs) < 2` or `> 5` with a clear message.
- In `world/tick-combined-world`, filter the `(:robots starting-world)` reduce to skip robots where `:alive?` is false (no-op so far since none are dead).
- Add new constants (placeholder values, used in later steps):
  - `MAX-BLAST-DAMAGE = 30.0`
  - `BLAST-RADIUS = 21.0`
  - `MAX-WALL-DAMAGE = 15.0`
  - `MAX-COLLISION-DAMAGE = 25.0`
  - `V-MAX = 25.5` (decimeters→meters conversion already baked into SPEEDX/SPEEDY multipliers; double-check the math)

**Tests:** add a test asserting `init-world` throws on bad input. Existing tests should still pass.

### Step 2 — Shell explosion damage

**Files:** `world.cljc`, `shell.cljc`, `constants.cljc`

The TODO at `world.cljc:31` becomes real code. After shells tick:

1. Split shells into live and exploded (already done).
2. For each exploded shell, compute distance from `(dest-x, dest-y)` to each *alive* robot's center.
3. Apply quadratic-falloff damage to each robot in range. Stack across multiple shells in the same tick.
4. Return a world with updated `:robots` (with new `:damage` floats) and live shells only.

**Implementation note:** the simplest shape is to fold over exploded shells, then over robots, accumulating damage deltas per robot into a map, then apply the map to robots in one pass. Keeps the update atomic.

**TACTICAL:** decide whether to compute damage before or after the robot-tick (i.e., does the damage apply to the robot's pre-tick or post-tick position?). The latter is closer to "shells exploded *this* tick, robots moved *this* tick, what's the geometry at end-of-tick" — recommended.

**Tests:** unit-test the damage curve against a few representative distances (0m → 30%, 7m → 13.3%, 14m → 3.3%, 21m → 0%, 25m → 0%). Test stacking. Test self-damage.

### Step 3 — Robot death + victory detection

**Files:** `world.cljc`, `robot.cljc`, `app.cljs`

- Replace the `(if false ...)` guard in `robot/tick-robot` (line 155) with `(if (not (:alive? robot)) world ...)`. The actual death-marking happens elsewhere.
- After damage is applied in `tick-combined-world` (Step 2), mark robots with `:damage <= 0` as `:alive? false`.
- Compute `:result` on the world after the death pass:
  - `(count alive)` `> 1` → `nil` (still running)
  - `= 1` → `{:winner <idx>}`
  - `= 0` → `{:tie? true}`
- In `app.cljs:loop-step`, after computing `next-world`, check `(:result next-world)`. If non-nil, render the final frame, draw the overlay (Step 7), and stop the loop.

**Tests:** test the death threshold (damage exactly 0 → dead; damage > 0 → alive). Test victory result computation for various alive counts.

### Step 3.5 — Robot-status legend (DOM sidebar)

**Files:** `public/index.html`, `public/css/main.css`, `src/main/robotwar/legend.cljs` (new), `app.cljs`

A development aid pulled forward from later polish: a DOM sidebar to the right of the battle arena listing every robot in the lineup with a color square, its program name, and its current health percentage. Design decisions below are the outcome of a grilling-style design conversation (2026-06-11) and are locked like everything else in §2.

**Design decisions:**

- **Substrate: DOM, not canvas.** An HTML sidebar updated from CLJS. Real text rendering, CSS styling (Data 70 font for free), and a natural foundation for Tier 2's DOM-overlay work. The §1.2 deferral of the "DOM-overlay victory display" to Tier 2 still stands — that item is specifically about the victory display.
- **Colors: no assignment changes.** Robot color is already deterministic and unique per robot: `(nth canvas/robot-colors idx)` over a fixed 5-distinct-color palette (`canvas.cljs:5`), and the engine now enforces 2–5 robots. Robots with duplicate names therefore already have different colors. The legend reuses the same `idx → color` mapping and is guaranteed to match the arena. Do not relocate the palette; `legend.cljs` requires `robotwar.canvas` and reads `robot-colors` directly.
- **Row content + order.** One row per robot, in lineup (idx) order — same order the colors are assigned in. Each row: a small square filled with the robot's color, the program name (from the world's `:program-names` vector, which `app.cljs:start-game` already stores idx-aligned), and the health percentage **right-aligned in a fixed column** so the numbers line up vertically as they change. Duplicate names appear twice; the color square disambiguates.
- **Health number.** `(max 0 (Math.round damage))` rendered as e.g. `87%`. The rounding intentionally matches the DAMAGE register read semantics (`rw-round`, `register.cljc:42-44`) so the legend shows what the robot's own brain sees; the clamp at 0 exists only because an overkilled robot's internal damage can go negative (e.g. `-13`), and a dead row should read `0%`.
- **Dead robots: row stays, dimmed, at 0%.** Toggle a CSS class (e.g. reduced opacity) when `:alive?` is false. The roster stays stable for the whole battle; rows are never removed or reordered.
- **No hit-flash.** The arena flashes a robot white on damage frames; the legend deliberately does not mirror this. The number dropping is the feedback.
- **Update cadence: every frame, no diffing.** Wherever `loop-step` calls `canvas/animate-world!`, also call `legend/update-legend!` with the new world. It overwrites ≤5 rows' text content and dead-class state per animation frame — trivially cheap, no bookkeeping, can't drift.
- **Lifecycle.** The sidebar container is empty and invisible (opacity 0) at page load. `start-game` builds the rows from the selected program names (`legend/build-legend!`) and the container fades in alongside the canvas's existing 0.5s opacity transition. Every `start-game` rebuilds the rows from scratch. When the game ends, the legend simply freezes at its final values (last frame's update) and stays visible under the victory overlay until restart (see Step 7).
- **Layout: flex row, widened centerer.** Wrap the canvas and the legend in a flex container and widen `.centerer` (≈850px) so the pair is centered as a unit. The header/instructions span the new width.

**Implementation sketch:** new `robotwar.legend` namespace with two functions — `build-legend!` (clear the container, create one row per program name with the color square inline-styled from `canvas/robot-colors`) and `update-legend!` (per robot: set the health text, toggle the dead class). `app.cljs` calls `build-legend!` in `start-game` after constructing `world-with-names`, and `update-legend!` in `loop-step` next to `animate-world!`.

**TACTICAL:** exact sidebar styling — width, font size, row spacing, whether it gets a green border like the canvas — is the implementer's call; stay within the existing Data 70 / green-on-black scheme.

**TACTICAL:** whether the fade-in reuses the `setTimeout` in `start-transition!` or gets its own CSS transition triggered the same way — implementer's call.

**Tests:** frontend-only; no engine code is touched. Verify manually per §4.4: legend appears with the battle; row colors/names match the arena robots (test with a duplicate-name lineup, e.g. `sniper, sniper, mover`); health counts down as robots take damage; a dead robot's row dims and reads 0%; a second battle rebuilds the legend with the new lineup.

### Step 4 — Wall blocking + damage

**Files:** `robot.cljc`, `constants.cljc`

- Add per-robot wall-touching state: `:touching-walls #{}` (a set of `:left`, `:right`, `:top`, `:bottom`).
- In `move-robot`, after computing `new-pos-x`/`new-pos-y`:
  - Detect which walls the new position would cross. Walls sit at `pos-x = 0` (left), `pos-x = ROBOT-RANGE-X` (right), `pos-y = 0` (top), `pos-y = ROBOT-RANGE-Y` (bottom) — `pos-x/y` is the robot's *center* and the canvas visually pads by `ROBOT-RADIUS` on every side so the render doesn't clip. Legal range is `[0, ROBOT-RANGE-X] × [0, ROBOT-RANGE-Y]`, matching `(rand ROBOT-RANGE-X)` at init. So: `new-pos-x <= 0` → `:left`, `>= ROBOT-RANGE-X` → `:right`, etc.
  - Clamp position to the legal range.
  - For each wall the robot is newly touching (in new set but not in old): compute `v_perp` (the velocity component into that wall at impact), apply quadratic damage.
  - Zero the perpendicular velocity component for each currently-touching wall.
  - Update `:touching-walls` on the robot.
- **Do not touch SPEEDX/SPEEDY.** Those are `:desired-v-x`/`:desired-v-y` and represent the brain's wish; leave them alone.

**TACTICAL:** the constants currently use `pos-x` in meters but `desired-v-x` is set via the SPEEDX multiplier (`0.1`). Be careful that `v_perp` and `V_MAX` are in the same units. Verify by deriving: SPEEDX max = ±255, multiplier = 0.1 → `v-x` max = ±25.5 m/s. So `V_MAX = 25.5`. Double-check this math while writing.

**Tests:** unit-test the wall-damage formula at representative impact speeds. Test that a stationary robot pressed against a wall by a brain that keeps setting SPEEDX takes damage only once. Test the slide behavior (diagonal into wall preserves parallel motion).

### Step 5 — Robot-robot collision rewrite

**Files:** `robot.cljc`, `constants.cljc`

This step replaces `collide-two-robots`, `collide-all-robots`, and the `(dec damage)` placeholder.

- Detection: `distance = sqrt((dx)² + (dy)²) < 2 × ROBOT-RADIUS`.
- Per-pair contact state: track on each robot a `:colliding-with #{idxs}` set. Damage applies only on transition (in new set, not in old).
- Compute contact normal, approach speed, apply quadratic damage to both robots.
- Collision response: swap velocity components along the normal (the existing billiard-ball intent but properly 2D). Separate the robots so they're exactly `2 × ROBOT-RADIUS` apart along the normal.

**Watch out:** the current `collide-all-robots` runs collision detection per-actor inside `tick-robot`, meaning collisions are detected twice (once when actor A ticks, once when actor B ticks). The first-contact tracking via the symmetric `:colliding-with` sets handles this correctly: the second detection sees both already in each other's set and applies no damage.

**Alternative refactor:** lift collision detection out of `tick-robot` into `tick-combined-world` as a single post-tick pass over all robot pairs. Cleaner separation, single source of truth. **TACTICAL:** decide based on diff size and test complexity.

**Tests:** head-on collision at max speed → 25% to each. Glancing collision → small damage. Stationary contact → no damage. Two robots touching for many ticks → damage applied exactly once.

### Step 6 — RADAR register implementation

**Files:** `register.cljc`, `physics.cljc`

**(Landed 2026-07-11 — see §A.25–A.30 addendum.)**

- Define `RadarRegister` record `[robot-idx reg-name val]` and instantiate it in `init-registers` alongside `AimRegister` / `ShotRegister`.
- Implement `IReadRegister` (`radar-scan` in `register.cljc`):
  - Get the firing robot's position and the value previously written to RADAR (the direction in degrees).
  - Decompose the direction via `physics/decompose-angle` (same robotwar-degrees convention as AIM).
  - For each *alive* robot except the firing one, call `physics/ray-disc-hit-distance` with `ROBOT-RADIUS`. Take the min of the resulting hit distances (skip nils).
  - Compute the wall-exit distance via `physics/ray-arena-exit-distance` on `[0, ROBOT-RANGE-X] × [0, ROBOT-RANGE-Y]`.
  - If any robot hit: return `(rw-round (- min_hit_distance))`. Else: return `(rw-round wall_distance)`.
- Implement `IWriteRegister`: store `(mod (double data) 360)` into the register's `:val` slot via `path-to-val`. No side effects on the robot.

**Where the helpers went:** `physics.cljc` gained `ray-disc-hit-distance` (closest-approach quadratic formulation; returns `nil` on miss, `0.0` if origin is inside the disc, positive entry distance otherwise) and `ray-arena-exit-distance` (per-axis parametric; assumes origin inside the arena).

**Tests:** JVM (`register_test.clj`) covers each geometry case explicitly: hit at (100-ROBOT-RADIUS), closer-of-two wins, no hit → wall distance, self excluded, dead excluded, ray along -y, write mods 360. CLJS (`register_test.cljs`) covers the same in a compact smoke test.

### Step 7 — Frontend: victory overlay + restart UX + alive-only rendering

**Files:** `canvas.cljs`, `app.cljs`

- **Render only alive robots** in `canvas/animate-world`. Skip the loop body for any robot where `:alive?` is false.
- **Victory overlay** drawn on the canvas:
  - Triggered when `world.result` becomes non-nil.
  - Use the project's existing `Data_70_LET.ttf` font (already loaded — see `public/fonts/`).
  - Centered text. Winner case: program name in winner's color. Tie case: "TIE" in white.
- **Restart UX** in `app.cljs`:
  - On canvas click (or Enter keypress) while a victory overlay is showing: reverse the start-transition. Set canvas opacity to 0, restore the instruction-box height (to whatever its original CSS value was — store it before collapsing, or use CSS class toggling instead).
  - Also fade out the robot-status legend (Step 3.5) along with the canvas. **Note (from Step 3.5 execution):** the legend's fade-in is an *inline* style — `start-transition!`'s 500ms `setTimeout` sets `#legend`'s `style.opacity = "1"` alongside the canvas — so the restart must reset the same inline property to `"0"` (a CSS class toggle alone won't override it). Rows do not need to be cleared on restart: `legend/build-legend!` clears the container as its first act, so the next `start-game` rebuilds the roster from scratch regardless.
  - Clear `:world`, `:tick-count`, etc. in the app state.
  - User can now type a new lineup and press Enter as usual.
- **Input validation** in `app.cljs:on-program-input-keydown`:
  - If `(count program-names) < 2` after `valid-program-names`, surface an error (e.g. set a `:input-error` in state, render it under the input box, do not start the game).
  - The `> 5` case is already gated by `valid-program-names` taking `(take max-program-count)`. Leave it.

**Tests:** these are hard to unit-test; verify manually by running `npx shadow-cljs watch app` and `npx serve public` per the README.

---

## 4. Test approach

Engine code is pure CLJC and runs on both JVM (for `terminal.clj`, the REPL, and headless tests) and CLJS (for the browser). The repo has an established cross-platform test pattern that should be followed.

### 4.1 Test layout

- **JVM tests** live at `src/test/robotwar/*_test.clj` and use `clojure.test`. This is where the *full* coverage lives — multiple `deftest`s per namespace, edge cases, representative values along curves.
- **CLJS tests** live at `src/main/robotwar/*_test.cljs` and use `cljs.test` (`:refer-macros [deftest is testing]`). These are deliberately *smoke tests* — typically a single `deftest` per namespace that exercises the main behaviors in a few `let` blocks. Their job is to prove the code runs under JS, not to re-derive the JVM coverage.
- **No automatic replication.** The two test files are separate, hand-maintained. Look at `register_test.clj` (multiple deftests, exhaustive) vs. `register_test.cljs` (one `register-smoke-test` covering the same surface in a compact form) for the canonical example.

### 4.2 Test wiring

`npm test` runs both suites via `scripts/test-all.sh`:

```bash
clj -M:test                                       # JVM
npx shadow-cljs compile test && node target/test/node-tests.js   # CLJS
```

Both must pass for a step to be considered done.

### 4.3 Per-step test obligations

For each Tier 1 step that touches engine code (steps 1–6):

1. **Add full JVM tests** in `src/test/robotwar/` for the new behavior. Use the existing files where they exist (`robot_test.clj`, `register_test.clj`, etc.); create new ones for namespaces that don't yet have a test file (`world_test.clj`, `shell_test.clj`).
2. **Extend the CLJS smoke test** for each touched namespace. If a `*_test.cljs` file already exists, add a few `is`-assertions covering the new behavior to the existing `deftest`. If no smoke test exists for that namespace yet, create one with a single `deftest` covering the main path.
3. **Reader conditionals** (`#?(:clj … :cljs …)`) are needed only for genuine JVM/JS interop differences (math functions, regex flags, etc.) — see the existing pattern in `physics.cljc` and `robot.cljc`.

### 4.4 Frontend testing

Step 7 (canvas + `app.cljs`) is much harder to test programmatically. Verify manually with the dev workflow:

```bash
npx shadow-cljs watch app    # terminal 1
npx serve public -l 3000     # terminal 2
```

Open `http://localhost:3000/`. For each Tier 1 user-visible behavior in the Definition-of-done checklist (§6), exercise it in the browser and confirm.

---

## 5. Risks and gotchas

### 5.1 Float damage and rounding

The DAMAGE register read already rounds via `rw-round` in `register.cljc:42-44`. Internal storage stays float. Don't introduce extra rounding — small near-misses accumulate correctly only if stored as floats. If a test reads `damage` and expects an integer, use the register read path or call `Math/round` explicitly in the test.

### 5.2 The dec-damage placeholder

**(No longer present — landed in Step 5.)** `collide-two-robots` and `collide-all-robots` were removed entirely, along with the two `(dec damage)` placeholder calls. Kept here for historical context.

### 5.3 The "if false" death guard

**(No longer present — landed in Step 3.)** `tick-robot` now branches on `(if (not (:alive? robot)) ...)`. Kept here for historical context.

### 5.4 Self vs. other collision indexing

In Step 5, the `:colliding-with` set on each robot stores other robots' `:idx` values. `collision-pass` only resets *alive* robots' sets, so a dead robot's `:colliding-with` retains whatever value it had on its final tick — this is harmless (dead robots are never iterated as collision candidates) and preserves the "dead robot state is frozen" invariant from Step 3.

### 5.5 The 5-robot color fallback in canvas

`canvas.cljs:93` currently has `(or (nth robot-colors idx nil) "#fff")` — falls back to white when `idx >= 5`. Once Step 1's engine invariant prevents `> 5` robots, this fallback is unreachable. Remove it during Step 7 (replace with `(nth robot-colors idx)` — let it throw if the invariant is ever broken).

### 5.6 Shell explosion uses dest, not current position

Shells in the current code (`shell.cljc:34-38`) snap to `dest-x`/`dest-y` on the tick they explode. Step 2's damage calculation should use those final coordinates, not the pre-snap position. Verify by reading the shell tick code closely — the `:exploded true` flag is set on the same tick the position becomes `(dest-x, dest-y)`.

### 5.7 Order of operations within a tick

Within `tick-combined-world`, the current order is: tick all robots (which moves them and handles collisions), then tick shells. Steps 2-5 add more operations. The recommended end-of-tick order is:

1. Tick each *alive* robot's brain + movement
2. Run robot-robot collision pass (Step 5)
3. Run wall-collision pass (Step 4) — though this is currently embedded inside `move-robot`; keep it there
4. Tick shells (Step 2's damage pass folds in here)
5. Apply accumulated damage from shells + collisions to all robots
6. Mark `:alive? false` for any robot with `damage <= 0` (Step 3)
7. Compute `:result` (Step 3)

**TACTICAL:** the implementer may discover a cleaner ordering. The principle: damage is computed against end-of-tick positions, then applied, then death is determined, then victory is determined.

---

## 6. Definition of done for Tier 1

The slice is complete when, with a fresh browser load:

- A user types `random, mover, shooter` (or any other valid 2-5-robot lineup) and presses Enter.
- The game runs. Robots use radar to find each other (visible from their AIM rotations + shooting behavior).
- Shells explode. Robots within blast radius take damage.
- A legend to the right of the arena lists each robot with a color square and name matching the arena (duplicate names get distinct colors), and a health percentage that counts down as the robot takes damage. Dead robots' rows dim to 0% but stay listed.
- Robots that take enough damage stop rendering and stop acting.
- Robots that run into walls stop at the wall and take damage proportional to impact speed.
- Robots that run into each other take angle-scaled damage.
- The game ends when ≤ 1 robot remains. The winning program name appears on the canvas in its color (or "TIE" if both die simultaneously).
- The user can click the canvas (or press Enter) to dismiss the overlay, get the input form back, and start a new battle.
- Trying to start a battle with fewer than 2 valid programs shows a visible error and does not begin a battle.
- Trying to start a battle with more than 5 valid programs is impossible from the UI (already gated), and the engine throws cleanly if called directly.

All existing tests still pass; new tests cover the new gameplay rules.

---

## Addendum — Lessons from Step 1 execution

### A.1 Existing tests break on the new `< 2` invariant (hidden prerequisite)

The spec says Step 1 should "add a test asserting `init-world` throws on bad input." What it does *not* say is that **five existing test files** were already calling `init-world` with a single program, which became illegal once the `(< program-count 2)` check was added (it was already in the code, but the tests predated it or had never been run with it).

Files that had to be updated:
- `src/test/robotwar/brain_test.clj`
- `src/test/robotwar/register_test.clj`
- `src/main/robotwar/brain_test.cljs`
- `src/main/robotwar/register_test.cljs`
- `src/main/robotwar/robot_test.cljs`

In each case, the fix was to pass a second (dummy) program string to `init-world` so the count was ≥ 2. The `brain_test` files additionally had to duplicate the same program source so robot-0's tick sequence remained unchanged (the assertions only inspect robot 0).

**Recommendation for future agents:** Before running `npm test` after Step 1, search for all `init-world` calls in test files and verify they pass at least 2 programs. This is a prerequisite, not optional new-test coverage.

### A.2 Several "add" directives in Step 1 were already implemented

The Step 1 spec says to "add" three things that were in fact already present in the codebase at the time of execution:
- `:alive? true` in `robot/init-robot`
- `init-world` invariants for `< 2` and `> 5`
- Constants (`MAX-BLAST-DAMAGE`, `BLAST-RADIUS`, `MAX-WALL-DAMAGE`, `MAX-COLLISION-DAMAGE`, `V-MAX`)

This means the *actual* work of Step 1 was entirely test-related: fixing broken existing tests (§A.1) and writing new tests for the already-present invariants and alive-skipping logic. Future agents should not assume they need to write those code changes again; verify the file contents first.

### A.3 `tick-combined-world` alive-skipping was already present

The `alive-indices` filter in `tick-combined-world` was already implemented. The new test coverage for it (`world_test.clj` / `world_test.cljs`) should assert that a dead robot's state does not change across a tick while an alive robot's state does. Be careful with exact-value assertions on brain fields like `:instr-ptr` — they are integers, not floats, so an assertion like `(= 0.0 0)` will fail even though the values are semantically equal.

---

## Addendum — Lessons from Step 2 execution

### A.4 `physics.cljc` did not have `rw-sqrt`

The `tick-combined-world` damage formula needs Euclidean distance (`sqrt(dx² + dy²)`), but `physics.cljc` only had `rw-pow`. A cross-platform `rw-sqrt` had to be added (using `Math/sqrt` / `js/Math.sqrt`). Step 5 (circle-circle collision) will also need it for distance checks.

**Recommendation for future agents:** Check `physics.cljc` before assuming a math helper exists. Add any missing cross-platform wrappers there rather than inlining JVM-only or JS-only math.

### A.5 Float damage tests need an `approx=` helper

The quadratic falloff `30 * max(0, 1 - d/21)²` produces float values that can differ in the last bit between the test's expected calculation and the engine's actual calculation. Example: at `d = 7.0`, the expected value `100 - 30*(14/21)²` computed in the test produced `86.66666666666667`, but the engine's result was `86.66666666666666`. The difference is `~1e-14`, but `(= ...)` fails.

**Recommendation for future agents:** Define an `approx=` helper (e.g., `(< (Math/abs (- a b)) epsilon)`) and use it for all damage-curve assertions in Steps 4 and 5 as well. This applies to wall-damage and collision-damage formulas, which are also quadratic.

### A.6 Exploded shells already have their final coordinates in `pos-x` / `pos-y`

When `tick-shell` marks a shell as `:exploded true`, it simultaneously snaps `pos-x` and `pos-y` to `dest-x` and `dest-y`. This means the Step 2 damage computation can read `(:pos-x shell)` directly — there is no need to look at `dest-x`. The spec §5.6 mentions this, but it is easy to miss because the shell map still contains both keys.

**Recommendation for future agents:** If you refactor `tick-shell`, preserve this invariant. If you need the explosion point for any other feature (e.g., canvas particle origin), `(:pos-x shell)` is sufficient once `:exploded` is true.

### A.7 Shell damage must be accumulated per-robot before applying

Multiple shells can explode in the same tick and hit the same robot. The natural nested-loop approach (reduce over shells, then over robots) would repeatedly update a single robot's damage if applied inside the inner loop. The clean pattern is:

1. Build a `damage-per-robot` accumulator map (`idx -> total-damage`).
2. Apply the map to all robots in one pass after the loops finish.

This keeps the tick atomic (all damage from the same tick is applied simultaneously) and avoids ordering-dependent results.

**Recommendation for future agents:** Use the same accumulator pattern for Step 4 (wall damage) and Step 5 (collision damage), even though those are applied per-pair rather than per-shell. The principle is: compute all deltas first, then apply them in one pass.

### A.8 No `shell_test.clj` / `shell_test.cljs` existed — shell behavior is tested at the `world` level

There were no shell-specific test files before Step 2. Shell behavior is best tested in `world_test.clj` / `world_test.cljs` because it requires both shells and robots to interact in a single `tick-combined-world` call. The spec §4.3 mentions creating `shell_test.clj` if needed, but in practice the world-level tests cover the behavior more directly.

**Recommendation for future agents:** Add wall-damage tests to `robot_test.clj` / `robot_test.cljs` and collision-damage tests to `world_test.clj` / `world_test.cljs`, matching the existing pattern where the test file corresponds to the namespace that orchestrates the interaction.

### A.9 Shells were stored as a heterogeneous sequence, not a map — refactored to `{id shell}`

Before Step 2, `shells` was `{}` at init, then became a `()` or `({:id 0 ...})` sequence after the first tick. When a shot fired, `register.cljc` used `(merge shells (shell/init-shell ...))` — which in ClojureScript prepends a map onto a list via `conj`, an implementation detail that happens to work but is non-idiomatic and fragile.

Worse, there was a **latent tick-0 bug**: if a shell fired on the very first tick (when `shells` was still `{}`), `merge` on two maps would flatten the shell into the top-level map, breaking `tick-shell`'s destructuring. No existing program hit this, but it was a real crash waiting to happen.

The canvas had a `shell-map` helper that defensively converted this runtime type (`map?` → `sequential?` → `:else {}`) back into a map for rendering. This was a code smell.

**What was changed:**
1. `register.cljc` (both `:clj` and `:cljs` branches): `(merge shells (shell/init-shell ...))` → `(assoc shells next-shell-id (shell/init-shell ...))`
2. `world.cljc`: shell iteration changed from `map`/`filter`/`remove` on a sequence to `for`/`into`/`vals` on a map
3. `canvas.cljs`: deleted the `shell-map` helper; canvas reads `(:shells world)` directly

**Recommendation for future agents:** `shells` is now a proper map throughout the entire lifecycle. Use `assoc` to add, `for`/`into` to iterate/filter, and `vals` to extract a collection. Do not reintroduce sequence-based storage or `merge` for shell insertion. If you touch shell code in later steps, verify the type stays a map in all code paths.

---

## Addendum — Lessons from Step 3.5 execution

### A.10 TACTICAL choices made

- **Fade-in:** reused the existing `setTimeout` in `start-transition!` (option A from the spec's TACTICAL note). The same 500ms callback that sets the canvas's inline `opacity` to `"1"` now does the same for `#legend`; the CSS transition on `#legend` handles the animation. **Consequence for Step 7:** because this is an inline style, restart must reset `#legend`'s inline `opacity` to `"0"` — see the note added to Step 7.
- **Styling:** no border on the legend (the canvas keeps its green border as the only frame). 18px Data 70 in the standard green (`#3ef74e`), 18px color swatches, 12px row spacing, health column fixed at 60px right-aligned. Dead rows get `.legend-row.dead { opacity: 0.35 }`. Layout is `.arena-row` (flex, 20px gap) with `.centerer` widened to 850px.

### A.11 Row-element refs live in a `defonce` atom

`legend.cljs` stores `{:row el :health el}` refs per robot in a `defonce rows` atom, populated by `build-legend!` and consumed by `update-legend!` (which `map`s `(:robots world)` against it positionally — robot `:idx` order and row order are both lineup order, so no lookup is needed). `build-legend!` clears the container (`innerHTML = ""`) and resets the atom as its first act, so repeated `start-game` calls cannot leak stale rows. If Step 7's restart wants to visually empty the legend before the next battle, it can simply not bother — the rebuild handles it — or call `build-legend!` with an empty vector.

### A.12 Verification approach (no test code)

As specified, no engine code was touched and no automated tests were added. Manual verification per §4.4 was done with a headless browser driving the real page: duplicate-name lineup (`shooter, shooter, mover`) confirmed distinct swatch colors matching the arena; health counted down per frame; the dead robot's row dimmed to 0.35 opacity at `0%` and never went negative (the `max 0` clamp held against overkill); a second `start-game` with a different lineup rebuilt the legend with no stale rows. One observation for later steps: two `shooter` robots stalemate indefinitely until radar (Step 6) exists, so end-of-battle legend freezing under the victory overlay could not be observed yet — re-check it during Step 7 verification.

---

## Addendum — Lessons from Step 4 execution

### A.13 Walls live at `pos-x = 0` / `pos-x = ROBOT-RANGE-X`, not at `± ROBOT-RADIUS`

The original Step 4 body said "detect which walls the new position would cross (`new-pos-x < ROBOT-RADIUS` → `:left`, etc)." That's inconsistent with how the coordinate system actually works and would have made every random init position (0 ≤ `pos-x` < `ROBOT-RADIUS`) start life "already touching" the left wall.

`pos-x`/`pos-y` is the robot's **center** in game coordinates. The canvas visually pads by `ROBOT-RADIUS` on every side (see `canvas.cljs`'s `room-for-robots` / `arena-width`) so a robot at `pos-x = 0` renders with its center at canvas x = `ROBOT-RADIUS` — its left edge flush against the visible wall. The legal range is `[0, ROBOT-RANGE-X] × [0, ROBOT-RANGE-Y]`, matching `(rand ROBOT-RANGE-X)` at init.

The Step 4 body has been corrected inline. Future steps that reason about wall geometry should treat walls as at `0` and `ROBOT-RANGE-{X,Y}` on the corresponding axis.

### A.14 Impact velocity is the physics tick's returned `v` (before wall clamping)

The spec asks for "the velocity component into the wall at impact," but doesn't pin down which velocity. The natural choice is the `v` returned by `physics/d-and-v-given-desired-v` for the tick — i.e., what the velocity *would* have been at end-of-tick if the wall weren't there. This slightly overestimates for impacts that happen mid-tick with acceleration still being applied (the robot might have hit the wall *before* the acceleration boost took full effect), but it's simple, deterministic, and reads correctly under the spec language.

**Recommendation for Step 5:** use the same convention — compute `approach_speed` from robot velocities at end-of-tick (post-`move-robot`, pre-collision-response). Don't try to reconstruct "velocity at the exact moment of contact"; it's not worth the physics.

### A.15 Tests need `v = desired-v` to assert exact damage magnitudes

Any test that asserts a specific wall-damage value must set `desired-v-{x,y}` equal to `v-{x,y}` at test setup. Otherwise the physics accelerates or decelerates the robot during the tick and the returned `v` (which drives damage) is not what the test-writer expected.

Worse: with `v-x = V-MAX` and `desired-v-x = 0.0`, `Math/copySign(MAX-ACCEL, 0.0)` returns `+MAX-ACCEL` (positive zero has positive sign under IEEE 754), so the physics *accelerates* the robot in the +x direction rather than decelerating. Combined with a resulting *negative* `time-to-reach-desired-v`, `d-and-v-given-desired-v` can produce a position that runs **backwards** from where the robot started — clean state but nonsense physics. This is an artifact of the existing physics helper being written for the "brain sets a new desired-v" use case, not "velocity is decoupled from desired-v at rest." Not worth fixing in Step 4, but worth knowing when writing Step 5 tests.

### A.16 Wall damage integrates cleanly with the existing tick order

Damage is applied directly on the robot's `:damage` inside `move-robot` (same tick as movement). No world-level accumulator was needed — wall damage is per-robot and doesn't cross robot boundaries. The existing `tick-combined-world` sequence still works:

1. Each alive robot ticks → `move-robot` applies wall damage → robot's `:damage` may already be `≤ 0`
2. Shells tick → shell-damage accumulator applied
3. Death mark → any robot with `:damage ≤ 0` gets `:alive? false`
4. `:result` computed → `:just-died` correctly includes wall-killed robots

**Recommendation for Step 5:** robot-robot collisions *do* cross robot boundaries, so they should use the per-robot accumulator pattern from §A.7 (compute all deltas over pairs first, apply in one pass). Wall damage's in-place mutation would double-apply if used naively for collisions.

### A.17 `MAX-ACCEL`'s comment is wrong (but the value is right)

`constants.cljc` comments `MAX-ACCEL` as "decimeters per second per second" and `init-robot`'s docstring says "distance and distance/time units are all in decimeters." Both are stale. The unit is m/s² — verified from the existing acceleration test: starting at `v-x = 0` with `desired-v-x = 14` (from SPEEDX=140 × 0.1 multiplier) and `MAX-ACCEL = 4`, after 1 second `v-x = 4` and `pos-x = 2` — which matches `a = 4 m/s²`, not `4 dm/s²`. `V-MAX = 25.5` and `ROBOT-RANGE-X = 256.0` are also both in meters. Don't take the comments at face value; grep the constants file for the units and verify against the acceleration test if in doubt.

### A.18 Two stale TODO comments removed

The pre-Step-4 `robot.cljc` carried two stale TODOs about walls: `; TODO: deal with bumping into walls.` at the top of the file and a `TODO: add support for collision with walls first…` in `tick-robot`'s docstring. Both were removed. Step 5 will remove any remaining collision-related TODOs.

---

## Addendum — Lessons from Step 5 execution

### A.19 TACTICAL: collision lifted out of `tick-robot` into `tick-combined-world`

Step 5 offered two paths — leave detection inside `tick-robot` (per-actor duplicate detection, handled by the symmetric `:colliding-with` sets), or lift it into a single post-tick pass in `tick-combined-world`. The lift was chosen: `robot/collision-pass` now takes the robots vec and returns it with positions, velocities, `:colliding-with` sets, and damage all updated. `tick-combined-world` calls it exactly once per tick, after all robots have ticked and before shells are ticked.

Benefits confirmed by the resulting code:
- Every pair is examined once per tick — no more "each pair detected twice per world tick" that the old `collide-all-robots` had.
- The damage-accumulator pattern from §A.7 falls out naturally: build a `{idx → total-damage}` map in the reduce, then apply once at the end.
- `tick-robot` shrunk to just brain-tick + shot-timer + `move-robot`. The old `update-robots` helper (only used by the collision code) became dead and was deleted.
- The whole surface is a pure function on a robots vec, which makes it trivial to unit-test without constructing a full world.

### A.20 Collision-response policy: separate always, swap only when approaching, damage only on transition

The spec was slightly ambiguous about which collision-response effects apply on repeat contact. The implementation split them by physical intent:

- **Position separation:** applied *every* tick the pair overlaps. If we skipped it, robots would tunnel through each other after the first tick.
- **Normal-velocity swap:** applied only when `approach > 0` (they're moving toward each other along the contact normal). If they're already separating from a previous swap, we don't re-swap and re-approach them.
- **Damage:** applied only on the tick the pair *transitions* into contact — the "first contact only" rule from §2.4, tracked via each robot's `:colliding-with` set of counterparty idxs.

The transition-detection is symmetric: for pair (a, b), `was-touching? = (contains? (old-a-set) b-idx)`. Both robots' old sets carry the same information, so checking either works. At the end of the pass, each alive robot's `:colliding-with` is rebuilt from the current tick's contacts. Dead robots' sets are left alone (see §5.4).

### A.21 The 1e-12 floor guards against divide-by-zero at exact coincidence

`resolve-collision-pair` computes the unit normal by dividing `dx`/`dy` by `sqrt(d2)`. Two robots at exactly the same position would give `d2 = 0` → NaN normal. The floor `(max d2 1e-12)` picks an arbitrary but consistent direction (falls out of the sign of `dx`/`dy` before flooring). This never happens organically because `move-robot` runs before collisions and produces separated positions, but the guard keeps `collision-pass` a total function for tests and future scenarios.

### A.22 Test overlap sizing gotcha

The first draft of `collision-head-on-max-damage-test` set the two robots 1.5 × ROBOT-RADIUS apart from center → `gap = 10.5`, robots at `x = 89.5` and `x = 110.5`, distance = 21 = 3 × ROBOT-RADIUS. That's *not* overlapping (min collision distance = 2 × ROBOT-RADIUS = 14). The test silently failed on every assertion because no collision fired.

Fix: use `gap = 0.5 × ROBOT-RADIUS` so the distance between centers is `ROBOT-RADIUS = 7 < 14`. All the collision tests in `robot_test.clj` / `.cljs` use this pattern.

**Recommendation for future geometry tests:** if you're setting up two robots to collide, distance-between-centers should be *strictly less than* `2 × ROBOT-RADIUS`, not "close to each other." An easy way is `gap = 0.5 × ROBOT-RADIUS` for each so total distance = `ROBOT-RADIUS`.

### A.23 The `clj` wrapper is silently broken on this machine

`npm test` invokes `./scripts/test-all.sh`, which runs `clj -M:test`. On this machine `clj` requires `rlwrap` (missing), so it prints a one-line warning and exits 0 *without running any tests*. Every prior "npm test passes" claim in the Step 1–4 addenda was therefore likely false for the JVM half.

Workaround used in Step 5 verification: run `clojure -M:test` and `node target/test/node-tests.js` directly. Both suites pass (72 JVM tests / 144 assertions, 10 CLJS tests / 56 assertions, 0 failures).

**Recommendation for future agents:** either install `rlwrap`, patch `scripts/test-all.sh` to call `clojure` instead of `clj`, or run the two suites directly and verify each one printed a real "Ran N tests" line before claiming a pass.

### A.24 Docstring unit fix carried over from A.17

While editing `init-robot`, the stale docstring claim about "decimeters" (called out in §A.17) was replaced with the accurate "meters." No behavior change.

---

## Addendum — Lessons from Step 6 execution

### A.25 TACTICAL choices made

- **Distance-to-disc math:** solved the ray-vs-disc quadratic in the "closest-approach then step back to entry" form: given ray origin `P`, unit direction `D`, target center `C`, and radius `r`, compute `v = C - P`, `tc = v · D`, `d² = |v|² - tc²`. If `d² ≤ r²`, entry distance is `t_near = tc - sqrt(r² - d²)`. This is `physics/ray-disc-hit-distance`. Returns `nil` (miss), `0.0` (origin inside the disc), or the positive entry distance.
- **Ray-vs-wall math:** parametric line-plane intersection per axis (`t = (wall - p) / dir` on whichever axis's direction is non-zero), then `min` of the resulting positive `t`s. This is `physics/ray-arena-exit-distance`. Since the origin is always inside the arena (positions are clamped in `move-robot`), it always returns a non-negative distance.
- **Helper location:** both helpers live in `physics.cljc` — pure math, no world knowledge. The RADAR register logic in `register.cljc` glues them together and does the alive/self filtering.
- **Aim convention reused:** RADAR direction uses the same robotwar-degrees convention as AIM (0° = up = -y, 90° = right = +x), so `physics/decompose-angle` gives the ray direction directly. No new angle plumbing.
- **Rounding:** the return value is rounded to an integer via `rw-round` before being handed back to the brain, matching the DAMAGE register convention and the assumption that the Robotwar VM sees integers.

### A.26 RADAR write is *not* a triggering side effect

Unlike SHOT (which spawns a shell as its write side effect) and AIM (which mods 360 into a robot field), RADAR's write is a plain store into the register's `:val` slot. The ray is cast at *read* time, against the world state present when the brain reads the register. This means:

- A brain that writes RADAR at tick T and reads RADAR at tick T+1 sees geometry as of T+1, not T.
- Nothing prevents a brain from reading RADAR without ever writing it — the initial `:val` is 0, which is a valid direction (aim 0° = up).

**Recommendation for future agents:** RadarRegister doesn't need to touch any robot field; it's a self-contained storage-plus-computed-read pattern. Don't add a `:radar-dir` field on the robot — it duplicates the register's own `:val`.

### A.27 The extra require in register.cljc

`radar-scan` needs `ROBOT-RADIUS`, `ROBOT-RANGE-X`, `ROBOT-RANGE-Y` from `constants`, plus `decompose-angle` and the two new helpers from `physics`. `register.cljc` didn't previously require `physics` — that require had to be added. There is no dep cycle (physics requires nothing, shell requires physics + constants, register requires all three).

### A.28 Reader-conditional paren juggling gotcha

Adding a new `(extend ...)` / `(extend-type ...)` block to an existing `#?(:clj (do ...) :cljs (do ...))` requires **subtracting a closing paren from the previously-last extend block** and **adding the removed count back to the new last extend block**, because the final block is the one that closes both the `(do ...)` and (for the `:cljs` branch) the entire `#?(...)` form. Getting this wrong produces an off-by-one paren error that reads at the wrong offset (the reader reports the problem where the count *first* stops matching, not where you actually miscounted).

For this step, the paren totals ended up:
- `:clj` branch ShotRegister's trailing line changed from 8 `)` + `}` (closing extend + do) → 7 `)` + `}` (closing extend only), and the new RadarRegister extend ends with `))))` + `}` + `))` (closing mod, assoc-in, fn, `}`, extend, do).
- `:cljs` branch ShotRegister's trailing line changed similarly, and the new RadarRegister extend-type ends with `))))))` (closing mod, assoc-in, method, extend-type, do, and the `#?(...)` outer paren).

**Recommendation for future agents:** if you add a record to `register.cljc`, use a paren-checker (e.g. `python3 -c "..." | count`, or your editor's rainbow brackets) *before* running the test suite — the reader error is confusing, and manual counting inside dense `#?` blocks is error-prone.

### A.29 Tests that live entirely at the register layer

RADAR unit tests don't need a whole world tick; they need only:
1. A 2- or 3-robot world (via `world/init-world`) whose robots are then `update-in`-patched to have specific `:pos-x`/`:pos-y`/`:alive?` values.
2. `write-register` to store a direction into RADAR.
3. `read-register` to trigger the ray cast.

The `radar-world` and `read-radar` helpers in `register_test.clj` / `register_test.cljs` encapsulate this. No brain, no `tick-combined-world`, no assembler. This keeps the tests direct and independent of unrelated moving parts.

**Verification approach:** JVM (`clojure -M:test`) and CLJS (`node target/test/node-tests.js`, after `npx shadow-cljs compile test`) both pass with 79 JVM tests / 151 assertions and 11 CLJS tests / 60 assertions. Per §A.23, `npm test` invokes the broken `clj` wrapper; run the two suites directly.

### A.30 Return value on ray with no robot hit

When no alive non-self robot's disc intersects the ray, we return the positive wall distance. Because the ray always exits the arena eventually, this is always a positive number, so the sign of the return value cleanly encodes "robot vs. wall" as the spec specifies. Edge case: if the firing robot is exactly on a wall and shoots into it, `ray-arena-exit-distance` returns `0.0`, which rounds to `0`. `0` reads as neither "robot" (would be negative) nor "meaningful wall distance." No behavior workaround was added — it's a rare case (positions are clamped to `[0, RANGE]` inclusive so a robot pinned in a corner shooting outward is the only way to hit this), and it degrades gracefully: the brain reads `0`, which typically already means "point-blank hit."
