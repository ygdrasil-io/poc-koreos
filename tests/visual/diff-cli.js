#!/usr/bin/env node
// CLI de diff d'images cross-plateforme (Redmine #88).
//
// Compare deux PNG (capture courante vs baseline) avec une tolérance en % de pixels
// différents. Conçu pour être appelé par n'importe quel job CI de plateforme, quelle
// que soit la source de la capture (readback GPU desktop, Playwright web, etc.).
//
// Usage : node diff-cli.js <actual.png> <baseline.png> <diffOut.png> [tolerance] [summaryOut.md]
//
// NON BLOQUANT : affiche le verdict et écrit le diff/summary, mais sort toujours en 0
// (le rendu GPU n'est pas déterministe au pixel près entre machines).
const fs = require('fs');
const path = require('path');
const { PNG } = require('pngjs');
const pixelmatch = require('pixelmatch').default || require('pixelmatch');

const [actualPath, baselinePath, diffOut, toleranceArg, summaryOut, label] = process.argv.slice(2);
const tolerance = toleranceArg ? parseFloat(toleranceArg) : 0.02;
const platform = label || 'inconnu';

function fail(msg) { console.error(`[visual] ${msg}`); process.exit(0); }

if (!actualPath || !baselinePath) fail('usage: diff-cli.js <actual> <baseline> <diffOut> [tol] [summary]');
if (!fs.existsSync(actualPath)) fail(`capture absente: ${actualPath}`);

let status, ratio = 0, diffPixels = 0, total = 0, reason = '';
if (!fs.existsSync(baselinePath)) {
  status = 'no-baseline';
  reason = `baseline absente: ${baselinePath} (lancer la mise à jour des baselines)`;
} else {
  const actual = PNG.sync.read(fs.readFileSync(actualPath));
  const baseline = PNG.sync.read(fs.readFileSync(baselinePath));
  total = actual.width * actual.height;
  if (actual.width !== baseline.width || actual.height !== baseline.height) {
    status = 'mismatch';
    ratio = 1; diffPixels = total;
    reason = `dimensions ${actual.width}x${actual.height} != baseline ${baseline.width}x${baseline.height}`;
  } else {
    const diff = new PNG({ width: actual.width, height: actual.height });
    diffPixels = pixelmatch(actual.data, baseline.data, diff.data, actual.width, actual.height, { threshold: 0.1 });
    ratio = diffPixels / total;
    status = ratio > tolerance ? 'mismatch' : 'match';
    if (status === 'mismatch' && diffOut) {
      fs.mkdirSync(path.dirname(diffOut), { recursive: true });
      fs.writeFileSync(diffOut, PNG.sync.write(diff));
    }
  }
}

const pct = (ratio * 100).toFixed(3);
const icon = status === 'match' ? '✅' : status === 'no-baseline' ? '🆕' : '⚠️';
const line = `${icon} ${status} — diff ${pct}% (tolérance ${tolerance * 100}%${total ? `, ${diffPixels}/${total} px` : ''})${reason ? ` — ${reason}` : ''}`;
console.log(`[visual] ${line}`);

if (summaryOut) {
  // Intègre les captures en base64 directement dans le Job Summary GitHub pour les
  // visualiser sans télécharger les artefacts (avec repli texte si non rendu).
  const imgTag = (p, alt) => {
    if (!p || !fs.existsSync(p)) return '';
    const b64 = fs.readFileSync(p).toString('base64');
    return `<figure style="display:inline-block;margin:4px;text-align:center">` +
      `<img alt="${alt}" width="320" src="data:image/png;base64,${b64}"><figcaption>${alt}</figcaption></figure>`;
  };
  const gallery = [
    imgTag(baselinePath, 'baseline'),
    imgTag(actualPath, 'courante'),
    fs.existsSync(diffOut || '') ? imgTag(diffOut, 'diff') : '',
  ].filter(Boolean).join('\n');

  fs.mkdirSync(path.dirname(summaryOut), { recursive: true });
  fs.writeFileSync(summaryOut,
    `### Régression visuelle — hello-triangle (${platform}, readback GPU)\n\n${line}\n\n` +
    `${gallery}\n\n` +
    `_Captures aussi disponibles en artefacts du run._\n`);
}

// Non bloquant.
process.exit(0);
