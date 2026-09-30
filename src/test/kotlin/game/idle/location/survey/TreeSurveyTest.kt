package game.idle.location.survey

import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TreeSurveyTest {

    @Test
    fun `the densest group comes first`() {
        val map = surveyMap(placed(10, 10), placed(12, 10), placed(200, 200), placed(201, 200), placed(202, 200))

        val anchors = TreeSurvey(map, window = 5).candidates(Tree.NORMAL).map { it.anchor }

        assertEquals(listOf(Tile(201, 200), Tile(11, 10)), anchors)
    }

    @Test
    fun `a tree taken by one candidate does not seed or join another`() {
        val map = surveyMap(placed(10, 10), placed(14, 10), placed(18, 10), placed(22, 10))

        val counts = TreeSurvey(map, window = 4).candidates(Tree.NORMAL).map { it.anchor to it.count }

        assertEquals(listOf(Tile(14, 10) to 3), counts)
    }

    @Test
    fun `a group smaller than the minimum is no candidate`() {
        val map = surveyMap(placed(10, 10), placed(12, 10), placed(200, 200))

        assertEquals(1, TreeSurvey(map, window = 5, minTrees = 2).candidates(Tree.NORMAL).size)
    }

    @Test
    fun `the anchor is the rounded centre of the group`() {
        val map = surveyMap(placed(10, 10), placed(11, 10), placed(11, 13))

        assertEquals(Tile(11, 11), TreeSurvey(map, window = 5).candidates(Tree.NORMAL).single().anchor)
    }

    @Test
    fun `the radius is the smallest that holds the whole group`() {
        val map = surveyMap(placed(10, 10), placed(16, 12))

        assertEquals(3, TreeSurvey(map, window = 8).candidates(Tree.NORMAL).single().radius)
    }

    @Test
    fun `a lone tree still gets a radius the data file accepts`() {
        val map = surveyMap(placed(10, 10))

        assertEquals(1, TreeSurvey(map, minTrees = 1).candidates(Tree.NORMAL).single().radius)
    }

    @Test
    fun `a candidate counts every kind within its square`() {
        val map = surveyMap(placed(10, 10, Tree.YEW), placed(12, 10, Tree.YEW), placed(11, 11), placed(40, 40))

        assertEquals(mapOf(Tree.YEW to 2, Tree.NORMAL to 1), TreeSurvey(map).candidates(Tree.YEW).single().trees)
    }

    @Test
    fun `a candidate knows the nearest booth`() {
        val map = surveyMap(placed(10, 10), placed(12, 10), booths = listOf(Tile(21, 10)))

        assertEquals(BoothDistance(Tile(21, 10), distance = 10), TreeSurvey(map).candidates(Tree.NORMAL).single().bank)
    }

    @Test
    fun `trees on another floor never join a group`() {
        val map = surveyMap(placed(10, 10), placed(10, 10, z = 1))

        assertEquals(emptyList<SpotCandidate>(), TreeSurvey(map).candidates(Tree.NORMAL))
    }

    @Test
    fun `other kinds never seed a candidate`() {
        val map = surveyMap(placed(10, 10), placed(12, 10))

        assertEquals(emptyList<SpotCandidate>(), TreeSurvey(map).candidates(Tree.OAK))
    }

    @Test
    fun `the default window groups trees up to half the largest radius apart`() {
        val map = surveyMap(placed(100, 100), placed(116, 100), placed(133, 100))

        assertEquals(listOf(2), TreeSurvey(map).candidates(Tree.NORMAL).map { it.count })
    }
}
