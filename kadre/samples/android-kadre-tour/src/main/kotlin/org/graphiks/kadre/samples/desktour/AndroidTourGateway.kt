package org.graphiks.kadre.samples.desktour

import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.samples.desktour.core.KadreTourGateway
import org.graphiks.kadre.samples.desktour.core.NoteWindowHost
import org.graphiks.kadre.samples.desktour.core.TourGateway

/**
 * Le gateway du host Android : tout est le gateway commun, délégué tel quel — cycle de
 * vie, écrans, entrée, saisie, périphériques et capture parlent l'API publique commune
 * de Kadre, portée par les managers RÉELS de la plateforme (P7 displays/devices, P3
 * input, P8 capture). Contre le host web ([WebTourGateway] — dont les notes sont des
 * divs de la démo et dont la création reste possible, override à l'appui), AUCUNE
 * dérogation ici : la vérité de CE host est la capability elle-même. Android ne crée
 * pas de fenêtre (`requestWindow = Unsupported(RequestWindow)`, BACKEND-CAPABILITIES
 * §3), le gateway commun la présente désactivée avec son motif, et l'affordance du
 * route Desk reste visible, porteuse de la raison — la démonstration du chemin
 * unsupported, exactement ce que la visite vient montrer.
 */
internal class AndroidTourGateway(
    scope: KadreScope,
    noteWindows: NoteWindowHost,
) : TourGateway by KadreTourGateway(scope, noteWindows)
