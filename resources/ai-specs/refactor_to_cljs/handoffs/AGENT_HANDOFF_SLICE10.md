# AGENT HANDOFF — Execute Refactor Spec Slice 10

You are working in the `robotwar` repo. Execute **Slice 10** from `REFACTOR_SPEC.md` exactly, while keeping the app working at every commit.

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
- Slice 2 complete (tests migrated to `clojure.test`).
- Slice 3 complete (programs moved to `public/programs/*.rw`, manifest added, `source_programs.clj` deleted, JVM loaders updated).
- Slices 4–9 complete (engine namespaces ported to `.cljc`: constants, physics, assembler, shell, brain, register, robot, world).
- `brain` op resolution now uses static op map (no `read-string`/`eval`).
- Deterministic sorting for `/program-names` is in place.
- `REFACTOR_SPEC.md` has additional clarifications for slices 4–10 (shell interop note, CLJS parse semantics note, division semantics lock, formatting hygiene note).

Recent commits:
- Slice 2: `cc12977`
- Slice 3 implementation: `81d3308`
- Spec amendments after Slice 3: `8ab9e40`
- Slices 4–9 implementation: `86501fe`
- Spec clarifications for Slice 10 prep: `45e8ca7`

## Scope you must implement now

From spec **Slice 10**:

- Configure shadow-cljs `:test` build to run engine tests on Node.
- Verify the same engine test suite passes on both JVM and Node.

## Constraints

- Do not implement future slices (11+).
- Do not change game mechanics.
- Keep behavior bug-for-bug compatible.
- Minimize unrelated refactors.
- Preserve Slice 3 file-based program loading behavior.
- Keep JVM test workflow intact (`clj -M:test` remains canonical JVM command).

## Notes on expected touch points

Likely files to update:
- `shadow-cljs.edn` (node-test config details)
- `package.json` (scripts for CLJS test invocation, if needed)
- Possibly add CLJS-specific test namespaces under `src/test/robotwar/` if required by shadow runner
- Possibly add small helper script/command wrapper for running both test targets (if you choose to do this, keep it minimal)

Likely no engine behavior changes should be necessary.

## Verification requirements

At minimum, after each small step:

1. JVM tests:
```bash
clj -M:test
```

2. CLJS tests (Node via shadow-cljs):
```bash
npx shadow-cljs compile test
node target/test/node-tests.js
```
(If your final config uses a different invocation, run and report that exact command.)

3. Dev server still boots:
```bash
clj -M:dev
```

4. Endpoint sanity checks:
```bash
curl -i http://localhost:3000/index.html
curl -i http://localhost:3000/program-names
curl -i "http://localhost:3000/init?programs=mover"
```

## Deliverable format

When done, report:
1. files added/changed/deleted
2. tactical decisions taken (if any)
3. verification command outputs summary (JVM + CLJS)
4. surprises/risks discovered

## Quality bar for Slice 10

A Slice 10 implementation is considered complete only if:
- JVM tests pass (`clj -M:test`)
- CLJS engine tests pass on Node using the shadow `:test` build
- No engine semantic changes were introduced
- Existing dev server + endpoints still work
