# Findings from Tier 1 Steps 4–7 review (2026-07-11)

A code review of the four gap-closing commits (`2bf9836` Step 4, `65a15af` Step 5,
`4dfba97` Step 6, `19255cc` Step 7) against
[`implementation-gap-closing/tier1-plan.md`](./implementation-gap-closing/tier1-plan.md).

Overall verdict: the implementation is faithful to the spec. Formulas, constants,
first-contact tracking, per-axis wall state, separation-to-exactly-2R, mod-360 radar
writes, alive/self filtering, and the §5.7 tick ordering all check out. The deliberate
TACTICAL deviations (end-of-tick impact velocity per A.14, approaching-only collision
damage per A.20, disc-entry-point radar distance per A.25) are correctly documented in
the spec's addenda and are **not** defects.

## Already fixed (do not redo)

- **`scripts/test-all.sh` called the broken `clj` wrapper** (see addendum A.23) — it
  silently exited 0 without running any JVM tests on this machine, so `npm test` only
  ran the CLJS half. Fixed: the script now calls `clojure -M:test`. Verified: `npm test`
  runs both suites (80 JVM tests / 154 assertions, 11 CLJS tests / 62 assertions).
- **The spec's "glancing collision" test scenario was never actually written.** The old
  `collision-glancing-small-damage-test` in `src/test/robotwar/robot_test.clj` was a slow
  *head-on* collision with a comment falsely claiming a y offset. Fixed: renamed it to
  `collision-slow-head-on-small-damage-test`, and added a real
  `collision-glancing-angle-scaled-damage-test` (45° contact normal, moving robot vs.
  stationary → exactly `MAX-COLLISION-DAMAGE/8`, half the same-speed head-on damage).
  A compact version was added to the `collision-smoke-test` in
  `src/main/robotwar/robot_test.cljs` per the §4.3 convention.

## Open findings

### 1. Tie result shape deviates from spec §2.6 — `{:game-over? true}` vs `{:tie? true}`

- **Where:** `src/main/robotwar/world.cljc` (~line 94, the `result` cond), plus the
  matching assertion in `src/test/robotwar/world_test.clj` (`victory-result-test`).
- **What:** Spec §2.6 locks the world's `:result` field to `nil`, `{:winner robot-idx}`,
  or `{:tie? true}`. The code uses `{:game-over? true}` for the zero-alive case.
  Introduced in Step 3 (commit `417ffa7`, before the reviewed range); Steps 5–7 built on
  it without correcting.
- **Impact:** Functionally benign today — `canvas.cljs` draws "TIE" whenever `:winner`
  is absent, and `app.cljs` only checks that `:result` is non-nil. But any future code
  written to the spec that checks `(:tie? result)` will silently misbehave.
- **Suggested fix:** Rename the key to `:tie?` in `world.cljc`, update
  `world_test.clj` / `world_test.cljs`, and grep for any other `:game-over?` consumers
  (there were none at review time). Alternatively, amend spec §2.6 to bless
  `:game-over?` — either way, make code and spec agree.

### 2. RADAR returns an ambiguous `0` when the firing robot overlaps a target

- **Where:** `src/main/robotwar/physics.cljc` (`ray-disc-hit-distance`, the
  origin-inside-disc branch returning `0.0`) and `src/main/robotwar/register.cljc`
  (`radar-scan`).
- **What:** The radar sign convention is negative = robot hit, positive = wall
  distance. When the ray origin lies *inside* another robot's disc,
  `ray-disc-hit-distance` returns `0.0`, which negates and rounds to plain `0` —
  indistinguishable from the corner-pinned wall case already documented in addendum
  A.30. Overlap genuinely occurs: radar reads happen during brain ticks, which run
  *before* `collision-pass` separates overlapping robots within the same world tick.
- **Impact:** Low. A brain touching its target reads `0` instead of a small negative
  number, so a "negative means enemy" check misses at point-blank range for one tick.
- **Suggested fix (pick one):** clamp robot-hit distances to a minimum of some epsilon
  (e.g. return `-1` when the entry distance rounds to 0), or document the robot-overlap
  side of the ambiguity in addendum A.30 and declare `0` as "point-blank / degenerate."

### 3. Stranded UI when the program manifest fails to load

- **Where:** `src/main/robotwar/app.cljs` — `on-program-input-keydown` (manifest-nil
  fallback, documented in addendum A.36) interacting with `start-game`'s promise chain
  and `start-transition!`.
- **What:** If the manifest fetch failed, raw program names bypass
  `valid-program-names`. Two or more garbage names then pass the `< 2` check,
  `start-transition!` collapses the instruction box and blurs the input, and
  `start-game` fetches nonexistent `.rw` files. `js/fetch` does **not** reject on HTTP
  404 — it resolves with the 404 body — so the assembler blows up inside the `.then`,
  the `.catch` merely logs, and the user is left with a collapsed input box, an
  invisible canvas, and no recovery path: `game-over?` is false, so neither Enter nor
  canvas click triggers `restart-game!`. Only a page reload recovers.
- **Impact:** Low likelihood (requires the manifest fetch to have failed) but a hard
  dead-end when it happens.
- **Suggested fix:** In `start-game`'s `.catch`, surface the error via
  `show-input-error!` and reverse the transition (the guts of `restart-game!` minus the
  state clearing already do this). Optionally also check `resp.ok` in
  `fetch-text`/`fetch-json` and reject on non-2xx so failures are caught uniformly.

### 4. Step 7's interactive behavior has never been verified in a browser

- **Where:** Definition-of-done §6 items: restart on canvas click, restart on Enter,
  the `< 2` input error display, fade transitions on restart, legend freeze under the
  victory overlay (carried forward from addendum A.12).
- **What:** Addendum A.37 is upfront that the Step 7 pass verified only via clean
  compile, test suites, and `curl` — no browser drove the click/keypress paths. Code
  reading found no bugs in those paths (the Enter routing through the document-level
  `on-keydown` per A.32 and the inline-opacity resets per A.33 both look correct), but
  they remain unexercised end-to-end.
- **Suggested fix:** Run the manual §4.4 workflow (`npx shadow-cljs watch app` +
  `npx serve public`), walk the §6 checklist, and record the outcome in a new addendum
  before declaring Tier 1 shipped.
