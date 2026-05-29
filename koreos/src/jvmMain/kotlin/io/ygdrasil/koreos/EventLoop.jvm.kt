/**
 * Implémentation JVM de la boucle d'événements koreos — sélectionne le backend
 * selon le système d'exploitation hôte.
 *
 * Sur macOS → délègue à [io.ygdrasil.koreos.appkit.AppKitEventLoopKt.runApp]
 *   (koreos-appkit, dépendance directe de jvmMain).
 * Sur Windows → délègue à [io.ygdrasil.koreos.win32.Win32EventLoopKt.runApp]
 *   (koreos-win32, chargé par réflexion — lazy loading, pas d'import direct).
 *
 * Le lazy loading par réflexion garantit que les bindings Win32 (user32.dll, etc.)
 * ne sont jamais initialisés sur macOS, même si koreos-win32 se trouve sur le
 * classpath (build multi-OS).
 *
 * GRA-129 : façade KMP — actual jvmMain.
 * Redmine #8 : façade EventLoop Windows.
 */
package io.ygdrasil.koreos

/**
 * Implémentation JVM de [EventLoop].
 *
 * Route vers le backend approprié via réflexion :
 * - macOS → `io.ygdrasil.koreos.appkit.AppKitEventLoopKt#runApp`
 * - Windows → `io.ygdrasil.koreos.win32.Win32EventLoopKt#runApp`
 *
 * Aucun import direct de koreos-win32 — le chargement est différé à l'exécution.
 */
actual class EventLoop actual constructor() {

    /**
     * Démarre la boucle d'événements et délègue les rappels au gestionnaire fourni.
     *
     * Bloquant — ne retourne qu'à la fermeture de l'application.
     *
     * @param handler Gestionnaire du cycle de vie et des événements de l'application.
     * @throws UnsupportedOperationException si l'OS n'est pas supporté ou si le
     *   backend correspondant est introuvable sur le classpath.
     */
    actual fun runApp(handler: ApplicationHandler) {
        val os = System.getProperty("os.name", "").lowercase()
        val backendClass = when {
            os.contains("mac") -> "io.ygdrasil.koreos.appkit.AppKitEventLoopKt"
            os.contains("win") -> "io.ygdrasil.koreos.win32.Win32EventLoopKt"
            else -> throw UnsupportedOperationException(
                "Système d'exploitation non supporté par koreos-jvm : '$os'. " +
                "Seuls macOS et Windows sont pris en charge."
            )
        }

        val klass = try {
            Class.forName(backendClass)
        } catch (e: ClassNotFoundException) {
            val module = if (os.contains("win")) "koreos-win32" else "koreos-appkit"
            throw UnsupportedOperationException(
                "$backendClass introuvable sur le classpath. " +
                "Ajoutez la dépendance implementation(project(\":$module\")).",
                e,
            )
        }

        val method = klass.getMethod("runApp", ApplicationHandler::class.java)
        method.invoke(null, handler)
    }
}
