package api.bot.webwalk.plan

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BotCapabilitiesTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private val varrock = TeleportDefinition("spell_varrock", TeleportKind.SPELL, "VARROCK", null, Position(3212, 3423), "t:varrock", cost = 5)

    /** A plain player, not a bot, with Magic 25 and the runes of a Varrock teleport. */
    private fun mage() = TestWorld.login("capable", Position(3200, 3200)).apply {
        rights = PlayerRights.PLAYER
        skill(Skill.MAGIC).staticLevel = 25
        skill(Skill.MAGIC).level = 25
        inventory.add(Item(556, 3))
        inventory.add(Item(554))
        inventory.add(Item(563))
        inventory.add(Item(CapabilitySnapshot.COINS, 25))
    }

    @Test
    fun `a player who is not a bot is read as a bot is`() {
        val snapshot = BotCapabilities.snapshot(mage())
        assertEquals(listOf(25, 25), listOf(snapshot.coins, snapshot.skillLevels[Skill.MAGIC]))
    }

    @Test
    fun `a player who is not a bot can plan by the teleports they can cast`() {
        assertEquals(setOf(varrock.id), BotCapabilities.snapshot(mage(), listOf(varrock)).usableTeleports)
    }
}
