/**
 * The prompting-API canaries the capture specs share.
 *
 * **The phase-6 precedent, restated for the consent APIs.** What a capture control plane must
 * never do is touch a prompting API outside the one explicit request path — so the harness patches
 * every prompting API the page carries (before any script runs) to record what it is asked and
 * then forward, mirroring the shapes the real Chromium reads. A spec asserting an empty record has
 * caught every attempt there was; a forwarded record entry still happened, so a call that slips
 * through fails the spec twice — once as a record entry, once as whatever the browser did with it.
 * Each patch is guarded on the member itself: the browser offers these APIs unevenly, and a
 * missing member is one less prompt to catch, not an error.
 *
 * The one permission call a control plane IS allowed is the readback query —
 * `permissions.query({ name: 'display-capture' })` never prompts — so the query is not a canary
 * but a counted spy: the readback spec pins its exact count, and a readback that polled or
 * re-queried would move it.
 *
 * **The session scenarios' track accounting (BCK-011).** The record proves which consent APIs the
 * page touched; it says nothing about what a granted capture did afterwards. So the two seams a
 * granted stream can arrive through — the display-capture pick and the host canvas's own
 * `captureStream` (the Surface source; not a prompting API, and never recorded as one) — are also
 * wrapped record-then-forward, and every video track they hand out is enrolled: the stub counts the
 * `stop()` calls it forwards and keeps the live track object so a spec can read the browser's own
 * `readyState` after the fact. `trackFacts()` is the readback: a capture whose track was never
 * stopped shows `readyState: "live"` with zero stop calls, and a leaked one is a fact a spec can
 * fail on. The accounting observes; only the page's own capture paths put tracks in it.
 */

/** The stub's in-page shape: the prompting record, the readback query count, and the track roll. */
const stubInitScript = () => {
  const stub = {
    prompts: [],
    permissionQueries: 0,
    tracks: [],
  };

  /** Enrolls one granted video track: the forwarded stop is counted, the handle kept readable. */
  const enroll = (source, track) => {
    const record = { source, stopCalls: 0, track };
    const nativeStop = track.stop.bind(track);
    track.stop = () => {
      record.stopCalls += 1;
      return nativeStop();
    };
    stub.tracks.push(record);
  };

  // The picker and the camera: record-then-forward. `getDisplayMedia` is the one call an explicit
  // request may make (the session scenarios make exactly one, from a real click); `getUserMedia` is
  // out of the web capture scope entirely — no scenario of this contract may touch it under any
  // name. The granted stream's tracks are enrolled after the browser answers.
  if (typeof navigator.mediaDevices?.getDisplayMedia === 'function') {
    const nativePick = navigator.mediaDevices.getDisplayMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getDisplayMedia = (constraints) => {
      stub.prompts.push('getDisplayMedia');
      const granted = nativePick(constraints);
      granted.then((stream) => {
        stream.getVideoTracks().forEach((track) => enroll('display-capture', track));
        return stream;
      });
      return granted;
    };
  }
  if (typeof navigator.mediaDevices?.getUserMedia === 'function') {
    const nativeUser = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getUserMedia = (constraints) => {
      stub.prompts.push('getUserMedia');
      return nativeUser(constraints);
    };
  }
  if (typeof navigator.permissions?.request === 'function') {
    const nativeRequest = navigator.permissions.request.bind(navigator.permissions);
    navigator.permissions.request = (permission) => {
      stub.prompts.push('permissions.request');
      return nativeRequest(permission);
    };
  }
  // The readback spy: counted, then forwarded. A query never prompts, so it never enters the
  // record — its count is what proves the readback rode the honest mechanism and only it.
  if (typeof navigator.permissions?.query === 'function') {
    const nativeQuery = navigator.permissions.query.bind(navigator.permissions);
    navigator.permissions.query = (descriptor) => {
      stub.permissionQueries += 1;
      return nativeQuery(descriptor);
    };
  }
  // The neighbouring prompting APIs a capture path must also never reach (the phase-6 net).
  if (typeof navigator.getScreenDetails === 'function') {
    const nativeScreenDetails = navigator.getScreenDetails.bind(navigator);
    navigator.getScreenDetails = () => {
      stub.prompts.push('getScreenDetails');
      return nativeScreenDetails();
    };
  }
  if (typeof navigator.requestMIDIAccess === 'function') {
    const nativeMidi = navigator.requestMIDIAccess.bind(navigator);
    navigator.requestMIDIAccess = () => {
      stub.prompts.push('requestMIDIAccess');
      return nativeMidi();
    };
  }
  if (typeof window.Notification?.requestPermission === 'function') {
    const nativeNotification = Notification.requestPermission.bind(Notification);
    Notification.requestPermission = () => {
      stub.prompts.push('Notification.requestPermission');
      return nativeNotification();
    };
  }
  // The Surface source's seam: a canvas stream is no consent, so it is never recorded as a prompt —
  // but the tracks it hands out are enrolled like any other granted capture.
  if (typeof HTMLCanvasElement !== 'undefined' && typeof HTMLCanvasElement.prototype.captureStream === 'function') {
    const nativeCanvasStream = HTMLCanvasElement.prototype.captureStream;
    HTMLCanvasElement.prototype.captureStream = function (...args) {
      const stream = nativeCanvasStream.apply(this, args);
      stream.getVideoTracks().forEach((track) => enroll('canvas-capture-stream', track));
      return stream;
    };
  }

  /** The live readback the teardown sentinels fail on: source, forwarded stop count, browser state. */
  stub.trackFacts = () => stub.tracks.map((record) => ({
    source: record.source,
    stopCalls: record.stopCalls,
    readyState: record.track.readyState,
  }));

  window.__kadreCaptureStub = stub;
};

/** Installs the canaries into every frame [page] will ever load, before its scripts run. */
export async function installCaptureStub(page) {
  await page.addInitScript(stubInitScript);
}
