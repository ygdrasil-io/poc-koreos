import { createServer } from 'node:http';
import { readFile, readdir, rm, stat } from 'node:fs/promises';
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

if (!['js', 'wasmJs'].includes(target) || !distribution || !evidence) {
  throw new Error('expected --target=js|wasmJs, --distribution=<directory>, and --evidence=<directory>');
}
if (!existsSync(distribution)) {
  throw new Error(`missing ${target} browser distribution: ${distribution}`);
}

const junitDirectory = join(evidence, 'test-results', 'browser', 'chromium');
const junitOutput = join(junitDirectory, 'TEST-web-phase0.xml');
const playwrightOutput = join(evidence, 'diagnostics', 'playwright');
const entryScript = await findEntryScript(distribution);
const server = await serveDistribution(distribution, entryScript);
let passed = false;

try {
  const command = process.platform === 'win32' ? 'node_modules/.bin/playwright.cmd' : 'node_modules/.bin/playwright';
  const result = await run(command, ['test', '--config', 'playwright/playwright.config.mjs'], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      KADRE_FIXTURE_URL: `${server.url}/index.html`,
      KADRE_JUNIT_OUTPUT: junitOutput,
      KADRE_PLAYWRIGHT_OUTPUT_DIR: playwrightOutput,
    },
    stdio: 'inherit',
  });
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

function run(command, argumentsList, options) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, argumentsList, options);
    child.once('error', rejectRun);
    child.once('close', (status) => resolveRun({ status }));
  });
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

async function serveDistribution(directory, entryScript) {
  const instance = createServer(async (request, response) => {
    try {
      const path = request.url === '/index.html'
        ? null
        : resolve(directory, `.${new URL(request.url, 'http://127.0.0.1').pathname}`);
      if (path === null) {
        response.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
        response.end(`<!doctype html><html><body><script src="/${entryScript}"></script></body></html>`);
        return;
      }
      if (!path.startsWith(`${resolve(directory)}${sep}`) || !(await stat(path)).isFile()) {
        response.writeHead(404).end();
        return;
      }
      response.writeHead(200, { 'content-type': contentType(path) });
      response.end(await readFile(path));
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

function contentType(path) {
  switch (extname(basename(path))) {
    case '.js': return 'text/javascript; charset=utf-8';
    case '.wasm': return 'application/wasm';
    case '.map': return 'application/json; charset=utf-8';
    default: return 'application/octet-stream';
  }
}
