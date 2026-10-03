// IdleRS: line and branch coverage with a missed-count ratchet, applied by idlers.gradle.kts (server) and by
// ../luna-client/build.gradle.kts (client). The applying script first sets these extra properties:
// idlersCoverageName (shown in the summary), idlersCoverageClasses (the class files to count), maxMissedLines,
// maxMissedBranches and idlersRatchetFile (where those two live, for the "lower the ratchet" hint).

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

// jacoco is a core plugin, so it needs no entry in a plugins block.
apply(plugin = "jacoco")

configure<JacocoPluginExtension> {
    toolVersion = "0.8.15"
}

val coverageName = extra["idlersCoverageName"] as String
val coverageClasses = extra["idlersCoverageClasses"] as FileTree
val maxMissedLines = extra["maxMissedLines"] as Int
val maxMissedBranches = extra["maxMissedBranches"] as Int
val ratchetFile = extra["idlersRatchetFile"] as String

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(coverageClasses)
    reports {
        xml.required = true
        html.required = true
    }
    doLast {
        printCoverageSummary(reports.xml.outputLocation.get().asFile, reports.html.outputLocation.get().asFile)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    classDirectories.setFrom(coverageClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "MISSEDCOUNT"
                maximum = maxMissedLines.toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "MISSEDCOUNT"
                maximum = maxMissedBranches.toBigDecimal()
            }
        }
    }
}

data class ReportLine(val number: Int, val missed: Boolean, val missesBranches: Boolean)

fun printCoverageSummary(xmlReport: File, htmlReport: File) {
    val report = DocumentBuilderFactory.newInstance()
        .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        .newDocumentBuilder()
        .parse(xmlReport)
        .documentElement
    logger.lifecycle("IdleRS $coverageName coverage: ${report.coverageText()}")
    for (pkg in report.children("package")) {
        for (source in pkg.children("sourcefile")) {
            val lines = source.children("line").map { line ->
                ReportLine(
                    number = line.getAttribute("nr").toInt(),
                    missed = line.getAttribute("ci") == "0",
                    missesBranches = line.getAttribute("mb") != "0",
                )
            }
            val missedLines = missedLineRanges(lines)
            val partialBranches = lines.filter { it.missesBranches }.map { it.number }
            if (missedLines.isEmpty() && partialBranches.isEmpty()) continue
            logger.lifecycle("  ${pkg.getAttribute("name")}/${source.getAttribute("name")}: ${source.coverageText()}")
            if (missedLines.isNotEmpty()) logger.lifecycle("    missed lines ${missedLines.joinToString()}")
            if (partialBranches.isNotEmpty()) {
                logger.lifecycle("    missed branches on lines ${partialBranches.joinToString()}")
            }
        }
    }
    val missedLines = report.missedCount("LINE")
    val missedBranches = report.missedCount("BRANCH")
    logger.lifecycle("Ratchet allows $maxMissedLines missed lines and $maxMissedBranches missed branches.")
    if (missedLines < maxMissedLines || missedBranches < maxMissedBranches) {
        logger.lifecycle(
            "Coverage improved: lower the ratchet in $ratchetFile to " +
                "maxMissedLines = $missedLines, maxMissedBranches = $missedBranches.",
        )
    }
    logger.lifecycle("HTML report: ${htmlReport.resolve("index.html").toURI()}")
}

fun Element.children(tag: String): List<Element> =
    (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>().filter { it.tagName == tag }

fun Element.counter(type: String): Element? = children("counter").firstOrNull { it.getAttribute("type") == type }

fun Element.missedCount(type: String): Int = counter(type)?.getAttribute("missed")?.toInt() ?: 0

fun Element.coverageText(): String = listOf("LINE" to "lines", "BRANCH" to "branches").joinToString { (type, label) ->
    val counter = counter(type)
    if (counter == null) {
        "no $label"
    } else {
        val covered = counter.getAttribute("covered").toInt()
        val total = covered + counter.getAttribute("missed").toInt()
        "$label $covered/$total (${"%.1f".format(100.0 * covered / total)}%)"
    }
}

// The report only lists lines that hold code, so a blank line or comment between two missed lines does not split
// their range.
fun missedLineRanges(lines: List<ReportLine>): List<String> {
    val ranges = mutableListOf<IntRange>()
    lines.forEachIndexed { index, line ->
        if (!line.missed) return@forEachIndexed
        if (index > 0 && lines[index - 1].missed) {
            ranges[ranges.lastIndex] = ranges.last().first..line.number
        } else {
            ranges += line.number..line.number
        }
    }
    return ranges.map { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
}
