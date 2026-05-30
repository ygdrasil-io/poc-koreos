/**
 * Point d'entrée de koreos pour la cible Kotlin/Wasm (wasmJs).
 *
 * Ce module expose l'API koreos aux consommateurs WebAssembly.
 * L'implémentation délègue vers koreos-web-common (WebEventLoop, WebWindow)
 * qui fournit la boucle d'événements basée sur requestAnimationFrame et
 * la gestion des événements DOM (clavier, souris, tactile).
 *
 * ## Usage depuis Kotlin/Wasm
 * ```kotlin
 * import io.ygdrasil.koreos.wasm.KoreosWasm
 *
 * fun main() {
 *     KoreosWasm.version
 * }
 * ```
 *
 * GRA-32 : setup initial du module koreos-wasm.
 */
package io.ygdrasil.koreos.wasm

/**
 * Objet singleton exposant les métadonnées et l'API d'initialisation de koreos-wasm.
 *
 * Stub — l'implémentation complète sera fournie dans un ticket ultérieur.
 */
object KoreosWasm {

    /**
     * Version du module koreos-wasm.
     *
     * Correspond à la version du projet définie dans `gradle.properties`.
     */
    val version: String = "0.1.1"
}
