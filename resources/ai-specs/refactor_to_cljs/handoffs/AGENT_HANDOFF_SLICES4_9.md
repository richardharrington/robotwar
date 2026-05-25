# AGENT HANDOFF — Execute Refactor Spec Slices 4–9

You are working in the `robotwar` repo. Execute **Slices 4–9** from `REFACTOR_SPEC.md` exactly, while keeping the app working at every commit.

## Required first step

Before making changes, **read the whole codebase** (all source + tests + relevant public assets + `REFACTOR_SPEC.md`) so you have full context and avoid regressions.

At minimum, read:
- `REFACTOR_SPEC.md` (including Section 8 amendments)
- `deps.edn`, `shadow-cljs.edn`, `package.json`
- `src/main/robotwar/**/*.clj` and `src/main/robotwar/**/*.cljs`
- `src/test/robotwar/**/*.clj`
- `public/index.html`, `public/js/main.js`
- `public/programs/*.rw`, `public/programs/programs.json`

## Current project state (already done)

- Slice 1 complete (toolchain + shadow-cljs + dev server path).
- Slice 2 complete (tests migrated to `clojure.test`).
- Slice 3 complete (programs moved to `public/programs/*.rw`, manifest added, `source_programs.clj` deleted, JVM loaders updated).
- Deterministic sorting for `/program-names` is in place.

Recent commits:
- Slice 2: `cc12977`
- Slice 3 implementation: `81d3308`
- Spec amendments: `8ab9e40`

## Scope you must implement now

From spec slices 4–9:

- **Slice 4**: `constants.clj` → `constants.cljc`; `physics.clj` → `physics.cljc` (Math interop conditionals)
- **Slice 5**: `assembler.clj` → `assembler.cljc`
- **Slice 6**: `shell.clj` → `shell.cljc`
- **Slice 7**: `brain.clj` → `brain.cljc`
  - Replace `read-string`/`eval` op resolution with a static op map (required by spec)
- **Slice 8**: `register.clj` → `register.cljc`
  - JVM/CLJS protocol extension interop (`extend` vs `extend-type`/`extend-protocol`)
- **Slice 9**: `robot.clj` → `robot.cljc`; `world.clj` → `world.cljc`
  - Math interop conditionals
  - Drop `clj-time` usage from `world`
  - Verify retained JVM tools still work (`terminal.clj`, `core.clj`)

## Constraints

- Do not implement future slices (10+).
- Do not change game mechanics.
- Keep behavior bug-for-bug compatible.
- Minimize unrelated refactors.
- Preserve Slice 3 file-based program loading behavior.

## Verification requirements

At minimum, after each slice (or each small step):

1. Tests:
```bash
clj -M:test
```

2. Dev server still boots:
```bash
clj -M:dev
```

3. Endpoint sanity checks:
```bash
curl -i http://localhost:3000/index.html
curl -i http://localhost:3000/program-names
curl -i "http://localhost:3000/init?programs=mover"
```

## Notes on expected touch points

Likely files to convert during these slices:
- `src/main/robotwar/constants.clj`
- `src/main/robotwar/physics.clj`
- `src/main/robotwar/assembler.clj`
- `src/main/robotwar/shell.clj`
- `src/main/robotwar/brain.clj`
- `src/main/robotwar/register.clj`
- `src/main/robotwar/robot.clj`
- `src/main/robotwar/world.clj`

Likely supporting edits:
- requires/usages in `core.clj`, `terminal.clj`, `handler.clj`, tests, etc., due to extension and namespace/file changes.

## Deliverable format

When done, report:
1. files added/changed/deleted
2. tactical decisions taken (if any)
3. verification command outputs summary
4. surprises/risks discovered
