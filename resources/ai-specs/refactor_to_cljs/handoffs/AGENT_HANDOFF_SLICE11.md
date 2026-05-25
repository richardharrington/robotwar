# AGENT HANDOFF — Execute Refactor Spec Slice 11

You are working in the `robotwar` repo. Execute **Slice 11** from `REFACTOR_SPEC.md` exactly, while keeping the app working at every commit.

## Required first step

Before making changes, **read the whole codebase** (all source + tests + relevant public assets + `REFACTOR_SPEC.md`) so you have full context and avoid regressions.

At minimum, read:
- `REFACTOR_SPEC.md` (including Section 8 amendments)
- `deps.edn`, `shadow-cljs.edn`, `package.json`
- `src/main/robotwar/**/*.clj`, `src/main/robotwar/**/*.cljc`, `src/main/robotwar/**/*.cljs`
- `src/test/robotwar/**/*.clj`
- `public/index.html`, `public/js/main.js`
- `public/programs/*.rw`, `public/programs/programs.json`

## Current project state (already done)

- Slice 1 complete (toolchain + shadow-cljs + dev server path).
- Slice 2 complete (`clojure.test` migration).
- Slice 3 complete (programs moved to `public/programs/*.rw`, manifest added, deterministic program-name ordering).
- Slices 4–9 complete (engine namespaces ported to `.cljc`).
- Slice 10 complete:
  - shadow `:test` build runs on Node
  - CLJS parity tests added with `cljs.test`
  - dedicated runner ns in `:node-test` build
  - unified test script: `scripts/test-all.sh` (wired to `npm test`)

Recent commits:
- Slice 10 implementation: `e451946`
- Slice 10 spec updates: `50a85ff`

## Scope you must implement now

From spec **Slice 11**:

- Add CLJS engine driver namespace behavior that:
  - fetches `public/programs/programs.json` at startup
  - exposes a function (e.g. `start-game`) that accepts program names
  - fetches selected `.rw` program files
  - initializes world via existing engine `.cljc` code
  - runs the engine using a time-accumulator loop
  - logs world state each tick to browser console
- Keep existing JS app running alongside it (no canvas/input/audio migration yet).

## Constraints

- Do not implement future slices (12+).
- Do not change game mechanics.
- Keep behavior bug-for-bug compatible.
- Minimize unrelated refactors.
- Preserve Slice 3 file-based manifest/program loading model.
- Keep JVM and CLJS test workflows intact.

## Notes on expected touch points

Likely files to update:
- `src/main/robotwar/app.cljs`
- possibly add CLJS helper namespace(s) under `src/main/robotwar/`
- `public/index.html` only if needed for invocation ergonomics (keep minimal)

Likely no changes needed to engine `.cljc` code.

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

5. Slice 11 runtime check in browser console:
- load `http://localhost:3000`
- confirm manifest fetch on startup (or observable startup log)
- manually invoke exported function, e.g.:
```js
robotwar.app.start_game(["mover", "shooter"])
```
- verify world ticks are logged to console

(If final exported name differs, use and report exact invocation.)

## Deliverable format

When done, report:
1. files added/changed/deleted
2. tactical decisions taken (state shape, exported functions, loop wiring)
3. verification summary (tests + compile + server + console tick logging)
4. surprises/risks discovered

## Quality bar for Slice 11

A Slice 11 implementation is complete only if:
- `npm test` passes (JVM + CLJS Node)
- `npx shadow-cljs compile app` succeeds
- CLJS driver fetches manifest/program files and runs engine ticks in browser
- world state logs per tick from CLJS loop
- no engine semantic changes were introduced
- existing app/server behavior remains functional during transition
