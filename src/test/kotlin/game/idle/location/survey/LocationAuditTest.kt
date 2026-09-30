package game.idle.location.survey

import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocationAuditTest {

    @Test
    fun `a location with its trees and its booth has no problems`() {
        val map = surveyMap(placed(100, 100), placed(104, 96), booths = listOf(Tile(110, 100)))

        assertEquals(emptyList<String>(), LocationAudit(map).problems(location(bank = Tile(110, 100))))
    }

    @Test
    fun `too few trees of a named kind is a problem`() {
        val map = surveyMap(placed(100, 100), placed(106, 100))

        assertEquals(
            listOf("Location 'spot' has 1 normal trees within 5 tiles of its anchor, expected at least 2"),
            LocationAudit(map).problems(location()),
        )
    }

    @Test
    fun `a named kind that does not grow there is a problem`() {
        val map = surveyMap(placed(100, 100, Tree.OAK), placed(101, 100, Tree.OAK))

        assertEquals(
            listOf("Location 'spot' has 0 willow trees within 5 tiles of its anchor, expected at least 2"),
            LocationAudit(map).problems(location(trees = listOf("oak", "willow"))),
        )
    }

    @Test
    fun `a bank tile without a booth is a problem`() {
        val map = surveyMap(placed(100, 100), placed(101, 100), booths = listOf(Tile(110, 101)))

        assertEquals(
            listOf("Location 'spot' has no bank booth at 110,100,0"),
            LocationAudit(map).problems(location(bank = Tile(110, 100))),
        )
    }
}
