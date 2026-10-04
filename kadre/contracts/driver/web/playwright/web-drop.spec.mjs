import { expect, test } from '@playwright/test';

const fixtureUrl = process.env.KADRE_FIXTURE_URL;

async function loadScenario(page, scenario) {
  await page.goto(`${fixtureUrl}?scenario=${scenario}`);
  await expect(page.locator('body')).toHaveAttribute('data-kadre-ready', 'true', { timeout: 2_000 });
}

/** Dispatches one of the fixture's command events; every command is driven from the browser side. */
async function command(page, name) {
  await page.evaluate((event) => document.dispatchEvent(new Event(event)), name);
}

/** Waits for one real animation frame of the page. */
async function nextFrame(page) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())));
}

/**
 * The capability snapshot every drop test opens with, kept strictly apart from the flow assertions
 * that follow it: the structural claim of this phase is that drag-and-drop observation exists, and
 * a test that fails below must never be readable as "the capability was missing". The whole public
 * cell is asserted, so a regression in any member of it is named by this one line.
 */
async function awaitDropCapability(host) {
  await expect(host).toHaveAttribute(
    'data-kadre-input-caps',
    'keyboard=available pointer=available touch=available gestures=unsupported:gestureinput '
      + 'dragAndDrop=available textInput=supported rawInput=unsupported:rawinputaccess',
  );
}

/** The descriptors the host's own drag carries: its 16-byte file item and its text item. */
const dropItems = 'file:text/plain:kadre-drop.txt:16,text:text/plain:none:none';
const transferItems = 'file:text/plain:kadre-drop.txt:16:replayable;text:text/plain:none:none:replayable';
/** The reads of those items, each delivered whole (both fit in one chunk of the session's budget). */
const dropRead = '0=kadre-drop-bytes,1=kadre-drop-text';

/** The journal entry of one presentation, at the surface position the host's drag step named. */
const entered = (position) => new RegExp(`drop:entered:items=\\[${dropItems}\\]:@\\(${position}\\):rev=\\d+`);
/** The journal entry of one performed drop, at the surface position the host's drop step named. */
const performed = (position) => new RegExp(`drop:performed:items=\\[${dropItems}\\]:@\\(${position}\\):rev=\\d+`);

/**
 * Enters the host's drag and waits for the offer the element presented: the entry is the host's own
 * drag step (a `DragEvent` of the host's own `DataTransfer`, which no test builds), observed as a
 * `DropEntered` on the surface's own input stream.
 */
async function dragEnter(page, host) {
  await command(page, 'kadre-drag-enter');
  await expect(host).toHaveAttribute('data-kadre-input-events', entered('60,40'));
}

test('web-drop-presented-single-active', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);
  await expect(host).toHaveAttribute('data-kadre-drop-armed', 'true');

  // The entry presented offer #0, whose descriptors are the host's own items snapshotted at the
  // entry — read from the public `DropEntered` event, never from a fixture channel — and the
  // handler a real application installed accepted it in the entry's own frame.
  await dragEnter(page, host);
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'accepted');

  // A second entry without a leave presents exactly one active offer: the reducer ends the offer it
  // held (left surface) and the seam presents the new one, which the handler accepts again.
  await command(page, 'kadre-drag-enter');
  await expect(host).toHaveAttribute('data-kadre-input-events', entered('60,40'));
  await expect(host).toHaveAttribute(
    'data-kadre-input-events',
    new RegExp(`${entered('60,40').source};${entered('60,40').source}`),
  );
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'terminated:left-surface');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-1', 'accepted');
});

test('web-drop-accept-synchronous', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);
  await expect(host).toHaveAttribute('data-kadre-drop-armed', 'true');

  // The accept is decided inside the drag's own DOM frame: the handler's `AcceptDrop` of the offer
  // the seam dispatched is committed in that same frame, and the engine's outcome names the offer it
  // committed — the interaction lane's answer tied to the offer the input stream carries.
  await dragEnter(page, host);
  await expect(host).toHaveAttribute('data-kadre-drop-interaction', 'committed#0');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'accepted');

  // An accepted offer makes the element a drop target: the dragover's default — the browser's
  // "refuse this drop" — is the one thing prevented, and it is prevented on the very event the host
  // dispatched. The motion is delivered while the offer is held.
  await command(page, 'kadre-drag-over');
  await expect(host).toHaveAttribute('data-kadre-input-events', /;drop:moved@\(70,50\):rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-drop-default', 'enter=kept;over=prevented');

  // The performed drop is prevented the same way, and the offer it spent becomes claimable.
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-input-events', performed('80,60'));
  await expect(host).toHaveAttribute('data-kadre-drop-default', 'enter=kept;over=prevented;drop=prevented');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'transfer-available');
});

test('web-drop-rejected-without-handler', async ({ page }) => {
  // The rejecting variant: no handler is installed, which is the seam's "handler absent" arm.
  await loadScenario(page, 'drop-reject');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  // The offer was presented and died rejected: nothing accepted it, and no interaction outcome
  // exists — there was no handler to produce one.
  await dragEnter(page, host);
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'terminated:rejected');
  expect(await host.getAttribute('data-kadre-drop-interaction')).toBeNull();

  // An offer-less dragover re-presents the entry (a child's churn must not end the drag), which
  // dies rejected too — and the browser keeps the default of the drag it was having, read on the
  // very event the host dispatched.
  await command(page, 'kadre-drag-over');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-1', 'terminated:rejected');
  await expect(host).toHaveAttribute('data-kadre-drop-default', 'enter=kept;over=kept');
  const afterOver = await host.getAttribute('data-kadre-input-events');

  // The drop without an offer is nothing: the navigation default is kept, and no drop observation
  // was reduced — a drop the surface never accepted is one the surface never received.
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-drop-default', 'enter=kept;over=kept;drop=kept');
  await nextFrame(page);
  await expect(host).toHaveAttribute('data-kadre-input-events', afterOver);
  expect(await host.getAttribute('data-kadre-input-events')).not.toMatch(/drop:moved|drop:performed/);
});

test('web-drop-moved-after-accept', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  await dragEnter(page, host);
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'accepted');

  // The motion while the offer is held: two overs, two exact payloads, nothing merged away.
  await command(page, 'kadre-drag-over');
  await expect(host).toHaveAttribute('data-kadre-input-events', /;drop:moved@\(70,50\):rev=\d+$/);
  await command(page, 'kadre-drag-over');
  await expect(host).toHaveAttribute(
    'data-kadre-input-events',
    /;drop:moved@\(70,50\):rev=\d+;drop:moved@\(70,50\):rev=\d+$/,
  );

  // The leave ends the drag the surface held: the offer it presented terminates left-surface.
  await command(page, 'kadre-drag-leave');
  await expect(host).toHaveAttribute('data-kadre-input-events', /;drop:exited:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'terminated:left-surface');
});

test('web-drop-exit-terminates', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  await dragEnter(page, host);
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'accepted');

  // The leave ends the offer the surface held — and an offer that ended with a leave cannot be
  // performed any more: the drop that follows is a drop the surface holds nothing for, its default
  // kept and nothing reduced.
  await command(page, 'kadre-drag-leave');
  await expect(host).toHaveAttribute('data-kadre-input-events', /;drop:exited:rev=\d+$/);
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'terminated:left-surface');

  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-drop-default', 'enter=kept;leave=kept;drop=kept');
  await nextFrame(page);
  const events = await host.getAttribute('data-kadre-input-events');
  expect(events).toMatch(entered('60,40'));
  expect(events).not.toMatch(/drop:moved|drop:performed/);

  // The next dragover is a new drag: the seam re-presents through the same entry path, and the
  // fresh offer has an identity of its own — the dead one was never revived.
  await command(page, 'kadre-drag-over');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-1', 'accepted');
  expect(await host.getAttribute('data-kadre-input-events')).toMatch(/;drop:moved@\(70,50\):rev=\d+$/);
});

test('web-drop-performed-claimable', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  await dragEnter(page, host);
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-input-events', performed('80,60'));
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'transfer-available');

  // The transfer becomes claimable exactly once the drop was performed, and what is claimed carries
  // the descriptors the entry snapshotted with the read modes they honour.
  await command(page, 'kadre-drop-claim');
  await expect(host).toHaveAttribute('data-kadre-drop-claim', 'claimed');
  await expect(host).toHaveAttribute('data-kadre-drop-transfer', transferItems);
});

test('web-drop-claim-single-winner', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  await dragEnter(page, host);
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'transfer-available');
  await command(page, 'kadre-drop-claim');
  await expect(host).toHaveAttribute('data-kadre-drop-claim', 'claimed');

  // The claim is single-winner: the second claim of the same offer is the closed failure the
  // runtime promised, and the one winner keeps a transfer that still reads.
  await command(page, 'kadre-drop-claim-second');
  await expect(host).toHaveAttribute('data-kadre-drop-claim-second', 'failure:alreadyInUse:droptransfer');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'claimed');
  expect(await host.getAttribute('data-kadre-drop-read')).toBeNull();
  await command(page, 'kadre-drop-read');
  await expect(host).toHaveAttribute('data-kadre-drop-read', dropRead);
});

test('web-drop-read-bounded-copy', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  await dragEnter(page, host);
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'transfer-available');
  await command(page, 'kadre-drop-claim');
  await expect(host).toHaveAttribute('data-kadre-drop-claim', 'claimed');

  // The read delivers exactly the bytes the drag carried, in fresh copies the collector owns.
  await command(page, 'kadre-drop-read');
  await expect(host).toHaveAttribute('data-kadre-drop-read', dropRead);

  // The read is bounded by the budget the caller named: a read whose budget is below the item's
  // known size is refused before any byte moves, with the failure that names the item and the
  // budget it was handed.
  await command(page, 'kadre-drop-read-bounded');
  await expect(host).toHaveAttribute('data-kadre-drop-read-bounded', 'failure:resourceLimitExceeded:dropitem:4');

  // The refused read consumed nothing: the next read still delivers the whole payload.
  await command(page, 'kadre-drop-read');
  await expect(host).toHaveAttribute('data-kadre-drop-read', `${dropRead};${dropRead}`);
});

test('web-drop-teardown-closes', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  // A claimed transfer in hand, read once for real.
  await dragEnter(page, host);
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'transfer-available');
  await command(page, 'kadre-drop-claim');
  await expect(host).toHaveAttribute('data-kadre-drop-claim', 'claimed');
  await command(page, 'kadre-drop-read');
  await expect(host).toHaveAttribute('data-kadre-drop-read', dropRead);

  // A second drag while the transfer is held presents a fresh offer, accepted as before.
  await command(page, 'kadre-drag-enter');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-1', 'accepted');

  // The session's own stop is the teardown: the accepted offer does not survive it (owner closed),
  // and the claimed transfer is closed with the session — a read of it is the closed failure.
  await command(page, 'kadre-stop-drop');
  await expect(page.locator('body')).toHaveAttribute('data-kadre-drop-session', 'terminated', { timeout: 5_000 });
  await expect(host).toHaveAttribute('data-kadre-drop-offer-1', 'terminated:owner-closed');
  await expect(host).toHaveAttribute('data-kadre-input-flow-closed', 'true');
  await command(page, 'kadre-drop-read');
  await expect(host).toHaveAttribute(
    'data-kadre-drop-read',
    `${dropRead};0=failure:closed:droptransfer,1=failure:closed:droptransfer`,
  );

  // A surface that stopped admitting presents nothing: the post-terminal drag step is observed by
  // nothing — no new offer, no new event, the journal frozen where the terminal left it.
  const frozen = await host.getAttribute('data-kadre-input-events');
  await command(page, 'kadre-drag-enter');
  await nextFrame(page);
  await expect(host).toHaveAttribute('data-kadre-input-events', frozen);
});

test('web-drop-no-data-transfer-leak', async ({ page }) => {
  await loadScenario(page, 'drop');
  const host = page.locator('[data-kadre-host="drop"]');
  await awaitDropCapability(host);

  await dragEnter(page, host);
  await command(page, 'kadre-drag-drop');
  await expect(host).toHaveAttribute('data-kadre-drop-offer-0', 'transfer-available');
  await command(page, 'kadre-drop-claim');
  await expect(host).toHaveAttribute('data-kadre-drop-claim', 'claimed');

  // What crossed the boundary is the closed public shape and nothing else: descriptors with their
  // canonical mimes, kinds, names and sizes — and the drag's `DataTransfer` never appeared as one of
  // them. The encoding of the claimed transfer is exactly the interface the application reads.
  await expect(host).toHaveAttribute('data-kadre-drop-transfer', transferItems);

  // The close is the end of the transfer's world: after it, the handles of the drag are gone with
  // it — a read is the closed failure, and a second close is the harmless no-op the contract makes
  // of closing an already-closed transfer.
  await command(page, 'kadre-drop-close-transfer');
  await expect(host).toHaveAttribute('data-kadre-drop-transfer-closed', 'closed');
  await command(page, 'kadre-drop-close-transfer');
  await expect(host).toHaveAttribute('data-kadre-drop-transfer-closed', 'closed');
  await command(page, 'kadre-drop-read');
  await expect(host).toHaveAttribute('data-kadre-drop-read', '0=failure:closed:droptransfer,1=failure:closed:droptransfer');
});
