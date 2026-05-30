/**
 * Point d'entrée de koreos pour la cible Kotlin/JS.
 *
 * Ce module expose l'API koreos aux consommateurs JavaScript/TypeScript.
 * L'implémentation délègue vers koreos-web-common (WebEventLoop, WebWindow)
 * qui fournit la boucle d'événements basée sur requestAnimationFrame et
 * la gestion des événements DOM (clavier, souris, tactile).
 *
 * ## Usage depuis JavaScript
 * ```javascript
 * import { KoreosJs } from 'koreos-js'
 * KoreosJs.version // "0.1.0"
 * ```
 *
 * ## Usage depuis Kotlin/JS
 * ```kotlin
 * import io.ygdrasil.koreos.js.KoreosJs
 *
 * fun main() {
 *     KoreosJs.version
 * }
 * ```
 *
 * GRA-30 : setup initial du module koreos-js.
 */
@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package io.ygdrasil.koreos.js

/**
 * Objet singleton exposant les métadonnées et l'API d'initialisation de koreos-js.
 *
 * Stub — l'implémentation complète sera fournie dans un ticket ultérieur.
 */
@JsExport
object KoreosJs {

    /**
     * Version du module koreos-js.
     *
     * Correspond à la version du projet définie dans `gradle.properties`.
     */
    val version: String = "0.1.1"
}
