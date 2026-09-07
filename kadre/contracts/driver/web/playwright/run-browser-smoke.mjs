import { createServer } from 'node:http';
import { readFile, readdir, realpath, rm, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { basename, extname, join, relative, resolve, sep } from 'node:path';
import { spawn } from 'node:child_process';

const argumentsByName = new Map(process.argv.slice(2).map((argument) => {
  const [name, value] = argument.split('=', 2);
  return [name, value];
}));
const target = argumentsByName.get('--target');
const distribution = argumentsByName.get('--distribution');
const evidence = argumentsByName.get('--evidence');
const timeoutMilliseconds = parseTimeout(argumentsByName.get('--timeout-ms'));

if (!['js', 'wasmJs'].includes(target) || !distribution || !evidence) {
  throw new Error('expected --target=js|wasmJs, --distribution=<directory>, and --evidence=<directory>');
}
if (!existsSync(distribution)) {
  throw new Error(`missing ${target} browser distribution: ${distribution}`);
}

const junitDirectory = join(evidence, 'test-results', 'browser', 'chromium');
const junitOutput = join(junitDirectory, 'TEST-web-phase0.xml');
const playwrightOutput = join(evidence, 'diagnostics', 'playwright');
const distributionRoot = await realpath(distribution);
const entryScript = await findEntryScript(distributionRoot);
const server = await serveDistribution(distributionRoot, entryScript);
let passed = false;

try {
  const command = process.platform === 'win32' ? 'node_modules/.bin/playwright.cmd' : 'node_modules/.bin/playwright';
  const result = await runWithWatchdog(command, ['test', '--config', 'playwright/playwright.config.mjs'], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      KADRE_FIXTURE_URL: `${server.url}/index.html`,
      KADRE_JUNIT_OUTPUT: junitOutput,
      KADRE_PLAYWRIGHT_OUTPUT_DIR: playwrightOutput,
    },
    stdio: 'inherit',
  }, timeoutMilliseconds);
  if (result.status !== 0) {
    throw new Error(`Playwright ${target} smoke failed with exit status ${result.status}`);
  }
  const junit = await readFile(junitOutput, 'utf8');
  if (/<(?:failure|error|skipped)\b|(?:failures|errors|skipped)="[1-9]\d*"/.test(junit)) {
    throw new Error(`Playwright ${target} smoke reported a failure, error, or skip in ${junitOutput}`);
  }
  passed = true;
} finally {
  if (passed) await rm(playwrightOutput, { force: true, recursive: true });
  await new Promise((resolveClose, rejectClose) => {
    server.instance.close((error) => error ? rejectClose(error) : resolveClose());
  });
}

function parseTimeout(value) {
  if (value === undefined) return 90_000;
  if (!/^\d+$/.test(value) || Number(value) === 0) {
    throw new Error('--timeout-ms must be a positive integer');
  }
  return Number(value);
}

function runWithWatchdog(command, argumentsList, options, timeout) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, argumentsList, {
      ...options,
      detached: process.platform !== 'win32',
    });
    let settled = false;
    let timedOut = false;
    let termination = Promise.resolve();
    const timer = setTimeout(() => {
      timedOut = true;
      termination = terminateProcessTree(child.pid);
    }, timeout);

    const settle = async (callback) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      await callback();
    };

    child.once('error', (error) => {
      void settle(async () => {
        await terminateProcessTree(child.pid);
        rejectRun(error);
      });
    });
    child.once('close', (status) => {
      void settle(async () => {
        if (timedOut) {
          await termination;
          rejectRun(new Error(`Playwright exceeded configured timeout of ${timeout}ms; terminated its process tree`));
          return;
        }
        if (status !== 0) await terminateProcessTree(child.pid);
        resolveRun({ status });
      });
    });
  });
}

async function terminateProcessTree(pid) {
  if (!pid) return;
  if (process.platform === 'win32') {
    await new Promise((resolveTermination) => {
      const killer = spawn('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
      killer.once('error', resolveTermination);
      killer.once('close', resolveTermination);
    });
    return;
  }
  try {
    process.kill(-pid, 'SIGTERM');
  } catch (error) {
    if (error.code === 'ESRCH') return;
    throw error;
  }
  await new Promise((resolveGrace) => setTimeout(resolveGrace, 1_000));
  try {
    process.kill(-pid, 'SIGKILL');
  } catch (error) {
    if (error.code !== 'ESRCH') throw error;
  }
}

async function findEntryScript(directory) {
  const scripts = await findFiles(directory, (path) => extname(path) === '.js');
  if (scripts.length !== 1) {
    throw new Error(`expected exactly one JavaScript entry bundle in ${directory}, found ${scripts.length}: ${scripts.join(', ')}`);
  }
  return relative(directory, scripts[0]).split(sep).join('/');
}

async function findFiles(directory, matches) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return findFiles(path, matches);
    return matches(path) ? [path] : [];
  }));
  return files.flat().sort();
}

async function serveDistribution(realRoot, entryScript) {
  const instance = createServer(async (request, response) => {
    try {
      const requestedPath = request.url === '/index.html'
        ? null
        : resolve(realRoot, `.${new URL(request.url, 'http://127.0.0.1').pathname}`);
      if (requestedPath === null) {
        response.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
        response.end(`<!doctype html><html><body><script src="/${entryScript}"></script></body></html>`);
        return;
      }
      if (!isWithin(realRoot, requestedPath)) {
        response.writeHead(404).end();
        return;
      }
      const realFile = await realpath(requestedPath);
      if (!isWithin(realRoot, realFile) || !(await stat(realFile)).isFile()) {
        response.writeHead(404).end();
        return;
      }
      response.writeHead(200, { 'content-type': contentType(realFile) });
      response.end(await readFile(realFile));
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolveListen, rejectListen) => {
    instance.once('error', rejectListen);
    instance.listen(0, '127.0.0.1', () => resolveListen());
  });
  const address = instance.address();
  return { instance, url: `http://127.0.0.1:${address.port}` };
}

function isWithin(root, path) {
  return path.startsWith(`${root}${sep}`);
}

function contentType(path) {
  switch (extname(basename(path))) {
    case '.js': return 'text/javascript; charset=utf-8';
    case '.wasm': return 'application/wasm';
    case '.map': return 'application/json; charset=utf-8';
    default: return 'application/octet-stream';
  }
}
