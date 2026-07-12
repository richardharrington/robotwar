#!/usr/bin/env bash
set -euo pipefail

clojure -M:test
npx shadow-cljs compile test
node target/test/node-tests.js
