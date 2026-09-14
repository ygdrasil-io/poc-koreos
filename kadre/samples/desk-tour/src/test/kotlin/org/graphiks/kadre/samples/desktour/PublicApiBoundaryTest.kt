package org.graphiks.kadre.samples.desktour

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertFalse

class PublicApiBoundaryTest {
    @Test
    fun `sample source never imports Kadre internals`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val internalImports = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { Files.readAllLines(it).stream() }
                .filter { it.startsWith("import org.graphiks.kadre.internal.") }
                .toList()
        }

        assertFalse(internalImports.isNotEmpty(), internalImports.joinToString("\n"))
    }
}
