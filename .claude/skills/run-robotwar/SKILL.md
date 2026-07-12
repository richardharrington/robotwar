---
name: run-robotwar
description: Build, run, and drive the RobotWar browser app headlessly. Use when asked to run or start the app, take screenshots of it, watch a battle, or verify a UI/rendering change in the real app.
---

RobotWar is a static browser app (plain CLJS + Canvas, no backend). Drive it
with `.claude/skills/run-robotwar/driver.mjs`, which serves `public/`, runs
headless system Chrome via playwright-core, optionally starts a battle, and
writes timed screenshots. All paths below are relative to the repo root.

## Prerequisites

macOS with Google Chrome installed at
`/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`
(override with `CHROME_PATH`), plus `python3` and Node. No browser download
needed — playwright-core drives the installed Chrome.

## Setup

One-time (and again if `node_modules` is missing):

```bash
npm install                                      # repo deps (shadow-cljs)
npm install --prefix .claude/skills/run-robotwar # driver dep (playwright-core)
```

## Build

```bash
npx shadow-cljs compile app
```

The driver fails fast with a message if `public/js/cljs-runtime/cljs-app.js`
is missing. If a `shadow-cljs watch app` is already running, skip this.

## Run (agent path)

Screenshot the input form only:

```bash
node .claude/skills/run-robotwar/driver.mjs
```

Start a battle and screenshot it at 0ms / 2s / 6s:

```bash
node .claude/skills/run-robotwar/driver.mjs \
  --programs "mover shooter top-shooter speedy" \
  --shots 0,2000,6000
```

| option | meaning |
|---|---|
| `--programs "<names>"` | space-separated program names typed into the form + Enter; omit to stay on the form |
| `--shots 0,2000,6000` | ms offsets (from battle start) at which to screenshot |
| `--out <dir>` | screenshot directory (default `.claude/skills/run-robotwar/screenshots/`, gitignored) |
| `--port <n>` | static-server port (default 3111) |

Each screenshot path is printed as it's written; console/page errors are
printed at the end and make the exit code non-zero. **Look at the
screenshots** (Read the PNGs) — a green empty arena border with no robots
means the battle didn't start.

Valid program names are whatever `public/programs/programs-live.json` lists —
currently `speedy, mover, left-shooter, top-shooter, shooter`.

## Run (human path)

```bash
npx shadow-cljs watch app     # dev build + hot reload
python3 -m http.server 3000 -d public
open http://localhost:3000    # type program names, press return. Ctrl-C both to stop.
```

## Test

```bash
npm test    # JVM tests (clj -M:test) + CLJS node tests
```

## Gotchas

- **No dev-http in shadow-cljs.edn** — `shadow-cljs watch` compiles but does
  not serve `public/`; you always need a separate static server. The driver
  spawns its own (`python3 -m http.server`) and kills it on exit.
- **Unknown program names are silently dropped**, not rejected: the app
  filters input against `programs-live.json` (`app.cljs`
  `valid-program-names`). `random.rw` exists on disk but is only in
  `programs-test.json`, so typing `random` just yields fewer robots.
- **`chromium-cli` is not on this machine** — that's why the driver uses
  playwright-core + system Chrome instead.
- **Audio is unverifiable headless.** Sound code runs (watch for console
  errors) but nothing is audible; don't claim sound works from a driver run.
- **Prefer `python3 -m http.server` over `npx serve`** — the latter fetches
  a package on first use.

## Troubleshooting

- **`Missing .../cljs-app.js — run: npx shadow-cljs compile app`**: the app
  was never built (or `public/js/cljs-runtime/` was cleaned). Build it.
- **`server never came up on :3111`**: port already in use from an earlier
  run — `pkill -f "http.server 3111"` or pass `--port`.
- **Chrome launch fails**: Chrome not at the default path; set `CHROME_PATH`
  to the browser binary.
