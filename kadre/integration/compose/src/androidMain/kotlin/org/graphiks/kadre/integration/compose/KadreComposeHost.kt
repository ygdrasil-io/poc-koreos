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
 *    refuse une vue hors fenêtre) : l'événement `onViewAttachedToWindow` de LA VUE DE
 *    SURFACE — l'attach cible de l'admission — pilote la création via
 *    [KadreComposeBinding], par l'overload ACTIVITY public
 *    (`ComponentActivity.attachKadre`) : un `KadreResult.Failure` (vue refusée, réservation
 *    encore vivante…) laisse le créneau ouvert, l'événement suivant retente. (Revue de
 *    phase 9, gate d'instrumentation AND-010 : Android dispatch les événements d'attach
 *    de HAUT en bas AVANT d'attacher les enfants — l'événement du conteneur ne garantit
 *    PAS l'attach de la vue cible ; le déclencheur est donc la vue elle-même, jamais le
 *    conteneur.)
 *  — **la sortie ferme** : le `onDispose` du [DisposableEffect] demande l'arrêt de la
 *    session vivante (le `close()` Kotlin = `requestHostStop`, le modèle P1).
 *  — **le détachement terminal recrée** : le détachement de la vue de la fenêtre est un
 *    terminal dont l'arrêt appartient au runtime (le binding P1 propose
 *    `Stopped(HostDetached)`) — la liaison ne double pas cet arrêt ; si le host compose à
 *    nouveau, l'attach suivant recrée une session sur la MÊME vue (la réservation P1 est
 *    libérée à la terminaison effective).
 *  — **limite documentée — la course de re-attach rapide** : après un détachement
 *    terminal, un re-attach tenté PENDANT que la réservation P1 est encore tenue (avant
 *    sa libération à la terminaison effective) est refusé (`AlreadyInUse(Host)`) — et ce
 *    refus laisse le créneau ouvert SANS qu'aucun nouvel événement d'attach ne survienne
 *    (la vue est déjà dans la fenêtre) : le host reste sans session jusqu'au PROCHAIN
 *    cycle détachement/rattachement, seul à re-déclencher la création. La liaison ne
 *    retente pas d'elle-même ; un retry au niveau de la machine (ré-arme différée) est un
 *    raffinement différé, pas une promesse de cette phase.
 *
 * Aucun type interne kadre ne traverse cette signature — la surface publique d'attach et
 * [KadreSession] (le slot [content], composé AU-DESSUS du conteneur tant qu'une session
 * vit) seulement. Tout le cycle de vie court sur le thread principal (composition, view
 * attach events, dispose) — la machine n'a besoin d'aucune synchronisation.
 *
 * Review T3 (M-2) : les captures de CREATION survivent à la RECRÉATION — l'activité, le
 * conteneur, la vue, la machine et les paramètres `applicationFactory`/`policy` sont tenus
 * dans des `remember` SANS clé, qui ne meurent qu'à la sortie de composition. Une session
 * recréée après un détachement terminal (le `recreate` de [KadreComposeBinding]) est donc
 * construite avec la policy et l'usine de la PREMIÈRE composition — une recomposition qui
 * passe d'autres lambdas n'atteint ni la session vivante, ni la recréée (une NOUVELLE
 * composition est un NOUVEAU host : ses captures sont à elle).
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
        // LE DÉCLENCHEUR EST LA VUE CIBLE — la revue de phase 9 (Task 4, gate d'instrumentation
        // AND-010) : Android dispatch `onViewAttachedToWindow` de HAUT en bas, AVANT d'attacher
        // les enfants — l'événement du conteneur ne garantit PAS l'attach de [surfaceView],
        // et l'échelle d'admission refuse alors une vue hors fenêtre SANS re-événement (la
        // session n'aurait jamais lieu). L'attach de la vue de surface est exactement la
        // précondition d'admission P1 : c'est lui qui porte la création — et son détachement,
        // le terminal dont l'arrêt appartient au runtime.
        val surfaceListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                binding.onSurfaceAttached()
                session = binding.current
            }

            override fun onViewDetachedFromWindow(view: View) {
                binding.onSurfaceDetached()
                session = null
            }
        }
        surfaceView.addOnAttachStateChangeListener(surfaceListener)
        // La composition peut arriver APRÈS l'attachement de la fenêtre : l'événement du
        // listener ne viendra jamais — l'état réel est relu une fois ici. La course avec
        // l'enregistrement est absorbée par le « keep » de la machine (double événement
        // inoffensif).
        if (surfaceView.isAttachedToWindow) {
            binding.onSurfaceAttached()
            session = binding.current
        }
        onDispose {
            surfaceView.removeOnAttachStateChangeListener(surfaceListener)
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
