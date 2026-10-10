package game.idle.autopilot.rest

import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RestStepTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a rest step reads the player's hitpoints`() {
        val player = TestWorld.login("rester", Position(3200, 3200))
        val hitpoints = player.skill(Skill.HITPOINTS)
        hitpoints.level = hitpoints.staticLevel - 1
        val activity = RestStep(100).activity(player, Tile(3200, 3200))

        val waiting = activity.isDone()
        hitpoints.level = hitpoints.staticLevel

        assertEquals(listOf(false, true), listOf(waiting, activity.isDone()))
    }
}
