import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { XMLParser } from 'fast-xml-parser';

const execFileAsync = promisify(execFile);

/** The registry header of `kadre/contracts/registry/contracts.tsv`, which the producer re-reads. */
const REGISTRY_HEADER =
  'contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef';

/** The mapping header of `contracts/evidence.tsv`, which the producer re-reads. */
const MAPPING_HEADER = 'contractId\ttarget\tkind\tevidenceId\ttestClass\ttestName';

/**
 * The consumer of these documents is `ContractEvidence.readAndValidate`, which requires this exact
 * schema, field set and evidence identity — the shape `ContractEvidence.create` writes.
 */
const SCHEMA_VERSION = 1;
const EXECUTION_KIND = 'browser';
const ADAPTER = 'web-browser';
const EVIDENCE_DIRECTORY = 'contract-evidence';
const BROWSER_DIRECTORY = 'browser';
const JUNIT_DIRECTORY = 'test-results';

const GIT_SHA = /^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$/;
const CONTRACT_ID = /^[A-Z]{3}-[0-9]{3}$/;
const DECIMAL = /^\d+(?:\.\d+)?$/;
const EXPECTED_TARGETS = ['js', 'wasmJs'];
const EXPECTED_ORACLES = ['O1', 'O2', 'O3'];
const TERMINAL_STATUSES = ['skipped', 'failure', 'error'];
const MAPPING_KINDS = { scenario: 'scenarioId', sentinel: 'sentinelId' };

/**
 * The one place the browser evidence layout of `kadre/WEB-IMPLEMENTATION-ROADMAP.md` section 3.8 is
 * written: `<evidence>/contract-evidence/browser/<engine>/<contractId>.json` is validated against the
 * JUnit report under `<evidence>/test-results/browser/<engine>/`.
 */
export function browserEvidenceLocations(evidenceRoot, engine) {
  return {
    junitDirectory: join(evidenceRoot, JUNIT_DIRECTORY, BROWSER_DIRECTORY, engine),
    evidenceDirectory: join(evidenceRoot, EVIDENCE_DIRECTORY, BROWSER_DIRECTORY, engine),
  };
}

/**
 * The engine version of the pinned revision Playwright launches.
 *
 * The runner cannot ask the `playwright test` child for its engine version, so the producer reads it
 * from a launch of the same engine through the same locally pinned Playwright installation. The
 * version belongs to that pinned revision, not to this process.
 */
export async function resolveEngineVersion(engine) {
  const { chromium, firefox, webkit } = await import('@playwright/test');
  const engineType = { chromium, firefox, webkit }[engine];
  if (!engineType) throw new Error(`unsupported browser engine: ${engine}`);
  const browser = await engineType.launch({ headless: true });
  try {
    return browser.version();
  } finally {
    await browser.close();
  }
}

/** The commit the evidence belongs to, exactly as the validator derives it from the repository. */
export async function resolveRepositoryCommit() {
  const projectDirectory = fileURLToPath(new URL('..', import.meta.url));
  let stdout;
  try {
    ({ stdout } = await execFileAsync('git', ['rev-parse', 'HEAD'], { cwd: projectDirectory }));
  } catch (error) {
    throw new Error(`could not read the repository commit: ${error.message}`, { cause: error });
  }
  const commit = stdout.trim();
  if (!GIT_SHA.test(commit)) {
    throw new Error(`git rev-parse HEAD returned an invalid commit: ${commit}`);
  }
  return commit;
}

/**
 * Writes one canonical JSON per active browser contract of `target`.
 *
 * The documents are built from the registry and the mapping file, never from the JUnit report titles:
 * a scenario the registry declares but no mapping row names is an error here rather than a silently
 * missing entry, and a mapped testcase that did not pass is an error rather than a `Passed` claim.
 * No JUnit report, Playwright launch or repository command is read when no active contract requires
 * this target.
 */
export async function generateContractEvidence({
  target,
  engine,
  version,
  bundlePath,
  junitDirectory,
  outputDirectory,
  commit,
  registryPath,
  mappingPath,
}) {
  requireValue(target, 'target');
  requireValue(engine, 'engine');
  requireValue(bundlePath, 'bundlePath');
  requireValue(registryPath, 'registryPath');
  requireValue(mappingPath, 'mappingPath');
  if (!EXPECTED_TARGETS.includes(target)) {
    throw new Error(`browser contract evidence requires one of ${EXPECTED_TARGETS.join(', ')}: ${target}`);
  }

  const contracts = await readActiveContracts(registryPath, target);
  if (contracts.length === 0) return [];
  const mappings = await readMappings(mappingPath);
  const junit = await readJUnitSummary(junitDirectory);
  const resolvedVersion = version ?? await resolveEngineVersion(engine);
  requireValue(resolvedVersion, 'version');
  const resolvedCommit = commit ?? await resolveRepositoryCommit();
  if (!GIT_SHA.test(resolvedCommit)) throw new Error(`commit must be a Git SHA: ${resolvedCommit}`);
  const bundle = await hashBundle(bundlePath);

  const written = [];
  for (const contract of contracts) {
    const targetMappings = mappings.filter(
      (mapping) => mapping.contractId === contract.contractId && mapping.target === target,
    );
    const mappingErrors = validateTargetMappings(contract, target, targetMappings);
    if (mappingErrors.length > 0) throw new Error(mappingErrors.join('\n'));
    for (const mapping of targetMappings) {
      const status = junit.cases.get(`${mapping.testClass}#${mapping.testName}`);
      if (status === undefined) {
        throw new Error(`mapped testcase is missing: ${mapping.testClass}#${mapping.testName}`);
      }
      if (status !== 'passed') {
        throw new Error(`mapped testcase did not pass: ${mapping.testClass}#${mapping.testName} (${status})`);
      }
    }
    const document = {
      schemaVersion: SCHEMA_VERSION,
      commit: resolvedCommit,
      target,
      execution: {
        kind: EXECUTION_KIND,
        engine,
        version: resolvedVersion,
        bundleName: bundle.name,
        bundleSha256: bundle.sha256,
      },
      adapter: ADAPTER,
      environment: {
        os: `${process.platform} ${process.arch}`,
        runtime: `Node.js ${process.versions.node}`,
        toolchain: await playwrightToolchain(),
      },
      durationMillis: junit.durationMillis,
      capabilities: { initial: [], transitions: [] },
      scenarios: targetMappings
        .filter((mapping) => mapping.kind === 'scenario')
        .map((mapping) => ({
          contractId: contract.contractId,
          scenarioId: mapping.evidenceId,
          result: 'Passed',
          oracle: contract.oracle,
        }))
        .sort(byIdentity('scenarioId')),
      sentinels: targetMappings
        .filter((mapping) => mapping.kind === 'sentinel')
        .map((mapping) => ({
          contractId: contract.contractId,
          sentinelId: mapping.evidenceId,
          result: 'Killed',
        }))
        .sort(byIdentity('sentinelId')),
      tests: {
        tests: junit.tests,
        skipped: junit.skipped,
        failures: junit.failures,
        errors: junit.errors,
      },
    };
    const artifact = join(outputDirectory, `${contract.contractId}.json`);
    await writeAtomically(artifact, `${JSON.stringify(document, null, 4)}\n`);
    written.push(artifact);
  }
  return written;
}

function requireValue(value, name) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${name} must be a non-blank string`);
  }
}

/** Code-unit order, the order `sorted()` gives the validator's declared identities. */
function byIdentity(field) {
  return (left, right) => {
    if (left[field] === right[field]) return 0;
    return left[field] < right[field] ? -1 : 1;
  };
}

/** The active contracts of the registry whose `requiredTargets` contain `target`. */
async function readActiveContracts(registryPath, target) {
  const rows = await readTable(registryPath, REGISTRY_HEADER, 'contract registry');
  return rows
    .map(({ columns, number }) => {
      if (columns.length !== 11) throw new Error(`${registryPath} line ${number}: expected 11 columns`);
      const [contractId, status, , , , oracle, scenarios, requiredTargets, , sentinels] = columns;
      if (!CONTRACT_ID.test(contractId)) {
        throw new Error(`${registryPath} line ${number}: invalid contractId: ${contractId}`);
      }
      if (status !== 'active') return null;
      if (oracle === 'O4') throw new Error(`${contractId}: uses O4 and requires differential evidence`);
      if (!EXPECTED_ORACLES.includes(oracle)) {
        throw new Error(`${contractId}: must use oracle O1, O2 or O3`);
      }
      if (!list(requiredTargets).includes(target)) return null;
      return { contractId, oracle, scenarios: list(scenarios), sentinels: list(sentinels) };
    })
    .filter((contract) => contract !== null);
}

async function readMappings(mappingPath) {
  const rows = await readTable(mappingPath, MAPPING_HEADER, 'contract evidence mapping');
  return rows.map(({ columns, number }) => {
    if (columns.length !== 6) throw new Error(`${mappingPath} line ${number}: expected 6 columns`);
    if (columns.some((column) => column.length === 0)) {
      throw new Error(`${mappingPath} line ${number}: columns must not be blank`);
    }
    const [contractId, target, kind, evidenceId, testClass, testName] = columns;
    if (!(kind in MAPPING_KINDS)) throw new Error(`unknown evidence kind: ${kind}`);
    return { contractId, target, kind, evidenceId, testClass, testName };
  });
}

/** Reads one TSV table: the header must match, and comments and blank lines carry no row. */
async function readTable(path, header, description) {
  const text = await readFile(path, 'utf8').catch((error) => {
    throw new Error(`${description} does not exist: ${path} (${error.message})`, { cause: error });
  });
  const rows = text.replace(/^\uFEFF/, '')
    .split('\n')
    .map((line) => line.trimEnd())
    .map((line, index) => ({ line, number: index + 1 }))
    .filter(({ line }) => line.trim().length > 0 && !line.startsWith('#'));
  if (rows.length === 0 || rows[0].line !== header) {
    throw new Error(`invalid ${description} header in ${path}`);
  }
  return rows.slice(1).map(({ line, number }) => ({ columns: line.split('\t'), number }));
}

/** `-` is the empty list of the registry, exactly as `ContractRegistry` reads it. */
function list(cell) {
  return cell === '-' ? [] : cell.split(',').map((value) => value.trim());
}

/**
 * The mapping completeness rule of `validateTargetMappings`: the mapped identities of one kind are
 * exactly the declared ones, and no identity is mapped twice.
 */
function validateTargetMappings(contract, target, mappings) {
  const errors = [];
  for (const kind of Object.keys(MAPPING_KINDS)) {
    const declared = kind === 'scenario' ? contract.scenarios : contract.sentinels;
    const mapped = mappings.filter((mapping) => mapping.kind === kind).map((mapping) => mapping.evidenceId);
    const mappedIds = new Set(mapped);
    [...mappedIds].filter((id) => mapped.indexOf(id) !== mapped.lastIndexOf(id)).sort()
      .forEach((duplicate) => errors.push(`${contract.contractId}[${target}]: duplicate ${kind}: ${duplicate}`));
    const declaredIds = new Set(declared);
    declared.filter((id) => !mappedIds.has(id)).sort()
      .forEach((missing) => errors.push(`${contract.contractId}[${target}]: missing ${kind}: ${missing}`));
    [...mappedIds].filter((id) => !declaredIds.has(id)).sort()
      .forEach((unknown) => errors.push(`${contract.contractId}[${target}]: unknown ${kind}: ${unknown}`));
  }
  return errors;
}

/**
 * The JUnit totals and identities, derived exactly as `JUnitEvidence.read` derives them: every
 * `TEST-*.xml` of the directory contributes its declared counts, each testsuite's `time` is scaled to
 * whole milliseconds with `HALF_UP` and summed, and a testcase is identified by its `classname` and
 * `name`.
 */
async function readJUnitSummary(directory) {
  const entries = await readdir(directory, { withFileTypes: true }).catch((error) => {
    throw new Error(`JUnit report directory does not exist: ${directory} (${error.message})`, { cause: error });
  });
  const reports = entries
    .filter((entry) => entry.isFile() && entry.name.startsWith('TEST-') && entry.name.endsWith('.xml'))
    .map((entry) => join(directory, entry.name))
    .sort();
  if (reports.length === 0) {
    throw new Error(`JUnit report directory contains no TEST-*.xml: ${directory}`);
  }

  const cases = new Map();
  let tests = 0;
  let skipped = 0;
  let failures = 0;
  let errors = 0;
  let durationMillis = 0;
  for (const report of reports) {
    for (const suite of await readSuites(report)) {
      const suiteCases = suite.testcase ?? [];
      const declared = {
        tests: junitCount(suite, 'tests', report),
        skipped: junitCount(suite, 'skipped', report),
        failures: junitCount(suite, 'failures', report),
        errors: junitCount(suite, 'errors', report),
      };
      const actual = {
        tests: suiteCases.length,
        skipped: suiteCases.filter((testCase) => statusOf(testCase, report) === 'skipped').length,
        failures: suiteCases.filter((testCase) => statusOf(testCase, report) === 'failed').length,
        errors: suiteCases.filter((testCase) => statusOf(testCase, report) === 'error').length,
      };
      for (const count of ['tests', 'skipped', 'failures', 'errors']) {
        if (declared[count] !== actual[count]) {
          throw new Error(
            `JUnit XML report ${report} declares ${declared[count]} ${count} but contains ${actual[count]}`,
          );
        }
      }
      for (const testCase of suiteCases) {
        const className = requiredAttribute(testCase, 'classname', report);
        const name = requiredAttribute(testCase, 'name', report);
        const identity = `${className}#${name}`;
        if (cases.has(identity)) throw new Error(`duplicate testcase: ${identity}`);
        cases.set(identity, statusOf(testCase, report));
      }
      tests += declared.tests;
      skipped += declared.skipped;
      failures += declared.failures;
      errors += declared.errors;
      durationMillis += decimalToMillis(requiredAttribute(suite, 'time', report), report);
    }
  }
  if (tests === 0) throw new Error('JUnit evidence contains no tests');
  return { tests, skipped, failures, errors, durationMillis, cases };
}

async function readSuites(report) {
  const document = new XMLParser({
    attributeNamePrefix: '@_',
    ignoreAttributes: false,
    isArray: (_name, elementPath) => elementPath === 'testsuites.testsuite'
      || elementPath === 'testsuites.testsuite.testcase',
  }).parse(await readFile(report, 'utf8'));
  const root = document.testsuites;
  if (!root) throw new Error(`JUnit XML report ${report} must contain a testsuites root`);
  const suites = root.testsuite ?? [];
  if (suites.length === 0) throw new Error(`JUnit XML report ${report} must contain at least one testsuite`);
  return suites;
}

/** The terminal status of one testcase, as `JUnitEvidence.parseCase` reduces it. */
function statusOf(testCase, report) {
  const declared = TERMINAL_STATUSES.filter((status) => testCase[status] !== undefined);
  if (declared.length > 1) {
    throw new Error(`JUnit XML report ${report} testcase declares multiple terminal statuses`);
  }
  switch (declared[0]) {
    case 'skipped': return 'skipped';
    case 'failure': return 'failed';
    case 'error': return 'error';
    default: return 'passed';
  }
}

function junitCount(suite, name, report) {
  const value = suite[`@_${name}`];
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    throw new Error(`JUnit XML report ${report} has an invalid ${name} count`);
  }
  return Number(value);
}

function requiredAttribute(element, name, report) {
  const value = element[`@_${name}`];
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`JUnit XML report ${report} requires the ${name} attribute`);
  }
  return value;
}

/**
 * `time` is the seconds decimal of the suite; milliseconds are `time * 1000` rounded `HALF_UP`,
 * computed on the decimal string so that no binary rounding can land a millisecond away from what
 * `JUnitEvidence.durationMillis` derives from the same attribute.
 */
function decimalToMillis(value, report) {
  if (!DECIMAL.test(value)) throw new Error(`JUnit XML report ${report} has an invalid time: ${value}`);
  const [whole, fraction = ''] = value.split('.');
  const padded = fraction.padEnd(3, '0');
  const millis = Number(whole) * 1000 + Number(padded.slice(0, 3));
  return fraction.length > 3 && padded[3] >= '5' ? millis + 1 : millis;
}

async function hashBundle(bundlePath) {
  const file = await stat(bundlePath).catch((error) => {
    throw new Error(`browser bundle does not exist: ${bundlePath} (${error.message})`, { cause: error });
  });
  if (!file.isFile()) throw new Error(`browser bundle is not a file: ${bundlePath}`);
  return {
    name: basename(bundlePath),
    sha256: createHash('sha256').update(await readFile(bundlePath)).digest('hex'),
  };
}

async function playwrightToolchain() {
  const manifest = JSON.parse(
    await readFile(new URL('../node_modules/@playwright/test/package.json', import.meta.url), 'utf8'),
  );
  return `@playwright/test ${manifest.version}`;
}

/** The write discipline of `ContractEvidence.writeAtomically`: a temporary file, then one rename. */
async function writeAtomically(path, contents) {
  await mkdir(dirname(path), { recursive: true });
  const temporary = `${path}.${process.pid}-${Date.now()}.tmp`;
  try {
    await writeFile(temporary, contents);
    await rename(temporary, path);
  } catch (error) {
    await rm(temporary, { force: true });
    throw error;
  }
}

/** Parses `--name=value` arguments; every flag this producer understands requires a value. */
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

/** The whole surface of `kadre/WEB-IMPLEMENTATION-ROADMAP.md` section 3.8 for one target and engine. */
export async function main(argumentsList) {
  const argumentsByName = parseArguments(argumentsList);
  for (const name of ['--target', '--engine', '--bundle', '--junit', '--output', '--contracts', '--mapping']) {
    if (!argumentsByName.has(name)) throw new Error(`missing required argument: ${name}`);
  }
  const written = await generateContractEvidence({
    target: argumentsByName.get('--target'),
    engine: argumentsByName.get('--engine'),
    version: argumentsByName.get('--version'),
    bundlePath: argumentsByName.get('--bundle'),
    junitDirectory: argumentsByName.get('--junit'),
    outputDirectory: argumentsByName.get('--output'),
    commit: argumentsByName.get('--commit'),
    registryPath: argumentsByName.get('--contracts'),
    mappingPath: argumentsByName.get('--mapping'),
  });
  for (const artifact of written) console.log(`contract evidence: ${artifact}`);
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main(process.argv.slice(2));
}
