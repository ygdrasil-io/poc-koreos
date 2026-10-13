package org.graphiks.kadre.platform.android

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner

/**
 * Le seam SDK du consentement MediaProjection — la réalisation réelle en production, un fake
 * pilotable en test hôte (le stub android.jar lance à la construction ; les jetons `Any`
 * existent pour cela, le même parti pris que [AndroidDisplaySource]). Le port de capture ne
 * nomme JAMAIS un type SDK du consentement : c'est la traduction de ce seam en états de
 * permission qui est prouvée, pas les lectures SDK elles-mêmes.
 */
internal interface AndroidCaptureConsentSource {
    /**
     * L'intent du dialogue système de consentement — `MediaProjectionManager
     * .createScreenCaptureIntent()` en production, un jeton quelconque en test. Le port ne
     * lit JAMAIS l'intent : il le remet au launcher que [register] a rendu.
     */
    public fun createScreenCaptureIntent(): Any

    /**
     * Enregistre le launcher de CETTE demande sur `activity.activityResultRegistry` — LE
     * registre DIRECT. [owner] est toujours `null` du port : la forme trois-arguments du
     * registre ne valide AUCUN état lifecycle, c'est elle qui rend l'attach tardif légal
     * (l'anti-pattern kadre-old épinglé ci-dessous). Le résultat — qu'il arrive ou pas —
     * est délivré au plus une fois à [onResult] avec `(resultCode, data)` ; `close()` du
     * launcher retourné désenregistre exactement cette clé.
     */
    public fun register(
        key: String,
        owner: LifecycleOwner?,
        onResult: (resultCode: Int, data: Any?) -> Unit,
    ): AndroidCaptureConsentLauncher
}

/**
 * Le launcher d'UNE demande de consentement — un enregistrement du registre direct, consommé
 * avec le jeton de session qui l'a créé. [launch] part le dialogue système ; [close]
 * désenregistre la clé (le résultat qui suivrait est alors absorbé par le port).
 */
internal interface AndroidCaptureConsentLauncher : AutoCloseable {
    public fun launch(intent: Any)

    override public fun close()
}

/**
 * La réalisation de production du consentement — LE point où l'anti-pattern kadre-old est
 * corrigé. `AndroidScreenCapturer` (kadre-old :67-85) enregistrait le launcher DANS la
 * méthode de demande par `ComponentActivity.registerForActivityResult`, qui valide l'état
 * lifecycle et **THROW après STARTED** — un attach tardif (déjà resumed) ne pouvait jamais
 * demander le consentement, et chaque appel créait un launcher de plus.
 *
 * Le port passe par le registre DIRECT (`activityResultRegistry.register(key, owner = null,
 * contract, callback)`) : la forme trois-arguments ne consulte aucun état lifecycle, elle
 * est appelable à tout moment après RESUMED comme avant — c'est ce point exact que le test
 * hôte `lateRegistryRegistrationIsLegal` épinglé. `launch` exige seulement STARTED, qu'un
 * attach tardif resumed satisfait déjà.
 *
 * La clé est créée par demande (`"kadre-capture-<sessionToken>"`) et désenregistrée à la
 * consommation du résultat : un launcher par jeton, jamais réutilisé.
 */
internal class AndroidActivityCaptureConsentSource(
    private val activity: ComponentActivity,
) : AndroidCaptureConsentSource {

    override fun createScreenCaptureIntent(): Any {
        val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: throw IllegalStateException("the media projection service is absent on this device")
        return manager.createScreenCaptureIntent()
    }

    override fun register(
        key: String,
        owner: LifecycleOwner?,
        onResult: (resultCode: Int, data: Any?) -> Unit,
    ): AndroidCaptureConsentLauncher {
        // owner = null : LE registre direct, la forme trois-arguments — JAMAIS la forme
        // `ComponentActivity.registerForActivityResult` qui throw après STARTED (le KDoc
        // de la classe porte le point exact de l'anti-pattern). La surcharge quatre-arguments
        // exige un owner non-null : la branche nulle EST la forme directe.
        val launcher = if (owner == null) {
            activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.StartActivityForResult(),
            ) { result: ActivityResult -> onResult(result.resultCode, result.data) }
        } else {
            activity.activityResultRegistry.register(
                key,
                owner,
                ActivityResultContracts.StartActivityForResult(),
            ) { result: ActivityResult -> onResult(result.resultCode, result.data) }
        }
        return object : AndroidCaptureConsentLauncher {
            override fun launch(intent: Any) {
                launcher.launch(intent as Intent)
            }

            override fun close() {
                launcher.unregister()
            }
        }
    }
}

/**
 * L'activité hôte d'un contexte, dépliée des [android.content.ContextWrapper] — la forme
 * View n'a pas d'activité typée, son contexte l'héberge en pratique. `null` quand aucun
 * [ComponentActivity] n'héberge le contexte : le port répondra alors `Unsupported
 * (CapturePermission)` à une demande de consentement plutôt que de deviner un registre.
 */
internal fun activityOf(context: Context): ComponentActivity? {
    var current: Context = context
    while (true) {
        when (current) {
            is ComponentActivity -> return current
            is android.content.ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
}
