package io.ygdrasil.koreos.android

import io.ygdrasil.koreos.core.ApplicationHandler

/**
 * Registre global pour le runtime Koreos sur Android.
 *
 * Stocke le [ApplicationHandler] enregistré via [io.ygdrasil.koreos.EventLoop.runApp]
 * afin qu'il soit récupéré par [KoreosActivity.createHandler].
 */
object AndroidKoreosRuntime {
    var currentHandler: ApplicationHandler? = null
}
