package io.ygdrasil.koreos.uikit

import io.ygdrasil.koreos.core.ApplicationHandler
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExportObjCClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIResponder

/**
 * AppDelegate Koreos pour iOS.
 *
 * Déclaré @ExportObjCClass pour être visible par l'Objective-C runtime
 * (requis pour UIApplicationMain). L'ApplicationHandler est injecté via
 * le registre global [KoreosRegistry] avant le démarrage de l'application.
 *
 * Décision M3 : AppDelegate-only (pas de UISceneDelegate) — simplifie
 * la configuration et évite la dépendance à UISceneConfiguration/Info.plist.
 * Scene-based sera envisagé post-V1 si le multi-fenêtre iOS est requis.
 */
@OptIn(BetaInteropApi::class)
@ExportObjCClass
class KoreosAppDelegate : UIResponder(), UIApplicationDelegateProtocol {

    private var eventLoop: UIKitActiveEventLoop? = null

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?,
    ): Boolean {
        println("[KoreosAppDelegate] applicationDidFinishLaunching")
        val handler = KoreosRegistry.handler
            ?: error("[KoreosAppDelegate] Aucun handler enregistré — appelez startKoreosApplication avant UIApplicationMain")
        val loop = UIKitActiveEventLoop(handler)
        eventLoop = loop
        handler.canCreateSurfaces(loop)
        return true
    }

    override fun applicationDidBecomeActive(application: UIApplication) {
        println("[KoreosAppDelegate] applicationDidBecomeActive → resumed")
        eventLoop?.let { it.handler.resumed(it) }
    }

    override fun applicationWillResignActive(application: UIApplication) {
        println("[KoreosAppDelegate] applicationWillResignActive → suspended")
        eventLoop?.let { it.handler.suspended(it) }
    }

    override fun applicationWillTerminate(application: UIApplication) {
        println("[KoreosAppDelegate] applicationWillTerminate → destroySurfaces")
        eventLoop?.let { it.handler.destroySurfaces(it) }
        eventLoop = null
        KoreosRegistry.handler = null
    }
}
