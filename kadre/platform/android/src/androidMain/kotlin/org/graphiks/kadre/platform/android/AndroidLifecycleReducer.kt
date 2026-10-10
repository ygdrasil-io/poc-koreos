package org.graphiks.kadre.platform.android

/** Événements observables du host Android, copiés avant tout traitement (frontière SDK). */
internal enum class AndroidHostEvent {
    LifecycleCreated, LifecycleStarted, LifecycleResumed, LifecyclePaused, LifecycleStopped,
    LifecycleDestroyed, ViewAttached, ViewDetached,
}

/** Décision pure : la publication Kadre correspondante, ou rien (dédupliquée), ou le terminal. */
internal sealed interface AndroidLifecycleDecision {
    public data object Ignore : AndroidLifecycleDecision
    public data class Publish(val snapshot: AndroidHostSnapshot) : AndroidLifecycleDecision
    public data class Terminate(val snapshot: AndroidHostSnapshot) : AndroidLifecycleDecision
}

/** Instantané canonique du host — publié AVANT l'événement associé (roadmap §3.3). */
internal data class AndroidHostSnapshot(
    val attachment: Boolean, // Attached / Detached
    val foreground: Boolean, // lifecycle >= STARTED
    val active: Boolean, // lifecycle >= RESUMED (et fenêtre interactive en forme Activity)
)

/**
 * Réducteur pur lifecycle Android → Kadre (roadmap §3.2 : « Foreground signifie lifecycle
 * au moins STARTED ; Active exige au moins RESUMED »). Chaque événement calcule le snapshot
 * cible ; `Ignore` si identique au courant (déduplication), `Publish` sinon ; le premier
 * signal terminal (`LifecycleDestroyed` / `ViewDetached`) gagne et propose `Terminate` avec
 * le snapshot canonique tout-faux ; après le terminal, tout est absorbé (`Ignore`).
 *
 * Mapping par événement (les événements sont des notifications, pas des transitions
 * strictes — les doublons hors ordre restent cohérents grâce à la déduplication) :
 * - `LifecycleCreated`  → CREATED < STARTED : foreground=false, active=false ;
 * - `LifecycleStarted`  → foreground=true (active inchangée : la reprise depuis RESUMED
 *   est un doublon, cf. `AndroidLifecycleReducerTest.notificationsAreDeduplicated`) ;
 * - `LifecycleResumed`  → foreground=true, active=true ;
 * - `LifecyclePaused`   → active=false (foreground reste true : PAUSED est toujours
 *   au moins STARTED, roadmap §3.2) ;
 * - `LifecycleStopped`  → foreground=false, active=false ;
 * - `ViewAttached`      → attachment=true (foreground/active inchangés) ;
 * - `ViewDetached` / `LifecycleDestroyed` → terminal, snapshot canonique tout-faux.
 */
internal class AndroidLifecycleReducer(initial: AndroidHostSnapshot) {
    private var current = initial
    private var terminal = false

    val isTerminal: Boolean get() = terminal

    /** Priorité terminale : première proposition gagnante ; après terminal, tout est Ignore. */
    fun onEvent(event: AndroidHostEvent): AndroidLifecycleDecision {
        if (terminal) return AndroidLifecycleDecision.Ignore
        return when (event) {
            AndroidHostEvent.ViewDetached, AndroidHostEvent.LifecycleDestroyed -> {
                terminal = true
                val canonical = AndroidHostSnapshot(attachment = false, foreground = false, active = false)
                current = canonical
                AndroidLifecycleDecision.Terminate(canonical)
            }

            else -> {
                val target = targetFor(event)
                if (target == current) {
                    AndroidLifecycleDecision.Ignore
                } else {
                    current = target
                    AndroidLifecycleDecision.Publish(target)
                }
            }
        }
    }

    private fun targetFor(event: AndroidHostEvent): AndroidHostSnapshot = when (event) {
        AndroidHostEvent.LifecycleCreated -> current.copy(foreground = false, active = false)
        AndroidHostEvent.LifecycleStarted -> current.copy(foreground = true)
        AndroidHostEvent.LifecycleResumed -> current.copy(foreground = true, active = true)
        AndroidHostEvent.LifecyclePaused -> current.copy(active = false)
        AndroidHostEvent.LifecycleStopped -> current.copy(foreground = false, active = false)
        AndroidHostEvent.ViewAttached -> current.copy(attachment = true)
        AndroidHostEvent.ViewDetached, AndroidHostEvent.LifecycleDestroyed -> current
    }
}
