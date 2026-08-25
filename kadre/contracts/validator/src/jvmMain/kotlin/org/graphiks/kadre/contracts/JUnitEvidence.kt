package org.graphiks.kadre.contracts

import org.w3c.dom.Element
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

internal enum class JUnitStatus {
    Passed,
    Skipped,
    Failed,
    Error,
}

internal data class JUnitCase(
    val className: String,
    val name: String,
    val status: JUnitStatus,
)

internal data class JUnitSummary(
    val tests: Int,
    val skipped: Int,
    val failures: Int,
    val errors: Int,
    val durationMillis: Long,
    val cases: Map<Pair<String, String>, JUnitCase>,
)

internal object JUnitEvidence {
    fun read(directory: Path): JUnitSummary = read(listOf(directory))

    fun read(directories: List<Path>): JUnitSummary {
        require(directories.isNotEmpty()) { "JUnit report directories must not be empty" }
        val reports = directories.flatMap { directory ->
            require(Files.isDirectory(directory)) { "JUnit report directory does not exist: $directory" }
            Files.list(directory).use { paths ->
                paths.filter { it.isRegularFile() && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
                    .sorted()
                    .toList()
            }
        }
        require(reports.isNotEmpty()) { "JUnit report directories contain no TEST-*.xml" }

        val cases = linkedMapOf<Pair<String, String>, JUnitCase>()
        var tests = 0
        var skipped = 0
        var failures = 0
        var errors = 0
        var durationMillis = 0L
        reports.forEach { report ->
            parseSuites(report).forEach { suite ->
                val suiteName = suite.getAttribute("name")
                val suiteCases = suite.directChildren("testcase").map(::parseCase)
                val declaredTests = suite.requiredInt("tests")
                val declaredSkipped = suite.requiredInt("skipped")
                val declaredFailures = suite.requiredInt("failures")
                val declaredErrors = suite.requiredInt("errors")
                check(declaredTests == suiteCases.size) {
                    "$suiteName declares $declaredTests tests but contains ${suiteCases.size}"
                }
                check(declaredSkipped == suiteCases.count { it.status == JUnitStatus.Skipped }) {
                    "$suiteName declares $declaredSkipped skipped tests but its testcases disagree"
                }
                check(declaredFailures == suiteCases.count { it.status == JUnitStatus.Failed }) {
                    "$suiteName declares $declaredFailures failures but its testcases disagree"
                }
                check(declaredErrors == suiteCases.count { it.status == JUnitStatus.Error }) {
                    "$suiteName declares $declaredErrors errors but its testcases disagree"
                }

                suiteCases.forEach { testCase ->
                    val identity = testCase.className to testCase.name
                    check(cases.put(identity, testCase) == null) {
                        "duplicate testcase: ${testCase.className}#${testCase.name}"
                    }
                }
                tests += declaredTests
                skipped += declaredSkipped
                failures += declaredFailures
                errors += declaredErrors
                durationMillis += suite.durationMillis()
            }
        }
        return JUnitSummary(tests, skipped, failures, errors, durationMillis, cases)
    }

    private fun parseSuites(path: Path): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val root = Files.newInputStream(path).use { input ->
            factory.newDocumentBuilder().parse(input).documentElement
        }
        return when (root.tagName) {
            "testsuite" -> listOf(root)
            "testsuites" -> root.directChildren("testsuite")
            else -> error("unsupported JUnit root element in $path: ${root.tagName}")
        }
    }

    private fun parseCase(element: Element): JUnitCase {
        val className = element.requiredAttribute("classname")
        val name = element.requiredAttribute("name")
        val statuses = listOf(
            "skipped" to JUnitStatus.Skipped,
            "failure" to JUnitStatus.Failed,
            "error" to JUnitStatus.Error,
        ).filter { (tag, _) -> element.directChildren(tag).isNotEmpty() }
        check(statuses.size <= 1) { "$className#$name declares multiple terminal statuses" }
        return JUnitCase(className, name, statuses.singleOrNull()?.second ?: JUnitStatus.Passed)
    }

    private fun Element.requiredAttribute(name: String): String =
        getAttribute(name).also { require(it.isNotBlank()) { "$tagName requires attribute $name" } }

    private fun Element.requiredInt(name: String): Int = requiredAttribute(name).toInt().also {
        require(it >= 0) { "$tagName attribute $name must be non-negative" }
    }

    private fun Element.durationMillis(): Long = requiredAttribute("time")
        .toBigDecimal()
        .movePointRight(3)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

    private fun Element.directChildren(tag: String): List<Element> = buildList {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == tag) add(child)
        }
    }
}
