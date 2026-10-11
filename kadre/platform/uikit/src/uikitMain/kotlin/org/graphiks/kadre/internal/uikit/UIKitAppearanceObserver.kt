@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.cValue
import org.graphiks.kadre.internal.runtime.RuntimeLock
import org.graphiks.kadre.internal.runtime.withLock
import org.graphiks.kadre.surface.SurfaceTheme
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIWindowScene

/**
 * L'observer d'apparence (phase 2, Task 3, Step 2 du brief) : sur iOS/tvOS 17+, le push de
 * changement de traits `scene.registerForTraitChanges(listOf(UITraitUserInterfaceStyle), handler)`
 * devait alerter [onTheme] — un push ADDITIONNEL à côté des re-lectures du binding (le thème est
 * déjà relu à chaque observation du binding).
 *
 * POSTURE RELEVÉE (Task 3, preuves de compile dans le rapport) : l'API d'enregistrement n'est PAS
 * exposée par le mapping Kotlin/Native 2.4.20 — `registerForTraitChanges:withHandler:` existe
 * comme sélecteur dans le klib, mais le protocole `UITraitChangeObservable` est importé en
 * `UITraitChangeObservableProtocol` SANS qu'aucune classe UIKit (`UIWindowScene`, `UIView`)
 * n'hérite de l'interface : l'appel sur la scène est « unresolved reference » et le cast vers le
 * protocole est un type mismatch. Le repli DOCUMENTÉ du brief s'applique donc À TOUTES les
 * versions (et pas seulement < 17) : re-lecture du thème sur les notifications du binding
 * uniquement — [onTheme] n'est jamais invoqué aujourd'hui ; la couture reste en place (port et
 * signature exacts du brief) pour que le push se branchât en une ligne le jour où le klib expose
 * la conformance. Aucun contournement (cinterop .def, wrapper Swift, objc_msgSend) — interdit.
 *
 * La gate 17+ reste codée et PROUVÉE au compile (`NSOperatingSystemVersion` est une struct C →
 * `cValue`, voir [isAtLeast17]) : c'est elle qui gardera le push le jour de l'exposition de l'API.
 * install/teardown sont idempotents, sous verrou, file principale uniquement — même discipline
 * que le binding.
 */
internal class UIKitAppearanceObserver(
    private val scene: UIWindowScene,
    private val onTheme: (SurfaceTheme) -> Unit,
) {
    private val lock = RuntimeLock()
    private var installed: Boolean = false
    private var tornDown: Boolean = false

    /**
     * Pose le push de traits si la plateforme le porte. Idempotent. Aujourd'hui : la gate est
     * évaluée puis l'enregistrement est court-circuité (API absente du mapping, cf. KDoc de
     * classe) — l'observer reste un no-op sûr, jamais une fausse promesse de livraison.
     */
    fun install() {
        lock.withLock {
            if (tornDown || installed) return
            installed = true
        }
        if (!isAtLeast17()) return
        // 17+ : le point de branchement exact du brief serait
        // `scene.registerForTraitChanges(listOf(UITraitUserInterfaceStyle.metaClass/…), handler)`
        // avec handler `{ environment, previousCollection -> … }` — la forme K/N du bloc ET la
        // réception de la méthode sur la scène sont à la fois non compilables (cf. rapport) ;
        // repli documenté : re-lectures sur notifications uniquement.
    }

    /** Retrait au teardown — rien à retirer tant que l'enregistrement est indisponible. */
    fun teardown() {
        lock.withLock { tornDown = true }
    }

    /** La gate 17+ du brief (`NSProcessInfo`, struct C version → `cValue`). */
    internal fun isAtLeast17(): Boolean =
        NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
            cValue<NSOperatingSystemVersion> {
                majorVersion = 17L
                minorVersion = 0L
                patchVersion = 0L
            },
        )
}
