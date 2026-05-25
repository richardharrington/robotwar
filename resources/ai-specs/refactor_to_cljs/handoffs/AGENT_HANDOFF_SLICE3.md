# AGENT HANDOFF — Execute Refactor Spec Slice 3

You are working in the `robotwar` repo. Execute **Slice 3** from `REFACTOR_SPEC.md` exactly, while keeping the app working.

## Required first step

Before making changes, **read the whole codebase** (all source + tests + relevant public assets + `REFACTOR_SPEC.md`) so you have full context and avoid regressions.

At minimum, read:
- `REFACTOR_SPEC.md`
- `deps.edn`, `shadow-cljs.edn`, `package.json`
- `src/main/robotwar/**/*.clj` and `src/main/robotwar/**/*.cljs`
- `src/test/robotwar/**/*.clj`
- `public/index.html`, `public/js/main.js`

## Current project state (already done)

- Slice 1 is complete (toolchain + shadow-cljs + dev server path).
- Slice 2 is complete (tests migrated from Midje/ring-mock to `clojure.test`).
- Current test command is:
  - `clj -M:test`
- Slice 2 commit: `cc12977`

## Scope you must implement now (Slice 3)

From spec:
1. Create `public/programs/{name}.rw` for each entry in `source_programs.clj`.
2. Create `public/programs/programs.json` manifest listing program names.
3. Update JVM callers of `source-programs/programs` to read from `.rw` files.
4. Delete `src/main/robotwar/source_programs.clj`.
5. Verify existing app still runs.

## Tactical decision already made (mandatory)

For the spec’s tactical question about `dev-programs`:
- **Dev programs must be represented as `.rw` files too**, and structured as close as possible to non-dev programs.
- So do **not** keep `dev-programs` in code as inline strings if avoidable.

## Program inventory to migrate

Current `source_programs.clj` contains:
- main programs: `speedy`, `mover`, `left-shooter`, `top-shooter`, `shooter`
- dev programs: `multi-use`, `index-data`, `random`

Create `.rw` files for all eight names.

## Implementation guidance

- Put files at `public/programs/<name>.rw`.
- Create `public/programs/programs.json` with simple manifest shape:

```json
{ "programs": ["speedy", "mover", "left-shooter", "top-shooter", "shooter", "multi-use", "index-data", "random"] }
```

- Update server-side loading so runtime reads program text from files (not in-memory map from deleted namespace).
- Preserve existing behavior for current routes (`/program-names`, `/init`, etc.) during this transition.
- Keep test fixtures behavior equivalent (e.g., brain tests currently use `multi-use`).

## Expected code touch points

Likely files to edit:
- `src/main/robotwar/handler.clj`
- tests that currently require `robotwar.source-programs` (notably `src/test/robotwar/brain_test.clj`)

Likely file to delete:
- `src/main/robotwar/source_programs.clj`

Likely new files:
- `public/programs/*.rw`
- `public/programs/programs.json`

## Verification requirements

Run and confirm all pass/work:

1. Tests:
```bash
clj -M:test
```

2. Dev server boots:
```bash
clj -M:dev
```

3. App endpoint sanity checks (in another terminal):
```bash
curl -i http://localhost:3000/index.html
curl -i http://localhost:3000/program-names
curl -i "http://localhost:3000/init?programs=mover"
```

4. Confirm browser app still loads at `http://localhost:3000`.

## Constraints

- Do not implement future slices.
- Do not change game mechanics.
- Minimize unrelated refactors.
- Keep behavior bug-for-bug compatible.

## Deliverable format

When done, report:
1. files added/changed/deleted
2. tactical decisions taken (if any beyond the mandated one)
3. verification command outputs summary
4. any surprises/risks discovered
