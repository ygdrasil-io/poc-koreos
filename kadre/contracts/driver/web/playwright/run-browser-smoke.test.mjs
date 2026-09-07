import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import {
  access,
  chmod,
  mkdtemp,
  mkdir,
  readFile,
  readdir,
  rm,
  writeFile,
} from 'node:fs/promises';
import { constants as fsConstants, watch } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const runner = resolve(dirname(fileURLToPath(import.meta.url)), 'run-browser-smoke.mjs');
const signalExitCodes = { SIGINT: 130, SIGTERM: 143 };
const validJunit = '<?xml version="1.0"?><testsuites tests="1" failures="0" errors="0" skipped="0"><testsuite name="web" tests="1" failures="0" errors="0" skipped="0"><testcase name="attaches" classname="web-phase0"/></testsuite></testsuites>';

test('launch construction uses a shell for the Windows Playwright cmd shim only', async () => {
  const { createPlaywrightLaunch } = await import('./browser-smoke-launch.mjs');
  const baseOptions = { cwd: '/work', stdio: 'inherit' };

  assert.deepEqual(createPlaywrightLaunch('win32', baseOptions), {
    argumentsList: ['test', '--config', 'playwright/playwright.config.mjs'],
    command: 'node_modules/.bin/playwright.cmd',
    options: { ...baseOptions, detached: false, shell: true },
  });
  assert.deepEqual(createPlaywrightLaunch('linux', baseOptions), {
    argumentsList: ['test', '--config', 'playwright/playwright.config.mjs'],
    command: 'node_modules/.bin/playwright',
    options: { ...baseOptions, detached: true },
  });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  test(`${signal} overrides a watchdog after process-tree cleanup has started`, {
    skip: process.platform === 'win32' && 'Windows does not deliver POSIX signals to a child as catchable console signals',
    timeout: 10_000,
  }, async (context) => {
    const fixture = await createFixture('watchdog');
    context.after(() => fixture.dispose());

    const execution = runSmoke(fixture, ['--timeout-ms=500']);
    await waitForFileWhileRunning(fixture.childReady, execution, 4_000);
    await waitForFileWhileRunning(fixture.cleanupStarted, execution, 4_000);
    execution.child.kill(signal);

    const result = await execution.completion;
    assert.equal(normalizedExitStatus(result), signalExitCodes[signal], result.stderr);
  });
}

for (const [firstSignal, secondSignal] of [['SIGINT', 'SIGTERM'], ['SIGTERM', 'SIGINT']]) {
  test(`${secondSignal} overrides an earlier ${firstSignal} while cleanup is running`, {
    skip: process.platform === 'win32' && 'Windows does not deliver POSIX signals to a child as catchable console signals',
    timeout: 10_000,
  }, async (context) => {
    const fixture = await createFixture('watchdog');
    context.after(() => fixture.dispose());

    const execution = runSmoke(fixture, ['--timeout-ms=500']);
    await waitForFileWhileRunning(fixture.childReady, execution, 4_000);
    await waitForFileWhileRunning(fixture.cleanupStarted, execution, 4_000);
    execution.child.kill(firstSignal);
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 50));
    execution.child.kill(secondSignal);

    const result = await execution.completion;
    assert.equal(normalizedExitStatus(result), signalExitCodes[secondSignal], result.stderr);
  });
}

test('a successful Playwright child reaps a descendant left in its process group', {
  skip: process.platform === 'win32' && 'Windows has no POSIX process-group probe',
  timeout: 10_000,
}, async (context) => {
  const fixture = await createFixture('success-with-descendant');
  context.after(() => fixture.dispose());

  const execution = runSmoke(fixture);
  await waitForFileWhileRunning(fixture.descendantReady, execution, 4_000);
  const result = await execution.completion;

  assert.equal(normalizedExitStatus(result), 0, result.stderr);
  await waitForFile(fixture.descendantTerminated, 1_000);
  assert.equal(processExists(await readPid(fixture.descendantPid)), false, 'successful runner left a process-group descendant alive');
});

test('a partial HTTP request is held through graceful drain and closed by forced teardown', {
  timeout: 10_000,
}, async (context) => {
  const fixture = await createFixture('held-connection');
  context.after(() => fixture.dispose());
  await mkdir(fixture.preservedDiagnosticTraceDirectory, { recursive: true });
  await writeFile(fixture.preservedDiagnosticMarker, 'stale diagnostic evidence');

  const startedAt = Date.now();
  const execution = runSmoke(fixture);
  await waitForFileWhileRunning(fixture.connectionOpened, execution, 4_000);
  const result = await execution.completion;
  const elapsed = Date.now() - startedAt;

  assert.equal(normalizedExitStatus(result), 0, result.stderr);
  assert.ok(elapsed >= 750, `server drained before its graceful deadline (${elapsed}ms)`);
  assert.ok(elapsed < 4_000, `server teardown exceeded its bounded drains (${elapsed}ms)`);
  await waitForFile(fixture.connectionClosed, 1_000);
  await assert.rejects(access(fixture.diagnostics, fsConstants.F_OK), { code: 'ENOENT' });
  await assert.rejects(access(fixture.preservedDiagnostics, fsConstants.F_OK), { code: 'ENOENT' });
});

test('a diagnostic-removal failure preserves diagnostics and still tears down the server', {
  skip: process.platform === 'win32' && 'Windows chmod does not provide a deterministic remove failure',
  timeout: 10_000,
}, async (context) => {
  const fixture = await createFixture('remove-failure');
  context.after(async () => {
    await chmod(fixture.diagnosticsParent, 0o700).catch(() => undefined);
    await fixture.dispose();
  });

  const startedAt = Date.now();
  const execution = runSmoke(fixture);
  await waitForFileWhileRunning(fixture.connectionOpened, execution, 4_000);
  const result = await withTimeout(execution.completion, 4_000, async () => {
    await stopProcess(execution.child);
    throw new Error('runner remained alive after diagnostic removal failed');
  });

  assert.notEqual(normalizedExitStatus(result), 0, 'a finalization failure must fail the smoke');
  assert.match(result.stderr, /EACCES|EPERM/, 'the runner must surface the diagnostic removal failure');
  assert.match(result.stderr, /playwright-preserved/, 'the runner must report the preserved diagnostic path');
  assert.ok(Date.now() - startedAt >= 750, 'diagnostic removal ran before bounded server teardown');
  assert.equal(await readFile(fixture.preservedDiagnosticMarker, 'utf8'), 'diagnostic evidence');
  await waitForFile(fixture.connectionClosed, 1_000);
});

test('a quarantine deletion failure restores complete diagnostics to the visible preservation path', {
  skip: process.platform === 'win32' && 'Windows chmod does not provide a deterministic remove failure',
  timeout: 20_000,
}, async (context) => {
  const fixture = await createFixture('quarantine-remove-failure');
  const execution = runSmoke(fixture);
  context.after(async () => {
    await writeFile(fixture.preservationRelease, 'release').catch(() => undefined);
    await stopProcess(execution.child);
    await chmod(fixture.preservedDiagnostics, 0o700).catch(() => undefined);
    await fixture.dispose();
  });

  await waitForFileWhileRunning(fixture.preservationPublished, execution, 10_000);
  await chmod(fixture.preservedDiagnostics, 0o500);
  await writeFile(fixture.preservationRelease, 'release');

  const result = await execution.completion;

  assert.notEqual(normalizedExitStatus(result), 0, 'a quarantine deletion failure must fail the smoke');
  assert.match(result.stderr, /EACCES|EPERM/, 'the runner must surface the quarantine deletion failure');
  assert.equal(await readFile(fixture.preservedDiagnosticMarker, 'utf8'), 'diagnostic evidence');
  assert.equal((await readdir(fixture.preservedDiagnosticBulkDirectory)).length, 2_500);
  assert.equal(
    (await readdir(fixture.diagnosticsParent)).some((entry) => entry.startsWith('.playwright-delete-')),
    false,
    'the only diagnostic copy must not remain at a hidden random path',
  );
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  test(`${signal} during asynchronous diagnostic preservation retains complete visible diagnostics`, {
    skip: process.platform === 'win32' && 'Windows does not deliver POSIX signals to a child as catchable console signals',
    timeout: 20_000,
  }, async (context) => {
    const fixture = await createFixture('signal-during-preservation');
    await mkdir(fixture.diagnosticsParent, { recursive: true });
    const preservationStarted = watchForEntry(fixture.diagnosticsParent, '.playwright-preservation-');
    const execution = runSmoke(fixture);
    context.after(async () => {
      preservationStarted.close();
      await stopProcess(execution.child);
      await fixture.dispose();
    });

    await waitForEntryWhileRunning(preservationStarted.completion, execution, 10_000);
    execution.child.kill(signal);
    const result = await execution.completion;

    assert.equal(normalizedExitStatus(result), signalExitCodes[signal], result.stderr);
    assert.equal(await readFile(fixture.preservedDiagnosticMarker, 'utf8'), 'diagnostic evidence');
    assert.equal((await readdir(fixture.preservedDiagnosticBulkDirectory)).length, 2_500);
  });
}

for (const [name, report, expectedError] of [
  ['malformed', '<testsuites tests="1"><testsuite><testcase></testsuites>', /not parseable/],
  ['empty', '<testsuites tests="0" failures="0" errors="0" skipped="0"></testsuites>', /at least one testsuite/],
  ['aborted', '<testsuites tests="1" failures="0" errors="0" skipped="0"><testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="case" status="aborted"/></testsuite></testsuites>', /has aborted status/],
  ['unknown', '<testsuites tests="1" failures="0" errors="0" skipped="0"><testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="case" status="unknown"/></testsuite></testsuites>', /has unknown status/],
  ['count-mismatched', '<testsuites tests="2" failures="0" errors="0" skipped="0"><testsuite tests="2" failures="0" errors="0" skipped="0"><testcase name="only-case"/></testsuite></testsuites>', /declares 2 tests but contains 1 testcase/],
]) {
  test(`rejects ${name} JUnit and retains diagnostics`, { timeout: 10_000 }, async (context) => {
    const fixture = await createFixture('report', report);
    context.after(() => fixture.dispose());

    const result = await runSmoke(fixture).completion;

    assert.notEqual(normalizedExitStatus(result), 0, `${name} JUnit report was accepted`);
    assert.match(result.stderr, expectedError, result.stderr);
    assert.equal(await readFile(fixture.diagnosticMarker, 'utf8'), 'diagnostic evidence');
  });
}

async function createFixture(scenario, junit = validJunit) {
  const root = await mkdtemp(join(tmpdir(), 'kadre-browser-runner-'));
  const distribution = join(root, 'distribution');
  const evidence = join(root, 'evidence');
  const binDirectory = join(root, 'node_modules', '.bin');
  const fakePlaywright = join(root, 'fake-playwright.cjs');
  const heldClient = join(root, 'held-client.cjs');
  const descendant = join(root, 'descendant.cjs');
  const childReady = join(root, 'child-ready');
  const cleanupStarted = join(root, 'cleanup-started');
  const connectionOpened = join(root, 'connection-opened');
  const connectionClosed = join(root, 'connection-closed');
  const heldClientPid = join(root, 'held-client.pid');
  const descendantPid = join(root, 'descendant.pid');
  const descendantReady = join(root, 'descendant-ready');
  const descendantTerminated = join(root, 'descendant-terminated');
  const preservationPause = join(root, 'preservation-pause.cjs');
  const preservationPublished = join(root, 'preservation-published');
  const preservationRelease = join(root, 'preservation-release');
  const diagnostics = join(evidence, 'diagnostics', 'playwright');
  const diagnosticsParent = dirname(diagnostics);
  const diagnosticTraceDirectory = join(diagnostics, 'trace');
  const diagnosticMarker = join(diagnosticTraceDirectory, 'trace.txt');
  const preservedDiagnostics = join(evidence, 'diagnostics', 'playwright-preserved');
  const preservedDiagnosticTraceDirectory = join(preservedDiagnostics, 'trace');
  const preservedDiagnosticMarker = join(preservedDiagnosticTraceDirectory, 'trace.txt');
  const preservedDiagnosticBulkDirectory = join(preservedDiagnostics, 'bulk');

  await mkdir(distribution, { recursive: true });
  await mkdir(binDirectory, { recursive: true });
  await writeFile(join(distribution, 'fixture.js'), 'globalThis.kadreFixture = true;\n');
  await writeFile(heldClient, heldClientSource, { mode: 0o755 });
  await writeFile(descendant, descendantSource, { mode: 0o755 });
  await writeFile(fakePlaywright, fakePlaywrightSource, { mode: 0o755 });
  await writeFile(preservationPause, preservationPauseSource, { mode: 0o755 });

  if (process.platform === 'win32') {
    await writeFile(
      join(binDirectory, 'playwright.cmd'),
      `@"${process.execPath}" "${fakePlaywright}" %*\r\n`,
    );
  } else {
    const executable = join(binDirectory, 'playwright');
    await writeFile(executable, `#!/bin/sh\nexec "${process.execPath}" "${fakePlaywright}" "$@"\n`, { mode: 0o755 });
    await chmod(executable, 0o755);
  }

  return {
    childReady,
    cleanupStarted,
    connectionClosed,
    connectionOpened,
    descendantPid,
    descendantReady,
    descendantTerminated,
    diagnosticMarker,
    diagnosticTraceDirectory,
    diagnostics,
    diagnosticsParent,
    distribution,
    evidence,
    preservedDiagnosticMarker,
    preservedDiagnosticBulkDirectory,
    preservedDiagnosticTraceDirectory,
    preservedDiagnostics,
    preservationPublished,
    preservationRelease,
    root,
    async dispose() {
      try {
        const pid = Number(await readFile(heldClientPid, 'utf8'));
        if (Number.isInteger(pid) && pid > 0) process.kill(pid, 'SIGKILL');
      } catch (error) {
        if (error.code !== 'ENOENT' && error.code !== 'ESRCH') throw error;
      }
      try {
        const pid = await readPid(descendantPid);
        if (pid > 0) process.kill(pid, 'SIGKILL');
      } catch (error) {
        if (error.code !== 'ENOENT' && error.code !== 'ESRCH') throw error;
      }
      await chmod(diagnosticsParent, 0o700).catch(() => undefined);
      await chmod(diagnosticTraceDirectory, 0o700).catch(() => undefined);
      await chmod(preservedDiagnostics, 0o700).catch(() => undefined);
      await chmod(preservedDiagnosticTraceDirectory, 0o700).catch(() => undefined);
      await rm(root, { force: true, recursive: true });
    },
    environment: {
      KADRE_RUNNER_TEST_CLEANUP_STARTED: cleanupStarted,
      KADRE_RUNNER_TEST_CHILD_READY: childReady,
      KADRE_RUNNER_TEST_CONNECTION_CLOSED: connectionClosed,
      KADRE_RUNNER_TEST_CONNECTION_OPENED: connectionOpened,
      KADRE_RUNNER_TEST_DESCENDANT: descendant,
      KADRE_RUNNER_TEST_DESCENDANT_PID: descendantPid,
      KADRE_RUNNER_TEST_DESCENDANT_READY: descendantReady,
      KADRE_RUNNER_TEST_DESCENDANT_TERMINATED: descendantTerminated,
      KADRE_RUNNER_TEST_HELD_CLIENT: heldClient,
      KADRE_RUNNER_TEST_HELD_CLIENT_PID: heldClientPid,
      KADRE_RUNNER_TEST_JUNIT: junit,
      KADRE_RUNNER_TEST_PAUSE_AFTER_RENAME: scenario === 'quarantine-remove-failure'
        ? preservedDiagnostics
        : '',
      KADRE_RUNNER_TEST_PRESERVATION_PUBLISHED: preservationPublished,
      KADRE_RUNNER_TEST_PRESERVATION_RELEASE: preservationRelease,
      KADRE_RUNNER_TEST_SCENARIO: scenario,
      ...(scenario === 'quarantine-remove-failure' ? {
        NODE_OPTIONS: [process.env.NODE_OPTIONS, `--require=${preservationPause}`].filter(Boolean).join(' '),
      } : {}),
    },
  };
}

function runSmoke(fixture, extraArguments = []) {
  const child = spawn(process.execPath, [
    runner,
    '--target=js',
    `--distribution=${fixture.distribution}`,
    `--evidence=${fixture.evidence}`,
    ...extraArguments,
  ], {
    cwd: fixture.root,
    env: { ...process.env, ...fixture.environment },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stdout = '';
  let stderr = '';
  child.stdout.setEncoding('utf8').on('data', (chunk) => { stdout += chunk; });
  child.stderr.setEncoding('utf8').on('data', (chunk) => { stderr += chunk; });
  return {
    child,
    completion: new Promise((resolveCompletion, rejectCompletion) => {
      child.once('error', rejectCompletion);
      child.once('close', (status, signal) => resolveCompletion({ signal, status, stderr, stdout }));
    }),
  };
}

function normalizedExitStatus({ signal, status }) {
  if (status !== null) return status;
  return signalExitCodes[signal];
}

async function waitForFile(path, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try {
      await access(path, fsConstants.F_OK);
      return;
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 20));
  }
  throw new Error(`timed out waiting for ${path}`);
}

function waitForFileWhileRunning(path, execution, timeout) {
  return Promise.race([
    waitForFile(path, timeout),
    execution.completion.then((result) => {
      throw new Error(`runner exited before creating ${path}: ${result.stderr}`);
    }),
  ]);
}

function watchForEntry(directory, prefix) {
  let resolveEntry;
  let rejectEntry;
  const completion = new Promise((resolveCompletion, rejectCompletion) => {
    resolveEntry = resolveCompletion;
    rejectEntry = rejectCompletion;
  });
  const watcher = watch(directory, { persistent: false }, (_event, filename) => {
    if (filename && String(filename).startsWith(prefix)) {
      watcher.close();
      resolveEntry(join(directory, String(filename)));
    }
  });
  watcher.once('error', rejectEntry);
  return { close: () => watcher.close(), completion };
}

function waitForEntryWhileRunning(entry, execution, timeout) {
  return withTimeout(Promise.race([
    entry,
    execution.completion.then((result) => {
      throw new Error(`runner exited before creating the watched diagnostic entry: ${result.stderr}`);
    }),
  ]), timeout, async () => {
    await stopProcess(execution.child);
    throw new Error('timed out waiting for the watched diagnostic entry');
  });
}

function withTimeout(promise, timeout, onTimeout) {
  return Promise.race([
    promise,
    new Promise((resolveTimeout, rejectTimeout) => {
      const timer = setTimeout(() => {
        Promise.resolve(onTimeout()).then(resolveTimeout, rejectTimeout);
      }, timeout);
      promise.finally(() => clearTimeout(timer)).catch(() => undefined);
    }),
  ]);
}

async function stopProcess(child) {
  if (child.exitCode !== null || child.signalCode !== null) return;
  child.kill('SIGKILL');
  await new Promise((resolveClose) => child.once('close', resolveClose));
}

async function readPid(path) {
  const pid = Number(await readFile(path, 'utf8'));
  assert.ok(Number.isInteger(pid) && pid > 0, `invalid pid in ${path}`);
  return pid;
}

function processExists(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    if (error.code === 'ESRCH') return false;
    throw error;
  }
}

const fakePlaywrightSource = String.raw`
const { chmodSync, existsSync, mkdirSync, writeFileSync } = require('node:fs');
const { dirname, join } = require('node:path');
const { spawn } = require('node:child_process');

const scenario = process.env.KADRE_RUNNER_TEST_SCENARIO;

if (scenario === 'watchdog') {
  process.on('SIGTERM', () => {
    writeFileSync(process.env.KADRE_RUNNER_TEST_CLEANUP_STARTED, 'cleanup started');
  });
  writeFileSync(process.env.KADRE_RUNNER_TEST_CHILD_READY, 'child ready');
  setInterval(() => undefined, 1_000);
} else if (scenario === 'held-connection' || scenario === 'remove-failure') {
  const helper = spawn(process.execPath, [process.env.KADRE_RUNNER_TEST_HELD_CLIENT], {
    detached: true,
    env: process.env,
    stdio: 'ignore',
  });
  writeFileSync(process.env.KADRE_RUNNER_TEST_HELD_CLIENT_PID, String(helper.pid));
  helper.unref();
  waitForConnection().then(() => {
    writeResults();
  });
} else if (scenario === 'success-with-descendant') {
  const descendant = spawn(process.execPath, [process.env.KADRE_RUNNER_TEST_DESCENDANT], {
    detached: false,
    env: process.env,
    stdio: 'ignore',
  });
  writeFileSync(process.env.KADRE_RUNNER_TEST_DESCENDANT_PID, String(descendant.pid));
  descendant.unref();
  waitForFile(process.env.KADRE_RUNNER_TEST_DESCENDANT_READY, 'descendant did not start').then(writeResults);
} else {
  writeResults();
}

async function waitForConnection() {
  await waitForFile(process.env.KADRE_RUNNER_TEST_CONNECTION_OPENED, 'held client did not connect');
}

async function waitForFile(path, message) {
  const deadline = Date.now() + 3_000;
  while (!existsSync(path)) {
    if (Date.now() >= deadline) throw new Error(message);
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 20));
  }
}

function writeResults() {
  mkdirSync(dirname(process.env.KADRE_JUNIT_OUTPUT), { recursive: true });
  writeFileSync(process.env.KADRE_JUNIT_OUTPUT, process.env.KADRE_RUNNER_TEST_JUNIT);
  const traceDirectory = join(process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR, 'trace');
  mkdirSync(traceDirectory, { recursive: true });
  writeFileSync(join(traceDirectory, 'trace.txt'), 'diagnostic evidence');
  if (scenario === 'quarantine-remove-failure' || scenario === 'signal-during-preservation') {
    const bulkDirectory = join(process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR, 'bulk');
    mkdirSync(bulkDirectory, { recursive: true });
    for (let index = 0; index < 2_500; index += 1) {
      writeFileSync(join(bulkDirectory, String(index).padStart(4, '0')), 'diagnostic evidence');
    }
  }
  if (scenario === 'remove-failure') chmodSync(traceDirectory, 0o500);
}
`;

const heldClientSource = String.raw`
const { writeFileSync } = require('node:fs');
const { connect } = require('node:net');

const url = new URL(process.env.KADRE_FIXTURE_URL);
const socket = connect({ host: url.hostname, port: Number(url.port) }, () => {
  socket.write('GET /index.html HTTP/1.1\r\nHost: 127.0.0.1\r\n');
  writeFileSync(process.env.KADRE_RUNNER_TEST_CONNECTION_OPENED, 'connection opened');
});
socket.on('close', () => {
  writeFileSync(process.env.KADRE_RUNNER_TEST_CONNECTION_CLOSED, 'connection closed');
});
`;

const descendantSource = String.raw`
const { writeFileSync } = require('node:fs');

writeFileSync(process.env.KADRE_RUNNER_TEST_DESCENDANT_READY, 'descendant ready');
process.on('SIGTERM', () => {
  writeFileSync(process.env.KADRE_RUNNER_TEST_DESCENDANT_TERMINATED, 'descendant terminated');
  process.exit(0);
});
setInterval(() => undefined, 1_000);
`;

const preservationPauseSource = String.raw`
const { existsSync, writeFileSync } = require('node:fs');
const fileSystem = require('node:fs/promises');
const { resolve } = require('node:path');

const rename = fileSystem.rename.bind(fileSystem);
fileSystem.rename = async (source, destination) => {
  const result = await rename(source, destination);
  if (process.env.KADRE_RUNNER_TEST_PAUSE_AFTER_RENAME
      && resolve(destination) === resolve(process.env.KADRE_RUNNER_TEST_PAUSE_AFTER_RENAME)) {
    writeFileSync(process.env.KADRE_RUNNER_TEST_PRESERVATION_PUBLISHED, 'published');
    const deadline = Date.now() + 10_000;
    while (!existsSync(process.env.KADRE_RUNNER_TEST_PRESERVATION_RELEASE)) {
      if (Date.now() >= deadline) throw new Error('timed out waiting to release diagnostic preservation');
      await new Promise((resolveDelay) => setTimeout(resolveDelay, 10));
    }
  }
  return result;
};
`;
