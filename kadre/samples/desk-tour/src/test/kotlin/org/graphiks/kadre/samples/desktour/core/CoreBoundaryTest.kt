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
        Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.forEach { file ->
                file.toFile().readLines().forEachIndexed { index, line ->
                    forbidden.firstOrNull { line.trimStart().startsWith("import $it") }?.let {
                        offenders += "${file.fileName}:${index + 1} imports $it"
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "core must stay renderer-free: $offenders")
    }
}
