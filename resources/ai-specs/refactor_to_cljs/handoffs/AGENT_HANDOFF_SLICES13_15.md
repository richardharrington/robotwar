# AGENT HANDOFF — Execute Refactor Spec Slices 13, 14, 15

You are working in the `robotwar` repo. Execute **Slices 13, 14, and 15** from `REFACTOR_SPEC.md` exactly, while keeping the app working at every commit.

## Required first step

Before making changes, **read the whole codebase** (all source + tests + relevant public assets + `REFACTOR_SPEC.md`) so you have full context and avoid regressions.

At minimum, read:
- `REFACTOR_SPEC.md` (including Section 8 amendments and recent slice-order/audio updates)
- `AGENT_HANDOFF_SLICE12.md`
- `AGENT_HANDOFF_SLICES13_15.md` (this file)
- `deps.edn`, `shadow-cljs.edn`, `package.json`
- `src/main/robotwar/**/*.clj`, `src/main/robotwar/**/*.cljc`, `src/main/robotwar/**/*.cljs`
- `src/test/robotwar/**/*.clj`
- `public/index.html`, `public/js/main.js`, `public/css/main.css`
- `public/programs/*.rw`, `public/programs/programs.json`

## Current project state (already done)

- Slice 1 complete (toolchain + shadow-cljs + dev server path).
- Slice 2 complete (`clojure.test` migration).
- Slice 3 complete (programs moved to `public/programs/*.rw`, manifest added, deterministic ordering).
- Slices 4–9 complete (engine namespaces ported to `.cljc`).
- Slice 10 complete (shadow `:test` Node build + CLJS parity tests + unified `npm test`).
- Slice 11 complete (CLJS engine driver + manifest/program fetch + accumulator loop + compact tick logs).
- Slice 12 complete:
  - canvas rendering ported to CLJS (`src/main/robotwar/canvas.cljs`)
  - CLJS loop draws each frame from CLJS world state
  - JS fetch/queue simulation flow removed
  - `public/js/lib/Queue.js` deleted
  - shell-fire audio pool behavior ported to CLJS for parity
  - fast-forward defaults/controls matched to legacy runtime feel

Recent commits:
- Slice 12 implementation: `6babb76`
- Spec updates reflecting Slice 12 realities: `89ad64f`

## Scope you must implement now

### Slice 13 (commit 1)
- Port remaining input chrome from JS to CLJS:
  - program-name input submit handling
  - instruction-box/canvas transition behavior
  - program list display wiring
- Remove jQuery usage from runtime app flow.
- Delete `public/js/lib/jquery-2.0.3.min.js`.
- Remove/empty/delete `public/js/main.js` once no longer needed.
- Keep behavior equivalent to current UX unless explicitly improved by spec.

### Slice 14 (commit 2)
- Treat as cleanup/hardening slice (per amended spec):
  - verify CLJS audio lifecycle remains correct after Slice 13 cleanup
  - remove transitional hooks/shims no longer needed
  - ensure no JS audio remnants remain
- Do **not** introduce new audio architecture/dependencies.

### Slice 15 (commit 3)
- Backend strip:
  - delete `src/main/robotwar/handler.clj`
  - delete `src/main/robotwar/browser.clj`
  - delete `public/js/lib/template.js`
  - delete `Procfile`
  - drop `compojure`, `ring/*` deps from `deps.edn`
- Verify retained JVM tools still load/work (`core.clj`, `terminal.clj`) with updated requires as needed.
- Ensure any route-backed data still needed by UI already has static equivalent (manifest already exists).

## Hard constraints

- **Three separate commits required**: one for Slice 13, one for Slice 14, one for Slice 15.
- Do not implement Slice 16+.
- Do not change game mechanics.
- Keep behavior bug-for-bug compatible.
- Minimize unrelated refactors.
- Preserve file-based manifest/program model.
- Keep JVM + CLJS test workflows intact.

## Verification requirements (run after each slice commit)

1. Unified tests:
```bash
npm test
```

2. App compile:
```bash
npx shadow-cljs compile app
```

3. If backend still present (before Slice 15 removal), dev server boot + endpoint checks:
```bash
clj -M:dev
curl -i http://localhost:3000/index.html
curl -i http://localhost:3000/program-names
curl -i "http://localhost:3000/init?programs=mover"
```

4. Browser runtime checks:
- Load `http://localhost:3000`
- Confirm input UI starts game through CLJS path
- Confirm canvas draws/updates
- Confirm shell-fire sound plays
- Confirm no stale asset issues (hard refresh / disable cache if behavior seems old)

## Deliverable format

When done, report:
1. files added/changed/deleted per slice
2. tactical decisions taken
3. verification summary per slice
4. surprises/risks discovered
5. three commit SHAs in order (Slice 13, 14, 15)

## Quality bar

Complete only if:
- three commits exist (13/14/15 separately)
- `npm test` passes
- `npx shadow-cljs compile app` succeeds
- app runs fully on CLJS-side simulation/drawing/input/audio
- jQuery/legacy main.js/Queue/runtime JS dependencies removed per slice scope
- backend/server-era files removed in Slice 15
- no engine semantic changes introduced
