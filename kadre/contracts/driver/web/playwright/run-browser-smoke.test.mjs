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
import { constants as fsConstants } from 'node:fs';
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
  const quarantines = await diagnosticQuarantines(fixture);
  assert.equal(quarantines.length, 2, `expected the stale and current durable quarantines, found ${quarantines.join(', ')}`);
  await assertCompleteDiagnostics(quarantineByKind(quarantines, 'current'), 0);
  assert.equal(
    await readFile(join(quarantineByKind(quarantines, 'obsolete'), 'trace', 'trace.txt'), 'utf8'),
    'stale diagnostic evidence',
  );
});

test('GET /index.html with a scenario query serves the fixture HTML', {
  timeout: 10_000,
}, async (context) => {
  const fixture = await createFixture('query-route');
  context.after(() => fixture.dispose());

  const result = await runSmoke(fixture).completion;

  assert.equal(normalizedExitStatus(result), 0, result.stderr);
  assert.equal(
    await readFile(fixture.queryResponse, 'utf8'),
    '<!doctype html><html><body><script src="/fixture.js"></script></body></html>',
  );
});

test('successful cleanup atomically retains unreadable diagnostics without copying their contents', {
  skip: process.platform === 'win32' && 'Windows chmod does not provide a deterministic remove failure',
  timeout: 10_000,
}, async (context) => {
  const fixture = await createFixture('unreadable-diagnostics');
  context.after(async () => {
    await fixture.dispose();
  });

  const execution = runSmoke(fixture);
  const result = await execution.completion;

  assert.equal(normalizedExitStatus(result), 0, result.stderr);
  const quarantines = await diagnosticQuarantines(fixture);
  assert.equal(quarantines.length, 1, `expected one durable quarantine, found ${quarantines.join(', ')}`);
  await chmod(join(quarantines[0], 'trace'), 0o700);
  await assertCompleteDiagnostics(quarantines[0], 0);
});

test('a failed quarantine janitor runs before the current smoke can produce diagnostics', {
  skip: process.platform === 'win32' && 'Windows chmod does not provide a deterministic remove failure',
  timeout: 20_000,
}, async (context) => {
  const fixture = await createFixture('partial-quarantine-failure');
  await createObsoleteQuarantine(fixture);
  const execution = runSmoke(fixture);
  context.after(async () => {
    await stopProcess(execution.child);
    await fixture.dispose();
  });

  const result = await execution.completion;

  assert.notEqual(normalizedExitStatus(result), 0, 'a partial janitor failure must fail the smoke');
  assert.doesNotMatch(result.stderr, /complete obsolete diagnostics/i, result.stderr);
  await assert.rejects(access(fixture.diagnostics, fsConstants.F_OK), { code: 'ENOENT' });
  await assert.rejects(access(fixture.preservedDiagnostics, fsConstants.F_OK), { code: 'ENOENT' });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  test(`${signal} before the diagnostic commit retains complete visible diagnostics`, {
    skip: process.platform === 'win32' && 'Windows does not deliver POSIX signals to a child as catchable console signals',
    timeout: 20_000,
  }, async (context) => {
    const fixture = await createFixture('signal-before-diagnostic-commit');
    const execution = runSmoke(fixture);
    context.after(async () => {
      await writeFile(fixture.diagnosticPreservationRelease, 'release').catch(() => undefined);
      await stopProcess(execution.child);
      await fixture.dispose();
    });

    await waitForFileWhileRunning(fixture.diagnosticPreservationStarted, execution, 10_000);
    execution.child.kill(signal);
    await writeFile(fixture.diagnosticPreservationRelease, 'release');
    const result = await execution.completion;

    assert.equal(normalizedExitStatus(result), signalExitCodes[signal], result.stderr);
    await assertCompleteDiagnostics(fixture.preservedDiagnostics);
  });
}

test('a signal after the diagnostic commit retains the complete durable quarantine', {
  skip: process.platform === 'win32' && 'Windows does not deliver POSIX signals to a child as catchable console signals',
  timeout: 20_000,
}, async (context) => {
  const fixture = await createFixture('signal-after-diagnostic-commit');
  const execution = runSmoke(fixture);
  context.after(async () => {
    await writeFile(fixture.diagnosticCommitRelease, 'release').catch(() => undefined);
    await stopProcess(execution.child);
    await fixture.dispose();
  });

  await waitForFileWhileRunning(fixture.diagnosticCommitStarted, execution, 10_000);
  execution.child.kill('SIGTERM');
  await writeFile(fixture.diagnosticCommitRelease, 'release');
  const result = await execution.completion;

  assert.equal(normalizedExitStatus(result), signalExitCodes.SIGTERM, result.stderr);
  await assert.rejects(access(fixture.preservedDiagnostics, fsConstants.F_OK), { code: 'ENOENT' });
  const quarantines = await diagnosticQuarantines(fixture);
  assert.equal(quarantines.length, 1, `expected one durable quarantine, found ${quarantines.join(', ')}`);
  await assertCompleteDiagnostics(quarantines[0]);
});

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
  const fileSystemControl = join(root, 'filesystem-control.cjs');
  const diagnosticPreservationStarted = join(root, 'diagnostic-preservation-started');
  const diagnosticPreservationRelease = join(root, 'diagnostic-preservation-release');
  const diagnosticCommitStarted = join(root, 'diagnostic-commit-started');
  const diagnosticCommitRelease = join(root, 'diagnostic-commit-release');
  const queryResponse = join(root, 'query-response.html');
  const diagnostics = join(evidence, 'diagnostics', 'playwright');
  const diagnosticsParent = dirname(diagnostics);
  const diagnosticTraceDirectory = join(diagnostics, 'trace');
  const diagnosticMarker = join(diagnosticTraceDirectory, 'trace.txt');
  const preservedDiagnostics = join(evidence, 'diagnostics', 'playwright-preserved');
  const preservedDiagnosticTraceDirectory = join(preservedDiagnostics, 'trace');
  const preservedDiagnosticMarker = join(preservedDiagnosticTraceDirectory, 'trace.txt');

  await mkdir(distribution, { recursive: true });
  await mkdir(binDirectory, { recursive: true });
  await writeFile(join(distribution, 'fixture.js'), 'globalThis.kadreFixture = true;\n');
  await writeFile(heldClient, heldClientSource, { mode: 0o755 });
  await writeFile(descendant, descendantSource, { mode: 0o755 });
  await writeFile(fakePlaywright, fakePlaywrightSource, { mode: 0o755 });
  await writeFile(fileSystemControl, fileSystemControlSource, { mode: 0o755 });

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
    preservedDiagnosticTraceDirectory,
    preservedDiagnostics,
    queryResponse,
    diagnosticPreservationStarted,
    diagnosticPreservationRelease,
    diagnosticCommitStarted,
    diagnosticCommitRelease,
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
      for (const entry of await readdir(diagnosticsParent).catch(() => [])) {
        await chmod(join(diagnosticsParent, entry), 0o700).catch(() => undefined);
      }
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
      KADRE_RUNNER_TEST_QUERY_RESPONSE: queryResponse,
      KADRE_RUNNER_TEST_DIAGNOSTIC_PRESERVATION_STARTED: diagnosticPreservationStarted,
      KADRE_RUNNER_TEST_DIAGNOSTIC_PRESERVATION_RELEASE: diagnosticPreservationRelease,
      KADRE_RUNNER_TEST_DIAGNOSTIC_COMMIT_STARTED: diagnosticCommitStarted,
      KADRE_RUNNER_TEST_DIAGNOSTIC_COMMIT_RELEASE: diagnosticCommitRelease,
      KADRE_RUNNER_TEST_SCENARIO: scenario,
      ...([
        'partial-quarantine-failure',
        'signal-after-diagnostic-commit',
        'signal-before-diagnostic-commit',
      ].includes(scenario) ? {
        NODE_OPTIONS: [process.env.NODE_OPTIONS, `--require=${fileSystemControl}`].filter(Boolean).join(' '),
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

async function createObsoleteQuarantine(fixture) {
  const quarantine = join(fixture.diagnosticsParent, '.playwright-quarantine-obsolete-fixture');
  const traceDirectory = join(quarantine, 'trace');
  await mkdir(traceDirectory, { recursive: true });
  await writeFile(join(traceDirectory, 'trace.txt'), 'obsolete diagnostic evidence');
  return quarantine;
}

async function diagnosticQuarantines(fixture) {
  return (await readdir(fixture.diagnosticsParent))
    .filter((entry) => entry.startsWith('.playwright-quarantine-'))
    .map((entry) => join(fixture.diagnosticsParent, entry));
}

function quarantineByKind(quarantines, kind) {
  const quarantine = quarantines.find((path) => path.includes(`.playwright-quarantine-${kind}-`));
  assert.ok(quarantine, `missing ${kind} quarantine in ${quarantines.join(', ')}`);
  return quarantine;
}

async function assertCompleteDiagnostics(path, expectedBulkCount = 2_500) {
  assert.equal(await readFile(join(path, 'trace', 'trace.txt'), 'utf8'), 'diagnostic evidence');
  let bulkCount = 0;
  try {
    bulkCount = (await readdir(join(path, 'bulk'))).length;
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  assert.equal(bulkCount, expectedBulkCount, `incomplete diagnostic bulk data at ${path}`);
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
} else if (scenario === 'held-connection') {
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
} else if (scenario === 'query-route') {
  requestFixtureWithQuery().then(writeResults);
} else {
  writeResults();
}

async function requestFixtureWithQuery() {
  const response = await fetch(process.env.KADRE_FIXTURE_URL + '?scenario=x');
  if (response.status !== 200) throw new Error('query fixture returned HTTP ' + response.status);
  const html = await response.text();
  writeFileSync(process.env.KADRE_RUNNER_TEST_QUERY_RESPONSE, html);
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
  if (scenario === 'signal-before-diagnostic-commit'
      || scenario === 'signal-after-diagnostic-commit'
      || scenario === 'partial-quarantine-failure') {
    const bulkDirectory = join(process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR, 'bulk');
    mkdirSync(bulkDirectory, { recursive: true });
    for (let index = 0; index < 2_500; index += 1) {
      writeFileSync(join(bulkDirectory, String(index).padStart(4, '0')), 'diagnostic evidence');
    }
  }
  if (scenario === 'unreadable-diagnostics') chmodSync(traceDirectory, 0o000);
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

const fileSystemControlSource = String.raw`
const { existsSync, writeFileSync } = require('node:fs');
const fileSystem = require('node:fs/promises');
const { basename, join } = require('node:path');

const rename = fileSystem.rename.bind(fileSystem);
const rm = fileSystem.rm.bind(fileSystem);
fileSystem.rename = async (source, destination) => {
  const result = await rename(source, destination);
  if (process.env.KADRE_RUNNER_TEST_SCENARIO === 'signal-before-diagnostic-commit'
      && basename(destination) === 'playwright-preserved') {
    writeFileSync(process.env.KADRE_RUNNER_TEST_DIAGNOSTIC_PRESERVATION_STARTED, 'started');
    const deadline = Date.now() + 10_000;
    while (!existsSync(process.env.KADRE_RUNNER_TEST_DIAGNOSTIC_PRESERVATION_RELEASE)) {
      if (Date.now() >= deadline) throw new Error('timed out waiting to release diagnostic preservation');
      await new Promise((resolveDelay) => setTimeout(resolveDelay, 10));
    }
  }
  if (process.env.KADRE_RUNNER_TEST_SCENARIO === 'signal-after-diagnostic-commit'
      && basename(destination).startsWith('.playwright-quarantine-current-')) {
    writeFileSync(process.env.KADRE_RUNNER_TEST_DIAGNOSTIC_COMMIT_STARTED, 'started');
    const deadline = Date.now() + 10_000;
    while (!existsSync(process.env.KADRE_RUNNER_TEST_DIAGNOSTIC_COMMIT_RELEASE)) {
      if (Date.now() >= deadline) throw new Error('timed out waiting to release diagnostic commit');
      await new Promise((resolveDelay) => setTimeout(resolveDelay, 10));
    }
  }
  return result;
};

fileSystem.rm = async (path, options) => {
  if (!basename(path).startsWith('.playwright-quarantine-')) return rm(path, options);
  if (process.env.KADRE_RUNNER_TEST_SCENARIO === 'partial-quarantine-failure') {
    await rm(join(path, 'trace', 'trace.txt'), { force: false });
    await fileSystem.chmod(path, 0o500);
  }
  return rm(path, options);
};
`;
