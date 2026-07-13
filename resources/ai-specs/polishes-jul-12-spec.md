# Polish pass spec — July 12, 2026

Agreed via grilling session. Five polish items, in implementation order
(visuals first per Richard's call; the only hard constraint is that
constants tuning comes after the radar robots exist, so playtests
reflect real battles). One commit per item.

## Implementation order

1. Damage rendering rework (item 1)
2. Shell explosion rework (item 3)
3. Death polish: shake + scorch + burst beef-up (item 4)
4. Radar robots (item 5)
5. Contact-damage tuning via playtest (item 2)

Verify each visual slice in the real app via the run-robotwar skill;
use the state-injection pattern to drive hard-to-reach damage states
headlessly.

## 1. Damage rendering (canvas.cljs)

**Remove color desaturation.** Delete the `damage-color`
saturation-scaling behavior. Reasons: hurt robots' colors converged on
each other, diverged from the legend swatches, and got boring.

**Replace the continuous health cue with a radial darkening gradient.**
Body fill becomes a radial gradient centered on the body: center is
always the full palette color (exactly matching the legend swatch),
darkening toward the silhouette edge as damage accumulates. At full
health: flat color, no gradient. Edge darkness scales with damage lost,
**hard-capped at ~35% darker** — a soft secondary cue; legibility comes
mostly from marks. Applies to all three body shapes.

**Hit flash unchanged** — robot still draws solid white on any frame
its damage changed.

**Damage marks become an escalating mix, upgrading in place.**
- Cadence unchanged: 1 mark per 20 damage lost, max 5. Mark geometry
  stays a pure function of (robot idx, mark number) — positions never
  re-roll (§4.4).
- All marks render in the style of the robot's *current* tier
  ("upgrade in place" — dashes literally become cracks at the same
  seed positions):
  - **Tier 1 (1 mark):** small dents — short dark dashes, a beefier
    version of today's marks.
  - **Tier 2 (2–3 marks):** jagged cracks — dark polylines of 3–5
    segments radiating from the seed points.
  - **Tier 3 (4–5 marks):** cracks plus dark semi-transparent blotches
    and notches bitten out of the silhouette edge.
- Marks stay clipped to the body path; notches modify the silhouette.
- **Watch in playtest:** tier crossings restyle every existing mark at
  once; if that reads as a glitchy re-roll, revisit (accumulate-style
  was the alternative).

## 2. Contact-damage tuning (constants.cljc)

- First guess: **MAX-WALL-DAMAGE 15 → 30, MAX-COLLISION-DAMAGE
  25 → 50**. Quadratic falloff stays.
- Pacing goal is *feel*, judged by watching battles between the new
  radar robots. No numeric battle-length target.
- **Designated fallback** (agreed, not to be re-litigated): if bumps
  still feel free after doubling, switch `wall-damage` /
  `collision-damage` in robot.cljc from quadratic to linear falloff
  and retune the maxima down.
- Blast damage (MAX-BLAST-DAMAGE / BLAST-RADIUS) untouched.
- Check `robot_test` for assertions pinned to the old constants.

## 3. Shell explosion (canvas.cljs)

Rebuild `explode-shell` as a wall-clock-timed presentation animation
(same pattern as `death-animations`; fast-forward must not shrink it).

- **Lifetime ~500 ms** (matches the death ring phase).
- **Warm fireball gradient disc:** white-hot core → yellow/orange →
  fully transparent rim. No hard edge anywhere.
- **Motion:** disc grows from small to full radius over roughly the
  first third, then fades out in place.
- **Shockwave ring:** a thin expanding ring that outruns the disc —
  same visual language as the death animation's ring.
- **Blast honesty:** the disc's zero-alpha edge peaks exactly at
  BLAST-RADIUS (21 m) so players can learn the splash zone. The ring
  is a decorative transient and may travel past 21 m.
- Spawn on the same previous-vs-current shell transition that
  currently triggers the one-frame disc.

## 4. Death polish (canvas.cljs / app.cljs)

- **Screen shake, canvas only:** translate the canvas context a few px
  with decay over ~300 ms on each robot death. Legend and page stay
  still. No shake on shell explosions.
- **Scorch marks: deaths only, permanent.** Dark mottled blotch
  (radial gradient + a few irregular satellite blobs) at each death
  site, deterministic per robot idx, drawn beneath robots, persisting
  until the battle ends / resets. Max 4 per battle.
- **Burst beef-up (modest):** ~50 particles (from 30), total duration
  ~1.2 s (from 0.9), add a second ring. Same structure otherwise.

## 5. Radar robots (public/programs/)

Six new .rw programs — basic and target-leading variants of three
strategies:

- **Sweeper sniper:** stationary; sweeps RADAR in a loop; on a
  negative reading sets AIM to the radar bearing and fires SHOT with
  the reported |distance|.
- **Corner camper:** drives to a corner first (back protected, only
  ~90° to sweep), then snipes.
- **Hunter:** on radar contact, moves toward the target while
  re-pinging; fires at close range where splash is hard to dodge.
- **Leading variants:** ping twice, estimate drift from the bearing
  change, offset AIM accordingly. Crude is fine — the point is to
  learn whether leading matters at SHELL-SPEED 25 m/s.

**Manifest (programs-live.json):** prune the trivial demos `speedy`
and `shooter`; keep `mover`, `left-shooter`, `top-shooter` as fodder
plus the six new programs.

## Decisions log (who picked what)

Every open question was resolved jointly; notable divergences from
Claude's recommendations, accepted deliberately:

- Escalating mix over cracks-only (scope); upgrade-in-place over
  accumulate (watch for re-roll feel).
- Raise maxima over linear falloff (fallback designated instead).
- All-three leading variants over sweeper-only (max playtest data).
- Visuals-first work order (constraint preserved: tuning still last).
