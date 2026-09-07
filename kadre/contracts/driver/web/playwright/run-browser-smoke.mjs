import { createServer } from 'node:http';
import { readFile, readdir, realpath, rm, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { basename, extname, join, relative, resolve, sep } from 'node:path';
import { spawn } from 'node:child_process';

const POSIX_TERM_GRACE_MILLISECONDS = 1_000;
const POSIX_KILL_GRACE_MILLISECONDS = 1_000;
const PROCESS_TREE_TERMINATION_TIMEOUT_MILLISECONDS = 5_000;
const CHILD_CLOSE_TIMEOUT_MILLISECONDS = 5_000;
const WINDOWS_TASKKILL_TIMEOUT_MILLISECONDS = 3_000;
const WINDOWS_TASKKILL_REAP_TIMEOUT_MILLISECONDS = 1_000;
const signalExitCodes = { SIGINT: 2, SIGTERM: 15 };

class SmokeInterruptedError extends Error {
  constructor(signal, cleanupError) {
    super(`Playwright browser smoke interrupted by ${signal}`);
    this.signal = signal;
    this.cleanupError = cleanupError;
  }
}

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
let interruptedSignal;
let interruptedCleanupError;

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
} catch (error) {
  if (error instanceof SmokeInterruptedError) {
    interruptedSignal = error.signal;
    interruptedCleanupError = error.cleanupError;
  } else {
    throw error;
  }
} finally {
  if (passed) await rm(playwrightOutput, { force: true, recursive: true });
  await new Promise((resolveClose, rejectClose) => {
    server.instance.close((error) => error ? rejectClose(error) : resolveClose());
  });
}

if (interruptedSignal) {
  if (interruptedCleanupError) {
    console.error(`Playwright cleanup after ${interruptedSignal} failed: ${interruptedCleanupError.message}`);
  }
  exitForSignal(interruptedSignal, !interruptedCleanupError);
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
    let child;
    let timer;
    let settled = false;
    let cleanup;
    let resolveChildClose;
    const childClose = new Promise((resolveClose) => {
      resolveChildClose = resolveClose;
    });
    const signalHandlers = new Map([
      ['SIGINT', () => scheduleCleanup({ type: 'signal', signal: 'SIGINT' })],
      ['SIGTERM', () => scheduleCleanup({ type: 'signal', signal: 'SIGTERM' })],
    ]);

    for (const [signal, handler] of signalHandlers) process.on(signal, handler);

    const finish = (completion) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      for (const [signal, handler] of signalHandlers) process.off(signal, handler);
      completion();
    };
    const finishFailure = (error) => finish(() => rejectRun(error));
    const finishSuccess = (result) => finish(() => resolveRun(result));

    const scheduleCleanup = (reason) => {
      if (cleanup) return cleanup;
      cleanup = cleanUp(reason).then(
        () => {
          if (reason.type === 'exit-failure') {
            finishSuccess({ status: reason.status });
            return;
          }
          finishFailure(cleanupFailure(reason));
        },
        (error) => finishFailure(cleanupFailure(reason, error)),
      );
      return cleanup;
    };

    const cleanUp = async () => {
      const failures = [];
      if (child?.pid) {
        try {
          await withDeadline(
            terminateProcessTree(child.pid),
            PROCESS_TREE_TERMINATION_TIMEOUT_MILLISECONDS,
            `process-tree termination for ${child.pid}`,
          );
        } catch (error) {
          failures.push(error);
        }
      }
      try {
        await withDeadline(
          childClose,
          CHILD_CLOSE_TIMEOUT_MILLISECONDS,
          'Playwright child close observation',
        );
      } catch (error) {
        failures.push(error);
      }
      if (failures.length > 0) throw new AggregateError(failures, 'browser smoke cleanup did not complete');
    };

    try {
      child = spawn(command, argumentsList, {
        ...options,
        detached: process.platform !== 'win32',
      });
    } catch (error) {
      finishFailure(error);
      return;
    }

    child.once('error', (error) => {
      if (!child.pid) {
        finishFailure(error);
        return;
      }
      scheduleCleanup({ type: 'spawn-error', error });
    });
    child.once('close', (status) => {
      resolveChildClose();
      if (settled || cleanup) return;
      if (status === 0) {
        finishSuccess({ status });
        return;
      }
      scheduleCleanup({ type: 'exit-failure', status });
    });
    timer = setTimeout(() => {
      scheduleCleanup({ type: 'watchdog', timeout });
    }, timeout);
  });
}

async function terminateProcessTree(pid) {
  if (!pid) return;
  if (process.platform === 'win32') {
    await terminateWindowsProcessTree(pid);
    return;
  }
  const failures = [];
  try {
    process.kill(-pid, 'SIGTERM');
  } catch (error) {
    if (error.code === 'ESRCH') return;
    failures.push(error);
  }
  try {
    if (await waitForProcessGroupExit(pid, POSIX_TERM_GRACE_MILLISECONDS)) return;
  } catch (error) {
    failures.push(error);
  }
  try {
    process.kill(-pid, 'SIGKILL');
  } catch (error) {
    if (error.code === 'ESRCH') return;
    failures.push(error);
  }
  try {
    if (!await waitForProcessGroupExit(pid, POSIX_KILL_GRACE_MILLISECONDS)) {
      failures.push(new Error(`process group ${pid} remained alive after SIGKILL`));
    }
  } catch (error) {
    failures.push(error);
  }
  if (failures.length > 0) throw new AggregateError(failures, `could not fully terminate process group ${pid}`);
}

async function terminateWindowsProcessTree(pid) {
  let helper;
  try {
    helper = spawn('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
  } catch (error) {
    throw new Error(`could not start taskkill for ${pid}: ${error.message}`, { cause: error });
  }
  const helperClosed = new Promise((resolveClose, rejectClose) => {
    helper.once('error', rejectClose);
    helper.once('close', (status) => resolveClose(status));
  });
  try {
    const status = await withDeadline(
      helperClosed,
      WINDOWS_TASKKILL_TIMEOUT_MILLISECONDS,
      `taskkill helper for ${pid}`,
    );
    if (status !== 0) throw new Error(`taskkill for ${pid} exited with status ${status}`);
  } catch (error) {
    try {
      helper.kill('SIGKILL');
    } catch (killError) {
      if (killError.code !== 'ESRCH') throw new AggregateError([error, killError], `taskkill helper for ${pid} could not be stopped`);
    }
    try {
      await withDeadline(helperClosed.catch(() => undefined), WINDOWS_TASKKILL_REAP_TIMEOUT_MILLISECONDS, `taskkill helper reaping for ${pid}`);
    } catch {
      // The helper was explicitly killed; its bounded reaping failure is already represented by the original taskkill failure.
    }
    throw error;
  }
}

async function waitForProcessGroupExit(pid, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try {
      process.kill(-pid, 0);
    } catch (error) {
      if (error.code === 'ESRCH') return true;
      throw error;
    }
    await delay(25);
  }
  try {
    process.kill(-pid, 0);
    return false;
  } catch (error) {
    if (error.code === 'ESRCH') return true;
    throw error;
  }
}

function withDeadline(promise, timeout, operation) {
  return new Promise((resolveResult, rejectResult) => {
    const timer = setTimeout(() => {
      rejectResult(new Error(`${operation} exceeded ${timeout}ms`));
    }, timeout);
    promise.then(
      (result) => {
        clearTimeout(timer);
        resolveResult(result);
      },
      (error) => {
        clearTimeout(timer);
        rejectResult(error);
      },
    );
  });
}

function cleanupFailure(reason, cleanupError) {
  if (reason.type === 'signal') return new SmokeInterruptedError(reason.signal, cleanupError);
  if (cleanupError) {
    return new Error(`Playwright cleanup after ${cleanupDescription(reason)} failed: ${cleanupError.message}`, { cause: cleanupError });
  }
  if (reason.type === 'watchdog') {
    return new Error(`Playwright exceeded configured timeout of ${reason.timeout}ms; terminated its process tree`);
  }
  if (reason.type === 'spawn-error') return reason.error;
  return new Error(`unexpected browser smoke cleanup reason: ${reason.type}`);
}

function cleanupDescription(reason) {
  if (reason.type === 'signal') return `${reason.signal} signal`;
  if (reason.type === 'watchdog') return `the ${reason.timeout}ms watchdog`;
  if (reason.type === 'spawn-error') return 'a Playwright spawn error';
  return 'a Playwright failure';
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

function exitForSignal(signal, safeToResignal) {
  if (safeToResignal && process.listenerCount(signal) === 0) {
    try {
      process.kill(process.pid, signal);
      return;
    } catch {
      // Fall through to the conventional numeric exit status below.
    }
  }
  process.exitCode = 128 + signalExitCodes[signal];
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
