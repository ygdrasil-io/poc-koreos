/**
 * Implémentation wasmJs de [WebDomBridge].
 *
 * Stub minimal permettant la compilation du module pour la cible wasmJs.
 * L'implémentation complète (attachement des écouteurs via interop JS Wasm,
 * boucle RAF, etc.) sera réalisée dans les tickets #24 et #25.
 *
 * Ce fichier PEUT utiliser les interops JS Wasm car il est dans wasmJsMain.
 */
package io.ygdrasil.koreos.web

/**
 * Pont DOM wasmJs vers le moteur Koreos.
 *
 * Stub — attach/detach ne font rien dans cette version initiale.
 */
class WasmJsWebDomBridge : WebDomBridge {

    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null

    override fun attach(targetElementId: String) {
        // TODO : #24 — attacher les écouteurs DOM via interop JS Wasm
    }

    override fun detach() {
        // TODO : #25 — retirer les écouteurs et libérer les ressources wasmJs
    }
}
