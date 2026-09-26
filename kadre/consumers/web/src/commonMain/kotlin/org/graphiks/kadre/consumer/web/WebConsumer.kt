package org.graphiks.kadre.consumer.web

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.DelicateKadreApi
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadrePolicyComponent
import org.graphiks.kadre.diagnostics.KadrePlatformApi
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.InteractionFailureReason
import org.graphiks.kadre.input.KadrePermission
import org.graphiks.kadre.platform.web.KadreApplicationFactoryRef
import org.graphiks.kadre.platform.web.WebAttachmentPolicy
import org.graphiks.kadre.platform.web.asHostRef
import org.graphiks.kadre.platform.web.withWebElement
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.surface.HostSurface
import org.graphiks.kadre.surface.SurfaceEvent

/**
 * Consumer compile test of the published Web surface, for the Kotlin/JS and Kotlin/Wasm targets.
 *
 * Every name this file uses is part of the published API of `kadre/platform/web`; the shared part
 * is target-neutral, and the two `actual` declarations below are the only places that touch the SDK
 * `HTMLElement` of a target. The file is compiled against the artifacts of `-PkadreRepository`, so a
 * rename, a nullability change or a new opt-in requirement breaks this build.
 */
public object WebConsumer {
    /** The three published policy profiles, as the JVM consumer does. */
    public fun policies(): List<KadrePolicy> = listOf(
        KadrePolicies.Default,
        KadrePolicies.Realtime,
        KadrePolicies.Recording,
    )

    /** The opaque factory reference handed to JavaScript; `asHostRef` is pure and cannot fail. */
    public fun hostRef(factory: KadreApplicationFactory): KadreApplicationFactoryRef = factory.asHostRef()

    /** Both published attachment policies are named by the facade. */
    public fun attachmentPolicies(): List<WebAttachmentPolicy> = listOf(
        WebAttachmentPolicy.StopWhenDetached,
        WebAttachmentPolicy.Manual,
    )

    /** Attaches through the factory overload and through the direct-application overload. */
    public fun attachBothForms(scope: CoroutineScope, ref: KadreApplicationFactoryRef): List<KadreResult<KadreSession>> =
        listOf(
            attachElement(scope, ref),
            attachElementWithApplication(scope),
        )

    /** The escape hatch is component-version stability: it keeps the same name and opt-in. */
    @KadrePlatformApi
    @DelicateKadreApi
    public suspend fun leaseElement(surface: HostSurface): KadreResult<Boolean> = demonstrateElementLease(surface)

    /** `requestRedraw` is synchronous and returns a result, never a promise. */
    public fun redraw(surface: HostSurface): KadreResult<Unit> = surface.requestRedraw()

    /** The common surface event union stays observable from the published API. */
    public fun isRedrawEvent(event: SurfaceEvent): Boolean = event is SurfaceEvent.RedrawRequested

    /** Exhaustive over the closed failure set: a new variant makes this consumer fail to compile. */
    public fun describeFailure(failure: KadreFailure): String = when (failure) {
        is KadreFailure.Unsupported -> "unsupported:${failure.operation}"
        is KadreFailure.PermissionDenied -> "permissionDenied:${failure.permission}"
        is KadreFailure.UserCancelled -> "userCancelled:${failure.operation}"
        is KadreFailure.TemporarilyUnavailable -> "temporarilyUnavailable:${failure.retryable}"
        is KadreFailure.InvalidRequest -> "invalidRequest:${failure.field}"
        is KadreFailure.AlreadyInUse -> "alreadyInUse:${failure.resource}"
        is KadreFailure.Closed -> "closed:${failure.resource}"
        is KadreFailure.ResourceLimitExceeded -> "resourceLimitExceeded:${failure.resource}:${failure.limit}"
        is KadreFailure.SourceOverflow -> "sourceOverflow:${failure.resource}"
        is KadreFailure.StaleRevision -> "staleRevision:${failure.expected}:${failure.received}"
        is KadreFailure.InteractionRequired -> "interactionRequired:${failure.reason}"
        is KadreFailure.UnsupportedPolicy -> "unsupportedPolicy:${failure.component}"
        KadreFailure.ParentScopeCancelled -> "parentScopeCancelled"
        is KadreFailure.ShutdownTimedOut -> "shutdownTimedOut:${failure.timeout}"
        is KadreFailure.SourceLost -> "sourceLost:${failure.source}"
        KadreFailure.ApplicationFailure -> "applicationFailure"
        is KadreFailure.PlatformFailure -> "platformFailure:${failure.platform}:${failure.domain}:${failure.code}"
    }

    /** The published enum unions the failure model refers to. */
    public fun enumSamples(): List<Any> = listOf(
        KadreOperation.HostAttach,
        KadreOperation.GestureInput,
        KadreOperation.PlatformSurfaceAccess,
        KadrePermission.RawInput,
        KadrePolicyComponent.Resources,
        KadreResourceKind.EventSequence,
        KadrePlatform.Web,
        InteractionFailureReason.Expired,
    )

    /** The application keeps the session alive until the host stops it. */
    public fun application(): KadreApplication = KadreApplication { requestStop() }
}

internal expect fun attachElement(scope: CoroutineScope, ref: KadreApplicationFactoryRef): KadreResult<KadreSession>

internal expect fun attachElementWithApplication(scope: CoroutineScope): KadreResult<KadreSession>

internal expect suspend fun demonstrateElementLease(surface: HostSurface): KadreResult<Boolean>
