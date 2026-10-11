/*
 * Le filet e2e GROSSIER du demo desk-tour web — la règle de régression commise.
 *
 * Méthode (celle de la sonde, PROBE-NOTES.md, produite par le task 3) : la distribution
 * déjà construite est servie en statique ; le canvas Compose vit dans le shadow root
 * OUVERT du conteneur (`#kadre-desk-tour`), l'interface se clique en COORDONNÉES CANVAS :
 * le miroir d'accessibilité qui expose les nœuds nommés est `pointer-events: none`, donc
 * seuls de vrais événements souris aux coords du miroir touchent le canvas et voyagent
 * vers les DEUX piles (Compose d'abord, kadre par bouillonnement — Q2-c). Un clic qui
 * ouvre une note est donc aussi la preuve que l'attach kadre est vivant.
 *
 * Discipline grossière (tenue) : aucun pixel, aucun texte lu DANS le canvas (opaque) —
 * seulement zéro `pageerror`, présence/absence d'éléments DOM host, et géométrie. Le
 * miroir d'accessibilité est du DOM (pas des pixels) : s'en servir pour VISER est la
 * méthode ; les seules assertions textuelles sont d'existence/absence de nœuds du miroir.
 *
 * Ce que chaque test tue (la mutation que l'assertion attrape) :
 *  1. boot            — distribution cassée, wasm qui ne s'amorce pas, `attachKadre`
 *                       qui jette, canvas absent du conteneur ;
 *  2. note ouverte    — la porte de capacité du dispatcher qui refuse à tort (le clic
 *                       passe mais aucune note), le host web qui n'ajoute pas le div,
 *                       la boucle de montage qui ne boote pas le viewport de la note,
 *                       une note SANS son propre canvas (420×240) ;
 *  3. note fermée     — un div ZOMBIE (l'action Fermer ne retire plus l'élément), la
 *                       ligne qui reste dans le miroir, et un résidu qui CRIE après la
 *                       fermeture (le resize post-fermeture réveille l'écouteur du
 *                       viewport retiré — Q3) ;
 *  4. resize          — le chemin de resize cassé (Compose ne relit sa boîte qu'aux
 *                       resizes FENÊTRE — Q2-d) : canvas principal aux nouvelles
 *                       dimensions exactes, canvas de note à ses 420×240 fixes ;
 *  5. cascade         — la collision de cascade (place fondée sur le nombre de notes
 *                       VIVANTES : fermer puis rouvrir renotait la place d'une note
 *                       encore ouverte) — le décalage suit désormais la clé monotone
 *                       de la note (correctif démo du task 4) ; ce test l'épingle.
 *
 * Portée navigateur : chromium seul (sample, pas le driver de contrats — voir la config).
 * Captures : `playwright-output/screenshots/` (gitignoré — les artifacts commis pourrissent,
 * le spec commis est le filet) ; le rapport du task les cite par chemin.
 */

import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { isAbsolute, join } from 'node:path';
import { expect, test } from '@playwright/test';

const CONTAINER = '#kadre-desk-tour';
const NOTE_LAYER = '#kadre-desk-tour-notes';
const NOTE = '.desk-tour-note';

const VIEWPORT = { width: 1400, height: 1000 };
const RESIZED = { width: 1100, height: 850 };
const NOTE_SIZE = { width: 420, height: 240 };

const outputDir = process.env.KADRE_E2E_OUTPUT_DIR ?? 'playwright-output';
const shotsDir = isAbsolute(outputDir)
  ? join(outputDir, 'screenshots')
  : fileURLToPath(new URL(join(outputDir, 'screenshots'), import.meta.url));

// ---------------------------------------------------------------- helpers ---

/*
 * Le sac d'erreurs du test : zéro `pageerror` sur TOUTE la durée, et zéro erreur
 * console hors le bruit sanctionné — le 404 du favicon que le navigateur demande
 * tout seul (la distribution n'en embarque pas, sonde Q4).
 */
function watchErrors(page) {
  const pageErrors = [];
  const consoleErrors = [];
  page.on('pageerror', (error) => pageErrors.push(String(error)));
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    if (message.location()?.url?.includes('favicon')) return;
    consoleErrors.push(message.text());
  });
  return { pageErrors, consoleErrors };
}

function expectNoErrors(errors, stage) {
  expect(errors.pageErrors, `${stage} : pageerror inattendu`).toEqual([]);
  expect(errors.consoleErrors, `${stage} : erreur console inattendue`).toEqual([]);
}

async function screenshot(page, name) {
  mkdirSync(shotsDir, { recursive: true });
  await page.screenshot({ path: join(shotsDir, name) });
}

/*
 * Le nœud nommé de l'UI, tel que le miroir d'accessibilité le expose (Playwright
 * perce les shadow roots ouverts). Rôle bouton d'abord, texte sinon — les items de
 * navigation de Material3 n'ont pas forcément un rôle bouton, les boutons oui.
 */
async function uiTarget(page, name) {
  const byRole = page.getByRole('button', { name, exact: true });
  const byText = page.getByText(name, { exact: true });
  await byRole.or(byText).first().waitFor({ state: 'visible' });
  return (await byRole.count()) > 0 ? byRole.first() : byText.first();
}

/*
 * Un point du rectangle cible que AUCUNE note vivante ne couvre — le geste du vrai
 * utilisateur quand une fenêtre flottante recouvre un bouton : cliquer la partie
 * visible. Centre par défaut ; en balayage gauche→droite sinon ; null = le bouton est
 * entièrement recouvert, et le test échoue honnêtement.
 */
function uncoveredPoint(box, blockers) {
  const y = box.y + box.height / 2;
  const covered = (x, b) =>
    x > b.x - 1 && x < b.x + b.width + 1 && y > b.y - 1 && y < b.y + b.height + 1;
  const xs = [];
  for (let f = 0.03; f < 1; f += 0.03) xs.push(box.x + box.width * f);
  for (const x of xs) if (!blockers.some((b) => covered(x, b))) return { x, y };
  return null;
}

/* Clic réel (coordonnées canvas) sur l'élément nommé, hors des notes passées en blocantes. */
async function clickUi(page, name, blockers = []) {
  const box = await (await uiTarget(page, name)).boundingBox();
  expect(box, `rect du miroir pour « ${name} »`).not.toBeNull();
  const point = blockers.length === 0 ? { x: box.x + box.width / 2, y: box.y + box.height / 2 } : uncoveredPoint(box, blockers);
  expect(point, `« ${name} » est entièrement recouvert — aucune partie cliquable`).not.toBeNull();
  await page.mouse.click(point.x, point.y);
}

/*
 * Amorce : la page charge, le canvas du viewport principal apparaît (l'amorçage wasm
 * est asynchrone — onDomReady puis onSkikoReady, Q1) ; retourne le sac d'erreurs.
 */
async function openDemo(page) {
  const errors = watchErrors(page);
  await page.goto('/');
  await page.locator(`${CONTAINER} canvas`).waitFor({ state: 'visible' });
  return errors;
}

/* Géométrie du canvas principal : boîte CSS (viewport) et tampon (attributs). */
async function mainCanvasFacts(page) {
  const canvas = page.locator(`${CONTAINER} canvas`).first();
  const box = await canvas.boundingBox();
  const buffer = await canvas.evaluate((el) => ({ width: el.width, height: el.height }));
  return { box, buffer };
}

/* Géométrie de chaque note ouverte : boîte du div, canvas embarqué (CSS + tampon). */
async function noteFacts(page) {
  const notes = page.locator(NOTE);
  const facts = [];
  for (let i = 0; i < (await notes.count()); i++) {
    const note = notes.nth(i);
    const canvas = note.locator('canvas').first();
    facts.push({
      box: await note.boundingBox(),
      canvasBox: await canvas.boundingBox(),
      canvasBuffer: await canvas.evaluate((el) => ({ width: el.width, height: el.height })),
    });
  }
  return facts;
}

function expectBox(box, x, y, width, height, label) {
  expect(box, label).not.toBeNull();
  expect(box.x, `${label} : x`).toBeCloseTo(x, 0);
  expect(box.y, `${label} : y`).toBeCloseTo(y, 0);
  expect(box.width, `${label} : largeur`).toBeCloseTo(width, 0);
  expect(box.height, `${label} : hauteur`).toBeCloseTo(height, 0);
}

function expectNoteFacts(note, label) {
  // Le div : posé à la marge demandée ; la bordure de 1 px entre dans la boîte CSS
  // (content-box) — bornes tolérantes, pas de vérité au pixel sur le div hôte.
  expect(note.box.x, `${label} : x du div`).toBeCloseTo(48, 0);
  expect(note.box.y, `${label} : y du div`).toBeCloseTo(48, 0);
  expect(note.box.width, `${label} : largeur du div`).toBeGreaterThanOrEqual(NOTE_SIZE.width);
  expect(note.box.width, `${label} : largeur du div`).toBeLessThanOrEqual(NOTE_SIZE.width + 4);
  expect(note.box.height, `${label} : hauteur du div`).toBeGreaterThanOrEqual(NOTE_SIZE.height);
  expect(note.box.height, `${label} : hauteur du div`).toBeLessThanOrEqual(NOTE_SIZE.height + 4);
  // LE canvas de la note : exactement 420×240 (boîte CSS ET tampon), dans le div.
  expect(note.canvasBox.width, `${label} : largeur CSS du canvas`).toBeCloseTo(NOTE_SIZE.width, 0);
  expect(note.canvasBox.height, `${label} : hauteur CSS du canvas`).toBeCloseTo(NOTE_SIZE.height, 0);
  expect(Math.abs(note.canvasBox.x - note.box.x), `${label} : canvas à l'origine du div (x)`).toBeLessThanOrEqual(2);
  expect(Math.abs(note.canvasBox.y - note.box.y), `${label} : canvas à l'origine du div (y)`).toBeLessThanOrEqual(2);
  expect(note.canvasBuffer, `${label} : tampon du canvas`).toEqual(NOTE_SIZE);
}

function expectDistinctPositions(facts, label) {
  expect(facts.length, `${label} : nombre de notes`).toBeGreaterThanOrEqual(2);
  for (let i = 0; i < facts.length; i++) {
    for (let j = i + 1; j < facts.length; j++) {
      const a = facts[i].box;
      const b = facts[j].box;
      const stacked = Math.abs(a.x - b.x) < 1 && Math.abs(a.y - b.y) < 1;
      expect(stacked, `${label} : notes ${i} et ${j} empilées au même point (${a.x},${a.y})`).toBe(false);
    }
  }
}

/* Le chemin commun : Bureau (route de départ = Activité), puis la création de note. */
async function openDeskAndCreateNote(page) {
  await clickUi(page, 'Bureau');
  await clickUi(page, 'Créer une note flottante');
  await expect(page.locator(NOTE)).toHaveCount(1);
}

// ------------------------------------------------------------------ tests ---

test('boot : la page charge, le canvas Compose est dans le conteneur, zéro pageerror', async ({ page }) => {
  const errors = await openDemo(page);

  // Exactement UN canvas dans le conteneur — le viewport principal. Il n'est PAS un
  // enfant light-DOM : le localisateur perce le shadow root ouvert (sonde Q1) — et la
  // structure le prouve : la racine du canvas est un ShadowRoot attaché sous le conteneur.
  const canvas = page.locator(`${CONTAINER} canvas`);
  await expect(canvas).toHaveCount(1);
  const inShadowUnderContainer = await canvas.first().evaluate((el, selector) => {
    const root = el.getRootNode();
    return root instanceof ShadowRoot && root.host.closest(selector) !== null;
  }, CONTAINER);
  expect(inShadowUnderContainer, 'le canvas vit dans le shadow root ouvert du conteneur').toBe(true);

  // La couche à notes est vide à l'amorce — aucune note fantôme.
  await expect(page.locator(`${NOTE_LAYER} ${NOTE}`)).toHaveCount(0);

  // Le canvas principal remplit la fenêtre (le conteneur EST la fenêtre du demo) —
  // boîte CSS et tampon identiques au viewport, dpr 1.
  const facts = await mainCanvasFacts(page);
  expectBox(facts.box, 0, 0, VIEWPORT.width, VIEWPORT.height, 'canvas principal');
  expect(facts.buffer, 'tampon du canvas principal').toEqual({
    width: VIEWPORT.width,
    height: VIEWPORT.height,
  });

  await screenshot(page, '1-boot.png');
  expectNoErrors(errors, 'boot');
});

test('une note ouvre par le vrai UI et porte son propre canvas 420×240', async ({ page }) => {
  const errors = await openDemo(page);

  // Le clic création traverse les DEUX piles (canvas Compose, bouillonnement kadre) :
  // s'il produit une note, l'attach kadre et la porte de capacité sont vivants.
  await openDeskAndCreateNote(page);

  const [note] = await noteFacts(page);
  // Première note : la marge seule, aucun décalage de cascade.
  expectNoteFacts(note, 'première note');

  // La note est dans la couche à notes (jamais dans le conteneur — son layout ne
  // bouge pas quand une note apparaît/disparaît, construction de la page hôte).
  expect(
    await page.locator(`${CONTAINER} ${NOTE}`).count(),
    'aucune note dans le conteneur principal',
  ).toBe(0);

  await screenshot(page, '2-note-open.png');
  expectNoErrors(errors, 'ouverture de note');
});

test('fermer la note retire son div — aucun zombie, aucun cri du résidu', async ({ page }) => {
  const errors = await openDemo(page);
  await openDeskAndCreateNote(page);

  // « Fermer » vit dans le viewport de la LISTE (route Bureau de la surface principale),
  // sous les notes en cascade — son rect n'est pas recouvert, le centre suffit.
  await clickUi(page, 'Fermer');

  await expect(page.locator(NOTE)).toHaveCount(0);
  // La ligne de note a quitté le vrai UI (nœud du miroir — du DOM, pas des pixels).
  await expect(page.getByText('Fermer', { exact: true })).toHaveCount(0);

  // Fenêtre d'observation : le resize post-fermeture réveille DÉLIBÉRÉMENT l'écouteur
  // du viewport retiré (le résidu documenté de la sonde Q3) — il doit rester silencieux.
  await page.setViewportSize(RESIZED);
  await page.waitForTimeout(1_000);
  const facts = await mainCanvasFacts(page);
  expectBox(facts.box, 0, 0, RESIZED.width, RESIZED.height, 'canvas principal après fermeture');

  await screenshot(page, '3-note-closed.png');
  expectNoErrors(errors, 'fermeture + resize post-fermeture');
});

test('un resize de fenêtre re-rend les DEUX canvases aux bonnes dimensions', async ({ page }) => {
  const errors = await openDemo(page);
  await openDeskAndCreateNote(page);

  // Le resize FENÊTRE est le chemin de resize de Compose wasm (Q2-d) : les deux
  // viewports relisent leur boîte — le principal suit la fenêtre, la note reste fixe.
  await page.setViewportSize(RESIZED);
  await page.waitForTimeout(500); // un temps de rendu, pas une attente de sémaphore

  const main = await mainCanvasFacts(page);
  expectBox(main.box, 0, 0, RESIZED.width, RESIZED.height, 'canvas principal redimensionné');
  expect(main.buffer, 'tampon du canvas principal').toEqual(RESIZED);

  const [note] = await noteFacts(page);
  expectNoteFacts(note, 'note après resize');

  await screenshot(page, '4-resized.png');
  expectNoErrors(errors, 'resize avec note ouverte');
});

test(
  'cascade : deux notes à positions DISTINCTES, et fermer+rouvrir ne reparle pas ' +
    'sur une note vivante',
  async ({ page }) => {
    const errors = await openDemo(page);
    await clickUi(page, 'Bureau');
    await clickUi(page, 'Créer une note flottante');
    await expect(page.locator(NOTE)).toHaveCount(1);

    // La note 1 recouvre une partie du bouton création (div fixe 48,48 + 420×240,
    // pointer-events auto) : le second clic vise la partie VISIBLE du bouton —
    // le geste réel sous fenêtre flottante.
    const blockers = (await noteFacts(page)).map((fact) => fact.box);
    await clickUi(page, 'Créer une note flottante', blockers);
    await expect(page.locator(NOTE)).toHaveCount(2);

    expectDistinctPositions(await noteFacts(page), 'deux notes ouvertes');
    await screenshot(page, '5-two-notes.png');

    // Le pli de cascade (mineur du task 3) : la place suivait le NOMBRE de notes
    // vivantes — fermer puis rouvrir renotait la place d'une note encore ouverte.
    // Correctif démo du task 4 (DemoNoteWindows) : la place suit la clé monotone.
    // Ce second acte ÉPINGLE le correctif : sans lui, la note rouverte s'empile
    // exactement sur la survivante et expectDistinctPositions échoue.
    await clickUi(page, 'Fermer'); // la première ligne de la liste = la note la plus ancienne
    await expect(page.locator(NOTE)).toHaveCount(1);
    const survivors = (await noteFacts(page)).map((fact) => fact.box);
    await clickUi(page, 'Créer une note flottante', survivors);
    await expect(page.locator(NOTE)).toHaveCount(2);

    expectDistinctPositions(await noteFacts(page), 'après fermeture + réouverture');
    await screenshot(page, '5-two-notes-reopened.png');
    expectNoErrors(errors, 'cascade de notes');
  },
);
