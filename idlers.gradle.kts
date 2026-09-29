// IdleRS additions to the upstream build, applied from the last line of build.gradle.kts.

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

// Line and branch coverage of our own packages. jacoco is a core plugin, so it needs no entry in the plugins block.
apply(plugin = "jacoco")

configure<JacocoPluginExtension> {
    toolVersion = "0.8.15"
}

val idlersPackages = listOf("game/idle", "game/harness")

// Ratchet: the build fails when more lines or branches are missed than this. Lower it whenever tests close a gap.
val maxMissedLines = 311
val maxMissedBranches = 176

// Scripts only wire events to tested classes and cannot run without a booted server, so they are not counted.
fun idlersClasses(): FileTree {
    val scriptRoot = file("src/main/kotlin/game")
    val scriptClasses = fileTree(scriptRoot) { include(idlersPackages.map { "$it/**/*.kts" }) }.files.map { script ->
        val packagePath = script.parentFile.relativeTo(scriptRoot).invariantSeparatorsPath
        val className = script.name.removeSuffix(".kts").replaceFirstChar(Char::uppercaseChar).replace('.', '_')
        "$packagePath/$className"
    }
    return the<SourceSetContainer>()["main"].output.classesDirs.asFileTree.matching {
        include(idlersPackages.map { "$it/**/*.class" })
        exclude(scriptClasses.flatMap { listOf("$it.class", "$it$*.class") })
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(idlersClasses())
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
    classDirectories.setFrom(idlersClasses())
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
    logger.lifecycle("IdleRS coverage: ${report.coverageText()}")
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
            "Coverage improved: lower the ratchet in luna/idlers.gradle.kts to " +
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
