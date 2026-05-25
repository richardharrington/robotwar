# RobotWar

A reverse-engineered Clojure/ClojureScript implementation of Silas Warner’s 1981 game RobotWar.

## Current architecture

- Static app hosted from `public/`
- Browser runtime is ClojureScript (`shadow-cljs`)
- Engine namespaces are `.cljc` and shared by JVM + CLJS
- No Ring/Compojure backend runtime
- No jQuery/queue fetch loop

## Prerequisites

- Java (JDK 11+ recommended)
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Node.js + npm

## Install

```bash
git clone git@github.com:richardharrington/robotwar.git
cd robotwar
npm install
```

## Local development

Run CLJS watch build:

```bash
npm run watch
```

Then open `public/index.html` through a static server (or your editor/server workflow) and play.

## Tests

Unified test command:

```bash
npm test
```

This runs:
- JVM tests (`clj -M:test`)
- CLJS Node tests (`shadow-cljs compile test` + node runner)

## Build / release

Compile app once:

```bash
npx shadow-cljs compile app
```

Production build:

```bash
npx shadow-cljs release app
```

Compiled assets are written under `public/js/cljs-runtime/`.

## Program files and manifests

Programs are plain text files in:

- `public/programs/*.rw`

Manifest-driven discovery:

- `public/programs/programs-live.json` — programs shown in UI/runtime
- `public/programs/programs-test.json` — test/dev fixtures
- `public/programs/programs.json` — compatibility alias (mirrors live)

The app loads `programs-live.json` for selectable robots.

### Ordering expectations

Displayed program lists should be deterministic. Keep manifest program name order stable and intentional (typically lexicographic unless there is a deliberate UI ordering choice).

## Netlify

This repo includes `netlify.toml`:

- Build command: `npx shadow-cljs release app`
- Publish directory: `public/`

Connect repo in Netlify and deploy.

## Notes

Known engine gaps are intentionally preserved (for bug-for-bug compatibility), e.g. incomplete damage/radar behavior.
