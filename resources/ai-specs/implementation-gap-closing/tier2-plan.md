# RobotWar Tier 2 Polish Plan

The "minimally fun gameplay loop" is Tier 1's job. Tier 2 is the polish that
sits on top of it — proper audio infrastructure, sound effects for the events
Tier 1 fires, a more legible damage state on robots, per-robot visual identity,
a real death animation, and a styled victory screen.

Use this as the source of truth for design decisions. All previously-open
design questions were resolved and locked with the project owner on
2026-07-11; nothing in this plan requires further human input. Flagged
**TACTICAL** items are deliberately left to the implementer to decide in
context — each carries a recommendation that is a sound default. Follow the
recommendation unless the code in front of you argues otherwise, and note
any deviation in the commit message.

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
slice feels like it wants a dependency, do not add one — solve it within the
existing toolchain and note the friction in the commit message.

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

**TACTICAL — autoplay-policy bootstrap.** Most browsers block `AudioContext`
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

**TACTICAL — preload timing.** Buffer fetch/decode could happen at page
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

**Resolved during implementation (see Slice A addendum, §8):** namespace is
`src/main/robotwar/audio.cljs`; sound id `:shell-fire`; cache atom is local
to `audio.cljs`; ogg-with-mp3-fallback `canPlayType` check preserved;
autoplay bootstrap is option (a) — the context is built in the program-input
Enter handler; preload fetches raw `ArrayBuffer`s at page load and decoding
is deferred until the context exists (a constraint discovered in
implementation: `decodeAudioData` requires a context, and it detaches its
input `ArrayBuffer`, so each fetch result is decoded exactly once). A master
`GainNode` is already in place for Slice B's mixing.

---

### Slice B — Sound effects (LOCKED)

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

**Decision (locked) — all three new SFX are synthesized.** No new audio
assets are added and nothing is downloaded; `:robot-collision`,
`:wall-crash`, and `:robot-death` are all generated procedurally via Web
Audio. Use a single short synthesized "thud" function for `:robot-collision`
and `:wall-crash` (with different filter cutoffs / pitches so they're
distinguishable but obviously cousins). `:robot-death` gets its own bigger
synthesis: longer (~1–2 seconds) and clearly weightier than the
shell-explosion file — e.g. a layered filtered-noise burst, a low sine
pitch-drop, and a decaying rumble — with a mechanical/metallic character to
match the robot aesthetic. TACTICAL: exact synthesis recipes and parameters,
and whether synthesized sounds are rendered once into cached `AudioBuffer`s
(via `OfflineAudioContext`) or built from live nodes at each play — tune by
ear until all three are distinct from each other and from `concuss5`.

**TACTICAL — volume mixing.** Without any mixing, an explosion will sound
the same loudness as a robot-collision thud, which is wrong. Options:
- (a) A single master `GainNode` between source nodes and destination, with
  per-sound `gainNode.gain.value` set at play time from a static volume map.
- (b) Pre-normalize the audio files in an editor and skip per-sound gain
  entirely.
Recommendation: (a). It's a few extra lines and lets us re-tune mix in code
without touching assets. Define a `sound-volumes` map alongside the buffer
cache.

**Decisions (locked) — sound on/off toggle:**
- Single boolean in the audio module's state, defaulting to `true`.
- `play!` short-circuits when the toggle is off (no source node created).
- UI: a small button or icon somewhere unobtrusive (HTML, not canvas).

**Decision (locked) — toggle UI placement.** A small speaker button overlaid
top-right of the canvas, plus a "4" keypress shortcut that toggles the same
state (nod to the manual's literal "press 4" without forcing the
keyboard-only path). It should be visible during battle but not in the way.
Persist the state to `localStorage` so the user's choice survives reloads.

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

**Decision (locked) — continuous color, discrete marks.** The damage value
maps continuously to a saturation value (smooth desaturation, no jarring
snaps), while the damage-mark count steps in discrete increments — one step
per ~20 points of damage lost — so a noticeable hit visibly adds marks and
carries a clear "I just took a real hit" feel.

**TACTICAL — mark stability over time.** A damage mark added at damage=80
should still be in the same place at damage=40, not re-rolled each frame.
Random-per-frame is unpleasant sparkle. Two ways to achieve stability:
- (a) Store mark positions on the robot map when damage drops.
- (b) Re-derive mark positions deterministically from `(robot.idx, damage)`
  using a simple PRNG / hash, recomputed each frame but stable for a given
  damage value.
Recommendation: (b). No engine-state change required, and it's free: the
canvas function takes `(idx, damage)` and produces the same marks every time
for the same inputs. See §4.4.

**TACTICAL — mark style.** Short dark line segments, small dark dots, a
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

**Decision (locked) — shape palette.** `[:square :circle :diamond]`, chosen
by `idx mod 3`. Three shapes are enough at 5 robots; if two robots share a
shape they still differ by color. We want robots distinguishable at a
glance, not novel.

**TACTICAL — gun rendering across shapes.** The gun is currently a line
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

**TACTICAL — animation state ownership.** Particles are presentation, not
engine. Options:
- (a) A separate atom in `canvas.cljs` (or a new `animations.cljs`) that
  tracks live particle sets keyed by robot index.
- (b) Attach to the main app `state` atom.
- (c) Bolt onto the world map (engine state).
Recommendation: (a). Keeps engine pure and isolates particle state next to
the code that draws it. The animation loop in `app.cljs` calls a
`canvas/tick-animations!` (or similar) each frame, after the world tick.

**Decision (locked) — when to spawn.** The animation fires on the *transition*
from alive→dead, not every frame the robot is dead. The animation loop in
`app.cljs` already diffs previous-world vs current-world for the shell sound
trigger; do the same for robots: when a robot's `:alive?` flips, spawn a
particle set for its index and play the death sound.

**TACTICAL — particle count and lifetime.** Affects perceived weight of the
animation. Too few = unimpressive; too many = chaotic and slow. Starting
recommendation: 20–40 particles per death, ~800ms lifetime. Tune in place.

**Decision (locked) — body-to-particles transition: the implementer owns the
visual judgment.** Background: when `:alive?` flips
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
Locked process: implement (a) first, run the app, and judge the result
yourself from screenshots (or captured frames around the death tick) — do
not ask a human. If the disconnection between body and particles reads
badly, upgrade to (b) — it composes naturally with the existing shell visual
language. (c) is a fallback if (b) feels too slow. State which option
shipped, and why, in the commit message.

**Decision (locked) — bells and whistles are deferred.** Screen shake on the
arena and lingering scorch marks at the death position are out of scope for
Tier 2. Ship the particle/ring/flash core only; each extra is its own
follow-up half-slice for a later pass.

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
   animation (and how) is governed by the "body-to-particles transition"
   decision above. Once the animation is over the robot doesn't render.
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

**Decision (locked) — stats to surface.** Ticks survived, alongside the
winner name and color swatch. Kill counts, damage dealt, and shell counts
are deferred — they need attribution bookkeeping that stays out of Tier 2.

**Decision (locked) — visual style: terminal-vintage.** Green monospace text
on dark, matching the rest of the page's aesthetic — the existing site has
retro chrome (`fonts/`, the instruction-box animation, etc.), so a modern
victory screen would clash. A subtle CRT touch (e.g. scanlines) is welcome
but optional — TACTICAL.

**Decision (locked) — per-robot leaderboard.** Include a minimal
leaderboard: robot name, color swatch, alive/dead status, ordered winner
first then by ticks survived. No per-robot stats columns until scoring
lands.

**Decision (locked) — engine hooks for stats.** Record `:died-at-tick` on
the robot when it dies. The Tier 1 victory-detection step already iterates
robots and counts the living — extending it to record `:died-at-tick` is a
one-line change. Kill attribution (knowing who fired the killing shell or
caused the killing collision) is deferred; do not add it in Tier 2.

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

## 6. Consolidated list of decisions

For convenience, every decision in one place. There are no open questions;
do not ask a human for input during implementation.

### Locked decisions (human-decided 2026-07-11 — do not re-open)
1. Slice A — Web Audio API directly, no dependencies; lazy `AudioContext`
   on first user gesture; fetch/decode-once buffer cache; fresh source node
   per playback.
2. Slice B — all three new SFX (`:robot-collision`, `:wall-crash`,
   `:robot-death`) are synthesized via Web Audio; no new audio assets, no
   downloads. Death sound is longer/weightier with a mechanical character.
3. Slice B — sound toggle: top-right speaker button over the canvas + `4`
   keypress shortcut, persisted to `localStorage`, default on.
4. Slice C — continuous color desaturation + discrete damage-mark steps
   (~every 20 damage lost); existing hit-flash preserved.
5. Slice D — shape palette `[:square :circle :diamond]` chosen by
   `idx mod 3`; collision stays circle-circle.
6. Slice E — spawn on the alive→dead transition detected in the per-frame
   world diff; death sound fires once at animation start.
7. Slice E — body-to-particles transition: implement immediate-vanish
   first; the implementer judges the result from screenshots and upgrades
   to body fade+scale-up (then single-frame white-flash) if the
   body/particle disconnect reads badly.
8. Slice E — screen shake and scorch marks deferred out of Tier 2.
9. Slice F — terminal-vintage visual style.
10. Slice F — stats: ticks survived; minimal leaderboard (name, swatch,
    alive/dead, winner first); `:died-at-tick` engine hook; kill
    attribution deferred.

### Implementer's choice (TACTICAL — recommendations attached in each slice)
- Slice A — autoplay bootstrap timing (rec: build context on first
  gesture), preload timing (rec: page-load fetch, tolerant `play!`),
  namespace file location, sound-id naming, cache-atom ownership, audio
  format selection mechanism.
- Slice B — volume mixing (rec: per-sound `GainNode` values from a static
  map), synthesis recipes/parameters and buffer-vs-live-node rendering,
  engine `:last-damage-cause` (or equivalent) tagging if Tier 1 didn't
  already do it.
- Slice C — mark stability (rec: derive deterministically from
  `(idx, damage)`), mark visual style (rec: short dark line segments
  clipped to silhouette), color space / palette format for desaturation.
- Slice D — gun-stub sizing per shape.
- Slice E — animation state ownership (rec: separate atom in
  `canvas.cljs`), particle count/lifetime (rec: 20–40 particles, ~800ms;
  tune in place), particle record shape and fields.
- Slice F — DOM structure, CSS file edits, in-animation timing, restart
  affordance placement, optional CRT touches.

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

---

## 8. Implementation addendums

One addendum per slice, appended as each slice lands.

### Slice A addendum (landed 2026-07-11)

Implemented as specified, with all TACTICAL items resolved per the
recommendations:

- New namespace `src/main/robotwar/audio.cljs` owns everything:
  a local `audio-state` atom (`:context`, `:master-gain`, `:raw-data`,
  `:buffers`), `preload!`, `ensure-audio!`, and `play!`. No audio state
  in the app `state` atom.
- **Autoplay bootstrap:** option (a). `app.cljs`'s
  `on-program-input-keydown` calls `audio/ensure-audio!` on every Enter
  press — the first such press is the battle-start gesture. Subsequent
  calls no-op.
- **Preload split into fetch + decode.** The plan's "fetch/decode at page
  load" had a wrinkle: `decodeAudioData` needs an `AudioContext`, which
  can't exist before the first gesture. So `preload!` (called from `init`)
  fetches raw `ArrayBuffer`s only; `ensure-audio!` decodes whatever has
  arrived when the context is created, and later-arriving fetches decode
  themselves on completion. `decodeAudioData` detaches its input buffer,
  so `decode!` removes the raw entry from the atom before decoding —
  each buffer is decoded exactly once.
- **Format selection:** kept the `canPlayType`-based ogg/mp3 check from
  the old pool code (via a throwaway `js/Audio.` used only for sniffing).
- **Sound ids:** `:shell-fire` for `audio/trprsht1`. The naming scheme
  from the plan (`:shell-explosion`, `:wall-crash`, `:robot-collision`,
  `:robot-death`) is reserved for Slice B.
- A master `GainNode` sits between source nodes and the destination from
  day one, so Slice B's volume mixing has its insertion point ready.
- Deleted: `sound-state` atom, `init-sounds!`, `play-shell-release!`,
  and the 40-element `<audio>` pool in `app.cljs`.
- Verified: full battle run via the `run-robotwar` driver with zero
  console errors; JVM + CLJS test suites green. (Audible behavior is
  unverifiable headless; the play path executes without errors.)
