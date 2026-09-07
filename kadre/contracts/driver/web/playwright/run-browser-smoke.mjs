import { createServer } from 'node:http';
import { cp, readFile, readdir, realpath, rename, rm, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { basename, dirname, extname, join, relative, resolve, sep } from 'node:path';
import { spawn } from 'node:child_process';
import { XMLParser, XMLValidator } from 'fast-xml-parser';
import { createPlaywrightLaunch } from './browser-smoke-launch.mjs';

const POSIX_TERM_GRACE_MILLISECONDS = 1_000;
const POSIX_KILL_GRACE_MILLISECONDS = 1_000;
const PROCESS_TREE_TERMINATION_TIMEOUT_MILLISECONDS = 5_000;
const CHILD_CLOSE_TIMEOUT_MILLISECONDS = 5_000;
const WINDOWS_TASKKILL_TIMEOUT_MILLISECONDS = 3_000;
const WINDOWS_TASKKILL_REAP_TIMEOUT_MILLISECONDS = 1_000;
const SERVER_GRACEFUL_DRAIN_TIMEOUT_MILLISECONDS = 1_000;
const SERVER_FORCED_DRAIN_TIMEOUT_MILLISECONDS = 1_000;
const DIAGNOSTIC_OPERATION_TIMEOUT_MILLISECONDS = 5_000;
const signalExitCodes = { SIGINT: 2, SIGTERM: 15 };

async function runBrowserSmoke(argumentsList) {
  const argumentsByName = new Map(argumentsList.map((argument) => {
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
  const preservedPlaywrightOutput = join(evidence, 'diagnostics', 'playwright-preserved');
  const distributionRoot = await realpath(distribution);
  const entryScript = await findEntryScript(distributionRoot);
  const server = await serveDistribution(distributionRoot, entryScript);
  const coordinator = new TerminalCoordinator();
  let businessSucceeded = false;
  let serverFinalized = false;

  coordinator.installSignalHandlers();
  try {
    const launch = createPlaywrightLaunch(process.platform, {
      cwd: process.cwd(),
      env: {
        ...process.env,
        KADRE_FIXTURE_URL: `${server.url}/index.html`,
        KADRE_JUNIT_OUTPUT: junitOutput,
        KADRE_PLAYWRIGHT_OUTPUT_DIR: playwrightOutput,
      },
      stdio: 'inherit',
    });
    const result = await runWithWatchdog(
      launch.command,
      launch.argumentsList,
      launch.options,
      timeoutMilliseconds,
      coordinator,
    );

    if (!coordinator.hasTerminalReason()) {
      if (result.status !== 0) {
        coordinator.recordTerminal({ type: 'exit-failure', status: result.status, target });
      } else {
        const junit = await readFile(junitOutput, 'utf8');
        validateJunitReport(junit, junitOutput);
        if (!coordinator.hasTerminalReason()) businessSucceeded = true;
      }
    }
  } catch (error) {
    coordinator.recordTerminal({ type: 'business-error', error });
  } finally {
    const [serverResult] = await Promise.allSettled([
      closeServer(server),
      coordinator.cleanUpChild(),
    ]);
    if (serverResult.status === 'fulfilled') {
      serverFinalized = true;
    } else {
      coordinator.recordFinalizationFailure(serverResult.reason);
    }
    await coordinator.finishChildCleanup();
    coordinator.disposeChildHandles();

    if (businessSucceeded && serverFinalized && coordinator.canRemoveDiagnostics()) {
      try {
        await removeSuccessfulDiagnostics(playwrightOutput, preservedPlaywrightOutput, coordinator);
      } catch (error) {
        coordinator.recordFinalizationFailure(error);
      }
    }
    await coordinator.finishChildCleanup();
    coordinator.removeSignalHandlers();
  }

  const signal = coordinator.signal();
  if (signal) {
    for (const error of coordinator.allCleanupFailures()) {
      console.error(`Browser smoke cleanup after ${signal} failed: ${error.message}`);
    }
    exitForSignal(signal, coordinator.allCleanupFailures().length === 0);
    return;
  }

  const error = coordinator.failure(target);
  if (error) throw error;
}

function parseTimeout(value) {
  if (value === undefined) return 90_000;
  if (!/^\d+$/.test(value) || Number(value) === 0) {
    throw new Error('--timeout-ms must be a positive integer');
  }
  return Number(value);
}

function validateJunitReport(xml, path) {
  const syntax = XMLValidator.validate(xml);
  if (syntax !== true) {
    throw new Error(`JUnit XML report ${path} is not parseable: ${syntax.err.msg}`);
  }

  const document = new XMLParser({
    attributeNamePrefix: '@_',
    ignoreAttributes: false,
    isArray: (_name, elementPath) => elementPath === 'testsuites.testsuite'
      || elementPath === 'testsuites.testsuite.testcase',
  }).parse(xml);
  const root = document.testsuites;
  if (!root || typeof root !== 'object') {
    throw new Error(`JUnit XML report ${path} must contain a testsuites root`);
  }
  const suites = root.testsuite ?? [];
  if (suites.length === 0) {
    throw new Error(`JUnit XML report ${path} must contain at least one testsuite`);
  }

  const cases = suites.flatMap((suite, index) => {
    const suiteCases = suite?.testcase ?? [];
    const declaredTests = junitCount(suite, 'tests', `${path} testsuite ${index + 1}`);
    if (declaredTests !== suiteCases.length) {
      throw new Error(`JUnit XML report ${path} testsuite ${index + 1} declares ${declaredTests} tests but contains ${suiteCases.length} testcases`);
    }
    assertZeroJunitOutcomes(suite, `${path} testsuite ${index + 1}`);
    return suiteCases;
  });

  const declaredTests = junitCount(root, 'tests', path);
  if (declaredTests <= 0 || cases.length === 0) {
    throw new Error(`JUnit XML report ${path} must contain a positive test count and at least one testcase`);
  }
  if (declaredTests !== cases.length) {
    throw new Error(`JUnit XML report ${path} declares ${declaredTests} tests but contains ${cases.length} testcases`);
  }
  assertZeroJunitOutcomes(root, path);

  for (const [index, testCase] of cases.entries()) {
    if (testCase?.failure !== undefined || testCase?.error !== undefined || testCase?.skipped !== undefined) {
      throw new Error(`JUnit XML report ${path} testcase ${index + 1} is not successful`);
    }
    const status = testCase?.['@_status'];
    if (typeof status === 'string' && ['aborted', 'unknown'].includes(status.toLowerCase())) {
      throw new Error(`JUnit XML report ${path} testcase ${index + 1} has ${status} status`);
    }
  }
}

function junitCount(element, name, location) {
  const value = element?.[`@_${name}`];
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    throw new Error(`JUnit XML report ${location} has an invalid ${name} count`);
  }
  return Number(value);
}

function assertZeroJunitOutcomes(element, location) {
  for (const name of ['failures', 'errors', 'skipped']) {
    if (junitCount(element, name, location) !== 0) {
      throw new Error(`JUnit XML report ${location} reports nonzero ${name}`);
    }
  }
  const status = element?.['@_status'];
  if (typeof status === 'string' && ['aborted', 'unknown'].includes(status.toLowerCase())) {
    throw new Error(`JUnit XML report ${location} has ${status} status`);
  }
}

async function removeSuccessfulDiagnostics(output, preservedOutput, coordinator) {
  const suffix = `${process.pid}-${Date.now()}`;
  const stagingOutput = join(dirname(preservedOutput), `.playwright-preservation-${suffix}`);
  const committedQuarantine = join(dirname(preservedOutput), `.playwright-quarantine-current-${suffix}`);
  const obsoleteQuarantines = await findDiagnosticQuarantines(dirname(preservedOutput));

  try {
    if (existsSync(preservedOutput)) {
      const obsoletePreservation = join(dirname(preservedOutput), `.playwright-quarantine-obsolete-${suffix}`);
      await boundedDiagnosticOperation(
        rename(preservedOutput, obsoletePreservation),
        `retiring stale diagnostics at ${preservedOutput}`,
      );
      obsoleteQuarantines.push(obsoletePreservation);
    }
    if (!existsSync(output)) {
      return;
    }

    await boundedDiagnosticOperation(
      cp(output, stagingOutput, { errorOnExist: true, force: false, recursive: true }),
      `copying diagnostics from ${output}`,
    );
    await boundedDiagnosticOperation(
      rename(stagingOutput, preservedOutput),
      `publishing preserved diagnostics at ${preservedOutput}`,
    );
    if (coordinator.signal()) return;

    for (const quarantine of obsoleteQuarantines) {
      try {
        await boundedDiagnosticOperation(
          rm(quarantine, { force: true, recursive: true }),
          `removing obsolete diagnostic quarantine at ${quarantine}`,
        );
      } catch (error) {
        throw new Error(`Could not remove obsolete diagnostic quarantine at ${quarantine}; current diagnostics preserved at ${preservedOutput}: ${error.message}`, { cause: error });
      }
      if (coordinator.signal()) return;
    }

    try {
      await boundedDiagnosticOperation(
        rm(output, { force: true, recursive: true }),
        `removing diagnostics at ${output}`,
      );
    } catch (error) {
      throw new Error(`Could not remove successful diagnostics; complete diagnostics preserved at ${preservedOutput}: ${error.message}`, { cause: error });
    }
    if (coordinator.signal()) return;

    // Commit point: invoking this final atomic rename transfers the complete
    // snapshot to durable quarantine. No recursive deletion follows it.
    try {
      await boundedDiagnosticOperation(
        rename(preservedOutput, committedQuarantine),
        `committing durable diagnostic quarantine at ${committedQuarantine}`,
      );
    } catch (error) {
      throw new Error(`Could not commit durable diagnostic quarantine; complete diagnostics preserved at ${preservedOutput}: ${error.message}`, { cause: error });
    }
  } finally {
    await removeDiagnosticsBestEffort(stagingOutput);
  }
}

async function findDiagnosticQuarantines(directory) {
  let entries;
  try {
    entries = await boundedDiagnosticOperation(
      readdir(directory, { withFileTypes: true }),
      `listing durable diagnostic quarantines at ${directory}`,
    );
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
  return entries
    .filter((entry) => entry.isDirectory() && entry.name.startsWith('.playwright-quarantine-'))
    .map((entry) => join(directory, entry.name));
}

function boundedDiagnosticOperation(operation, description) {
  return withDeadline(operation, DIAGNOSTIC_OPERATION_TIMEOUT_MILLISECONDS, description);
}

async function removeDiagnosticsBestEffort(path) {
  try {
    await boundedDiagnosticOperation(
      rm(path, { force: true, recursive: true }),
      `best-effort diagnostic cleanup at ${path}`,
    );
  } catch {
    // Hidden retirement paths never masquerade as the normal diagnostics for a clean smoke.
  }
}

function reduceTerminalState(state, event) {
  if (event.type === 'terminal') {
    if (event.reason.type === 'signal') return { ...state, reason: event.reason };
    if (state.reason !== null) return state;
    return { ...state, reason: event.reason };
  }
  if (event.type === 'child-cleanup-failure') {
    return { ...state, childCleanupFailures: [...state.childCleanupFailures, event.error] };
  }
  if (event.type === 'finalization-failure') {
    return { ...state, finalizationFailures: [...state.finalizationFailures, event.error] };
  }
  throw new Error(`unexpected terminal coordinator event: ${event.type}`);
}

class TerminalCoordinator {
  constructor() {
    this.state = {
      reason: null,
      childCleanupFailures: [],
      finalizationFailures: [],
    };
    this.child = null;
    this.childClose = Promise.resolve({ signal: null, status: null });
    this.childCleanup = null;
    this.childHandles = new Set();
    this.signalHandlers = new Map([
      ['SIGINT', () => this.recordTerminal({ type: 'signal', signal: 'SIGINT' })],
      ['SIGTERM', () => this.recordTerminal({ type: 'signal', signal: 'SIGTERM' })],
    ]);
    this.terminalRequested = new Promise((resolveRequest) => {
      this.resolveTerminalRequest = resolveRequest;
    });
  }

  installSignalHandlers() {
    for (const [signal, handler] of this.signalHandlers) process.on(signal, handler);
  }

  removeSignalHandlers() {
    for (const [signal, handler] of this.signalHandlers) process.off(signal, handler);
  }

  attachChild(child) {
    this.child = child;
    this.childHandles.add(child);
    this.childClose = new Promise((resolveClose) => {
      child.once('close', (status, signal) => resolveClose({ signal, status }));
    });
    child.once('error', (error) => {
      this.recordTerminal({ type: 'spawn-error', error });
    });
  }

  recordTerminal(reason) {
    const previousReason = this.state.reason;
    this.state = reduceTerminalState(this.state, { type: 'terminal', reason });
    if (previousReason === null && this.state.reason !== null) this.resolveTerminalRequest();
    if (reason.type === 'signal') void this.cleanUpChild();
  }

  recordFinalizationFailure(error) {
    this.state = reduceTerminalState(this.state, { type: 'finalization-failure', error });
  }

  hasTerminalReason() {
    return this.state.reason !== null;
  }

  signal() {
    return this.state.reason?.type === 'signal' ? this.state.reason.signal : null;
  }

  canRemoveDiagnostics() {
    return this.state.reason === null
      && this.state.childCleanupFailures.length === 0
      && this.state.finalizationFailures.length === 0;
  }

  cleanUpChild() {
    if (!this.childCleanup) this.childCleanup = this.performChildCleanup();
    return this.childCleanup;
  }

  async performChildCleanup() {
    if (!this.child) return;
    const operations = [];
    if (this.child.pid) {
      operations.push(withDeadline(
        terminateProcessTree(this.child.pid, this.childHandles),
        PROCESS_TREE_TERMINATION_TIMEOUT_MILLISECONDS,
        `process-tree termination for ${this.child.pid}`,
      ));
    }
    operations.push(withDeadline(
      this.childClose,
      CHILD_CLOSE_TIMEOUT_MILLISECONDS,
      'Playwright child close observation',
    ));

    const results = await Promise.allSettled(operations);
    for (const result of results) {
      if (result.status === 'rejected') {
        this.state = reduceTerminalState(this.state, {
          type: 'child-cleanup-failure',
          error: result.reason,
        });
      }
    }
    this.disposeChildHandles();
  }

  async finishChildCleanup() {
    if (this.childCleanup) await this.childCleanup;
  }

  disposeChildHandles() {
    for (const child of this.childHandles) releaseChildHandle(child);
  }

  allCleanupFailures() {
    return [...this.state.childCleanupFailures, ...this.state.finalizationFailures];
  }

  failure(target) {
    const cleanupFailures = this.allCleanupFailures();
    const reason = this.state.reason;
    if (cleanupFailures.length > 0) {
      if (!reason && cleanupFailures.length === 1) return cleanupFailures[0];
      const description = reason ? cleanupDescription(reason) : 'finalization';
      return new AggregateError(cleanupFailures, `Browser smoke cleanup after ${description} failed`);
    }
    if (!reason) return null;
    if (reason.type === 'watchdog') {
      return new Error(`Playwright exceeded configured timeout of ${reason.timeout}ms; terminated its process tree`);
    }
    if (reason.type === 'spawn-error' || reason.type === 'business-error') return reason.error;
    if (reason.type === 'exit-failure') {
      return new Error(`Playwright ${target} smoke failed with exit status ${reason.status}`);
    }
    return new Error(`unexpected browser smoke terminal reason: ${reason.type}`);
  }
}

async function runWithWatchdog(command, argumentsList, options, timeout, coordinator) {
  if (coordinator.hasTerminalReason()) return { status: null };

  let child;
  try {
    child = spawn(command, argumentsList, {
      ...options,
    });
  } catch (error) {
    coordinator.recordTerminal({ type: 'spawn-error', error });
    return { status: null };
  }

  coordinator.attachChild(child);
  const timer = setTimeout(() => {
    coordinator.recordTerminal({ type: 'watchdog', timeout });
  }, timeout);

  try {
    const event = await Promise.race([
      coordinator.childClose.then((result) => ({ type: 'child-close', ...result })),
      coordinator.terminalRequested.then(() => ({ type: 'terminal-requested' })),
    ]);

    if (event.type === 'child-close' && event.status === 0 && !coordinator.hasTerminalReason()) {
      return { status: 0 };
    }
    if (event.type === 'child-close' && !coordinator.hasTerminalReason()) {
      coordinator.recordTerminal({ type: 'exit-failure', status: event.status });
    }

    return { status: event.type === 'child-close' ? event.status : null };
  } finally {
    clearTimeout(timer);
  }
}

async function terminateProcessTree(pid, cleanupChildren) {
  if (!pid) return;
  if (process.platform === 'win32') {
    if (!processExists(pid)) return;
    await terminateWindowsProcessTree(pid, cleanupChildren);
    return;
  }
  if (!processGroupExists(pid)) return;
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

function processExists(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    if (error.code === 'ESRCH') return false;
    if (error.code === 'EPERM') return true;
    throw error;
  }
}

function processGroupExists(pid) {
  try {
    process.kill(-pid, 0);
    return true;
  } catch (error) {
    if (error.code === 'ESRCH') return false;
    if (error.code === 'EPERM') return true;
    throw error;
  }
}

async function terminateWindowsProcessTree(pid, cleanupChildren) {
  let helper;
  try {
    helper = spawn('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
  } catch (error) {
    throw new Error(`could not start taskkill for ${pid}: ${error.message}`, { cause: error });
  }
  cleanupChildren?.add(helper);
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
    releaseChildHandle(helper);
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

function cleanupDescription(reason) {
  if (reason.type === 'signal') return `${reason.signal} signal`;
  if (reason.type === 'watchdog') return `the ${reason.timeout}ms watchdog`;
  if (reason.type === 'spawn-error') return 'a Playwright spawn error';
  return 'a Playwright failure';
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

function releaseChildHandle(child) {
  if (!child) return;
  try {
    child.unref();
  } catch {
    // A process that already closed has no live handle to release.
  }
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
  process.exit(128 + signalExitCodes[signal]);
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
  const sockets = new Set();
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
  instance.on('connection', (socket) => {
    sockets.add(socket);
    socket.once('close', () => sockets.delete(socket));
  });
  await new Promise((resolveListen, rejectListen) => {
    instance.once('error', rejectListen);
    instance.listen(0, '127.0.0.1', () => resolveListen());
  });
  const address = instance.address();
  return { instance, sockets, url: `http://127.0.0.1:${address.port}` };
}

function closeServer(server) {
  if (!server.closePromise) {
    server.closePromise = new Promise((resolveClose, rejectClose) => {
      try {
        server.instance.close((error) => error ? rejectClose(error) : resolveClose());
      } catch (error) {
        rejectClose(error);
      }
    });
  }
  return drainServer(server);
}

async function drainServer(server) {
  try {
    await withDeadline(
      server.closePromise,
      SERVER_GRACEFUL_DRAIN_TIMEOUT_MILLISECONDS,
      'browser smoke server graceful drain',
    );
    return;
  } catch (gracefulError) {
    const destroyFailures = destroyServerConnections(server);
    try {
      await withDeadline(
        server.closePromise,
        SERVER_FORCED_DRAIN_TIMEOUT_MILLISECONDS,
        'browser smoke server forced drain',
      );
      if (destroyFailures.length > 0) {
        throw new AggregateError(destroyFailures, 'browser smoke server forced teardown failed');
      }
      return;
    } catch (forcedError) {
      const releaseFailures = releaseServerHandle(server);
      throw new AggregateError(
        [gracefulError, ...destroyFailures, forcedError, ...releaseFailures],
        'browser smoke server did not close within bounded drains',
      );
    }
  }
}

function destroyServerConnections(server) {
  const failures = [];
  try {
    server.instance.closeAllConnections?.();
  } catch (error) {
    failures.push(error);
  }
  for (const socket of server.sockets) {
    try {
      socket.destroy();
    } catch (error) {
      failures.push(error);
    }
  }
  return failures;
}

function releaseServerHandle(server) {
  const failures = destroyServerConnections(server);
  try {
    server.instance.unref();
  } catch (error) {
    failures.push(error);
  }
  for (const socket of server.sockets) {
    try {
      socket.unref();
    } catch (error) {
      failures.push(error);
    }
  }
  return failures;
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

await runBrowserSmoke(process.argv.slice(2));
