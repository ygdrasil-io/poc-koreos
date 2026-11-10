package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

public enum class DesktopBackend { Auto, AppKit, Win32, X11, Wayland }

public enum class DesktopIntegration { AppKitMainLoop, AwtEventDispatchThread, JavaFxApplicationThread }

public sealed interface DesktopHostOptions {
    public data class Embedded(
        public val integration: DesktopIntegration,
        public val backend: DesktopBackend = DesktopBackend.Auto,
        public val allowUserAttention: Boolean = false,
    ) : DesktopHostOptions

    public data class Standalone(
        public val backend: DesktopBackend = DesktopBackend.Auto,
        public val stopWhenLastWindowClosed: Boolean = true,
    ) : DesktopHostOptions
}

public fun CoroutineScope.attachKadreDesktop(
    applicationFactory: KadreApplicationFactory,
    options: DesktopHostOptions,
    policy: KadrePolicy = KadrePolicies.Default,
): KadreResult<KadreSession> = defaultDesktopHostFacade.attach(this, applicationFactory, options, policy)

public fun CoroutineScope.attachKadreDesktop(
    options: DesktopHostOptions,
    policy: KadrePolicy = KadrePolicies.Default,
    application: KadreApplication,
): KadreResult<KadreSession> = attachKadreDesktop(
    applicationFactory = KadreApplicationFactory { application },
    options = options,
    policy = policy,
)

/**
 * Entrée ownership-first du desktop : Kadre possède le process et cette fonction en est la
 * boucle principale. La fonction est `suspend` pour garder la forme commune aux plateformes,
 * mais l'implémentation JVM **bloque le thread appelant** le temps de la session — le backend
 * sélectionné y déroule sa boucle OS (AppKit, Win32, Wayland).
 *
 * Le backend est résolu depuis [backend] (le `Auto` de l'hôte par défaut) via la découverte
 * ServiceLoader et son `isAvailable()` ; si aucun backend n'est disponible, le résultat est un
 * [KadreResult.Failure] explicite — jamais une exception. La façade possède le fil main du run
 * (dispatcher single-thread dédié, fermé au teardown de la session) : l'entrée fonctionne sur
 * une JVM desktop sans implémentation `Dispatchers.Main` et sans état process-global.
 *
 * @param applicationFactory crée l'application que le process exécute.
 * @param backend le backend demandé ; `Auto` suit l'ordre de priorité de l'hôte.
 * @param options les options ownership du run ([KadreRunOptions.policy], première fenêtre).
 */
public suspend fun runKadreApplication(
    applicationFactory: KadreApplicationFactory,
    backend: DesktopBackend = DesktopBackend.Auto,
    options: KadreRunOptions = KadreRunOptions(),
): KadreResult<SessionOutcome> = defaultDesktopHostFacade.run(applicationFactory, backend, options)

/**
 * Variante de [runKadreApplication] qui reçoit l'[application] directement — la forme
 * commune à toutes les plateformes de l'entrée ownership-first. Comme l'autre surcharge :
 * bloque le thread appelant le temps de la session (boucle main JVM), échoue explicitement
 * si aucun backend n'est disponible.
 */
public suspend fun runKadreApplication(
    options: KadreRunOptions = KadreRunOptions(),
    backend: DesktopBackend = DesktopBackend.Auto,
    application: KadreApplication,
): KadreResult<SessionOutcome> = runKadreApplication(
    applicationFactory = KadreApplicationFactory { application },
    backend = backend,
    options = options,
)
