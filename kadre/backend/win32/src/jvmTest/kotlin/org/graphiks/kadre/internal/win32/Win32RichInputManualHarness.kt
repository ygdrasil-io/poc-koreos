package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import javax.swing.*

/** Explicit manual reception. Text is displayed locally; stdout contains only counts and categories. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
internal object Win32RichInputManualHarness {
    @JvmStatic fun main(args: Array<String>) {
        check(System.getProperty("os.name").startsWith("Windows"))
        val previews = arrayOf(JTextArea(12, 40), JTextArea(12, 40))
        lateinit var viewer: JFrame
        SwingUtilities.invokeAndWait {
            viewer = JFrame("Documents locaux — les deux fenêtres Kadre reçoivent la saisie").apply {
                isAutoRequestFocus = false; focusableWindowState = false
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                contentPane.layout = java.awt.GridLayout(2, 1)
                previews.forEach { it.isEditable=false; it.isFocusable=false; contentPane.add(JScrollPane(it)) }
                pack(); setLocation(750, 20); isVisible=true
            }
        }
        println("Réception MANUELLE, sans verdict automatique. Activer alternativement les deux fenêtres Kadre.")
        println("Le curseur TSF fourni reste au point client logique (24,24); aucun layout de caractères n'est inventé.")
        println("Les logs contiennent des catégories et compteurs; les documents restent dans l'aperçu local.")
        try {
            runKadreApplication(options=DesktopHostOptions.Standalone(backend=DesktopBackend.Win32), application=KadreApplication {
                val jobs = mutableListOf<Job>()
                repeat(2) { index ->
                    val requested = windows.requestWindow(WindowSpec(title="Kadre texte/contacts ${index+1} — fermer pour terminer",
                        contentSize=LogicalSize(600.0, 480.0)))
                    val request = (requested as? KadreResult.Success)?.value ?: error("Window admission failed")
                    val window = (request.await() as? WindowRequestOutcome.OpenedHere)?.window ?: error("Window did not open")
                    var document="ab"; var revision=TextDocumentRevision(10)
                    val opened=window.surface.input.openTextInput(TextInputConfig(surroundingText=document,
                        selection=TextRange(1,1), documentRevision=revision, multiline=true))
                    val session=(opened as? KadreResult.Success)?.value ?: error("Text service admission failed")
                    fun preview(composition: String = "") {
                        val localDocument=document; val localRevision=revision.value
                        SwingUtilities.invokeLater { previews[index].text="Fenêtre ${index+1}, révision $localRevision\n$localDocument\nPréédition locale : $composition" }
                    }
                    preview()
                    jobs += launch(start=CoroutineStart.UNDISPATCHED) {
                        var count=0L
                        session.events.collect { event ->
                            count++
                            println("text window=${index+1} event=${event.javaClass.simpleName} count=$count revision=${event.baseRevision.value}")
                            val next = when(event) {
                                is TextInputEvent.Replace -> document.replaceRange(event.range.startUtf16, event.range.endExclusiveUtf16, event.text) to
                                    TextRange(event.range.startUtf16 + event.text.length, event.range.startUtf16 + event.text.length)
                                is TextInputEvent.SelectionChanged -> document to event.selection
                                is TextInputEvent.CompositionChanged -> event.range?.let { range ->
                                    val selection=event.selection
                                    document.replaceRange(range.startUtf16, range.endExclusiveUtf16, event.text) to
                                        TextRange(range.startUtf16 + (selection?.startUtf16 ?: event.text.length),
                                            range.startUtf16 + (selection?.endExclusiveUtf16 ?: event.text.length))
                                }
                                is TextInputEvent.Action -> null
                            }
                            if (next != null) {
                                val nextRevision=TextDocumentRevision(revision.value + 1)
                                when(session.updateSurroundingText(next.first, next.second, nextRevision)) {
                                    is KadreResult.Success -> { document=next.first; revision=nextRevision }
                                    is KadreResult.Failure -> println("text window=${index+1} writeback=failed")
                                }
                            }
                            preview((event as? TextInputEvent.CompositionChanged)?.text.orEmpty())
                            session.updateCursor(LogicalRect(LogicalPoint(24.0,24.0),LogicalSize(2.0,24.0)),revision)
                        }
                    }
                    jobs += launch(start=CoroutineStart.UNDISPATCHED) {
                        var count=0L
                        window.surface.input.events.collect { event ->
                            count++
                            when(event) {
                                is InputEvent.TouchChanged -> println("touch window=${index+1} id=${event.touchId} phase=${event.phase} pressure=${event.pressure}")
                                is InputEvent.Gesture -> println("gesture window=${index+1} kind=${event.kind} phase=${event.phase} delta=${event.delta} scale=${event.scale} rotation=${event.rotationRadians}")
                                is InputEvent.PointerButtonChanged -> println("pointer window=${index+1} id=${event.pointerId} kind=${event.kind} state=${event.buttonState}")
                                else -> println("input window=${index+1} category=${event.javaClass.simpleName} count=$count")
                            }
                        }
                    }
                    jobs += launch(start=CoroutineStart.UNDISPATCHED) {
                        window.surface.state.collect { state -> println("surface window=${index+1} focus=${state.focus} scale=${state.scaleFactor} pixels=${state.physicalSize}") }
                    }
                    jobs += launch(start=CoroutineStart.UNDISPATCHED) {
                        window.events.collect { if (it is WindowEvent.CloseRequested) window.close() }
                    }
                    jobs += launch { window.state.first { it.phase == WindowPhase.Closed }; session.close() }
                }
                windows.state.first { it.windows.isEmpty() }
                jobs.forEach { it.cancel() }; jobs.joinAll()
            })
        } catch (cause: Throwable) { println("manual harness stopped: ${cause.javaClass.simpleName}") }
        finally { SwingUtilities.invokeLater { viewer.dispose() } }
    }
}
