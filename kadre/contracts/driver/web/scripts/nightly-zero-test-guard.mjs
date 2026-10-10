import { readdir, readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { XMLParser } from 'fast-xml-parser';

/**
 * The zero-test guard of the nightly workflow (Review-Focus 1): the three-engine matrix declares
 * six engine×target smokes, and a nightly that "passed" while any declared combination executed
 * zero tests is silently unverified. The runner itself already refuses a JUnit report that exists
 * but declares no testcase (`run-browser-smoke.mjs` `validateJunitReport`), so the residual
 * zero-test class is everything upstream of that check: a smoke whose task never ran, whose
 * evidence landed in a different layout than the one the workflow declared, or a report file that
 * is missing or unreadable while the gradle line still exited zero. This guard re-derives the six
 * JUnit locations from the same two sources that produce them — the gradle `--evidence` roots and
 * `browserEvidenceLocations` — and fails unless every directory exists and positively declares
 * tests.
 */

/** Every engine×target combination the nightly's gradle line declares, in that line's order. */
export const nightlyEngineTargetCombinations = [
  { engine: 'chromium', target: 'js' },
  { engine: 'chromium', target: 'wasmJs' },
  { engine: 'firefox', target: 'js' },
  { engine: 'firefox', target: 'wasmJs' },
  { engine: 'webkit', target: 'js' },
  { engine: 'webkit', target: 'wasmJs' },
];

/**
 * The JUnit directory one declared combination must have produced.
 *
 * The two gradle registrations scope the `--evidence` root differently: the Chromium smokes write
 * `build/contract-evidence/<target>` (`registerBrowserSmoke`) while the nightly engines write
 * `build/contract-evidence/<engine>/<target>` (`registerEngineBrowserSmoke`, so the four tasks'
 * `outputs.dir` never overlap). `browserEvidenceLocations` then appends
 * `test-results/browser/<engine>` to whichever root it is given — the same segment the validator's
 * `junitReportRelativeDirectories` template `test-results/browser/{engine}` resolves to.
 */
export function nightlyJunitDirectory(evidenceRoot, engine, target) {
  const engineScope = engine === 'chromium' ? target : join(engine, target);
  return join(evidenceRoot, engineScope, 'test-results', 'browser', engine);
}

/**
 * The declared tests and testcase elements of one JUnit directory, reduced exactly as the evidence
 * producer's `readJUnitSummary` reduces them: every `TEST-*.xml` file contributes each testsuite's
 * declared `tests` count, and a suite whose declared count disagrees with its testcase elements is
 * malformed. The zero-test classes — missing directory, no reports, zero total tests — throw with
 * the directory named; the caller turns every throw into a violation.
 */
export async function countJunitTests(junitDirectory) {
  const entries = await readdir(junitDirectory, { withFileTypes: true }).catch((error) => {
    throw new Error(`JUnit directory does not exist: ${junitDirectory} (${error.message})`, { cause: error });
  });
  const reports = entries
    .filter((entry) => entry.isFile() && entry.name.startsWith('TEST-') && entry.name.endsWith('.xml'))
    .map((entry) => join(junitDirectory, entry.name))
    .sort();
  if (reports.length === 0) {
    throw new Error(`JUnit directory contains no TEST-*.xml reports: ${junitDirectory}`);
  }

  const parser = new XMLParser({
    attributeNamePrefix: '@_',
    ignoreAttributes: false,
    isArray: (_name, elementPath) => elementPath === 'testsuites.testsuite'
      || elementPath === 'testsuites.testsuite.testcase',
  });

  let tests = 0;
  let cases = 0;
  for (const report of reports) {
    const document = parser.parse(await readFile(report, 'utf8'));
    const root = document.testsuites;
    if (!root || typeof root !== 'object') {
      throw new Error(`JUnit XML report ${report} must contain a testsuites root`);
    }
    const suites = root.testsuite ?? [];
    if (suites.length === 0) {
      throw new Error(`JUnit XML report ${report} must contain at least one testsuite`);
    }
    for (const [index, suite] of suites.entries()) {
      const suiteCases = suite?.testcase ?? [];
      const declared = suite?.['@_tests'];
      if (typeof declared !== 'string' || !/^\d+$/.test(declared)) {
        throw new Error(`JUnit XML report ${report} testsuite ${index + 1} has an invalid tests count`);
      }
      if (Number(declared) !== suiteCases.length) {
        throw new Error(
          `JUnit XML report ${report} testsuite ${index + 1} declares ${declared} tests `
          + `but contains ${suiteCases.length} testcases`,
        );
      }
      tests += Number(declared);
      cases += suiteCases.length;
    }
  }
  if (tests === 0) {
    throw new Error(`JUnit reports of ${junitDirectory} declare zero tests in total`);
  }
  return { reports: reports.length, tests, cases };
}

/**
 * Audits every declared combination: one entry per engine×target with its JUnit directory, its
 * counts when they could be read, and the violation that replaces them when they could not.
 */
export async function auditNightlyJunit(evidenceRoot) {
  const entries = [];
  for (const { engine, target } of nightlyEngineTargetCombinations) {
    const junitDirectory = nightlyJunitDirectory(evidenceRoot, engine, target);
    const entry = { engine, target, junitDirectory };
    try {
      entry.counts = await countJunitTests(junitDirectory);
    } catch (error) {
      entry.violation = error.message;
    }
    entries.push(entry);
  }
  return entries;
}

/** Parses `--name=value` arguments; every flag this guard understands requires a value. */
function parseArguments(argumentsList) {
  const argumentsByName = new Map();
  for (const argument of argumentsList) {
    const separator = argument.indexOf('=');
    if (!argument.startsWith('--') || separator < 3) {
      throw new Error(`expected --name=value arguments, received: ${argument}`);
    }
    argumentsByName.set(argument.slice(0, separator), argument.slice(separator + 1));
  }
  return argumentsByName;
}

export async function main(argumentsList) {
  const argumentsByName = parseArguments(argumentsList);
  const evidenceRoot = argumentsByName.get('--evidence-root');
  if (!evidenceRoot) throw new Error('missing required argument: --evidence-root=<contract-evidence directory>');

  const entries = await auditNightlyJunit(resolve(evidenceRoot));
  for (const entry of entries) {
    if (entry.counts) {
      console.log(
        `zero-test guard: ${entry.engine}/${entry.target} ran ${entry.counts.tests} tests `
        + `(${entry.counts.reports} report${entry.counts.reports === 1 ? '' : 's'}) from ${entry.junitDirectory}`,
      );
    } else {
      console.error(`zero-test guard violation [${entry.engine}/${entry.target}]: ${entry.violation}`);
    }
  }
  const violations = entries.filter((entry) => entry.violation);
  if (violations.length > 0) {
    throw new Error(
      `the nightly declared ${entries.length} engine×target combinations and ${violations.length} of them ran zero tests: `
      + violations.map((entry) => `${entry.engine}/${entry.target}`).join(', '),
    );
  }
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main(process.argv.slice(2));
}
