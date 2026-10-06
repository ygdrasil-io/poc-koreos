/**
 * The synthetic gamepad source the device and effect specs share.
 *
 * **The D10 precedent, stated plainly.** Chromium cannot inject a real gamepad: no CDP channel
 * carries one into a headless page. What a real browser does give every page is the poll —
 * `navigator.getGamepads()` is the only state source the Gamepad API has, the DOM's connection
 * events carry no pad data, and the platform's hub reads exactly that poll every animation frame.
 * So the harness patches `Navigator.prototype.getGamepads` (before any page script runs) to return
 * scriptable fake pads — plain objects carrying `index`, `id`, `connected`, `mapping`,
 * `buttons: [{value, pressed}]`, `axes: [n]`, and a `vibrationActuator` whose `playEffect(type,
 * effectParameters)`/`reset()` record what they receive and answer resolved promises, mirroring the
 * WebIDL shape the real Chromium reads (the second argument is the `effectParameters` dictionary).
 * Connecting and disconnecting a pad is mutating the array the patched getter returns, announced
 * the way a browser announces it; the hub's next poll sees the mutation.
 *
 * What stays out of these smokes is the real-device privacy gate — pads stay invisible to a page
 * until the user activates it — which no synthetic source can exercise; it is manual-charter
 * material, and no scenario below claims it.
 */

/** The stub's in-page shape: the pad array the navigator's poll answers, and every fact it records. */
const stubInitScript = () => {
  const stub = {
    pads: [],
    calls: [],
    prompts: [],
    listeners: 0,
    listenerTypes: [],
  };

  /** Builds one fake pad as the browser's own `Gamepad` object would be shaped, holes included. */
  stub.makePad = (spec) => {
    const pad = {
      index: spec.index,
      id: spec.id,
      connected: true,
      mapping: spec.mapping,
      buttons: (spec.buttons ?? []).map((value) => ({ value, pressed: value >= 0.5 })),
      axes: [...(spec.axes ?? [])],
    };
    if (spec.effects !== 'none') {
      const actuator = {
        // The real Chromium reads the second argument as the `effectParameters` dictionary; the
        // recording keeps the members the dictionary carried, so a spec can assert the shape.
        playEffect(type, effectParameters) {
          stub.calls.push({
            kind: 'playEffect',
            index: spec.index,
            type,
            duration: effectParameters?.duration ?? null,
            strongMagnitude: effectParameters?.strongMagnitude ?? null,
            weakMagnitude: effectParameters?.weakMagnitude ?? null,
            keys: effectParameters ? Object.keys(effectParameters).sort() : [],
          });
          return Promise.resolve();
        },
        reset() {
          stub.calls.push({ kind: 'reset', index: spec.index });
          return Promise.resolve();
        },
      };
      if (spec.effects !== undefined) actuator.effects = spec.effects;
      pad.vibrationActuator = actuator;
    }
    return pad;
  };

  /** Replaces the pad array — `null` entries are the holes a real poll reports — and announces. */
  stub.setPads = (specs) => {
    const previous = stub.pads.filter((pad) => pad !== null).map((pad) => pad.index);
    stub.pads = specs.map((spec) => (spec === null ? null : stub.makePad(spec)));
    const current = stub.pads.filter((pad) => pad !== null).map((pad) => pad.index);
    current
      .filter((index) => !previous.includes(index))
      .forEach(() => window.dispatchEvent(new Event('gamepadconnected')));
    previous
      .filter((index) => !current.includes(index))
      .forEach(() => window.dispatchEvent(new Event('gamepaddisconnected')));
  };

  /** Rewrites one button reading; the string `'nan'` injects the non-finite reading a hostile pad sends. */
  stub.setButton = (index, buttonIndex, value) => {
    stub.mutate(index, { buttons: [[buttonIndex, value]] });
  };

  /** Rewrites one axis of the pad at the DOM index named; the same `'nan'` sentinel as `setButton`. */
  stub.setAxis = (index, axisIndex, value) => {
    stub.mutate(index, { axes: [[axisIndex, value]] });
  };

  /**
   * Rewrites several readings of one pad in one mutation, so one poll diffs the whole change —
   * exactly how a real pad moves: every control that changed moved together, in one frame.
   */
  stub.mutate = (index, changes) => {
    const pad = stub.pads[index];
    (changes.buttons ?? []).forEach(([control, value]) => {
      pad.buttons[control].value = value === 'nan' ? Number.NaN : value;
      pad.buttons[control].pressed = pad.buttons[control].value >= 0.5;
    });
    (changes.axes ?? []).forEach(([control, value]) => {
      pad.axes[control] = value === 'nan' ? Number.NaN : value;
    });
  };

  // The navigator's poll is the stub's whole state source: the patch answers the array above.
  Navigator.prototype.getGamepads = function () {
    return stub.pads;
  };

  // The prompting APIs the effects path must never touch: patched to record-if-called, never to
  // answer, so a spec asserting an empty record has caught every attempt there was. Each patch is
  // guarded on the member itself — the browser offers these APIs unevenly, and a missing member is
  // one less prompt to catch, not an error.
  if (typeof navigator.permissions?.request === 'function') {
    const nativeRequest = navigator.permissions.request.bind(navigator.permissions);
    navigator.permissions.request = (permission) => {
      stub.prompts.push('permissions.request');
      return nativeRequest(permission);
    };
  }
  if (typeof navigator.getScreenDetails === 'function') {
    navigator.getScreenDetails = () => {
      stub.prompts.push('getScreenDetails');
      return Promise.reject(new DOMException('the stub never prompts', 'InvalidStateError'));
    };
  }
  if (typeof navigator.requestMIDIAccess === 'function') {
    navigator.requestMIDIAccess = () => {
      stub.prompts.push('requestMIDIAccess');
      return Promise.reject(new DOMException('the stub never prompts', 'InvalidStateError'));
    };
  }
  if (typeof navigator.share === 'function') {
    navigator.share = (data) => {
      stub.prompts.push('share');
      return Promise.reject(new DOMException('the stub never prompts', 'InvalidStateError'));
    };
  }
  if (typeof window.Notification?.requestPermission === 'function') {
    Notification.requestPermission = () => {
      stub.prompts.push('Notification.requestPermission');
      return Promise.resolve('denied');
    };
  }

  // The page's own listener counter, installed before any script runs: every EventTarget
  // registration the page makes — whoever asks — is counted, which is how the raw-input spec
  // proves a refused request registers no channel at all.
  const nativeAddEventListener = EventTarget.prototype.addEventListener;
  EventTarget.prototype.addEventListener = function (type, callback, options) {
    stub.listeners += 1;
    stub.listenerTypes.push(type);
    return nativeAddEventListener.call(this, type, callback, options);
  };

  window.__kadreGamepadStub = stub;
};

/** Installs the synthetic source into every frame [page] will ever load, before its scripts run. */
export async function installGamepadStub(page) {
  await page.addInitScript(stubInitScript);
}

/**
 * One standard-mapping pad spec: the layout `mapping: "standard"` names, seventeen buttons and four
 * axes in DOM order. `effects` is the actuator's own declaration (`['dual-rumble']` — what newer
 * Chromium reports), `undefined` for a browser that declares nothing, `'none'` for a pad without an
 * actuator at all.
 */
export function standardPad(spec = {}) {
  return {
    index: 0,
    id: 'Kadre Standard Pad',
    mapping: 'standard',
    buttons: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    axes: [0, 0, 0, 0],
    effects: ['dual-rumble'],
    ...spec,
  };
}

/** One non-standard pad spec: an empty mapping word promising nothing, at whatever control count. */
export function nativePad(spec = {}) {
  return {
    index: 1,
    id: 'Vendor Pad',
    mapping: '',
    buttons: [0, 0, 0],
    axes: [0, 0],
    effects: ['dual-rumble'],
    ...spec,
  };
}

/** The DOM order of the standard mapping's buttons, as the model's canonical codes spell them. */
export const STANDARD_BUTTON_CODES = [
  'south',
  'east',
  'west',
  'north',
  'leftShoulder',
  'rightShoulder',
  'leftTrigger',
  'rightTrigger',
  'select',
  'start',
  'leftStick',
  'rightStick',
  'dpadUp',
  'dpadDown',
  'dpadLeft',
  'dpadRight',
  'mode',
];

/** The DOM order of the standard mapping's axes, as the model's canonical codes spell them. */
export const STANDARD_AXIS_CODES = ['leftX', 'leftY', 'rightX', 'rightY'];

/** One button cell of the fixture's controls encoding: the reading, with the press marker. */
export function button(code, value, pressed = false) {
  return `${code}=${value}${pressed ? '*' : ''}`;
}

/** One axis cell of the fixture's controls encoding. */
export function axis(code, value) {
  return `${code}=${value}`;
}
