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

- **Detection: circle-circle.** Two robots collide when the distance between their centers is `< 2 × ROBOT-RADIUS`. Replace the existing axis-by-axis square logic in `robot.cljc:76-146`.
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

### Step 4 — Wall blocking + damage

**Files:** `robot.cljc`, `constants.cljc`

- Add per-robot wall-touching state: `:touching-walls #{}` (a set of `:left`, `:right`, `:top`, `:bottom`).
- In `move-robot`, after computing `new-pos-x`/`new-pos-y`:
  - Detect which walls the new position would cross (`new-pos-x < ROBOT-RADIUS` → `:left`, etc).
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

**Files:** `register.cljc`, `physics.cljc` (probably)

- Define `RadarRegister` (currently stubbed as TODO at `register.cljc:164`).
- Implement `IReadRegister`:
  - Get the firing robot's position and the value previously written to RADAR (the direction in degrees).
  - Construct a ray from robot center along that direction.
  - For each other *alive* robot, compute the closest-point distance from the ray to that robot's center. If `<= ROBOT-RADIUS`, the ray hits — record the distance from firing-robot center to the entry point of the disc.
  - Compute the wall-intersection distance: where does the ray exit the arena `[0, ROBOT-RANGE-X] × [0, ROBOT-RANGE-Y]`?
  - If any robot hit: return `-min_hit_distance`. Else: return `+wall_distance`.
- Implement `IWriteRegister`: store the direction value (the AIM-like angle). Behavior: writing the direction is what "pings" the radar; the next read returns the result. Match the convention of the existing `AimRegister`.

**TACTICAL:** the exact "distance to disc-entry" math has a couple of acceptable formulations (closest-point-on-ray, or solve the quadratic for the intersection). Pick one — both are fine for game purposes. Document the choice.

**TACTICAL:** decide where to add the ray-vs-disc and ray-vs-wall helpers. Probably `physics.cljc` to keep them pure and testable.

**Tests:** unit-test the geometry: ray hitting one robot returns negative distance. Ray hitting two returns negative distance to the closer one. Ray hitting no robots returns positive distance to wall. Self-detection excluded. Dead robots excluded.

### Step 7 — Frontend: victory overlay + restart UX + alive-only rendering

**Files:** `canvas.cljs`, `app.cljs`

- **Render only alive robots** in `canvas/animate-world`. Skip the loop body for any robot where `:alive?` is false.
- **Victory overlay** drawn on the canvas:
  - Triggered when `world.result` becomes non-nil.
  - Use the project's existing `Data_70_LET.ttf` font (already loaded — see `public/fonts/`).
  - Centered text. Winner case: program name in winner's color. Tie case: "TIE" in white.
- **Restart UX** in `app.cljs`:
  - On canvas click (or Enter keypress) while a victory overlay is showing: reverse the start-transition. Set canvas opacity to 0, restore the instruction-box height (to whatever its original CSS value was — store it before collapsing, or use CSS class toggling instead).
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

`robot.cljc:101-119` has `(dec damage)` in two places as the placeholder collision damage. Step 5 removes both. Don't leave one behind. The README's Category 1 #5 explicitly calls this out.

### 5.3 The "if false" death guard

`robot.cljc:155` has `(if false ...)` with a comment `replace this real damage line when we get robot death implemented correctly: (if (<= (:damage robot) 0)`. Step 3 changes this to `(if (not (:alive? robot)) ...)`. Be careful not to apply the literal commented version — we want the `:alive?` flag check, not a direct damage check, because the dead-robot bookkeeping happens upstream in `tick-combined-world`.

### 5.4 Self vs. other collision indexing

In Step 5, the `:colliding-with` set on each robot stores other robots' `:idx` values. When a robot dies and is skipped, make sure to also clear its `:colliding-with` set so a future revive (not in scope, but hygienic) wouldn't false-positive.

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
