/**
 * `@kadre/host` — Kotlin/Wasm shim.
 *
 * The Kotlin module exports top-level functions only, because Kotlin/Wasm cannot export a class or an
 * object; this hand-written ESM module presents the surface `kadre/INTEROP-EXPORTS.md` section 6
 * promises — `KadreWeb.attach`, `KadreSessionHandle`, `KadreHostError` and the discriminated unions.
 *
 * Loading: the bindings come from the shared registry the Kotlin module publishes them into, never
 * from a sibling module of this package. A Kotlin/Wasm library module has no load hook of its own, so
 * the application whose wasm links this library publishes its bindings with `publishHostBindings()`
 * before it hands its opaque factory key to JavaScript; resolving the registry is what makes that
 * instance — the one that owns the factories and sessions — the one this shim drives.
 *
 * Encoding: the Kotlin bindings hand over JSON whose discriminant is `kind`. Every Kotlin `Long`
 * arrives as a JSON string, because Kotlin `Long` is an object on JS and a `BigInt` on Wasm; this
 * shim converts those fields, so both targets publish the same `bigint` the declaration promises.
 */

const hostBindingNames = [
  "kadreWebAttach",
  "kadreWebSessionId",
  "kadreWebSessionState",
  "kadreWebSubscribeState",
  "kadreWebSubscribeTermination",
  "kadreWebUnsubscribeState",
  "kadreWebRequestStop",
  "kadreWebClose",
];

const {
  kadreWebAttach,
  kadreWebSessionId,
  kadreWebSessionState,
  kadreWebSubscribeState,
  kadreWebSubscribeTermination,
  kadreWebUnsubscribeState,
  kadreWebRequestStop,
  kadreWebClose,
} = resolveHostBindings();

/**
 * Reads the eight bindings from the shared registry the Kotlin module publishes them into.
 *
 * The registry is the `globalThis["org.graphiks.kadre:web"]` object every Kotlin module instance of
 * this library populates: the eight names directly for a Kotlin/Wasm module and for an application
 * that published them, and under the module package path for a Kotlin/JS library build. The shim
 * therefore drives whichever instance owns the factories and sessions, and reports the explicit
 * error below when no Kotlin module has published anything.
 */
function resolveHostBindings() {
  const registry = globalThis["org.graphiks.kadre:web"];
  const published = registry?.org?.graphiks?.kadre?.platform?.web;
  const bindings = hostBindingNames.every((name) => typeof registry?.[name] === "function")
    ? registry
    : published;
  if (bindings === undefined || hostBindingNames.some((name) => typeof bindings[name] !== "function")) {
    throw new Error("@kadre/host: the Kotlin module did not publish its bindings");
  }
  return Object.fromEntries(hostBindingNames.map((name) => [name, bindings[name]]));
}

//<shim-body>

if (typeof kadreWebAttach !== "function") {
  throw new Error("@kadre/host: the Kotlin module did not publish its bindings");
}

/** Raised by `KadreWeb.attach` when the host refuses the attachment. */
export class KadreHostError extends Error {
  constructor(failure) {
    super(failure == null ? "Kadre host refused the attachment" : `Kadre host refused: ${failure.kind}`);
    this.name = "KadreHostError";
    this.failure = failure;
  }
}

/** The failure fields the published declaration types as `bigint`. */
const BIGINT_FIELDS = ["limit", "expected", "received", "timeoutNanoseconds"];

function toFailure(decoded) {
  if (decoded == null) {
    return null;
  }
  const failure = { ...decoded };
  for (const field of BIGINT_FIELDS) {
    if (failure[field] != null) {
      failure[field] = BigInt(failure[field]);
    }
  }
  return failure;
}

function toOutcome(decoded) {
  switch (decoded.kind) {
    case "completed":
      return { kind: "completed" };
    case "stopped":
      return { kind: "stopped", reason: decoded.reason };
    case "failed":
      return { kind: "failed", failure: toFailure(decoded.failure) };
    default:
      throw new Error(`@kadre/host: unknown outcome kind ${decoded.kind}`);
  }
}

function toSnapshot(decoded) {
  if (decoded.kind === "terminated") {
    return { kind: "terminated", outcome: toOutcome(decoded.outcome) };
  }
  return { kind: decoded.kind };
}

class SessionHandle {
  #key;

  constructor(key) {
    this.#key = key;
  }

  get id() {
    return kadreWebSessionId(this.#key);
  }

  get state() {
    return toSnapshot(JSON.parse(kadreWebSessionState(this.#key)));
  }

  subscribeState(observer) {
    const subscription = kadreWebSubscribeState(this.#key, (encoded) => {
      observer(toSnapshot(JSON.parse(encoded)));
    });
    return () => {
      kadreWebUnsubscribeState(subscription);
    };
  }

  requestStop() {
    kadreWebRequestStop(this.#key);
  }

  close() {
    kadreWebClose(this.#key);
  }

  awaitTermination() {
    return new Promise((resolve, reject) => {
      kadreWebSubscribeTermination(this.#key, (encoded) => {
        try {
          resolve(toOutcome(JSON.parse(encoded)));
        } catch (error) {
          reject(error);
        }
      });
    });
  }
}

/** The published entry point of the module. */
export const KadreWeb = {
  attach(element, applicationFactory, options) {
    if (element == null || typeof element.nodeType !== "number") {
      throw new KadreHostError({ kind: "invalidRequest", field: "element" });
    }
    if (typeof applicationFactory !== "string") {
      throw new KadreHostError({ kind: "invalidRequest", field: "factoryKey" });
    }
    const result = kadreWebAttach(
      element,
      applicationFactory,
      options?.policy ?? "default",
      options?.attachmentPolicy ?? "stopWhenDetached",
    );
    const separator = result.indexOf("|");
    const status = result.slice(0, separator);
    const payload = result.slice(separator + 1);
    if (status === "ok") {
      return new SessionHandle(Number(payload));
    }
    throw new KadreHostError(toFailure(JSON.parse(payload)));
  },
};
