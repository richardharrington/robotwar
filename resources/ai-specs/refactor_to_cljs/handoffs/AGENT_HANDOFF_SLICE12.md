# AGENT HANDOFF — Execute Refactor Spec Slice 12

You are working in the `robotwar` repo. Execute **Slice 12** from `REFACTOR_SPEC.md` exactly, while keeping the app working at every commit.

## Required first step

Before making changes, **read the whole codebase** (all source + tests + relevant public assets + `REFACTOR_SPEC.md`) so you have full context and avoid regressions.

At minimum, read:
- `REFACTOR_SPEC.md` (including Section 8 amendments)
- `AGENT_HANDOFF_SLICE11.md`
- `deps.edn`, `shadow-cljs.edn`, `package.json`
- `src/main/robotwar/**/*.clj`, `src/main/robotwar/**/*.cljc`, `src/main/robotwar/**/*.cljs`
- `src/test/robotwar/**/*.clj`
- `public/index.html`, `public/js/main.js`
- `public/programs/*.rw`, `public/programs/programs.json`
- `public/css/main.css`

## Current project state (already done)

- Slice 1 complete (toolchain + shadow-cljs + dev server path).
- Slice 2 complete (`clojure.test` migration).
- Slice 3 complete (programs moved to `public/programs/*.rw`, manifest added, deterministic program-name ordering).
- Slices 4–9 complete (engine namespaces ported to `.cljc`).
- Slice 10 complete (shadow `:test` Node build + CLJS parity tests + unified `npm test`).
- Slice 11 complete:
  - `robotwar.app` fetches `public/programs/programs.json` at startup
  - exported `start_game` function fetches named `.rw` files
  - initializes world via existing `.cljc` engine
  - runs engine via time-accumulator `requestAnimationFrame` loop
  - logs **compact per-tick summaries** to console

Recent commits:
- Slice 11 CLJS driver + compact tick logging: `e95295a`
- Spec clarification for compact tick logs: `69888d0`

## Scope you must implement now

From spec **Slice 12**:

- Port canvas drawing from `public/js/main.js` to CLJS:
  - geometry helpers
  - robot/shell drawing
  - shell explosion rendering
  - world animation function
- Wire CLJS loop so each frame draws the current world from CLJS state.
- Stop JS fetch/queue world simulation flow (engine now runs in CLJS already).
- Keep JS app running enough for transition, but remove obsolete queue dependency:
  - delete `public/js/lib/Queue.js`
  - remove fetch/queue portions of `public/js/main.js`
- Do **not** implement Slice 13+ work (full input chrome migration, audio migration, backend deletion, etc.).

## Constraints

- Do not implement future slices (13+).
- Do not change game mechanics.
- Keep behavior bug-for-bug compatible.
- Minimize unrelated refactors.
- Preserve Slice 3 file-based manifest/program loading model.
- Keep JVM and CLJS test workflows intact.

## Notes on expected touch points

Likely files to update:
- `src/main/robotwar/app.cljs`
- likely add CLJS helper namespace(s) under `src/main/robotwar/` (e.g. canvas/drawing)
- `public/js/main.js` (transition trimming only)
- `public/index.html` (minimal changes only if needed)

Likely file deletions this slice:
- `public/js/lib/Queue.js`

## Verification requirements

At minimum, after each small step:

1. Unified tests:
```bash
npm test
```

2. Dev server still boots:
```bash
clj -M:dev
```

3. App build still compiles:
```bash
npx shadow-cljs compile app
```

4. Endpoint sanity checks:
```bash
curl -i http://localhost:3000/index.html
curl -i http://localhost:3000/program-names
curl -i "http://localhost:3000/init?programs=mover"
```

5. Slice 12 runtime check in browser:
- load `http://localhost:3000`
- start CLJS engine manually:
```js
robotwar.app.start_game(["mover", "shooter"])
```
- verify canvas is drawn/updated from CLJS world state each frame
- verify existing JS queue/fetch architecture is no longer driving simulation

(If exported names differ, use and report exact invocation names.)

## Deliverable format

When done, report:
1. files added/changed/deleted
2. tactical decisions taken (canvas interop style, state/read model, loop/draw wiring)
3. verification summary (tests + compile + server + browser rendering behavior)
4. surprises/risks discovered

## Quality bar for Slice 12

A Slice 12 implementation is complete only if:
- `npm test` passes (JVM + CLJS Node)
- `npx shadow-cljs compile app` succeeds
- canvas drawing runs from CLJS state driven by CLJS engine loop
- JS fetch/queue simulation flow is removed/disabled
- `public/js/lib/Queue.js` is deleted
- no engine semantic changes were introduced
- existing app/server behavior remains functional during transition
