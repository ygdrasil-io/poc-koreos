package org.graphiks.kadre.samples.desktour

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicApiBoundaryTest {
    @Test
    fun `sample source never imports Kadre internals`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val internalImports = internalImports(sourceRoot)

        assertFalse(internalImports.isNotEmpty(), internalImports.joinToString("\n"))
    }

    @Test
    fun `an indented Kadre-internal import is forbidden`() {
        val sourceRoot = Files.createTempDirectory("desk-tour-public-api-boundary")
        try {
            Files.writeString(
                sourceRoot.resolve("IndentedInternalImport.kt"),
                "    import org.graphiks.kadre.internal.HiddenApi\n",
            )

            assertTrue(internalImports(sourceRoot).isNotEmpty())
        } finally {
            Files.walk(sourceRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    private fun internalImports(sourceRoot: Path): List<String> =
        Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { Files.readAllLines(it).stream() }
                .filter { it.trimStart().startsWith("import org.graphiks.kadre.internal.") }
                .toList()
        }
}
