package org.graphiks.kadre.internal.win32

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.graphiks.kadre.application.*
import org.graphiks.kadre.diagnostics.*
import org.graphiks.kadre.input.*
import org.graphiks.kadre.interaction.InteractionAction
import org.graphiks.kadre.interaction.InteractionEvent
import org.graphiks.kadre.interaction.InteractionHandler
import org.graphiks.kadre.platform.desktop.*
import org.graphiks.kadre.surface.*
import org.graphiks.kadre.window.*
import javax.swing.*
/** Explicit manual reception. Counters and categories only; paths and contents stay local. */
@OptIn(KadrePlatformApi::class, DelicateKadreApi::class)
internal object Win32DropManualHarness {
    @Volatile private var accepting = true

    @JvmStatic fun main(args: Array<String>) {
        check(System.getProperty("os.name").startsWith("Windows"))
        lateinit var viewer: JFrame
        val report = JTextArea(14, 56)
        SwingUtilities.invokeAndWait {
            val toggle = JToggleButton("Accepter les drops (basculer pour refuser)", true)
            toggle.addActionListener { accepting = toggle.isSelected }
            viewer = JFrame("Réception drop manuelle — fermer la fenêtre Kadre pour terminer").apply {
                isAutoRequestFocus = false; focusableWindowState = false
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                contentPane.layout = java.awt.BorderLayout()
                contentPane.add(toggle, java.awt.BorderLayout.NORTH)
                report.isEditable = false; report.isFocusable = false
                contentPane.add(JScrollPane(report), java.awt.BorderLayout.CENTER)
                pack(); setLocation(750, 20); isVisible = true
            }
        }
        println("Réception MANUELLE, sans verdict automatique. Glisser depuis Explorer/navigateur vers la fenêtre Kadre.")
        println("Le rapport n'affiche que des catégories et compteurs ; chemins et contenus restent locaux.")
        var offers = 0
        var drops = 0
        var rejected = 0
        runKadreApplication(options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32), application = KadreApplication {
            val requested = windows.requestWindow(WindowSpec(title = "Kadre drop — glisser ici",
                contentSize = LogicalSize(560.0, 420.0)))
            val request = (requested as? KadreResult.Success)?.value ?: error("Window admission failed")
            val window = (request.await() as? WindowRequestOutcome.OpenedHere)?.window ?: error("Window did not open")
            window.surface.installInteractionHandler(InteractionHandler { context, event ->
                if (event is InteractionEvent.DropEntered) {
                    if (accepting) context.request(InteractionAction.AcceptDrop(event.offer.id))
                    else null
                } else null
            })
            fun refresh(note: String) {
                val text = "Offres entrées : $offers  |  drops performés : $drops  |  refus : $rejected\n$note"
                SwingUtilities.invokeLater { report.text = text }
            }
            refresh("Glisser un fichier (Explorer), une URL (navigateur) ou du texte ; tester aussi le refus.")
            launch(start = CoroutineStart.UNDISPATCHED) {
                window.surface.input.events.collect { event ->
                    when (event) {
                        is InputEvent.DropEntered -> {
                            offers++
                            val categories = event.offer.items.groupingBy { it.kind }.eachCount()
                            refresh("Entrée : $categories")
                        }
                        is InputEvent.Dropped -> {
                            drops++
                            val transfer = (event.offer.claimTransfer() as? KadreResult.Success)?.value
                            if (transfer == null) { refresh("Claim refusé"); return@collect }
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                var total = 0L
                                for (item in transfer.items) {
                                    item.collectBytes(64 * 1024) { total += it.size }
                                }
                                transfer.close()
                                refresh("Drop lu : ${transfer.items.size} item(s), $total octets (contenu local)")
                            }
                        }
                        else -> Unit
                    }
                }
            }
            window.state.first { it.phase == WindowPhase.Closed }
            SwingUtilities.invokeLater { viewer.dispose() }
        })
        println("Offres=$offers drops=$drops refus=$rejected")
    }
}
