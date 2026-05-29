/**
 * Implémentation JS de [WebDomBridge].
 *
 * Stub minimal permettant la compilation du module pour la cible js(IR).
 * L'implémentation complète (attachement des écouteurs DOM, boucle RAF, etc.)
 * sera réalisée dans les tickets #24 et #25.
 *
 * Ce fichier PEUT utiliser kotlinx.browser et org.w3c.dom.* car il est dans jsMain.
 */
package io.ygdrasil.koreos.web

/**
 * Pont DOM JS vers le moteur Koreos.
 *
 * Stub — attach/detach ne font rien dans cette version initiale.
 */
class JsWebDomBridge : WebDomBridge {

    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null

    override fun attach(targetElementId: String) {
        // TODO : #24 — attacher les écouteurs DOM sur l'élément `#targetElementId`
    }

    override fun detach() {
        // TODO : #25 — retirer les écouteurs DOM et libérer les ressources
    }
}
