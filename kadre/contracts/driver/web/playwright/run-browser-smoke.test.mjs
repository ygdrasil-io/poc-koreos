import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import {
  access,
  chmod,
  mkdtemp,
  mkdir,
  readFile,
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

test('a partial HTTP request is held through graceful drain and closed by forced teardown', {
  timeout: 10_000,
}, async (context) => {
  const fixture = await createFixture('held-connection');
  context.after(() => fixture.dispose());

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
  assert.ok(Date.now() - startedAt >= 750, 'diagnostic removal ran before bounded server teardown');
  assert.equal(await readFile(fixture.diagnosticMarker, 'utf8'), 'diagnostic evidence');
  await waitForFile(fixture.connectionClosed, 1_000);
});

async function createFixture(scenario) {
  const root = await mkdtemp(join(tmpdir(), 'kadre-browser-runner-'));
  const distribution = join(root, 'distribution');
  const evidence = join(root, 'evidence');
  const binDirectory = join(root, 'node_modules', '.bin');
  const fakePlaywright = join(root, 'fake-playwright.cjs');
  const heldClient = join(root, 'held-client.cjs');
  const childReady = join(root, 'child-ready');
  const cleanupStarted = join(root, 'cleanup-started');
  const connectionOpened = join(root, 'connection-opened');
  const connectionClosed = join(root, 'connection-closed');
  const heldClientPid = join(root, 'held-client.pid');
  const diagnostics = join(evidence, 'diagnostics', 'playwright');
  const diagnosticsParent = dirname(diagnostics);
  const diagnosticMarker = join(diagnostics, 'trace.txt');

  await mkdir(distribution, { recursive: true });
  await mkdir(binDirectory, { recursive: true });
  await writeFile(join(distribution, 'fixture.js'), 'globalThis.kadreFixture = true;\n');
  await writeFile(heldClient, heldClientSource, { mode: 0o755 });
  await writeFile(fakePlaywright, fakePlaywrightSource, { mode: 0o755 });

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
    diagnosticMarker,
    diagnostics,
    diagnosticsParent,
    distribution,
    evidence,
    root,
    async dispose() {
      try {
        const pid = Number(await readFile(heldClientPid, 'utf8'));
        if (Number.isInteger(pid) && pid > 0) process.kill(pid, 'SIGKILL');
      } catch (error) {
        if (error.code !== 'ENOENT' && error.code !== 'ESRCH') throw error;
      }
      await chmod(diagnosticsParent, 0o700).catch(() => undefined);
      await rm(root, { force: true, recursive: true });
    },
    environment: {
      KADRE_RUNNER_TEST_CLEANUP_STARTED: cleanupStarted,
      KADRE_RUNNER_TEST_CHILD_READY: childReady,
      KADRE_RUNNER_TEST_CONNECTION_CLOSED: connectionClosed,
      KADRE_RUNNER_TEST_CONNECTION_OPENED: connectionOpened,
      KADRE_RUNNER_TEST_HELD_CLIENT: heldClient,
      KADRE_RUNNER_TEST_HELD_CLIENT_PID: heldClientPid,
      KADRE_RUNNER_TEST_SCENARIO: scenario,
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
} else {
  const helper = spawn(process.execPath, [process.env.KADRE_RUNNER_TEST_HELD_CLIENT], {
    detached: true,
    env: process.env,
    stdio: 'ignore',
  });
  writeFileSync(process.env.KADRE_RUNNER_TEST_HELD_CLIENT_PID, String(helper.pid));
  helper.unref();
  waitForConnection().then(() => {
    mkdirSync(dirname(process.env.KADRE_JUNIT_OUTPUT), { recursive: true });
    writeFileSync(
      process.env.KADRE_JUNIT_OUTPUT,
      '<testsuites tests="1" failures="0" errors="0" skipped="0"></testsuites>',
    );
    mkdirSync(process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR, { recursive: true });
    writeFileSync(join(process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR, 'trace.txt'), 'diagnostic evidence');
    if (scenario === 'remove-failure') {
      chmodSync(dirname(process.env.KADRE_PLAYWRIGHT_OUTPUT_DIR), 0o500);
    }
  });
}

async function waitForConnection() {
  const deadline = Date.now() + 3_000;
  while (!existsSync(process.env.KADRE_RUNNER_TEST_CONNECTION_OPENED)) {
    if (Date.now() >= deadline) throw new Error('held client did not connect');
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 20));
  }
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
