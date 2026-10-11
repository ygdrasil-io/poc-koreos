/*
 * Le serveur statique du filet e2e : il sert une distribution DÉJÀ CONSTRUITE
 * (`wasmJsBrowserDistribution`), il ne construit rien — la construction est le travail
 * de CI/local, jamais du test (la config Playwright refuse même de démarrer sans elle,
 * voir playwright.config.mjs). Zéro magie de dev-server : le fichier demandé, le bon
 * type MIME — `application/wasm` en particulier, ce que `WebAssembly.instantiateStreaming`
 * exige (la sonde Q4 : aucune en-tête CORS/COOP/COEP n'est nécessaire sur ce chemin).
 *
 * Configuré par la config Playwright (variables d'environnement, pas de CLI) :
 * - KADRE_DEMO_DIST : la racine de la distribution à servir (obligatoire) ;
 * - KADRE_DEMO_PORT : le port d'écoute (défaut 4173, même défaut que la config).
 */

import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, relative } from 'node:path';

const root = normalize(process.env.KADRE_DEMO_DIST ?? '');
if (root === '') {
  throw new Error('KADRE_DEMO_DIST doit pointer sur la distribution construite du demo');
}
const port = Number(process.env.KADRE_DEMO_PORT ?? 4173);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.wasm': 'application/wasm',
  '.json': 'application/json',
  '.map': 'application/json',
  '.license.txt': 'text/plain; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

createServer(async (req, res) => {
  try {
    let pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    if (pathname.endsWith('/')) pathname += 'index.html';
    const file = normalize(join(root, pathname));
    // Pas de traversée hors de la distribution : le chemin résolu doit y rester.
    if (relative(root, file).startsWith('..')) {
      res.writeHead(403);
      res.end();
      return;
    }
    const body = await readFile(file);
    res.writeHead(200, {
      'content-type': MIME[extname(file).toLowerCase()] ?? 'application/octet-stream',
      'cache-control': 'no-store',
    });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end('not found');
  }
}).listen(port, '127.0.0.1', () => {
  console.log(`desk-tour e2e: serving ${root} on http://127.0.0.1:${port}`);
});
