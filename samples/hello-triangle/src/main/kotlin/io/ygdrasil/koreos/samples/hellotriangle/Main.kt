/**
 * Sample hello-triangle — intégration wgpu4k sur macOS/AppKit (GRA-137).
 *
 * Ouvre une fenêtre Koreos et initialise la stack wgpu4k complète :
 *   Instance → Surface (depuis CAMetalLayer) → Adapter → Device → configure Surface.
 *
 * Pas encore de rendu — la fenêtre reste vide (clear par défaut).
 * Le rendu triangle est couvert par GRA-138.
 *
 * Usage : ./gradlew :samples:hello-triangle:run
 */
package io.ygdrasil.koreos.samples.hellotriangle

import ffi.JvmNativeAddress
import io.ygdrasil.koreos.ActiveEventLoop
import io.ygdrasil.koreos.ApplicationHandler
import io.ygdrasil.koreos.EventLoop
import io.ygdrasil.koreos.PhysicalSize
import io.ygdrasil.koreos.WindowAttributes
import io.ygdrasil.koreos.WindowId
import io.ygdrasil.koreos.core.RawWindowHandle
import io.ygdrasil.koreos.core.WindowEvent
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUInstanceBackend
import io.ygdrasil.webgpu.WGPULowLevelApi
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Gestionnaire du sample hello-triangle.
 *
 * Crée une fenêtre Koreos, extrait le [RawWindowHandle.AppKit], puis initialise
 * la stack wgpu4k complète : Instance → Surface → Adapter → Device → configure Surface.
 */
@OptIn(WGPULowLevelApi::class)
class HelloTriangleApp : ApplicationHandler {

    /**
     * Appelé dès qu'AppKit autorise la création de surfaces de rendu.
     *
     * Séquence :
     * 1. Création de la fenêtre
     * 2. Récupération du CAMetalLayer depuis le [RawWindowHandle.AppKit.nsView]
     * 3. Création de la WGPU Instance
     * 4. Création de la Surface depuis le CAMetalLayer (GRA-137)
     * 5. Acquisition de l'Adapter (GPU log)
     * 6. Acquisition du Device
     * 7. Configuration de la Surface (format BGRA8Unorm, usage RenderAttachment)
     */
    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        println("[hello-triangle] canCreateSurfaces — initialisation wgpu4k")

        // 1. Fenêtre Koreos
        val window = eventLoop.createWindow(
            WindowAttributes(
                title = "Hello Triangle — Koreos + wgpu4k M2",
                size = PhysicalSize(width = 800, height = 600),
                visible = true,
                resizable = true,
            )
        )
        println("[hello-triangle] Fenêtre créée — windowId=${window.id.value}")

        // 2. Récupérer le CAMetalLayer depuis le NSView
        val handle = window.rawWindowHandle
        if (handle !is RawWindowHandle.AppKit) {
            println("[hello-triangle] Plateforme non supportée : $handle")
            return
        }
        println("[hello-triangle] RawWindowHandle.AppKit — nsView=0x%x  nsWindow=0x%x"
            .format(handle.nsView, handle.nsWindow))

        val metalLayerAddr = getMetalLayerFromNsView(handle.nsView)
        if (metalLayerAddr == 0L) {
            println("[hello-triangle] Impossible d'obtenir le CAMetalLayer depuis le NSView")
            return
        }
        println("[hello-triangle] CAMetalLayer = 0x%x".format(metalLayerAddr))

        // 3. WGPU Instance (Metal backend)
        val wgpu = WGPU.createInstance(WGPUInstanceBackend.Metal)
            ?: run {
                println("[hello-triangle] Échec création WGPU Instance")
                return
            }
        println("[hello-triangle] WGPU Instance créée")

        // 4. Surface depuis le CAMetalLayer
        val metalLayerNativeAddr = JvmNativeAddress(MemorySegment.ofAddress(metalLayerAddr))
        val surface: NativeSurface = wgpu.getSurfaceFromMetalLayer(metalLayerNativeAddr)
            ?: run {
                println("[hello-triangle] Échec création Surface depuis CAMetalLayer")
                wgpu.close()
                return
            }
        println("[hello-triangle] Surface créée")

        // 5. Adapter
        val adapter = wgpu.requestAdapter(surface)
            ?: run {
                println("[hello-triangle] Échec acquisition Adapter")
                surface.close()
                wgpu.close()
                return
            }
        println("[hello-triangle] Adapter — info=${adapter.info}")

        // Capabilities (formats + alpha modes supportés)
        surface.computeSurfaceCapabilities(adapter)
        println("[hello-triangle] Formats supportés   : ${surface.supportedFormats}")
        println("[hello-triangle] Alpha modes supportés: ${surface.supportedAlphaMode}")

        // 6. Device
        val gpuDevice = runBlocking { adapter.requestDevice() }
            .getOrElse { err ->
                println("[hello-triangle] Échec acquisition Device : $err")
                adapter.close()
                surface.close()
                wgpu.close()
                return
            }
        println("[hello-triangle] Device créé")

        // 7. Configuration de la Surface
        val format = surface.supportedFormats
            .firstOrNull { it == GPUTextureFormat.BGRA8Unorm }
            ?: surface.supportedFormats.firstOrNull()
            ?: GPUTextureFormat.BGRA8Unorm
        val innerSize = window.innerSize
        val alphaMode = surface.supportedAlphaMode
            .firstOrNull { it == CompositeAlphaMode.Opaque }
            ?: CompositeAlphaMode.Auto

        surface.configure(
            SurfaceConfiguration(
                device = gpuDevice,
                format = format,
                usage = setOf(GPUTextureUsage.RenderAttachment),
                alphaMode = alphaMode,
            ),
            innerSize.width.toUInt(),
            innerSize.height.toUInt(),
        )
        println("[hello-triangle] Surface configurée — format=$format  size=${innerSize.width}×${innerSize.height}  alpha=$alphaMode")
        println("[hello-triangle] Stack wgpu4k prête — rendu triangle à venir (GRA-138)")
    }

    /**
     * Événements fenêtre — quitte sur [WindowEvent.CloseRequested].
     */
    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
        println("[hello-triangle] windowEvent($windowId) → $event")
        if (event is WindowEvent.CloseRequested) {
            println("[hello-triangle] CloseRequested — fermeture")
            eventLoop.exit()
        }
    }

    /**
     * Récupère le pointeur natif du `CAMetalLayer` attaché à un `NSView`.
     *
     * Appel ObjC : `[nsView layer]` via Panama FFM downcall sur `objc_msgSend`.
     * Le NSView doit déjà avoir `wantsLayer = YES` et un `CAMetalLayer` assigné
     * (ce que [io.ygdrasil.koreos.appkit.AppKitWindow] garantit).
     *
     * @param nsViewPtr Adresse mémoire du pointeur ObjC vers le NSView.
     * @return Adresse du CAMetalLayer, ou 0 en cas d'échec.
     */
    private fun getMetalLayerFromNsView(nsViewPtr: Long): Long {
        val linker = Linker.nativeLinker()
        val lib = SymbolLookup.loaderLookup()

        // sel_registerName("layer") → SEL
        val selRegisterNameSym = lib.find("sel_registerName")
            .orElseThrow { UnsatisfiedLinkError("sel_registerName non trouvé") }
        val selRegisterNameHandle = linker.downcallHandle(
            selRegisterNameSym,
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )
        val arena = Arena.ofAuto()
        val layerCStr = arena.allocateFrom("layer")
        val sel = selRegisterNameHandle.invokeExact(layerCStr) as MemorySegment

        // objc_msgSend(nsView, sel_layer) → CAMetalLayer*
        val msgSendSym = lib.find("objc_msgSend")
            .orElseThrow { UnsatisfiedLinkError("objc_msgSend non trouvé") }
        val msgSendHandle = linker.downcallHandle(
            msgSendSym,
            FunctionDescriptor.of(
                ValueLayout.ADDRESS, // return: CAMetalLayer*
                ValueLayout.ADDRESS, // self (NSView*)
                ValueLayout.ADDRESS, // SEL
            ),
        )
        val layer = msgSendHandle.invokeExact(
            MemorySegment.ofAddress(nsViewPtr),
            sel,
        ) as MemorySegment
        return layer.address()
    }
}

/**
 * Point d'entrée du sample hello-triangle.
 *
 * Doit être exécuté depuis le thread principal macOS (garanti par Gradle via
 * `-XstartOnFirstThread` dans [build.gradle.kts]).
 */
fun main() {
    println("[hello-triangle] Démarrage — Koreos M2 + wgpu4k")
    EventLoop().runApp(HelloTriangleApp())
    println("[hello-triangle] Terminé")
}
