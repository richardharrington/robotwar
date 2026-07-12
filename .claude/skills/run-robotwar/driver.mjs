#!/usr/bin/env node
// Headless driver for the RobotWar browser app.
// Serves public/ on a local port, drives it with playwright-core against
// system Chrome, and writes timed screenshots. See SKILL.md for usage.

import { spawn } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const skillDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(skillDir, '../../..');
const publicDir = path.join(repoRoot, 'public');

const args = process.argv.slice(2);
const opt = (name, dflt) => {
  const i = args.indexOf(`--${name}`);
  return i !== -1 && args[i + 1] !== undefined ? args[i + 1] : dflt;
};

const programs = opt('programs', null); // e.g. "mover shooter speedy"
const shots = opt('shots', '0,2000,6000').split(',').map(Number);
const outDir = path.resolve(opt('out', path.join(skillDir, 'screenshots')));
const port = Number(opt('port', '3111'));
const chromePath = process.env.CHROME_PATH
  || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

const builtJs = path.join(publicDir, 'js/cljs-runtime/cljs-app.js');
if (!existsSync(builtJs)) {
  console.error(`Missing ${builtJs} — run: npx shadow-cljs compile app`);
  process.exit(1);
}
mkdirSync(outDir, { recursive: true });

const server = spawn('python3', ['-m', 'http.server', String(port)], {
  cwd: publicDir,
  stdio: 'ignore',
});

const errors = [];
let browser;
try {
  const base = `http://localhost:${port}`;
  const deadline = Date.now() + 15000;
  for (;;) {
    try { await fetch(base); break; }
    catch {
      if (Date.now() > deadline) throw new Error(`server never came up on :${port}`);
      await new Promise((r) => setTimeout(r, 200));
    }
  }

  browser = await chromium.launch({ executablePath: chromePath, headless: true });
  const page = await browser.newPage({ viewport: { width: 1100, height: 900 } });
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push(String(e)));

  await page.goto(base);
  // Manifest fetch has resolved once the available-robots list is populated.
  await page.waitForFunction(() =>
    document.getElementById('programNames').textContent.trim().length > 0);

  if (programs) {
    await page.fill('#programsInput', programs);
    await page.press('#programsInput', 'Enter');
  }

  let elapsed = 0;
  for (const ms of shots) {
    await page.waitForTimeout(Math.max(0, ms - elapsed));
    elapsed = Math.max(elapsed, ms);
    const file = path.join(outDir, `shot-${String(ms).padStart(5, '0')}ms.png`);
    await page.screenshot({ path: file });
    console.log(file);
  }

  console.log('console errors:', errors.length ? errors : 'none');
} finally {
  await browser?.close();
  server.kill();
}
process.exit(errors.length ? 1 : 0);
