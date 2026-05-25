# RobotWar Refactor Spec

A specification for refactoring this Clojure project into a modern, browser-based
ClojureScript app. Use this as the source of truth for design decisions; flagged
**TACTICAL** items are deliberately left to the implementer to decide in context.

---

## 1. Project context

RobotWar is a reverse-engineered version of Silas Warner's 1981 Apple II game.
Players write programs in a Forth-like DSL that compiles to bytecode and runs
on a virtual machine "brain" inside each robot. Robots battle in an arena until
one survives.

The current implementation is a JVM Clojure backend (Ring/Compojure) plus a
jQuery/Canvas JavaScript frontend. The backend pre-computes an infinite lazy
seq of world ticks; the browser fetches batches of 1000 worlds as JSON, queues
them, and animates them at a fixed game-tick rate.

Background reading:
- The original game manual lives in `manual.txt` in this repo.
- https://www.filfre.net/2012/01/robot-war/ is a good narrative overview.
- The current README describes the existing namespace structure.

---

## 2. Goals (in scope)

1. **Modernize the Clojure stack.** Replace the Leiningen+Heroku-era setup with
   modern tooling, drop deprecated dependencies, and make the project trivially
   deployable.
2. **Move the frontend to ClojureScript.** Replace the existing
   jQuery/Queue.js/IIFE JavaScript with idiomatic CLJS.
3. **Move the game engine into the browser.** Eliminate the
   compute-on-server / fetch / queue / animate architecture. The engine runs
   locally in the browser; no per-tick network traffic.

## 3. Non-goals (out of scope)

- **Implementing missing game mechanics.** Shells don't currently damage
  robots, radar isn't implemented, etc. The manual describes more behavior
  than the code provides. These bugs/gaps are deliberately preserved as-is and
  are the subject of a follow-up project.
- **Lobby UI redesign.** The current text-input lobby is preserved as-is in
  the CLJS port; redesign is a follow-up.
- **Backend dynamic features.** No user accounts, no upload, no persistence.
  The backend becomes purely static file hosting. (See §4.)

---

## 4. Architecture decisions

Each decision is the outcome of a deliberate design conversation. Take them as
fixed unless something concrete forces a revisit.

### 4.1 Backend: static-only
No server-side code in production. Robot programs are served as static text
files. The repo no longer contains any Ring/Compojure handler code in the
shipped artifact.

### 4.2 Build tooling: `deps.edn` + `shadow-cljs`
- `deps.edn` for JVM Clojure (used for tests, for JVM REPL, and for running
  retained JVM tools like `terminal.clj`).
- `shadow-cljs.edn` for ClojureScript compilation.
- No more `project.clj`, no more `lein-ring`, no more `Procfile`.

### 4.3 Engine source format: all CLJC
All eight engine namespaces (`assembler`, `brain`, `register`, `robot`,
`world`, `shell`, `physics`, `constants`) become `.cljc` and run on both JVM
and CLJS. This preserves the option to run the engine on JVM for
`terminal.clj`, REPL inspection, and possible future headless tournament
scripts.

Specific interop points requiring reader conditionals:
- `brain` — replace `read-string`/`eval` of op symbols with a static op map.
  No conditional needed after that.
- `register` — `extend` (JVM) ↔ `extend-type`/`extend-protocol` (CLJS). ~8
  sites.
- `robot` — `Math/copySign`, `Math/abs` etc. JVM uses `Math/*`; CLJS uses
  `js/Math.*` or `goog.math`. ~6 sites.
- `world` — drop `clj-time` entirely; the only use is in helpers that aren't
  actually needed for the core `tick-combined-world`.

### 4.4 Migration approach: vertical slices, modernize during port
Each slice both ports and modernizes the relevant code. No separate
"first refactor JS, then port to CLJS" step — about half of the existing
`main.js` (the buffer/queue/fetch machinery) is being deleted entirely, so
polishing it first would be wasted work.

### 4.5 Frontend framework: none
Plain CLJS + atoms + Canvas. No Reagent, no re-frame. The HTML chrome is
tiny (one input, a few `<p>` tags, the canvas itself), and the canvas is
drawn imperatively from a `requestAnimationFrame` loop reading from a state
atom. A framework would be ceremony without payoff.

### 4.6 Game loop: time-accumulator pattern
A single `requestAnimationFrame` loop using the "fix your timestep" pattern.
Each frame:
1. Compute `elapsed-wall = now - last-frame-time`.
2. Cap `elapsed-wall` (e.g. at 250ms) to prevent catch-up spikes when the
   tab was backgrounded.
3. `elapsed-game = elapsed-wall * fast-forward`.
4. `accumulator += elapsed-game`.
5. While `accumulator >= tick-duration`: run one engine tick, subtract
   `tick-duration` from accumulator.
6. Draw the current world.

Fast-forward is implemented as a multiplier on `elapsed-game`. `fast-forward
= 0` pauses; arrow keys adjust the value (as in the existing JS).

### 4.7 Project layout: `src/main/` + `src/test/`
```
deps.edn
shadow-cljs.edn
package.json
src/
  main/
    robotwar/        (engine .cljc files + JVM-only tools)
    robotwar_cljs/   (CLJS-only namespaces, e.g. canvas drawing — see note)
  test/
    robotwar/
public/              (static assets, served by Netlify)
  index.html
  programs/          (.rw program files + programs.json manifest)
  audio/
  css/
  fonts/
```

Note: CLJS-only namespaces (canvas drawing, audio, input handling, top-level
app entry) need a home. **TACTICAL:** decide whether they live in
`src/main/robotwar/` alongside the `.cljc` engine (file extension
disambiguates), under a subdirectory like `src/main/robotwar/ui/`, or in a
sibling root namespace. Recommendation: subdirectory under `robotwar/`.

### 4.8 Migration order: engine-first
Order of work:
1. **Slice 1** — toolchain setup, hello-world CLJS.
2. **Slices 2–9** — engine port to `.cljc`, one or two namespaces per slice,
   tests passing on JVM throughout.
3. **Slice 10** — wire up CLJS test build; verify engine tests pass on both
   platforms.
4. **Slice 11** — CLJS-side engine driver: load programs from `.rw` files,
   run engine, dump state to console. No rendering yet.
5. **Slice 12** — port canvas drawing to CLJS. Replace JS canvas. Stop
   fetching from server.
6. **Slice 13** — port input chrome to CLJS.
7. **Slice 14** — port audio to CLJS.
8. **Slice 15** — delete obsoleted backend code and JS files (see §4.13).
9. **Slice 16** — Netlify deployment + README rewrite.

The app keeps working at every commit. The existing JS frontend continues to
function through slice 11.

### 4.9 Testing strategy
1. First, port all Midje tests to `clojure.test` (still JVM-only). This
   un-blocks the migration without changing any source code. Single test
   framework, single test command (`clj -M:test`).
2. As engine namespaces become `.cljc`, the existing JVM tests keep running.
   Add CLJS test runs at slice 10 (shadow-cljs `:target :node-test`).
3. End state: engine has both JVM and CLJS test suites; JVM remains the
   primary test command but a green CLJS run is required before merge.

### 4.10 Robot programs: static `.rw` files + manifest
- Each robot lives as a plain text file: `public/programs/mover.rw`,
  `public/programs/shooter.rw`, etc.
- A manifest file lists what's available: `public/programs/programs.json`.
- The CLJS client fetches the manifest at startup, then fetches individual
  programs on demand when a battle is initiated.
- This shape is forward-compatible with a future dynamic backend: the manifest
  endpoint becomes `/api/programs`, the program endpoints become
  `/api/programs/:name`, and the client code is unchanged.

**TACTICAL:** Manifest format. Simplest viable form is just a name list:
```json
{ "programs": ["mover", "shooter", "left-shooter", "top-shooter", "speedy"] }
```
A richer form (with descriptions, authors, etc.) is deferred per §3.

### 4.11 Hosting: Netlify
- Connect the repo to Netlify.
- Build command: `npx shadow-cljs release app` (plus whatever shells out to
  also produce static assets if needed).
- Publish directory: `public/`.
- Preview deploys on PR branches.

### 4.12 Lobby UI: defer redesign (slice 13 ports as-is)
Port the existing text-input lobby to CLJS as-is, with the only enhancement
being validation against the manifest. UX redesign is a follow-up project.

### 4.13 Fate of obsoleted code
**Delete** in the slice that obsoletes them:
- `src/main/robotwar/handler.clj` (slice 15)
- `src/main/robotwar/browser.clj` (slice 12 — its only purpose was
  compacting worlds for JSON transmission)
- `src/main/robotwar/source_programs.clj` (slice ~3, when `.rw` files exist)
- `public/js/main.js` (slice 12–14, piece by piece as CLJS takes over)
- `public/js/lib/jquery-2.0.3.min.js` (slice 13)
- `public/js/lib/Queue.js` (slice 12)
- `public/js/lib/template.js` (slice 15)
- `Procfile` (slice 16)
- `project.clj` (slice 1, replaced by `deps.edn`)

**Keep** as JVM-side tooling:
- `src/main/robotwar/terminal.clj` — still useful for running games in the
  terminal, headless debugging, screenshotting state without a browser. Will
  need `clj-time` → `java.time` (or `clojure.java-time`) migration when
  `clj-time` is dropped from deps. **TACTICAL:** which JVM time library to
  use, or whether to inline raw `java.time` calls.
- `src/main/robotwar/core.clj` — JVM REPL helpers (`pprint-robot`,
  `pprint-robot-at-combined-world`). Genuinely useful during engine
  development.

Both retained files need their `:require` paths verified against the new
engine `.cljc` files once those are in place.

If something we expected to delete turns out to still be load-bearing,
preserve it rather than scrambling — surface the surprise.

### 4.14 Dev workflow
- **Prerequisite:** Clojure CLI (`clojure`/`clj`) must be installed and on
  `PATH`. `shadow-cljs` shells out to `clojure`; without it,
  `npx shadow-cljs ...` will fail.
- `npx shadow-cljs watch app` for browser hot reload.
- Editor-integrated CLJS REPL connected to the browser tab (Calva, CIDER,
  or Cursive — depends on user's editor; setup is a prereq for slice 1).
- A separate JVM nREPL for engine inspection / running `terminal.clj` /
  running JVM tests.

### 4.15 CI: none
No GitHub Actions, no Netlify-side test gating. The developer runs tests
locally (`clj -M:test` and the CLJS test runner) before pushing. Acceptable
because the project has no downstream consumers.

### 4.16 Audio: port HTMLAudioElement pool pattern as-is
The existing approach (preload 40 `Audio` instances per sound, cycle through
them for rapid-fire playback) is ~15 lines of CLJS. No new dependencies, no
Web Audio API, no Howler.js. Future improvement is a follow-up.

---

## 5. Slice-by-slice plan

The slices below are a recommended decomposition. The executing agent may
combine adjacent small slices or split large ones, but **do not reorder**
slices in a way that breaks the "app works at every commit" property.

### Slice 1 — toolchain setup
- Create `deps.edn` with `:test` alias.
- Create `shadow-cljs.edn` with an `:app` build (target `:browser`) and a
  `:test` build (target `:node-test`).
- Create `package.json` with `shadow-cljs` as a devDependency.
- Reorganize source tree to `src/main/robotwar/` and `src/test/robotwar/`
  (this is a one-time `git mv` of every existing file).
- Delete `project.clj`.
- Add a minimal CLJS entry point that prints to `js/console.log` and is
  loaded by the existing `index.html` (alongside the still-running `main.js`).
- Add a JVM dev-server entrypoint run via `clj` (replacing `lein ring server`
  as the startup command while backend code still exists).
- Verify with explicit commands:
  1. `npm install`
  2. `npx shadow-cljs watch app` (leave running)
  3. `clj -M:dev` (in a second terminal)
  4. Open `http://localhost:3000`
  5. Confirm existing gameplay still works
  6. Confirm CLJS boot log appears in browser console
  7. If startup output is ambiguous, verify server via `curl -i http://localhost:3000/index.html`.

**TACTICAL:**
- Dependency versions in `deps.edn` and `shadow-cljs.edn`.
- `.gitignore` updates (`.shadow-cljs/`, `node_modules/`, `public/js/cljs-runtime/`, etc.).
- Whether to commit `package-lock.json` (recommendation: yes).

### Slice 2 — Midje → clojure.test
- Convert each test file from Midje syntax (`fact`, `=>`, `provided`) to
  `clojure.test` (`deftest`, `is`, `testing`).
- Drop `[midje "..."]` and `[ring-mock "..."]` from deps.
- Verify: `clj -M:test` passes on the unchanged source.

### Slice 3 — robot programs to `.rw` files
- Create `public/programs/{name}.rw` for each entry in
  `source_programs.clj` (the actual robot programs in `programs`, not the
  `dev-programs` test fixtures).
- Create `public/programs/programs.json` listing the names.
- Update any in-repo JVM callers of `source-programs/programs` to slurp
  from the new files (or inline-define test fixtures if the dev-programs
  were only ever for tests).
- Delete `source_programs.clj`.
- Verify: existing app still runs (server still loads programs, just from a
  new location).

**TACTICAL:**
- Whether `dev-programs` (the extra test fixtures) become `.rw` files,
  inline test data, or a separate `resources/test/programs/` directory.

### Slices 4–9 — engine port to `.cljc`
One slice per namespace, ordered by interop complexity (easiest first so
problems surface early):
- **Slice 4** — `constants.clj` → `constants.cljc` (zero interop) and
  `physics.clj` → `physics.cljc` (`Math/*` conditionals, ~3 sites).
- **Slice 5** — `assembler.clj` → `assembler.cljc` (almost no interop;
  watch for any `Integer/parseInt` that needs a reader conditional).
- **Slice 6** — `shell.clj` → `shell.cljc` (uses constants + physics, no
  other interop).
- **Slice 7** — `brain.clj` → `brain.cljc` (replace `read-string`/`eval` with
  a static op map; otherwise pure).
- **Slice 8** — `register.clj` → `register.cljc` (`extend` → `extend-type`
  via reader conditionals).
- **Slice 9** — `robot.clj` → `robot.cljc` and `world.clj` → `world.cljc`
  (`Math/*` conditionals; drop `clj-time` from `world`; verify
  `terminal.clj` still works after the rename).

Verify after each slice: `clj -M:test` still green. **Do not modify the
behavior of any engine code during the port — translate only.**

### Slice 10 — CLJS test runner
- Configure shadow-cljs `:test` build to run engine tests on Node.
- Verify: same test suite passes on both JVM and Node.

**TACTICAL:**
- Exact shadow-cljs test runner config.
- How to run both in one command (recommendation: a `make test` or a small
  shell script, since cross-runtime test invocation is awkward otherwise).

### Slice 11 — CLJS engine driver
- New CLJS namespace (e.g. `robotwar.app`) that:
  - Fetches `programs.json` at startup.
  - Has a function `start-game [program-names]` that fetches the named
    programs, initializes a world, and runs the engine in a
    time-accumulator loop.
  - Logs world state to console each tick (no canvas yet).
- The existing JS app still runs alongside, untouched.
- Verify: load page, open console, manually invoke
  `robotwar.app.start_game(["mover", "shooter"])`, see ticks logged.

**TACTICAL:**
- State atom shape. Recommendation: one atom holding `{:world ... :game-info
  ... :running? ... :fast-forward ...}` — simple is fine.
- Whether to expose start/stop functions on `js/window` for manual testing.

### Slice 12 — CLJS canvas drawing
- Port the canvas drawing functions from `main.js` (`drawRobot`,
  `drawShell`, `explodeShell`, `animateWorld`, the geometry helpers) to
  CLJS.
- Wire the time-accumulator loop's "draw the current world" step to these
  functions.
- Stop the JS fetch/queue loop. The page now runs the engine and draws
  entirely in CLJS, but the JS still handles startup and input.
- Delete `public/js/lib/Queue.js` and the fetch/queue portions of `main.js`.

**TACTICAL:**
- Canvas interop style: direct `js/` interop (`(.beginPath ctx)`) vs.
  helper wrappers (`(fill-circle ctx x y r color)`). Recommendation:
  helpers, the code is more readable and matches the shape of `main.js`.
- How CLJS receives the "start game" signal from the still-running JS code
  during this slice. Recommendation: expose a CLJS function on
  `js/window.robotwar` that JS calls. (This goes away in slice 13.)

### Slice 13 — CLJS input chrome
- Port the program-name input handler, the instruction box animation, and
  the program-names list fetch from `main.js` to CLJS.
- Remove the jQuery dependency (use native DOM APIs from CLJS — `document.querySelector`, `addEventListener`, etc.).
- Delete `public/js/lib/jquery-2.0.3.min.js`.
- After this slice, the only JS code remaining is the audio player in
  `main.js`.

**TACTICAL:**
- Whether to use `goog.dom`/`goog.events` (Closure utilities, no extra
  bundle size) or plain `js/document` interop. Recommendation: plain
  interop for simplicity; Closure utilities add little for this app.
- CSS animation triggers (the existing code animates the instruction box
  via class changes). Preserve the existing CSS; just trigger the same
  class changes from CLJS.

### Slice 14 — CLJS audio
- Port the audio pool pattern to CLJS (~15 lines).
- Hook into the world-diff logic that detects new shells (the existing JS
  checks `currentWorld["next-shell-id"] !== previousWorld["next-shell-id"]`).
- Delete the remaining `main.js` (now empty).

### Slice 15 — backend strip
- Delete `src/main/robotwar/handler.clj`,
  `src/main/robotwar/browser.clj`, `public/js/lib/template.js`,
  `Procfile`.
- Drop `compojure`, `ring/*` from `deps.edn`.
- Verify `terminal.clj` and `core.clj` still load and work; update their
  `:require`s if needed.
- `clj -M:test` still green.
- The repo now contains: engine `.cljc`, CLJS app, retained JVM tools
  (`terminal.clj`, `core.clj`), static assets in `public/`, build config.

### Slice 16 — deploy + README
- Set up Netlify (connect repo, configure build command and publish
  directory).
- Rewrite `README.md` to reflect the new architecture, dev workflow, and
  deploy story. Remove references to Leiningen, the queue architecture,
  the old `lein ring server` command.
- Update or delete `TODO.md` and `issues.txt` as appropriate (most items
  in `TODO.md` are years-stale; the executor should review and prune).

**TACTICAL:**
- Whether to add a `Makefile`, `bb.edn`, or shell scripts for common
  commands (`watch`, `release`, `test`, etc.). Recommendation: a minimal
  `Makefile` or `bb.edn` improves discoverability for future contributors.
- README content depth.

---

## 6. Consolidated list of tactical decisions for the executor

For convenience, every **TACTICAL** flag from above in one place:

1. **CLJS namespace organization** (§4.7) — subdirectory under
   `robotwar/`, alongside engine, or separate root namespace?
2. **Manifest format** (§4.10) — name-list only, or with metadata?
3. **JVM time library** (§4.13) — `clojure.java-time`, raw `java.time`, or
   delete the helpers in `terminal.clj` that used `clj-time`?
4. **Dependency versions** (slice 1) — pin recent stable versions of
   Clojure, ClojureScript, shadow-cljs.
5. **.gitignore content** (slice 1) — at minimum:
   `.shadow-cljs/`, `node_modules/`, `public/js/cljs-runtime/`,
   compiled output paths.
6. **Dev-server command during transition** (slice 1) — define and document a
   `clj`-based startup path (e.g. `clj -M:dev`) since `lein ring server` is removed.
7. **Whether to commit `package-lock.json`** (slice 1) — recommendation: yes.
8. **Whether `dev-programs` (test fixtures) become `.rw` files** (slice 3) — or
   inline test data, or `resources/test/programs/`.
9. **Test runner invocation** (slice 10) — how to run JVM + CLJS tests
   with one command; recommendation: small shell script or Makefile target.
10. **State atom shape** (slice 11) — recommendation: single atom holding
    game + UI state.
11. **Whether to expose functions on `js/window` for manual REPL/devtools
    testing** (slice 11).
12. **Canvas interop style** (slice 12) — direct interop vs. helper
    wrappers; recommendation: helpers.
13. **CLJS-JS bridge during slice 12** — recommendation: expose a CLJS
    function on `js/window.robotwar`, deleted by slice 13.
14. **DOM library choice** (slice 13) — `goog.dom`/`goog.events` vs. plain
    `js/document` interop; recommendation: plain interop.
15. **Build helper / Makefile / bb.edn** (slice 16) — recommendation: at
    least a `bb.edn` or `Makefile` with common targets.
16. **README depth** (slice 16) — minimum: install, dev, test, deploy.

---

## 7. Verification at the end

When all slices are done, the project should:
- Build to a static folder of HTML/CSS/JS/assets with `npx shadow-cljs release app`.
- Deploy to Netlify on every push to main.
- Be runnable locally via `npx shadow-cljs watch app` + visiting the
  configured port.
- Have `clj -M:test` and the CLJS test build both green.
- Have no Ring/Compojure/jQuery dependencies in the runtime bundle.
- Still let the user type program names and watch robots fight (with sound
  effects), with no per-tick network traffic.
- Still allow JVM-side inspection via `terminal.clj` and the
  `core.clj` REPL helpers.
- Have no implementation differences from the original game behavior — the
  engine semantics must be identical pre- and post-port. Bug-for-bug
  compatibility, including the unimplemented features listed in §3.
