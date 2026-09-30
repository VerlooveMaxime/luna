package game.idle.movement

import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigateToReachTest {

    private val boothId = 2213
    private val boothTile = Position(3200, 3200)
    private val deadTreeId = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `the player walks to the nearest tile from which the object is reached`() {
        val player = TestWorld.login("reach", Position(3195, 3200))
        val booth = TestWorld.place(boothId, boothTile)

        navigateToReach(player, booth)

        assertEquals(Position(3199, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `a nearer diagonal tile is passed over because the reach check refuses it`() {
        val player = TestWorld.login("reach", Position(3205, 3205))
        val booth = TestWorld.place(boothId, boothTile)

        navigateToReach(player, booth)

        assertEquals(Position(3200, 3201), player.navigator.currentTarget)
    }

    @Test
    fun `a blocked tile is passed over for the next one in reach`() {
        val player = TestWorld.login("reach", Position(3195, 3200))
        val booth = TestWorld.place(boothId, boothTile)
        TestWorld.place(deadTreeId, Position(3199, 3200))

        navigateToReach(player, booth)

        assertEquals(Position(3200, 3199), player.navigator.currentTarget)
    }

    @Test
    fun `with every tile around it blocked the player walks to the object itself`() {
        val player = TestWorld.login("reach", Position(3195, 3200))
        val booth = TestWorld.place(boothId, boothTile)
        approachTiles(boothTile, size = 1, from = player.position).forEach { TestWorld.place(deadTreeId, it) }

        navigateToReach(player, booth)

        assertEquals(booth, player.navigator.currentTarget)
    }
}
