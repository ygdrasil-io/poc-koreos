package org.graphiks.kadre.samples.desktour.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CoreBoundaryTest {
    @Test
    fun `core sources depend on no renderer and no internal Kadre type`() {
        val forbidden = listOf(
            "androidx.compose.",
            "org.graphiks.kffi.",
            "org.graphiks.kadre.internal.",
            "java.awt.",
            "javax.swing.",
        )
        val root = Path.of("src/main/kotlin/org/graphiks/kadre/samples/desktour/core")
        val offenders = mutableListOf<String>()
        var scanned = 0
        Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.forEach { file ->
                scanned++
                file.toFile().readLines().forEachIndexed { index, line ->
                    forbidden.firstOrNull { line.trimStart().startsWith("import $it") }?.let {
                        offenders += "${file.fileName}:${index + 1} imports $it"
                    }
                }
            }
        }
        // Sans ce garde-fou, renommer ou déplacer le paquet rendrait ce test vert en
        // n'ayant rien vérifié du tout.
        assertTrue(scanned > 0, "aucune source core scannée sous $root : la frontière n'est pas appliquée")
        assertTrue(offenders.isEmpty(), "core must stay renderer-free: $offenders")
    }
}
