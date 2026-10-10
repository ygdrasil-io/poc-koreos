package org.graphiks.kadre.samples.desktour

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicApiBoundaryTest {
    @Test
    fun `sample source uses public Kadre APIs and one host`() {
        // Le sample est devenu multiplateforme : la frontière porte sur le code partagé
        // (commonMain) ET le code de host bureau (jvmMain).
        val sourceRoots = listOf(Path.of("src/commonMain/kotlin"), Path.of("src/jvmMain/kotlin"))
        val forbiddenImports = sourceRoots.flatMap { forbiddenImports(it) }

        assertFalse(forbiddenImports.isNotEmpty(), forbiddenImports.joinToString("\n"))
    }

    @Test
    fun `an indented Kadre-internal import is forbidden`() {
        val sourceRoot = Files.createTempDirectory("desk-tour-public-api-boundary")
        try {
            Files.writeString(
                sourceRoot.resolve("IndentedInternalImport.kt"),
                "    import org.graphiks.kadre.internal.HiddenApi\n",
            )

            assertTrue(forbiddenImports(sourceRoot).isNotEmpty())
        } finally {
            Files.walk(sourceRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun `a second Compose or AWT host import is forbidden`() {
        val sourceRoot = Files.createTempDirectory("desk-tour-single-host-boundary")
        try {
            for (forbidden in listOf(
                "androidx.compose.ui.window.Window",
                "androidx.compose.ui.awt.ComposePanel",
                "java.awt.EventQueue",
                "javax.swing.SwingUtilities",
            )) {
                Files.writeString(sourceRoot.resolve("SecondHost.kt"), "import $forbidden\n")
                assertTrue(forbiddenImports(sourceRoot).isNotEmpty(), "Import must be forbidden: $forbidden")
            }
        } finally {
            Files.walk(sourceRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    private fun forbiddenImports(sourceRoot: Path): List<String> =
        Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { Files.readAllLines(it).stream() }
                .filter { line ->
                    listOf(
                        "org.graphiks.kadre.internal.",
                        "androidx.compose.ui.window.",
                        "androidx.compose.ui.awt.ComposePanel",
                        "java.awt.",
                        "javax.swing.",
                    ).any { line.trimStart().startsWith("import $it") }
                }
                .toList()
        }
}
