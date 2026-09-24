/**
 * `@kadre/host` — the curated TypeScript contract of `kadre/INTEROP-EXPORTS.md` section 6, phase 2.
 *
 * This file is the published `index.d.ts` of the npm package; it types `index.mjs`, the ESM shim
 * that presents `KadreWeb` over the module's exported function bindings, and it was reconciled with
 * the declarations each target generates for those bindings:
 *
 *   Kotlin/JS   `compileSync/js/main/productionLibrary/kotlin/kadre-platform-web.d.ts`
 *   Kotlin/Wasm `compileSync/wasmJs/main/productionLibrary/kotlin/kadre-platform-web.d.mts`
 *
 * Both declare exactly the eight bindings the shim loads (`kadreWebAttach`, `kadreWebSessionId`,
 * `kadreWebSessionState`, `kadreWebSubscribeState`, `kadreWebSubscribeTermination`,
 * `kadreWebUnsubscribeState`, `kadreWebRequestStop`, `kadreWebClose`), and the packaging task fails
 * when a target's declarations and its shim stop agreeing in either direction.
 *
 * The bindings are internal glue: they hand over JSON keyed by `kind`, with `Long` payloads as
 * strings. The shim, not the consumer, owns the conversion back to the shapes below — including
 * `BigInt` for the fields typed `bigint`, so Kotlin/JS and Kotlin/Wasm agree at runtime.
 */

export type KadrePolicyProfile = "default" | "realtime" | "recording";

export type KadreOperation =
  | "hostAttach" | "requestRedraw" | "displayAccess" | "requestWindow"
  | "updateWindow" | "requestWindowAttention" | "closeWindow" | "respondToCloseRequest" | "updateSurface"
  | "installInteractionHandler" | "armInteraction" | "interaction"
  | "gamepadEffect" | "stopGamepadEffects" | "textInput" | "updateTextInput"
  | "claimDropTransfer" | "readDropItem" | "capturePermission"
  | "captureRefreshSources" | "captureOpen" | "captureCollectFrames"
  | "rawInputAccess" | "gestureInput" | "platformSurfaceAccess" | "platformWindowAccess";

export type KadrePermission =
  | "displayEnumeration" | "inputMonitoring" | "rawInput" | "captureScreen" | "captureWindow";

export type KadrePolicyComponent =
  | "execution" | "lifecycleEvents" | "hostSignals" | "windowEvents"
  | "deviceEvents" | "inputEvents" | "devicePolicy" | "captureEvents" | "captureFrames"
  | "diagnostics" | "resources";

export type KadreResourceKind =
  | "host" | "surface" | "window" | "windowRequest" | "display"
  | "inputSource" | "rawInputAccess" | "inputDevice" | "gamepad" | "eventCollector" | "interaction"
  | "dropTransfer" | "dropItem" | "cursorImage" | "gamepadEffect"
  | "textInputSession" | "captureSource" | "captureSession" | "captureCollector"
  | "captureBuffer" | "retainedPayload" | "imageResource" | "eventSequence";

export type KadrePlatform = "android" | "uikit" | "web" | "appKit" | "win32" | "x11" | "wayland" | "fake";

export type InteractionFailureReason = "missing" | "expired" | "consumed" | "wrongSurface";

export type KadreStopReason =
  | "hostRequested"
  | "applicationRequested"
  | "applicationCancelled"
  | "parentCancelled"
  | "hostDetached";

export type KadreSessionOutcome =
  | { readonly kind: "completed" }
  | { readonly kind: "stopped"; readonly reason: KadreStopReason }
  | { readonly kind: "failed"; readonly failure: KadreFailure };

export type KadreSessionSnapshot =
  | { readonly kind: "starting" }
  | { readonly kind: "running" }
  | { readonly kind: "stopping" }
  | { readonly kind: "terminated"; readonly outcome: KadreSessionOutcome };

export type KadreFailure =
  | { readonly kind: "unsupported"; readonly operation: KadreOperation }
  | { readonly kind: "permissionDenied"; readonly permission: KadrePermission }
  | { readonly kind: "userCancelled"; readonly operation: KadreOperation }
  | { readonly kind: "temporarilyUnavailable"; readonly retryable: boolean }
  | { readonly kind: "invalidRequest"; readonly field: string | null }
  | { readonly kind: "alreadyInUse"; readonly resource: KadreResourceKind }
  | { readonly kind: "closed"; readonly resource: KadreResourceKind }
  | { readonly kind: "resourceLimitExceeded"; readonly resource: KadreResourceKind; readonly limit: bigint }
  | { readonly kind: "sourceOverflow"; readonly resource: KadreResourceKind }
  | { readonly kind: "staleRevision"; readonly expected: bigint; readonly received: bigint }
  | { readonly kind: "interactionRequired"; readonly reason: InteractionFailureReason }
  | { readonly kind: "unsupportedPolicy"; readonly component: KadrePolicyComponent }
  | { readonly kind: "parentScopeCancelled" }
  | { readonly kind: "shutdownTimedOut"; readonly timeoutNanoseconds: bigint }
  | { readonly kind: "sourceLost"; readonly sourceId: string }
  | { readonly kind: "applicationFailure" }
  | { readonly kind: "platformFailure"; readonly platform: KadrePlatform; readonly domain: string; readonly code: string };

/**
 * The opaque application factory reference of the Kotlin side, named here for orientation.
 *
 * It is a Kotlin class that never crosses the boundary, so no JavaScript value ever satisfies it:
 * the value JavaScript holds is the application's opaque `hostKey` — the `string` produced by the
 * application's Kotlin module — and that is what `KadreWeb.attach` carries back.
 *
 * @see KadreWeb.attach takes the key, and refuses any other type with `invalidRequest: "factoryKey"`.
 */
export interface KadreApplicationFactoryRef {
  readonly __kadreApplicationFactory: unique symbol;
}

/** The options of `KadreWeb.attach`. `windowProvider` arrives with the phase that delivers `WebWindowProvider`. */
export interface KadreWebOptions {
  readonly policy?: KadrePolicyProfile;
  readonly attachmentPolicy?: "stopWhenDetached" | "manual";
}

export declare class KadreHostError extends Error {
  readonly failure: KadreFailure;
}

export interface KadreSessionHandle {
  /**
   * An opaque identifier allocated for this handle by the interop layer: not the Kotlin `SessionId`,
   * not parseable and not stable across processes.
   */
  readonly id: string;
  readonly state: KadreSessionSnapshot;
  subscribeState(observer: (state: KadreSessionSnapshot) => void): () => void;
  requestStop(): void;
  close(): void;
  awaitTermination(): Promise<KadreSessionOutcome>;
}

export declare const KadreWeb: {
  /**
   * Attaches `element` to the application factory the Kotlin module owns.
   *
   * `applicationFactory` is that factory's opaque `hostKey`: the `string` the application's Kotlin
   * module produced, passed back unchanged and never inspected — the `KadreApplicationFactoryRef`
   * wrapper itself does not cross the boundary. Any other type is refused with
   * `invalidRequest: "factoryKey"`.
   */
  attach(
    element: HTMLElement,
    applicationFactory: string,
    options?: Readonly<KadreWebOptions>,
  ): KadreSessionHandle;
};
