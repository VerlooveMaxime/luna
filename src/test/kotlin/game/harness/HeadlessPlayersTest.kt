package game.harness

import game.testworld.TestWorld
import io.luna.game.LogoutService.LogoutRequest
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.persistence.PlayerData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CompletableFuture

class HeadlessPlayersTest {

    private val config = HarnessConfig()
    private val spawn = Position(3200, 3200)

    private fun freshSaves(): (String) -> CompletableFuture<PlayerData?> = { CompletableFuture.completedFuture(null) }

    private fun headless(loadSave: (String) -> CompletableFuture<PlayerData?> = freshSaves()) =
        HeadlessPlayers(TestWorld.context, config, loadSave)

    private fun <T> result(future: CompletableFuture<T>): T = awaitWithin(future, Duration.ofSeconds(1))

    private fun refusal(future: () -> CompletableFuture<Player>): Int =
        assertThrows<HarnessException> { result(future()) }.status

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a headless player logs in and is active in the world`() {
        result(headless().login("agent_one"))

        assertEquals(EntityState.ACTIVE, TestWorld.world.getPlayer("agent_one").map { it.state }.orElseThrow())
    }

    @Test
    fun `a player logged in here is headless`() {
        assertTrue(result(headless().login("agent_one")).isHeadless)
    }

    @Test
    fun `the name is lower-cased before logging in`() {
        val player = result(headless().login("Agent_One"))

        assertEquals("agent_one", player.username)
    }

    @Test
    fun `the save that was loaded is applied`() {
        val template = TestWorld.login("template", Position(3210, 3215))
        val save = PlayerData("agent_one").save(template)

        val player = result(headless { CompletableFuture.completedFuture(save) }.login("agent_one"))

        assertEquals(Position(3210, 3215), player.position)
    }

    @Test
    fun `a player with a real client is not headless`() {
        assertFalse(TestWorld.loginWithRealClient("human", spawn).isHeadless)
    }

    @Test
    fun `login is refused while the world is full`() {
        val filler = TestWorld.login("filler", spawn)
        TestWorld.world.playerMap.putAll((1..TestWorld.world.players.capacity()).associate { "filler_$it" to filler })

        assertEquals(503, refusal { headless().login("agent_one") })
    }

    @Test
    fun `a bot's name is refused`() {
        TestWorld.bot("agent_bot", spawn)

        assertEquals(409, refusal { headless().login("agent_bot") })
    }

    @Test
    fun `a name already online is refused`() {
        TestWorld.login("agent_one", spawn)

        assertEquals(409, refusal { headless().login("agent_one") })
    }

    @Test
    fun `a name whose save is still pending is refused`() {
        val leaving = TestWorld.login("agent_one", spawn)
        TestWorld.world.logoutService.submit("agent_one", LogoutRequest(leaving))
        TestWorld.world.players.remove(leaving)

        assertEquals(409, refusal { headless().login("agent_one") })
    }

    @Test
    fun `a login that became impossible while the save loaded is refused`() {
        val save = CompletableFuture<PlayerData?>()
        val login = headless { save }.login("agent_one")
        TestWorld.login("agent_one", spawn)

        save.complete(null)

        assertEquals(409, assertThrows<HarnessException> { result(login) }.status)
    }

    @Test
    fun `logging out a headless player requests a forced logout`() {
        val player = result(headless().login("agent_one"))

        headless().logout(player)

        assertEquals(
            listOf(true, true),
            listOf(player.client.isForcedLogout, TestWorld.world.logoutService.hasRequest("agent_one")),
        )
    }

    @Test
    fun `a player with a real client cannot be logged out here`() {
        val human = TestWorld.loginWithRealClient("human", spawn)

        val headless = HeadlessPlayers(TestWorld.context, config)

        assertEquals(409, assertThrows<HarnessException> { headless.logout(human) }.status)
    }
}
