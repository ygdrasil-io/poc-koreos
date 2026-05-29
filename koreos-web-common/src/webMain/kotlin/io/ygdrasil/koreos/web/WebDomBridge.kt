/**
 * Pont DOM partagé entre les cibles JS et wasmJs.
 *
 * Cette interface définit le contrat d'attachement d'un rendu Koreos à un
 * élément HTML. Les implémentations concrètes résident dans jsMain et wasmJsMain
 * afin de pouvoir utiliser les API DOM propres à chaque cible.
 *
 * ## Contrainte
 * Ce fichier est dans webMain — AUCUN import DOM n'est autorisé ici.
 * Seuls les types Kotlin purs (String, Lambda, types locaux) sont permis.
 *
 * ## Note sur koreos-core
 * La dépendance sur koreos-core (WindowEvent, Key, etc.) sera activée
 * quand koreos-core exposera des cibles JS/wasmJs (ticket #32).
 * En attendant, [WindowEvent] est défini localement dans ce module.
 *
 * @since 0.1.0
 */
package io.ygdrasil.koreos.web

/**
 * Interface de liaison entre le DOM du navigateur et le moteur Koreos.
 *
 * Implémentée séparément pour JS (via [org.w3c.dom]) et wasmJs (via interop JS Wasm).
 */
interface WebDomBridge {

    /**
     * Attache le rendu Koreos à l'élément HTML identifié par [targetElementId].
     *
     * @param targetElementId Valeur de l'attribut `id` de l'élément cible dans le DOM.
     */
    fun attach(targetElementId: String)

    /**
     * Détache le rendu Koreos de l'élément DOM et libère les ressources associées.
     */
    fun detach()

    /**
     * Callback invoqué à chaque événement de fenêtre produit par le pont DOM.
     *
     * Peut être null si aucun écouteur n'est enregistré.
     */
    var onWindowEvent: ((WebWindowEvent) -> Unit)?
}
