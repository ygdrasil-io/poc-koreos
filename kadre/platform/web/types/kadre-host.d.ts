/**
 * `@kadre/host` — the curated TypeScript contract of `kadre/INTEROP-EXPORTS.md` section 6, phase 2.
 *
 * This file is the published `index.d.ts` of the npm package; the Kotlin distributions only carry
 * the compiled module. It was reconciled against the declarations each target generates:
 *
 *   Kotlin/JS   `build/dist/js/productionLibrary/kadre-platform-web.d.ts`
 *               exported symbols: `KadreHostError`, `KadreWeb`, `KadreSessionHandle`
 *   Kotlin/Wasm `build/compileSync/wasmJs/main/productionLibrary/kotlin/kadre-platform-web.d.mts`
 *               exported symbols: none — Kotlin/Wasm only exports functions and generates no
 *               JavaScript surface for a library, so this target publishes the same Kotlin API
 *               without a JavaScript binding (see the task report).
 *
 * The generated Kotlin/JS declarations cannot name the value model: `KadreFailure`,
 * `KadreSessionOutcome`, `KadreSessionSnapshot` and the factory reference are Kotlin types that
 * appear in the generated file only as `any` comments. The shapes below are the ones the facade
 * actually produces at runtime, and are the only supported contract.
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

/** Opaque reference produced by the application's Kotlin code. Not constructible from JavaScript. */
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
  readonly id: string;
  readonly state: KadreSessionSnapshot;
  subscribeState(observer: (state: KadreSessionSnapshot) => void): () => void;
  requestStop(): void;
  close(): void;
  awaitTermination(): Promise<KadreSessionOutcome>;
}

export declare const KadreWeb: {
  attach(
    element: HTMLElement,
    applicationFactory: KadreApplicationFactoryRef,
    options?: Readonly<KadreWebOptions>,
  ): KadreSessionHandle;
};
