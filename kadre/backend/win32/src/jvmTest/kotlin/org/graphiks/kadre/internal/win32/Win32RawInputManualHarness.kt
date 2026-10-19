package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.*
import javax.swing.*

/** Explicit manual reception. Counters and deltas only; no raw device identity is published. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
internal object Win32RawInputManualHarness {
    @Volatile private var accepting = true

    @JvmStatic fun main(args: Array<String>) {
        check(System.getProperty("os.name").startsWith("Windows"))
        lateinit var viewer: JFrame
        val report = JTextArea(12, 56)
        SwingUtilities.invokeAndWait {
            val toggle = JToggleButton("Collecter les deltas (basculer pour fermer l'accès)", true)
            toggle.addActionListener { accepting = toggle.isSelected }
            viewer = JFrame("Réception raw input manuelle — fermer la fenêtre Kadre pour terminer").apply {
                isAutoRequestFocus = false; focusableWindowState = false
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                contentPane.layout = java.awt.BorderLayout()
                contentPane.add(toggle, java.awt.BorderLayout.NORTH)
                report.isEditable = false; report.isFocusable = false
                contentPane.add(JScrollPane(report), java.awt.BorderLayout.CENTER)
                pack(); setLocation(750, 20); isVisible = true
            }
        }
        println("Réception MANUELLE, sans verdict automatique. Déplacer la souris physique sur la fenêtre Kadre active.")
        println("Le rapport n'affiche que des compteurs et deltas ; aucune identité périphérique n'est publiée.")
        var count = 0L
        var totalX = 0.0
        var totalY = 0.0
        var suspensions = 0
        runKadreApplication(
            options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
            application = KadreApplication {
                val requested = windows.requestWindow(WindowSpec(title = "Kadre raw input — bouger la souris ici",
                    contentSize = LogicalSize(560.0, 420.0)))
                val window = ((requested as? KadreResult.Success)?.value?.await() as? WindowRequestOutcome.OpenedHere)?.window
                    ?: error("Window did not open")
                fun refresh(note: String) {
                    val text = "Événements : $count  |  somme dx=$totalX dy=$totalY  |  suspensions : $suspensions\n$note"
                    SwingUtilities.invokeLater { report.text = text }
                }
                refresh("Bouger la souris physique sur la fenêtre ; tester le focus perdu et le retour.")
                launch(start = CoroutineStart.UNDISPATCHED) {
                    val access = (window.surface.input.requestRawInput() as? KadreResult.Success)?.value
                        ?: error("raw input admission failed")
                    try {
                        access.events.collect { event ->
                            when (event) {
                                is RawInputEvent -> {
                                    count++
                                    totalX += event.deltaX
                                    totalY += event.deltaY
                                    if (count % 20 == 0L) refresh("flux actif (par 20)")
                                }
                            }
                        }
                    } finally {
                        access.close()
                    }
                    // State observation: Suspended/Closed transitions surface as state changes.
                    access.state.collect { state ->
                        if (state is RawInputState.Suspended) {
                            suspensions++
                            refresh("Accès suspendu : ${state.reason::class.simpleName}")
                        }
                    }
                }
                window.state.first { it.phase == WindowPhase.Closed }
                SwingUtilities.invokeLater { viewer.dispose() }
            },
        )
        println("Événements=$count somme=($totalX,$totalY) suspensions=$suspensions")
    }
}
