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
        // (commonMain), le code de host bureau (jvmMain) et le code de host web (wasmJsMain).
        val sourceRoots = listOf(Path.of("src/commonMain/kotlin"), Path.of("src/jvmMain/kotlin"))
        val forbiddenImports = sourceRoots.flatMap { forbiddenImports(it, ::forbiddenHostImport) }

        // Le host web a le même droit qu'un bureau : UN seul hôte Compose. Le sien est
        // `ComposeViewport` (le point d'entrée de la page) ; toute autre importation
        // `androidx.compose.ui.window.*` y reste interdite, comme toute API interne
        // Kadre ou hôte AWT/Swing.
        val webRoot = Path.of("src/wasmJsMain/kotlin")
        val webForbiddenImports = if (Files.isDirectory(webRoot)) {
            forbiddenImports(webRoot, ::forbiddenWebHostImport)
        } else {
            emptyList()
        }

        val offences = forbiddenImports + webForbiddenImports
        assertFalse(offences.isNotEmpty(), offences.joinToString("\n"))
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

    private fun forbiddenImports(sourceRoot: Path, forbidden: (String) -> Boolean = ::forbiddenHostImport): List<String> =
        Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { Files.readAllLines(it).stream() }
                .filter(forbidden)
                .toList()
        }

    /** La frontière commune : public Kadre, un seul hôte, jamais AWT ni Swing. */
    private fun forbiddenHostImport(line: String): Boolean =
        listOf(
            "org.graphiks.kadre.internal.",
            "androidx.compose.ui.window.",
            "androidx.compose.ui.awt.ComposePanel",
            "java.awt.",
            "javax.swing.",
        ).any { line.trimStart().startsWith("import $it") }

    /** La frontière du host web : `ComposeViewport` est SON hôte, le seul autorisé du paquet. */
    private fun forbiddenWebHostImport(line: String): Boolean =
        when {
            line.trimStart().startsWith("import androidx.compose.ui.window.ComposeViewport") -> false
            else -> forbiddenHostImport(line)
        }
}
