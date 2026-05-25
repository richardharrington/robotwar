# AGENT HANDOFF — Execute Refactor Spec Slice 16

You are working in the `robotwar` repo. Execute **Slice 16** from `REFACTOR_SPEC.md` exactly, while keeping the app working at every commit.

## Required first step

Before making changes, **read the whole codebase** (all source + tests + relevant public assets + `REFACTOR_SPEC.md`) so you have full context and avoid regressions.

At minimum, read:
- `REFACTOR_SPEC.md` (including Section 8 amendments, especially 8.7)
- `AGENT_HANDOFF_SLICES13_15.md`
- `AGENT_HANDOFF_SLICE16.md` (this file)
- `deps.edn`, `shadow-cljs.edn`, `package.json`
- `src/main/robotwar/**/*.clj`, `src/main/robotwar/**/*.cljc`, `src/main/robotwar/**/*.cljs`
- `src/test/robotwar/**/*.clj`
- `public/index.html`, `public/css/main.css`
- `public/programs/*.rw`, `public/programs/programs.json`, `public/programs/programs-live.json`, `public/programs/programs-test.json`
- `README.md`, `TODO.md`, `issues.txt`

## Current project state (already done)

- Slice 1 complete (deps + shadow + toolchain).
- Slice 2 complete (`clojure.test` migration).
- Slice 3 complete (`.rw` files + manifest model).
- Slices 4–9 complete (engine namespaces ported to `.cljc`).
- Slice 10 complete (CLJS Node parity tests + unified test command).
- Slice 11 complete (CLJS engine driver and tick loop).
- Slice 12 complete (CLJS canvas rendering + CLJS loop draw + audio pool parity).
- Slice 13 complete (input chrome moved to CLJS, jQuery removed, `public/js/main.js` deleted).
- Slice 14 complete (audio lifecycle hardening cleanup).
- Slice 15 complete (backend strip):
  - deleted `src/main/robotwar/handler.clj`
  - deleted `src/main/robotwar/browser.clj`
  - deleted `public/js/lib/template.js`
  - deleted `Procfile`
  - removed `ring/*` and `compojure` deps from `deps.edn`
  - removed legacy handler test
- Program manifest split now in place:
  - `public/programs/programs-live.json` (UI/runtime)
  - `public/programs/programs-test.json` (test/dev)
  - `public/programs/programs.json` compatibility alias (mirrors live)

Recent commits:
- Slice 13: `727a747`
- Slice 14: `74d484b`
- Slice 15: `d661b22`
- manifest split + README note: `4f152cd`
- spec updates through slices 13–15: `1f8dd78`

## Scope you must implement now (Slice 16)

### 1) Netlify deployment setup
- Add Netlify configuration so repo can deploy static site from `public/`.
- Use build command appropriate for current app architecture (`shadow-cljs release app` path per spec).
- Ensure publish directory is `public/`.
- Keep setup minimal and explicit (e.g. `netlify.toml`).

### 2) README rewrite for new architecture
- Rewrite `README.md` so it reflects the current post-backend architecture.
- Remove outdated instructions and references to:
  - Leiningen
  - Ring/Compojure runtime backend
  - jQuery/Queue fetch architecture
- Include at minimum:
  - install prerequisites
  - local dev workflow
  - test workflow
  - build/release commands
  - program manifest/file model (live/test split + compatibility alias)
  - deterministic ordering expectations for displayed program lists

### 3) Review and prune stale planning docs
- Review `TODO.md` and `issues.txt`.
- Update, prune, or remove stale items that no longer match the project state.
- Keep changes conservative: do not invent new roadmap items unless clearly needed.

## Hard constraints

- **Do not implement beyond Slice 16.**
- Do not change engine/game mechanics.
- Keep behavior bug-for-bug compatible.
- Minimize unrelated refactors.
- Preserve manifest-driven static program loading model.
- Keep JVM + CLJS test workflows intact.

## Verification requirements

Run and report:

1. Unified tests:
```bash
npm test
```

2. App compile:
```bash
npx shadow-cljs compile app
```

3. Release build:
```bash
npx shadow-cljs release app
```

4. Optional local static sanity check (recommended):
- serve `public/` with a static file server and load app in browser
- verify UI still starts game, canvas updates, shell-fire sound still plays

## Deliverable format

When done, report:
1. files added/changed/deleted
2. tactical decisions taken (Netlify config shape, README depth/content choices, TODO/issues pruning choices)
3. verification summary
4. surprises/risks discovered
5. commit SHA(s)

## Quality bar

Complete only if:
- Netlify config exists and matches current static app build/publish flow
- README is fully updated to modern workflow and architecture
- TODO/issues cleanup is completed (or explicitly justified if unchanged)
- `npm test` passes
- `npx shadow-cljs compile app` succeeds
- `npx shadow-cljs release app` succeeds
- no engine semantic changes introduced
