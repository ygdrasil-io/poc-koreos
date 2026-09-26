/**
 * Target-neutral consumer of `@kadre/host`.
 *
 * The same source type-checks and runs against the Kotlin/JS and the Kotlin/Wasm package: it only
 * uses the published TypeScript contract, mentions no Kotlin type, and needs no DOM library beyond
 * the standard one. The scenario attaches to an element, observes the session state, unsubscribes,
 * stops the session, awaits the terminal outcome and switches exhaustively over `KadreFailure`.
 */

import {
  KadreHostError,
  KadreWeb,
  type KadreFailure,
  type KadreSessionOutcome,
  type KadreSessionSnapshot,
} from "@kadre/host";

/**
 * Renders a failure through a `never`-guarded switch: adding a variant to the union makes this
 * consumer fail to compile instead of silently falling through.
 */
export function describeFailure(failure: KadreFailure): string {
  switch (failure.kind) {
    case "unsupported":
      return `unsupported:${failure.operation}`;
    case "permissionDenied":
      return `permissionDenied:${failure.permission}`;
    case "userCancelled":
      return `userCancelled:${failure.operation}`;
    case "temporarilyUnavailable":
      return `temporarilyUnavailable:${failure.retryable}`;
    case "invalidRequest":
      return `invalidRequest:${failure.field ?? "unknown"}`;
    case "alreadyInUse":
      return `alreadyInUse:${failure.resource}`;
    case "closed":
      return `closed:${failure.resource}`;
    case "resourceLimitExceeded":
      return `resourceLimitExceeded:${failure.resource}:${failure.limit}`;
    case "sourceOverflow":
      return `sourceOverflow:${failure.resource}`;
    case "staleRevision":
      return `staleRevision:${failure.expected}:${failure.received}`;
    case "interactionRequired":
      return `interactionRequired:${failure.reason}`;
    case "unsupportedPolicy":
      return `unsupportedPolicy:${failure.component}`;
    case "parentScopeCancelled":
      return "parentScopeCancelled";
    case "shutdownTimedOut":
      return `shutdownTimedOut:${failure.timeoutNanoseconds}`;
    case "sourceLost":
      return `sourceLost:${failure.sourceId}`;
    case "applicationFailure":
      return "applicationFailure";
    case "platformFailure":
      return `platformFailure:${failure.platform}:${failure.domain}:${failure.code}`;
    default: {
      const unexpected: never = failure;
      return `unexpected:${JSON.stringify(unexpected)}`;
    }
  }
}

/** Renders the terminal outcome; the `never` check keeps this switch closed as well. */
export function describeOutcome(outcome: KadreSessionOutcome): string {
  switch (outcome.kind) {
    case "completed":
      return "completed";
    case "stopped":
      return `stopped:${outcome.reason}`;
    case "failed":
      return `failed:${describeFailure(outcome.failure)}`;
    default: {
      const unexpected: never = outcome;
      return `unexpected:${JSON.stringify(unexpected)}`;
    }
  }
}

/** Renders a session snapshot; `terminated` carries the outcome the scenario awaits. */
export function describeSnapshot(snapshot: KadreSessionSnapshot): string {
  switch (snapshot.kind) {
    case "starting":
    case "running":
    case "stopping":
      return snapshot.kind;
    case "terminated":
      return `terminated:${describeOutcome(snapshot.outcome)}`;
    default: {
      const unexpected: never = snapshot;
      return `unexpected:${JSON.stringify(unexpected)}`;
    }
  }
}

/** Bounded wait for a condition the browser delivers later; never throws. */
async function waitUntil(condition: () => boolean, timeoutMillis: number): Promise<boolean> {
  const deadline = Date.now() + timeoutMillis;
  while (!condition() && Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  return condition();
}

/**
 * Runs the scenario and returns `"passed"` or the diagnostic of the first divergence.
 *
 * The application factory key is owned by the page's Kotlin application; the consumer only passes the
 * opaque key back to `KadreWeb.attach`, which types that parameter as the `string` it is.
 */
export async function runScenario(): Promise<string> {
  const page = globalThis as typeof globalThis & { kadreApplicationFactory?: string };
  const applicationFactory = page.kadreApplicationFactory;
  if (applicationFactory === undefined) {
    return "missing-application-factory";
  }

  const element = document.createElement("div");
  document.body.appendChild(element);

  try {
    const handle = KadreWeb.attach(element, applicationFactory);

    const observed: string[] = [];
    const unsubscribe = handle.subscribeState((state) => {
      observed.push(describeSnapshot(state));
    });

    if (!(await waitUntil(() => observed.includes("running"), 5000))) {
      unsubscribe();
      return `never-running:${observed.join(",") || "unobserved"}`;
    }
    unsubscribe();

    const observationsAtUnsubscribe = observed.length;
    handle.requestStop();
    const outcome = await handle.awaitTermination();
    const described = describeOutcome(outcome);
    if (described !== "stopped:hostRequested") {
      return `unexpected-outcome:${described}`;
    }
    if (observed.length !== observationsAtUnsubscribe) {
      return `unsubscribed-observer-heard:${observed.slice(observationsAtUnsubscribe).join(",")}`;
    }
    if (!(await waitUntil(() => handle.state.kind === "terminated", 2000))) {
      return `unexpected-final-state:${describeSnapshot(handle.state)}`;
    }
    return "passed";
  } catch (error) {
    if (error instanceof KadreHostError) {
      return `refused:${describeFailure(error.failure)}`;
    }
    throw error;
  } finally {
    element.remove();
  }
}

void runScenario().then((result) => {
  document.body.setAttribute("data-kadre-typescript-consumer", result);
});
