import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import test from 'node:test';
import {
  auditNightlyJunit,
  countJunitTests,
  nightlyEngineTargetCombinations,
  nightlyJunitDirectory,
} from './nightly-zero-test-guard.mjs';

const guard = resolve(dirname(fileURLToPath(import.meta.url)), 'nightly-zero-test-guard.mjs');

/**
 * The JUnit shape the runner's own validator accepts: one testsuites root, one suite, every
 * testcase passing. The guard must never be more lenient than the pipeline it audits, so the
 * fixtures reuse exactly that shape, with the counts derived from the cases it embeds.
 */
function junitXml(passedCases) {
  const cases = passedCases.map(([classname, name]) => `<testcase name="${name}" classname="${classname}"/>`).join('');
  const count = passedCases.length;
  return '<?xml version="1.0"?>'
    + `<testsuites tests="${count}" failures="0" errors="0" skipped="0">`
    + `<testsuite name="web" tests="${count}" failures="0" errors="0" skipped="0" time="0.001">${cases}</testsuite>`
    + '</testsuites>';
}

const passingCases = [
  ['web-phase0', 'attaches'],
  ['web-surface', 'publishes-bindings'],
];

/**
 * A fixture evidence tree: the contract-evidence layout the six nightly smokes write, one JUnit
 * directory per declared engine×target combination. `reportsFor` overrides single combinations so
 * each zero-test class can be staged against an otherwise green tree.
 */
async function createFixture(reportsFor = {}) {
  const root = await mkdtemp(join(tmpdir(), 'kadre-nightly-guard-'));
  const evidenceRoot = join(root, 'contract-evidence');
  for (const { engine, target } of nightlyEngineTargetCombinations) {
    const junitDirectory = nightlyJunitDirectory(evidenceRoot, engine, target);
    // `null` stages the missing-directory class, so the override's presence is checked with hasOwn
    // — a `??` default would swallow the null (it is nullish) and silently write the report.
    const report = Object.hasOwn(reportsFor, `${engine}/${target}`)
      ? reportsFor[`${engine}/${target}`]
      : junitXml(passingCases);
    if (report === null) continue;
    await mkdir(junitDirectory, { recursive: true });
    if (report !== 'empty') await writeFile(join(junitDirectory, 'TEST-web-phase0.xml'), report);
  }
  return {
    evidenceRoot,
    async dispose() {
      await rm(root, { force: true, recursive: true });
    },
  };
}

/** Runs the guard CLI exactly as the workflow does; resolves its exit status and output. */
function runGuard(evidenceRoot) {
  const child = spawn(process.execPath, [guard, `--evidence-root=${evidenceRoot}`], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stdout = '';
  let stderr = '';
  child.stdout.setEncoding('utf8').on('data', (chunk) => { stdout += chunk; });
  child.stderr.setEncoding('utf8').on('data', (chunk) => { stderr += chunk; });
  return new Promise((resolveCompletion, rejectCompletion) => {
    child.once('error', rejectCompletion);
    child.once('close', (status) => resolveCompletion({ status, stderr, stdout }));
  });
}

test('the declared matrix is exactly the nightly gradle line, in its order', () => {
  assert.deepEqual(
    nightlyEngineTargetCombinations,
    [
      { engine: 'chromium', target: 'js' },
      { engine: 'chromium', target: 'wasmJs' },
      { engine: 'firefox', target: 'js' },
      { engine: 'firefox', target: 'wasmJs' },
      { engine: 'webkit', target: 'js' },
      { engine: 'webkit', target: 'wasmJs' },
    ],
  );
});

test('the JUnit layout mirrors the gradle evidence roots and browserEvidenceLocations', () => {
  const root = '/repo/kadre/contracts/driver/web/build/contract-evidence';
  // The Chromium smokes write build/contract-evidence/<target>; the validator's chromium tasks
  // resolve the same directory plus test-results/browser/{engine}.
  assert.equal(
    nightlyJunitDirectory(root, 'chromium', 'js'),
    join(root, 'js', 'test-results', 'browser', 'chromium'),
  );
  assert.equal(
    nightlyJunitDirectory(root, 'chromium', 'wasmJs'),
    join(root, 'wasmJs', 'test-results', 'browser', 'chromium'),
  );
  // The nightly engines carry the engine segment the per-engine tasks' outputs.dir requires.
  assert.equal(
    nightlyJunitDirectory(root, 'firefox', 'wasmJs'),
    join(root, 'firefox', 'wasmJs', 'test-results', 'browser', 'firefox'),
  );
  assert.equal(
    nightlyJunitDirectory(root, 'webkit', 'js'),
    join(root, 'webkit', 'js', 'test-results', 'browser', 'webkit'),
  );
});

test('a green evidence tree passes with one counted entry per declared combination', async (context) => {
  const fixture = await createFixture();
  context.after(() => fixture.dispose());

  const entries = await auditNightlyJunit(fixture.evidenceRoot);

  assert.equal(entries.length, 6);
  for (const entry of entries) {
    assert.equal(entry.violation, undefined, entry.violation);
    assert.deepEqual(entry.counts, { reports: 1, tests: passingCases.length, cases: passingCases.length });
  }
  const result = await runGuard(fixture.evidenceRoot);
  assert.equal(result.status, 0, result.stderr);
  for (const { engine, target } of nightlyEngineTargetCombinations) {
    assert.match(result.stdout, new RegExp(`zero-test guard: ${engine}/${target} ran ${passingCases.length} tests`));
  }
});

test('a missing JUnit directory is a violation naming the combination', async (context) => {
  const fixture = await createFixture({ 'firefox/wasmJs': null });
  context.after(() => fixture.dispose());

  const entries = await auditNightlyJunit(fixture.evidenceRoot);
  const violation = entries.find((entry) => entry.engine === 'firefox' && entry.target === 'wasmJs');
  assert.match(violation.violation, /JUnit directory does not exist: .*firefox.wasmJs.test-results.browser.firefox/);
});

test('a JUnit directory without TEST-*.xml reports is a violation', async (context) => {
  const fixture = await createFixture({ 'webkit/js': 'empty' });
  context.after(() => fixture.dispose());

  await assert.rejects(
    countJunitTests(nightlyJunitDirectory(fixture.evidenceRoot, 'webkit', 'js')),
    /JUnit directory contains no TEST-\*\.xml reports/,
  );
});

test('a report declaring zero tests is a violation', async (context) => {
  const fixture = await createFixture({ 'chromium/js': junitXml([]) });
  context.after(() => fixture.dispose());

  await assert.rejects(
    countJunitTests(nightlyJunitDirectory(fixture.evidenceRoot, 'chromium', 'js')),
    /declare zero tests in total/,
  );
});

test('a suite whose declared count disagrees with its testcase elements is a violation', async (context) => {
  const mismatched = '<?xml version="1.0"?>'
    + '<testsuites tests="2" failures="0" errors="0" skipped="0">'
    + '<testsuite name="web" tests="2" failures="0" errors="0" skipped="0" time="0.001">'
    + '<testcase name="only-case" classname="web-phase0"/>'
    + '</testsuite></testsuites>';
  const fixture = await createFixture({ 'chromium/wasmJs': mismatched });
  context.after(() => fixture.dispose());

  await assert.rejects(
    countJunitTests(nightlyJunitDirectory(fixture.evidenceRoot, 'chromium', 'wasmJs')),
    /declares 2 tests but contains 1 testcases/,
  );
});

test('the guard CLI fails the job when any declared combination ran zero tests', async (context) => {
  const fixture = await createFixture({ 'webkit/wasmJs': 'empty' });
  context.after(() => fixture.dispose());

  const result = await runGuard(fixture.evidenceRoot);

  assert.notEqual(result.status, 0, 'a zero-test combination passed the guard');
  assert.match(result.stderr, /zero-test guard violation \[webkit\/wasmJs\]: JUnit directory contains no TEST-\*\.xml reports/);
  assert.match(
    result.stderr,
    /6 engine×target combinations and 1 of them ran zero tests: webkit\/wasmJs/,
  );
  // The five healthy combinations still report their counts on stdout: the log shows what ran.
  assert.match(result.stdout, /zero-test guard: chromium\/js ran 2 tests/);
  assert.match(result.stdout, /zero-test guard: firefox\/wasmJs ran 2 tests/);
});
