package org.graphiks.kadre.samples.desktour

import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.samples.desktour.core.CapabilityPresentation
import org.graphiks.kadre.samples.desktour.core.KadreTourGateway
import org.graphiks.kadre.samples.desktour.core.NoteWindowHost
import org.graphiks.kadre.samples.desktour.core.TourGateway

/**
 * Le gateway du host web : tout sauf la création de note est le gateway commun, délégué
 * tel quel (cycle de vie, écrans, entrée, saisie, périphériques, capture parlent l'API
 * publique commune de Kadre). La création de note est la seule pièce propre à cette
 * cible : sans windowProvider (PROBE-NOTES.md, correction 2 — Kadre refuse une fenêtre
 * enfant du même document ; décision T2), la capability `requestWindow` de Kadre serait
 * « non prise en charge » et désactiverait l'affordance pour la mauvaise raison. Ce que
 * la démo présente ici est la vérité de CE host : les notes sont des divs de la démo
 * ([DemoNoteWindows]) et leur création est toujours possible. Le motif d'indisponibilité
 * reste `null` — il n'y a rien à demander ni à attendre, la capacité est celle de la
 * démo elle-même, pas une capability Kadre (`requestable` reste `false`).
 */
internal class WebTourGateway(
    scope: KadreScope,
    noteWindows: NoteWindowHost,
) : TourGateway by KadreTourGateway(scope, noteWindows) {
    override fun createNoteAvailability(): CapabilityPresentation =
        CapabilityPresentation(enabled = true, motif = null)
}
