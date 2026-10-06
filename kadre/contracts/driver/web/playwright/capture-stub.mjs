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
 */

/** The stub's in-page shape: the prompting record and the readback query count. */
const stubInitScript = () => {
  const stub = {
    prompts: [],
    permissionQueries: 0,
  };

  // The picker and the camera: record-then-forward. `getDisplayMedia` is the one call an explicit
  // request may make (Task 5's scenarios make none); `getUserMedia` is out of the web capture
  // scope entirely — no scenario of this contract may touch it under any name.
  if (typeof navigator.mediaDevices?.getDisplayMedia === 'function') {
    const nativePick = navigator.mediaDevices.getDisplayMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getDisplayMedia = (constraints) => {
      stub.prompts.push('getDisplayMedia');
      return nativePick(constraints);
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

  window.__kadreCaptureStub = stub;
};

/** Installs the canaries into every frame [page] will ever load, before its scripts run. */
export async function installCaptureStub(page) {
  await page.addInitScript(stubInitScript);
}
