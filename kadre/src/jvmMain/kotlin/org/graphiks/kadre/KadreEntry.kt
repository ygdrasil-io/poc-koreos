package org.graphiks.kadre

import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.runKadreApplication as runKadreDesktopApplication

/**
 * Entrée ownership-first de Kadre sur JVM : lance une application dans un process que Kadre
 * possède — il n'y a plus d'hôte auquel s'attacher. Sur JVM, l'entrée délègue au backend
 * desktop avec [DesktopBackend.Auto] (priorité de l'hôte : AppKit, Win32, Wayland puis X11).
 *
 * Comme l'entrée desktop sous-jacente, la fonction est `suspend` par forme commune aux
 * plateformes mais **bloque le thread appelant** le temps de la session (boucle main JVM).
 * Si aucun backend n'est disponible sur le classpath (les backends sont des dépendances
 * `runtimeOnly`), le résultat est un [KadreResult.Failure] explicite — jamais une exception.
 *
 * @param options les options ownership du run ([KadreRunOptions.policy], première fenêtre).
 * @param application l'application que le process exécute.
 */
public suspend fun runKadreApplication(
    options: KadreRunOptions = KadreRunOptions(),
    application: KadreApplication,
): KadreResult<SessionOutcome> = runKadreDesktopApplication(
    applicationFactory = KadreApplicationFactory { application },
    backend = DesktopBackend.Auto,
    options = options,
)
