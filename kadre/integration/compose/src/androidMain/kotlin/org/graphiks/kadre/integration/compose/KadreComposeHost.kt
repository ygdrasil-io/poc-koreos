package org.graphiks.kadre.integration.compose

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.android.attachKadre
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

/**
 * La liaison Compose d'un host kadre Android (roadmap §9) : UNE session par host, attachée
 * quand la vue est réellement attachée à la fenêtre, fermée à la sortie de composition,
 * recréée après un détachement terminal — et une recomposition (policy ou content lambdas
 * changés) ne crée JAMAIS de session supplémentaire.
 *
 * Le contrat, vu du host :
 *  — **kadre ne crée jamais de vue** (la contrainte P1) : le composable fournit le
 *    conteneur [FrameLayout] (l'identité stable du host, tenue dans `remember`) et
 *    [onSurface] choisit/crée LA vue — typiquement en la remplissant
 *    (`SurfaceView(activity).also(container::addView)`) — et la rend ; c'est cette vue,
 *    l'attach cible de la session.
 *  — **l'attach attend l'attachement effectif** (l'exigence P1 — l'échelle d'admission
 *    refuse une vue hors fenêtre) : l'événement `onViewAttachedToWindow` du conteneur —
 *    ou la relecture initiale quand le conteneur est déjà attaché — pilote la création
 *    via [KadreComposeBinding], par l'overload ACTIVITY public
 *    (`ComponentActivity.attachKadre`) : un `KadreResult.Failure` (vue refusée, réservation
 *    encore vivante…) laisse le créneau ouvert, l'événement suivant retente.
 *  — **la sortie ferme** : le `onDispose` du [DisposableEffect] demande l'arrêt de la
 *    session vivante (le `close()` Kotlin = `requestHostStop`, le modèle P1).
 *  — **le détachement terminal recrée** : le détachement de la vue de la fenêtre est un
 *    terminal dont l'arrêt appartient au runtime (le binding P1 propose
 *    `Stopped(HostDetached)`) — la liaison ne double pas cet arrêt ; si le host compose à
 *    nouveau, l'attach suivant recrée une session sur la MÊME vue (la réservation P1 est
 *    libérée à la terminaison effective).
 *
 * Aucun type interne kadre ne traverse cette signature — la surface publique d'attach et
 * [KadreSession] (le slot [content], composé AU-DESSUS du conteneur tant qu'une session
 * vit) seulement. Tout le cycle de vie court sur le thread principal (composition, view
 * attach events, dispose) — la machine n'a besoin d'aucune synchronisation.
 *
 * @param onSurface le choix du host : crée la vue de surface pour CETTE activité dans CE
 *   conteneur et la rend. Invoqué UNE fois par identité de host (capturé avec la
 *   composition d'origine) — un remplissage répété du conteneur ne peut pas arriver.
 * @param applicationFactory l'application kadre de la session (capturé à la création de la
 *   session ; une recomposition qui le change n'atteint pas la session vivante).
 * @param policy la policy de la session (défaut [KadrePolicies.Default] ; capturée comme
 *   l'usine — la sémantique « une session par host » l'emporte sur le lambda changé).
 * @param modifier le modificateur du host entier (conteneur de surface + contenu).
 * @param content l'UI compose du host, recevant la session vivante — vide par défaut.
 */
@Composable
public fun KadreComposeHost(
    onSurface: (ComponentActivity, FrameLayout) -> View,
    applicationFactory: KadreApplicationFactory,
    policy: KadrePolicy = KadrePolicies.Default,
    modifier: Modifier = Modifier,
    content: @Composable (KadreSession) -> Unit = {},
) {
    // L'activité hôte — le `setContent` d'une ComponentActivity la pose dans LocalContext ;
    // la chaîne ContextWrapper est dépliée pour les thèmes interposés. Hors activité :
    // une erreur de programmation, l'échelle P1 la signale de la même voix (fail-fast).
    // La lecture `LocalContext.current` vit HORS du remember (invocation composable) ;
    // le remember sans clé capture la première résolution — le capture délibéré.
    val context = LocalContext.current
    val activity = remember { componentActivityOf(context) }

    // L'identité stable du host : LE conteneur de toute la vie du binding. Sans clé — le
    // capture délibéré qui rend une recomposition (policy/content changés) muette.
    val container = remember { FrameLayout(activity) }

    // La vue du host — kadre ne crée rien : le callback P1 choisit, remplit le conteneur,
    // et sa vue devient l'attach cible de chaque session (recrée ou non).
    val surfaceView = remember { onSurface(activity, container) }

    // La machine pure (create/keep/close/recreate) — testée hors compose runtime.
    val binding = remember {
        KadreComposeBinding(
            create = {
                val result = activity.attachKadre(surfaceView, applicationFactory, policy)
                (result as? KadreResult.Success)?.value
            },
            close = { session -> session.close() },
        )
    }
    var session by remember { mutableStateOf<KadreSession?>(null, referentialEqualityPolicy()) }

    DisposableEffect(container) {
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                binding.onSurfaceAttached()
                session = binding.current
            }

            override fun onViewDetachedFromWindow(view: View) {
                binding.onSurfaceDetached()
                session = null
            }
        }
        container.addOnAttachStateChangeListener(listener)
        // La composition peut arriver APRÈS l'attachement de la fenêtre : l'événement du
        // listener ne viendra jamais — l'état réel est relu une fois ici. La course avec
        // l'enregistrement est absorbée par le « keep » de la machine (double événement
        // inoffensif).
        if (container.isAttachedToWindow) {
            binding.onSurfaceAttached()
            session = binding.current
        }
        onDispose {
            container.removeOnAttachStateChangeListener(listener)
            binding.onHostDisposed()
            session = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())
        session?.let { live -> content(live) }
    }
}

/** L'activité hôte, dépliée de la chaîne `ContextWrapper` — fail-fast hors [ComponentActivity]. */
private fun componentActivityOf(context: Context): ComponentActivity {
    var current: Context = context
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    error("KadreComposeHost requires a ComponentActivity context, found: ${context.javaClass.name}")
}
