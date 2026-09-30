package game.idle.location.survey

import api.bot.zone.Zone
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

/** Plain-text tables of [SpotCandidate]s, one file per tree kind, for a human to pick location rows from. */
class SurveyReport(
    private val map: SurveyMap,
    private val survey: TreeSurvey,
    private val zoneNames: Map<Int, String>,
    private val date: LocalDate,
    private val maxRows: Int = MAX_ROWS,
) {

    /** Writes `<kind>.txt` for every [Tree] into [dir] and returns the files. */
    fun write(dir: Path): List<Path> {
        Files.createDirectories(dir)
        return Tree.entries.map { tree -> Files.writeString(dir.resolve("${tree.label}.txt"), format(tree)) }
    }

    fun format(tree: Tree): String {
        val candidates = survey.candidates(tree)
        val header = listOf(
            "Tree survey: ${tree.label}, $date",
            "${tree.label} trees in the cache: ${map.trees.count { it.tree == tree }}, candidates: ${candidates.size}.",
            "Densest first: each candidate is the untaken ${tree.label} trees near the one with the most of them.",
            "anchor: centre of those trees. r: smallest radius holding them all (the data file's radius).",
            "bank: nearest booth or chest on the floor, in straight-line tiles (walking is at least as far).",
            "other trees: the other kinds standing within r of the anchor.",
            "",
            ROW.format("#", "anchor", "r", tree.label, "bank", "region", "zone", "other trees"),
        )
        val rows = candidates.take(maxRows).mapIndexed { index, candidate -> row(index + 1, candidate) }
        val cut = candidates.size - rows.size
        val footer = if (cut > 0) listOf("$cut more candidates not shown.") else emptyList()
        return (header + rows + footer).joinToString(separator = "\n", postfix = "\n")
    }

    private fun row(rank: Int, candidate: SpotCandidate): String {
        val bank = candidate.bank?.let { "${it.booth.text} (${it.distance})" } ?: "-"
        val region = candidate.anchor.toPosition().region.id
        val others = candidate.trees.filterKeys { it != candidate.tree }.entries
            .sortedByDescending { it.value }
            .joinToString { "${it.key.label} ${it.value}" }
        return ROW.format(
            rank, candidate.anchor.text, candidate.radius, candidate.count, bank, region,
            zoneNames[region] ?: "-", others,
        ).trimEnd()
    }

    companion object {
        const val MAX_ROWS = 100
        private const val ROW = "%3s  %-14s %3s %7s  %-20s %6s  %-26s %s"
    }
}

/** Region id to the lower-case name of the bot [Zone] holding it, marked when the zone is not safe. */
fun zoneNames(zones: List<Zone>): Map<Int, String> =
    zones.flatMap { zone ->
        val name = if (zone.safe) zone.name.lowercase() else "${zone.name.lowercase()} (unsafe)"
        zone.regions.map { it to name }
    }.toMap()

/** `./gradlew treeSurvey`: writes the report for every tree kind into the directory given as the only argument. */
fun main(args: Array<String>) {
    val map = CacheMap.map
    val report = SurveyReport(map, TreeSurvey(map), zoneNames(Zone.entries), LocalDate.now())
    report.write(Paths.get(args.single())).forEach(::println)
}

private val Tree.label: String get() = name.lowercase()

private val Tile.text: String get() = "$x,$y,$z"
