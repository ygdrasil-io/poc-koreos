package io.ygdrasil.koreos.uikit

import io.ygdrasil.koreos.core.ApplicationHandler
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.getOriginalKotlinClass
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplicationMain
import platform.objc.objc_getClass

/**
 * Démarre l'application Koreos iOS.
 *
 * Enregistre le handler dans [KoreosRegistry] puis délègue à [UIApplicationMain]
 * avec [KoreosAppDelegate] comme classe de delegate.
 *
 * Cette fonction ne retourne pas (UIApplicationMain bloque jusqu'à la fin de l'app).
 *
 * @param handler Gestionnaire du cycle de vie de l'application.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun startKoreosApplication(handler: ApplicationHandler) {
    KoreosRegistry.handler = handler
    @Suppress("UNCHECKED_CAST")
    val delegateClass = objc_getClass("KoreosAppDelegate") as ObjCClass
    val delegateClassName = NSStringFromClass(delegateClass)
    UIApplicationMain(0, null, null, delegateClassName)
}
