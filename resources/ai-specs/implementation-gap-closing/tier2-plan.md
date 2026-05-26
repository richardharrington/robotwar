# RobotWar Tier 2 Polish Plan

The "minimally fun gameplay loop" is Tier 1's job. Tier 2 is the polish that
sits on top of it — proper audio infrastructure, sound effects for the events
Tier 1 fires, a more legible damage state on robots, per-robot visual identity,
a real death animation, and a styled victory screen.

Use this as the source of truth for design decisions. Flagged **TACTICAL**
items are deliberately left to the implementer to decide in context. Flagged
**TO DECIDE** items are open questions the executor should answer (often with a
recommendation attached) before or during the relevant slice.

---

## 1. Project context

RobotWar is a reverse-engineered Clojure/ClojureScript implementation of Silas
Warner's 1981 Apple II game. Engine code lives in `src/main/robotwar/*.cljc`
(shared between JVM and CLJS); the browser frontend is `src/main/robotwar/
*.cljs`. The current audio path is a small pool of `HTMLAudioElement`s in
`src/main/robotwar/app.cljs:49-62`; the renderer is `src/main/robotwar/
canvas.cljs`.

Background reading before starting work:
- The "Gaps in this implementation" section of `README.md`.
- The Tier 1 plan (sibling file in this directory) for the engine-side work
  these polish slices ride on top of.
- The original manual at `resources/manual.txt` (especially the "Option 4"
  sound toggle reference at line 52, and the death/victory language scattered
  through the early pages).
- The refactor spec at `resources/ai-specs/refactor_to_cljs/REFACTOR_SPEC.md`
  for the structural style this plan mirrors.

---

## 2. Prerequisites: Tier 1

Tier 2 assumes Tier 1 has landed. In particular it assumes the following
already exist in the engine and state:

- Shells apply damage on explosion (radius + falloff).
- Walls block robot motion and apply damage on impact.
- Robot-on-robot collisions use circle-circle detection and apply
  angle-scaled damage.
- Robots can die: an `:alive?` flag (or equivalent) drops to `false` once
  damage hits zero; dead robots are skipped by tick/render/collision/victory
  checks.
- A victory-detection step fires once one (or zero) robots remain alive.
- The arena enforces a 2–5 robot count invariant at battle start.
- A simple canvas-text victory overlay exists ("ECHO WINS" / "TIE" in the
  winner's color, centered).
- A "restart" flow exists that reverses the start-transition to restore the
  input form.

Tier 2 does **not** revisit any of this. It only adds the polish layer on top.
If a Tier 2 slice needs an engine hook that Tier 1 didn't add (e.g. an explicit
"this robot died on this tick" event), the slice may add that hook, but should
add it minimally and not re-litigate Tier 1's design.

## 3. Out of scope

Out of scope for Tier 2 (these belong in later tiers or separate projects):

- Scoring system + multi-battle matches.
- Assembler error catalogue, number-range validation, program-length cap.
- Recursion prevention.
- Test bench UI.
- AIM-aligned shell origin.
- Revisiting `ROBOT-RADIUS` (currently 7m) or the brain-tick ratio
  (currently 1 obj-code instruction per world tick at 30Hz). Both are
  deliberate balance knobs flagged for a later play-testing pass, not for
  Tier 2. New constants introduced in Tier 2 must respect the absolute-unit
  rule in §4.3 so they survive a future `ROBOT-RADIUS` retune.

The sound on/off toggle from the manual's "Option 4" (README Category 1 #14)
sits in a grey area. It is small, it is adjacent to the sound work in Slice B,
and the manual specifically calls for it. We slot it into Slice B rather than
defer it.

---

## 4. Cross-cutting decisions

These apply across multiple slices.

### 4.1 No new dependencies
The slices below are achievable with the existing toolchain: plain CLJS,
Web Audio API (a browser standard, no library), and the existing Canvas 2D
context. No `howler.js`, no particle library, no animation framework. If a
slice feels like it wants a dependency, surface that as a TACTICAL note for
discussion rather than just adding one.

### 4.2 Engine vs. presentation split
All Tier 2 work is presentation-layer. Engine code in `.cljc` files should
not change for Tier 2 unless a slice genuinely needs a new event/flag the
engine can expose cheaply (e.g. an `:exploded-this-tick?` marker on a shell
to drive both SFX and visual effects deterministically). Keep any such hooks
small, named clearly, and confined to a single namespace edit.

### 4.3 New constants live in absolute units
New constants (blast radius display size, particle speeds, damage-mark
spacing, etc.) should be expressed in absolute meters or pixels, **not** as
multiples of `ROBOT-RADIUS`. Background: `ROBOT-RADIUS` is currently 7m,
which is a deliberate playability choice that overrides the manual's "1.5
meter square chassis" spec — the original 1981 game itself rendered robots
at roughly that size on screen. The radius may be retuned (probably toward
4-5m) in a later balance pass. Expressing presentation constants in absolute
units keeps them stable across any such retune.

### 4.4 Determinism where it's free
Where a presentation choice is naturally random (particle velocities, damage
mark positions), seed it from a stable source (e.g. robot index + tick count,
or robot index + damage value) rather than calling `Math.random` each frame.
This keeps replays and screenshots reproducible and prevents distracting
sparkle on otherwise-static state. See per-slice TACTICAL notes for details.

### 4.5 Recommended slice order
A is a prerequisite for B (SFX should use the new audio system from day one).
C and D both edit `canvas.cljs` and the robot render path, so they ship
together. E follows once D's per-robot rendering and B's death sound exist.
F is independent and lands last.

```
A → B → (C + D) → E → F
```

Slices may be combined or split for tactical reasons, but do not reorder
A↔B or move E before B.

---

## 5. Slice-by-slice plan

### Slice A — Web Audio API migration (LOCKED)

**Goal:** Replace the `HTMLAudioElement` pool in `app.cljs:49-62` with a
Web Audio API pipeline. Pure refactor — same audible behavior, new internals.

**Why now:** B adds three or four new sound effects. Shipping them on the old
pooled-`Audio` system would mean migrating them again immediately. Doing the
audio infrastructure first lets B target the new API directly.

**Decisions (locked):**
- Use the browser's native Web Audio API directly. No `howler.js`, no other
  dependency.
- One `AudioContext` per page, created lazily on first user gesture (browsers
  require this — autoplay policy).
- Each sound file is `fetch`ed once at startup, decoded once into an
  `AudioBuffer`, and cached.
- Each playback creates a fresh `AudioBufferSourceNode`, connects it to the
  context destination (optionally through a master `GainNode`), and calls
  `.start()`. The node is cheap, GC's itself when finished, and gives
  unlimited polyphony for free.
- The existing `audio/trprsht1.ogg` shell-fire sound stays. It just becomes
  an `AudioBuffer` in the new system instead of 40 pooled `<audio>` elements.

**Shape of the work:**
1. New namespace, e.g. `robotwar.audio`, that owns the `AudioContext`,
   the buffer cache, and the play function. TACTICAL: file location — likely
   `src/main/robotwar/audio.cljs` alongside `canvas.cljs` and `app.cljs`.
2. `init-audio!` lazily constructs the context, kicks off `fetch` + `decodeAudioData`
   for each sound, stores resulting buffers in an atom keyed by sound id.
3. `play! [sound-id]` looks up the buffer; if missing (still loading, decode
   failed, or unknown id) it no-ops silently. Otherwise it builds a source
   node and calls `start()`.
4. Replace `init-sounds!` / `play-shell-release!` in `app.cljs` with calls
   into the new namespace.
5. Delete the pooled `Audio` code and the `:shell-release` / `:idx` keys
   from `sound-state`.

**TO DECIDE — autoplay-policy bootstrap.** Most browsers block `AudioContext`
construction (or leave it suspended) until a user gesture. The existing pool
doesn't hit this because `<audio>.play()` from a keydown handler already
counts as a gesture. With Web Audio we need to either:
- (a) construct the context inside the first keydown / battle-start handler,
  or
- (b) construct it eagerly and `await context.resume()` from the first
  gesture.
Recommendation: (a). The first user interaction is the Enter keypress that
starts a battle; constructing the context there is simple and avoids a
suspended-context state machine. Document this in the namespace docstring.

**TO DECIDE — preload timing.** Buffer fetch/decode could happen at page
load, on first battle start, or lazily per sound. Recommendation: kick off
all `fetch`es at page load (background, ignore failures), but make `play!`
tolerant of "buffer not yet ready" (just no-op). This gets sounds ready
before the player can possibly trigger them, without blocking the UI.

**TACTICAL:**
- Sound-id naming convention (`:shell-fire`, `:shell-explosion`, `:wall-crash`,
  `:robot-collision`, `:robot-death`) — pick something readable and stable.
- Whether the cache atom lives in `audio.cljs` or threads through the main
  `state` atom in `app.cljs`. Recommendation: local atom in `audio.cljs`;
  audio state is self-contained and doesn't interact with game state.
- Format selection (mp3 vs ogg). The existing code uses `canPlayType`;
  preserve that behavior with the new API or just always prefer ogg and let
  the browser fall back. Recommendation: keep the existing
  ogg-with-mp3-fallback check.

**Verification:** Start a battle; shells still produce the existing blaster
sound; no console errors; rapid-fire shooting overlaps cleanly (the original
pool was 40 deep — Web Audio is effectively unbounded, so this should
trivially pass).

---

### Slice B — Sound effects (PARTIALLY LOCKED)

**Goal:** Add SFX for the four "something happened" events Tier 1 introduces:
shell explosion, robot-robot collision, wall crash, and robot death. Add a
sound on/off toggle in the UI.

**Decisions (locked):**
- New SFX: `:shell-explosion`, `:robot-collision`, `:wall-crash`, `:robot-death`.
- `public/audio/concuss5.{mp3,ogg}` is the shell-explosion sound (it's
  already in the repo, parked for this slice).
- All new SFX go through the Slice A audio system. No `<audio>` elements
  reintroduced.
- A sound on/off toggle ships as part of this slice (manual's "Option 4",
  README Category 1 #14).

**TO DECIDE — source of the other three SFX.** Two viable approaches:
- (a) CC0 / open-license files from freesound.org or opengameart.org. Quick
  to source, characterful.
- (b) Procedural synthesis via Web Audio: a short filtered-noise burst plus
  a low sine for thuds, harsher for the wall crash.
Recommendation: hybrid. Use a single short synthesized "thud" function for
`:robot-collision` and `:wall-crash` (with different filter cutoffs / pitches
so they're distinguishable but obviously cousins). Use a sourced file for
`:robot-death` because the death animation deserves a real, dramatic boom
that synthesis won't easily match. Re-using `concuss5` for both shell
explosion and robot death is also viable but reduces distinctness; prefer a
separate death sound.

**TO DECIDE — volume mixing.** Without any mixing, an explosion will sound
the same loudness as a robot-collision thud, which is wrong. Options:
- (a) A single master `GainNode` between source nodes and destination, with
  per-sound `gainNode.gain.value` set at play time from a static volume map.
- (b) Pre-normalize the audio files in an editor and skip per-sound gain
  entirely.
Recommendation: (a). It's a few extra lines and lets us re-tune mix in code
without touching assets. Define a `sound-volumes` map alongside the buffer
cache.

**TO DECIDE — death-sound asset selection.** If we go with a sourced file,
the implementer picks something CC0 from freesound.org / opengameart.org and
commits it to `public/audio/` next to the existing sounds. Recommendation:
prefer a 1–2 second mechanical/metallic explosion (not a generic "boom") to
match the robot aesthetic. The implementer makes the call; surface the choice
in the commit message.

**Decisions (locked) — sound on/off toggle:**
- Single boolean in the audio module's state, defaulting to `true`.
- `play!` short-circuits when the toggle is off (no source node created).
- UI: a small button or icon somewhere unobtrusive (HTML, not canvas).

**TO DECIDE — toggle UI placement and styling.** It needs to be visible
during battle but not in the way. Options: a small speaker icon in a corner
of the canvas overlay; a button next to the program-name input that persists
during the battle; a keyboard shortcut (matching the manual's literal "press
4") plus a small status indicator. Recommendation: a small button overlaid
top-right of the canvas, plus a "4" keypress shortcut that toggles the same
state (nod to the manual without forcing the keyboard-only path). Persist the
state to `localStorage` so the user's choice survives reloads.

**Engine hook needed:** Slice B needs to know when each event fires. Most of
these are already implied by Tier 1's state diffs:
- Shell explosion → diff `:shells` previous→current, fire SFX for each shell
  that exploded this tick (canvas.cljs already does the visual version of
  this on lines 86-89).
- Robot collision, wall crash, robot death → these can be detected from
  `:damage` changes if Tier 1 doesn't tag the cause, but tagging the cause is
  cheaper and more correct. **TACTICAL:** if Tier 1 doesn't already attach
  `:last-damage-cause` (or similar) to robots, add it as a one-line change
  during this slice.

**Verification:** Start a battle. Shells still fire with the blaster sound;
shells exploding produce the concuss sound; robots bumping each other or a
wall produce the thud; a robot dying produces the death sound. Toggle off,
nothing plays. Toggle on, sounds resume. Reload page — toggle state persists.

---

### Slice C — Visual damage representation (LOCKED)

**Goal:** Make robot damage visible from across the arena without reading
numbers. Two combined cues: body color desaturates as damage drops; small
procedural marks ("cracks", "dents") accumulate on the robot body.

**Decisions (locked):**
- Color desaturation tracks `:damage` (which starts at 100 and decreases).
  At 100 the robot displays its full saturated color from `robot-colors`; at
  0 it's grey. Linear or curved interpolation — TACTICAL.
- Procedural damage marks (small lines or dots, drawn in a darker shade) are
  overlaid on the robot body. More damage → more marks.
- The existing "hit flash" in `canvas.cljs:91-93` (robot drawn white on the
  frame its damage decreased) is **preserved**. Desaturation is a *state*
  cue; hit flash is an *event* cue. They compose: a wounded robot still
  flashes white on each new hit.

**Why this shape:**
- Color desaturation reads at any distance without effort.
- Marks add detail when you look closely.
- Together they make damage legible without numbers or HUD bars.

**TO DECIDE — discrete tiers vs. continuous interpolation.** Three options:
- (a) Continuous: damage value maps directly to a saturation value via a
  function. Smooth, but the change is too subtle to notice frame-to-frame.
- (b) 3–4 discrete tiers (e.g. 100, 75, 50, 25 → tier 0–3). Visible "stage
  drops" become meaningful events.
- (c) Continuous color, discrete mark count.
Recommendation: (c). Lets the desaturation be smooth (no jarring snaps) while
the marks have a clear "I just took a noticeable hit" feel. Mark count steps
at e.g. every 20 points of damage lost.

**TO DECIDE — mark stability over time.** A damage mark added at damage=80
should still be in the same place at damage=40, not re-rolled each frame.
Random-per-frame is unpleasant sparkle. Two ways to achieve stability:
- (a) Store mark positions on the robot map when damage drops.
- (b) Re-derive mark positions deterministically from `(robot.idx, damage)`
  using a simple PRNG / hash, recomputed each frame but stable for a given
  damage value.
Recommendation: (b). No engine-state change required, and it's free: the
canvas function takes `(idx, damage)` and produces the same marks every time
for the same inputs. See §4.4.

**TO DECIDE — mark style.** Short dark line segments, small dark dots, a
mix? Should they be inside the robot silhouette only, or allowed to overhang
the edges? Recommendation: short dark line segments (1–2 pixels wide,
3–5 pixels long), rotated at fixed-per-mark angles, clipped to the robot
silhouette. Easy to draw, reads as "damage" rather than "decoration".

**Shape of the work:**
1. Add a saturation helper in `canvas.cljs` (hex → HSL → desaturated → hex,
   or a precomputed per-tier palette). TACTICAL: which color space / format.
2. Refactor `draw-robot` to take a damage value (currently it just takes
   color), compute the per-frame display color, and draw the marks after the
   body but before the gun.
3. Preserve the existing hit-flash branch in the `:animate-world` function.
4. Coordinate with Slice D — both touch `draw-robot`. Ship them together if
   it reduces churn.

**Verification:** Watch a battle. Robots noticeably grey out as they take
damage. Marks accumulate visibly after each hit and don't shimmer. The
hit-flash still fires on each new hit, even on heavily damaged robots.

---

### Slice D — Per-robot shape variety (LOCKED)

**Goal:** Different robots render with different body shapes, à la the
original 1981 game (verified from screenshots — see
`project_robot_shape_variety.md`).

**Decisions (locked):**
- Shape selected by `robot.idx mod N` for a small palette. With at most 5
  robots, N = 3 or 4 is plenty (squares, circles, plus maybe diamonds or
  hexagons).
- **Collision detection is unaffected.** Tier 1 already made collision
  circle-circle for all robots regardless of visual shape. Visual shape and
  collision shape are independent. Minor visual disagreement at corners of
  non-circle renders (~3m, ~1% of arena width) is acceptable — confirmed.
- Per-robot color from `robot-colors` is unchanged.

**TO DECIDE — shape palette.** Smaller is better; we want robots to be
distinguishable at a glance, not novel. Recommendation: `[:square :circle
:diamond]`. Three shapes are enough at 5 robots; if two robots share a shape
they still differ by color.

**TO DECIDE — gun rendering across shapes.** The gun is currently a line
drawn from the robot center; this works for any body shape with no
modification. The small stroked circle near the body center
(`canvas.cljs:64`) might want different sizing per shape — TACTICAL.

**Shape of the work:**
1. Add a `robot-shapes` vector parallel to `robot-colors`.
2. Add `fill-circle-shape` and `fill-diamond-shape` helpers (square is the
   existing `fill-square`).
3. Dispatch on `(nth robot-shapes (mod (:idx robot) N))` inside `draw-robot`.
4. Confirm damage marks (from Slice C) still look right on each shape;
   adjust clipping if needed. Coordinate ship order with Slice C.

**Verification:** Start a 5-robot battle. Robots have distinguishable
shapes. Collisions still feel right (they're still circle-circle under the
hood). Damage marks still look right on each shape.

---

### Slice E — Particle-based robot death animation (LOCKED)

**Goal:** Replace Tier 1's "robot stops rendering" placeholder with a
spectacular death animation: expanding ring(s), color flash, and sparks /
debris particles fanning outward.

**Decisions (locked):**
- Reuse `explode-shell`'s ring shape (`canvas.cljs:71-75`) as the inner
  ring element of the animation.
- Particle system: each particle has position, velocity, lifetime, and
  alpha. Particles spawn at the dying robot's last position with random
  outward velocities, drift, decelerate (optional), and fade.
- The death sound from Slice B fires once at the start of the animation.
- Tier 1's `:alive?` flag stays the authoritative "is this robot still in
  the battle" signal. The animation lives entirely in presentation state.

**TO DECIDE — animation state ownership.** Particles are presentation, not
engine. Options:
- (a) A separate atom in `canvas.cljs` (or a new `animations.cljs`) that
  tracks live particle sets keyed by robot index.
- (b) Attach to the main app `state` atom.
- (c) Bolt onto the world map (engine state).
Recommendation: (a). Keeps engine pure and isolates particle state next to
the code that draws it. The animation loop in `app.cljs` calls a
`canvas/tick-animations!` (or similar) each frame, after the world tick.

**TO DECIDE — when to spawn.** The animation needs to fire on the *transition*
from alive→dead, not every frame the robot is dead. The animation loop in
`app.cljs` already diffs previous-world vs current-world for the shell sound
trigger; do the same for robots: when a robot's `:alive?` flips, spawn a
particle set for its index and play the death sound.

**TO DECIDE — particle count and lifetime.** Affects perceived weight of the
animation. Too few = unimpressive; too many = chaotic and slow. Starting
recommendation: 20–40 particles per death, ~800ms lifetime. Tune in place.

**TO DECIDE — body-to-particles transition.** As written, when `:alive?` flips
false the robot body vanishes *immediately* on the same frame the particles
spawn. To the eye this can read less like "the robot exploded" and more like
"the robot teleported and someone independently threw sparks at the spot" —
the body and the particles aren't visually connected. Three options to
bridge this:
- (a) Vanish immediately. Simplest, possibly abrupt.
- (b) Brief body fade + slight scale-up over ~100–200ms before vanishing.
  The body acts as the seed of the explosion; particles fly out from a
  still-visible center for the first beat. Matches the language of the
  existing `explode-shell` which already expands-and-fades for shells
  (`canvas.cljs:71-75`).
- (c) Single-frame white-flash of the robot's silhouette on the death tick
  (matching the existing hit-flash language in `canvas.cljs:91-93`) before
  vanishing. Cheap; reads as "violent flash + particles."
Recommendation: mock up (a) first and judge whether the disconnection
actually bothers the eye. If it does, prefer (b) — it composes naturally
with the existing shell visual language. (c) is a fallback if (b) feels too
slow.

**TO DECIDE — bells and whistles.** Screen shake on the arena and lingering
scorch marks at the death position are explicitly optional extras on top of
the particle/ring/flash core. Recommendation: ship without them in the first
pass; add only if the base animation feels underwhelming. Each is its own
follow-up half-slice.

**Shape of the work:**
1. Define a particle record / map shape. TACTICAL: shape and which fields
   (`{:x :y :vx :vy :t :max-t :color}` is a starting point).
2. Add a particles atom (or equivalent) in `canvas.cljs`.
3. Add `spawn-death-particles! [robot-idx pos-x pos-y color]` and
   `tick-particles! [elapsed-ms]` and a draw loop integrated into
   `animate-world`.
4. In `app.cljs`, detect alive→dead transitions in the per-frame world diff
   and call `spawn-death-particles!` + the death sound.
5. Replace the "stop rendering dead robots" placeholder behavior with the
   new animation. Whether the robot body is rendered at all during the
   animation (and how) is the subject of the "body-to-particles transition"
   TO DECIDE above. Once the animation is over the robot doesn't render.
6. Once particles for a robot are all expired, the canvas is clean for that
   index again.

**Verification:** Start a battle. When a robot dies, a satisfying burst of
particles fans out from its last position, the inner ring expands and fades,
and the death sound plays. The animation completes within ~1 second and
doesn't leave debris on screen.

---

### Slice F — DOM-overlay victory display (LOCKED)

**Goal:** Replace Tier 1's canvas-text "ECHO WINS" overlay with a styled HTML
overlay positioned over the canvas. Should feel like a curtain coming down
on the match, not text scribbled in the corner.

**Decisions (locked):**
- HTML/CSS overlay positioned over the canvas (e.g. absolutely-positioned
  div). Not drawn on the canvas.
- Surfaces at minimum: winner program name and color swatch in the winner's
  color. (Or "TIE" — when zero robots remain, see Tier 1.)
- Restart UX from Tier 1 (reverse the start-transition to restore the
  input form) is preserved. This slice replaces only the visual presentation
  of the win state, not the flow.
- The Tier 1 canvas-text overlay is removed in this slice (the new overlay
  supersedes it).

**TO DECIDE — basic stats to surface.** Beyond the winner name, what else?
Candidates: ticks survived, total damage dealt, kill count (= number of
opponents the winner damaged-to-death), shell count fired. Recommendation:
ticks survived + kill count. They're trivially derivable from world history
(if we keep one) or by tracking a kill counter on each robot. Damage dealt
requires more bookkeeping; defer unless cheap.

**TO DECIDE — visual style.** Two reasonable directions:
- (a) Terminal-vintage, matching the rest of the page's aesthetic — green
  monospace text on dark, maybe a CRT scanline effect.
- (b) Modern minimal — clean type, big winner name, simple layout.
Recommendation: (a). The existing site has retro chrome (`fonts/`, the
instruction-box animation, etc.); a modern victory screen would clash.

**TO DECIDE — per-robot leaderboard.** A small table listing all robots
ordered by performance (winner first, then by ticks survived). More
interesting; takes more space; matters more if scoring + multi-battle
matches are added later. Recommendation: include a minimal leaderboard
(robot name, color swatch, alive/dead). Skip per-robot stats columns until
scoring lands.

**TO DECIDE — engine hooks for stats.** If we want "ticks survived" and
"kill count", we need:
- Tick count at death → either record `:died-at-tick` on the robot when it
  dies, or compute from world history.
- Kill count → bump a counter on the killer when a death is attributed.
The Tier 1 victory-detection step already iterates robots and counts the
living — extending it to record a `:died-at-tick` is a one-line change.
Kill attribution needs whoever fired the killing shell (or caused the
killing collision) to be known. Recommendation: track `:died-at-tick` (free).
Defer kill attribution to a later slice if it would balloon scope.

**TACTICAL:**
- DOM structure: a single `div.victory-overlay` with semantic children.
- CSS: prefer modifying existing stylesheets in `public/css/` over inlining;
  match the existing font/color palette.
- Animation in: fade-in / slide-in. Keep it brief (~300ms).
- Where the "restart" affordance lives: a button in the overlay vs. relying
  purely on the existing restart flow. Recommendation: a "Play again" button
  in the overlay that triggers the same restart path Tier 1 wired up.

**Verification:** Win a battle (or tie). The overlay appears with the
winner's name in the correct color, the small leaderboard, and a "Play
again" button. Clicking "Play again" returns to the input form (or however
Tier 1 wired restart). Try a tie scenario and confirm it displays
appropriately.

---

## 6. Consolidated list of decisions for the executor

For convenience, every **TO DECIDE** and **TACTICAL** flag in one place.

### Open questions (TO DECIDE)
1. Slice A — autoplay-policy bootstrap timing (rec: build context on first
   gesture).
2. Slice A — sound preload timing (rec: page-load fetch, tolerant `play!`).
3. Slice B — source of `:robot-collision` / `:wall-crash` / `:robot-death`
   SFX (rec: synthesize the thuds, source-file the death).
4. Slice B — volume mixing approach (rec: per-sound `GainNode` from a
   static map).
5. Slice B — death-sound asset selection (rec: 1–2s mechanical/metallic
   explosion).
6. Slice B — sound on/off toggle UI placement (rec: top-right canvas
   overlay + `4` keypress, `localStorage` persistence).
7. Slice C — discrete-tier vs. continuous damage visualization (rec:
   continuous color, discrete marks).
8. Slice C — mark stability over time (rec: deterministic from
   `(idx, damage)`).
9. Slice C — mark visual style (rec: short dark line segments, clipped to
   silhouette).
10. Slice D — shape palette (rec: `[:square :circle :diamond]`).
11. Slice D — per-shape gun-stub sizing (TACTICAL).
12. Slice E — animation state ownership (rec: separate atom in
    `canvas.cljs`).
13. Slice E — particle count and lifetime (rec: 20–40 particles, ~800ms;
    tune in place).
14. Slice E — body-to-particles transition (rec: try immediate-vanish first;
    fall back to brief body fade + scale-up, or a single-frame white-flash,
    if the disconnection between body and particles bothers the eye).
15. Slice E — bells and whistles (screen shake, scorch marks) — rec: defer.
16. Slice F — basic stats to surface (rec: ticks survived + kill count if
    cheap).
17. Slice F — visual style (rec: terminal-vintage).
18. Slice F — per-robot leaderboard inclusion (rec: minimal, no stats
    columns yet).
19. Slice F — engine hooks for stats (rec: track `:died-at-tick`, defer
    kill attribution).

### Implementer's choice (TACTICAL)
- Slice A — namespace file location, sound-id naming, cache-atom ownership,
  audio format selection mechanism.
- Slice B — engine `:last-damage-cause` (or equivalent) tagging if Tier 1
  didn't already do it.
- Slice C — color space / palette format for desaturation.
- Slice D — gun-stub sizing per shape.
- Slice E — particle record shape and fields.
- Slice F — DOM structure, CSS file edits, in-animation timing, restart
  affordance placement.

---

## 7. Verification at the end

When all six slices are done, the project should:

- Use Web Audio API for all sound playback; no `HTMLAudioElement` pool
  remains in `app.cljs`.
- Play distinct, mixed sounds on shell-fire, shell-explosion, robot-robot
  collision, wall-crash, and robot-death.
- Offer a sound on/off toggle in the UI that persists across reloads.
- Render robots in distinguishable shapes (per robot index) that grey out
  visibly as they take damage, accumulate stable damage marks, and still
  flash white on each new hit.
- Show a particle-based death animation accompanied by the death sound when
  a robot dies, with no lingering on-screen debris after the animation
  ends.
- Show a styled HTML victory overlay (not canvas text) at battle end, with
  winner name, color, a minimal leaderboard, and a "Play again" affordance
  that returns to the input form.
- Have no new runtime dependencies introduced by Tier 2.
- Have no engine behavior changes beyond minimal hooks (event tags,
  `:died-at-tick`) explicitly noted in the slice that added them.
- Still pass all existing Tier 1 tests (`clj -M:test` and the CLJS test
  build green).
