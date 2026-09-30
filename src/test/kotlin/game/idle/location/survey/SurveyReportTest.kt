package game.idle.location.survey

import api.bot.zone.Zone
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class SurveyReportTest {

    private val draynorRegion = 12338
    private val date = LocalDate.of(2026, 9, 30)

    private val draynorWillows = surveyMap(
        placed(3085, 3233, Tree.WILLOW),
        placed(3087, 3233, Tree.WILLOW),
        booths = listOf(Tile(3091, 3242)),
    )

    private fun report(map: SurveyMap, zoneNames: Map<Int, String> = mapOf(draynorRegion to "draynor"), maxRows: Int = 100) =
        SurveyReport(map, TreeSurvey(map), zoneNames, date, maxRows)

    private fun firstRow(text: String): String = text.lines()[8]

    @Test
    fun `the header names the kind, the date and the counts`() {
        val lines = report(draynorWillows).format(Tree.WILLOW).lines()

        assertEquals(
            listOf("Tree survey: willow, 2026-09-30", "willow trees in the cache: 2, candidates: 1."),
            lines.take(2),
        )
    }

    @Test
    fun `a row shows rank, anchor, radius, count, bank with its distance, region and zone`() {
        assertEquals(
            "  1  3086,3233,0      1       2  3091,3242,0 (9)       12338  draynor",
            firstRow(report(draynorWillows).format(Tree.WILLOW)),
        )
    }

    @Test
    fun `a candidate with no booth on its floor and outside every zone shows dashes`() {
        val map = surveyMap(placed(3085, 3233, Tree.WILLOW), placed(3087, 3233, Tree.WILLOW))

        assertEquals(
            "  1  3086,3233,0      1       2  -                     12338  -",
            firstRow(report(map, zoneNames = emptyMap()).format(Tree.WILLOW)),
        )
    }

    @Test
    fun `other kinds close the row, most first`() {
        val map = surveyMap(
            placed(3085, 3233, Tree.WILLOW),
            placed(3087, 3233, Tree.WILLOW),
            placed(3086, 3234, Tree.OAK),
            placed(3086, 3232),
            placed(3085, 3232),
        )

        assertEquals("normal 2, oak 1", firstRow(report(map).format(Tree.WILLOW)).substringAfter("draynor").trim())
    }

    @Test
    fun `rows past the maximum are cut and counted`() {
        val map = surveyMap(
            placed(3085, 3233, Tree.WILLOW),
            placed(3087, 3233, Tree.WILLOW),
            placed(3185, 3233, Tree.WILLOW),
            placed(3187, 3233, Tree.WILLOW),
        )

        val lines = report(map, maxRows = 1).format(Tree.WILLOW).trimEnd().lines()

        assertEquals(listOf(10, "1 more candidates not shown."), listOf(lines.size, lines.last()))
    }

    @Test
    fun `write puts one file per tree kind into a new directory`(@TempDir dir: Path) {
        val written = SurveyReport(draynorWillows, TreeSurvey(draynorWillows), emptyMap(), date).write(dir.resolve("survey"))

        assertEquals(Tree.entries.map { "${it.name.lowercase()}.txt" }, written.map { it.fileName.toString() })
    }

    @Test
    fun `a written file holds the report of its kind`(@TempDir dir: Path) {
        val written = report(draynorWillows).write(dir).single { it.fileName.toString() == "willow.txt" }

        assertEquals(report(draynorWillows).format(Tree.WILLOW), Files.readString(written))
    }

    @Test
    fun `a safe zone is named in lower case`() {
        assertEquals("draynor", zoneNames(Zone.entries)[draynorRegion])
    }

    @Test
    fun `an unsafe zone is marked`() {
        assertEquals("wilderness (unsafe)", zoneNames(Zone.entries)[Zone.WILDERNESS.regions.first()])
    }
}
