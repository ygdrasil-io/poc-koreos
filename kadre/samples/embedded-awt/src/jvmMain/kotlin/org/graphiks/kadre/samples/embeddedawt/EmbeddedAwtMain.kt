package org.graphiks.kadre.samples.embeddedawt

import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.integration.awt.AwtKadreHost
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec

fun main() {
    if (!System.getProperty("os.name").startsWith("Windows")) {
        println("embedded-awt sample: Windows only (win32 backend)")
        return
    }
    val frame = JFrame("kadre embedded-awt sample")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(400, 300)
    val attach = JButton("Attach a kadre session")
    attach.addActionListener {
        SwingUtilities.invokeLater {
            val scope = CoroutineScope(Job() + Dispatchers.Default)
            val attached = AwtKadreHost.attach(
                scope,
                KadreApplicationFactory { KadreApplication {
                    val requested = windows.requestWindow(
                        WindowSpec(title = "kadre session window", contentSize = LogicalSize(320.0, 240.0)),
                    )
                    check(requested is KadreResult.Success<*>) { "window request failed: $requested" }
                } },
            )
            when (attached) {
                is KadreResult.Success -> {
                    println("kadre session attached: ${attached.value.id}")
                    attach.isEnabled = false
                }
                is KadreResult.Failure -> {
                    println("kadre attach refused: ${attached.reason}")
                }
            }
        }
    }
    frame.contentPane.add(attach)
    frame.isVisible = true
}
