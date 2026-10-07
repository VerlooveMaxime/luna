package game.idle.autopilot.fighting

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FightingActivityTest {

    private val full = Health(10, 10)
    private val low = Health(4, 10)
    private val near = target(3100, 9510, distance = 2)
    private val far = target(3104, 9510, distance = 6)
    private val inReach = near.copy(distance = 0, usableFromHere = true)

    private fun target(x: Int, y: Int, distance: Int, usable: Boolean = false) =
        TargetCandidate(npcIndex = x, position = Position(x, y), distance, usable, approach = Position(x, y + 1))

    private fun view(
        health: Health = full,
        foodSlot: Int? = null,
        threats: Int = 0,
        atLocation: Boolean = true,
        targets: List<TargetCandidate> = listOf(near, far),
    ) = FightView(health, foodSlot, threats, atLocation, targets)

    private val fighter = FakeFighter(view())

    private fun activity(amount: Int? = null) = FightingActivity(fighter, eatBelow = 50, amount)

    private fun lowOnHealth(foodSlot: Int? = null, threats: Int = 0) {
        fighter.health = low
        fighter.view = view(health = low, foodSlot = foodSlot, threats = threats)
    }

    @Test
    fun `an npc in reach is attacked`() {
        fighter.view = view(targets = listOf(inReach))

        activity().act()

        assertEquals(listOf("attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `with no npc in reach the nearest is walked up to`() {
        activity().act()

        assertEquals(listOf("walk to 3100,9510"), fighter.steps)
    }

    @Test
    fun `an npc in reach is attacked before a nearer one out of reach`() {
        fighter.view = view(targets = listOf(near.copy(distance = 1), inReach.copy(position = Position(3108, 9510))))

        activity().act()

        assertEquals(listOf("attack 3108,9510"), fighter.steps)
    }

    @Test
    fun `npcs at the same distance are picked west to east, then south to north`() {
        fighter.view = view(targets = listOf(target(3102, 9513, 2), target(3102, 9512, 2), target(3101, 9515, 2)))

        activity().act()

        assertEquals(listOf("walk to 3101,9515"), fighter.steps)
    }

    @Test
    fun `away from the work spot with nothing to fight the player walks back`() {
        fighter.view = view(atLocation = false, targets = emptyList())

        activity().act()

        assertEquals(listOf("walk to location"), fighter.steps)
    }

    @Test
    fun `at the work spot with nothing to fight the player is told once`() {
        fighter.view = view(targets = emptyList())
        val activity = activity()

        activity.act()
        activity.act()

        assertEquals(listOf("tell ${FightBlockedReason.NO_TARGET.message}"), fighter.steps)
    }

    @Test
    fun `below the barrier food is eaten`() {
        lowOnHealth(foodSlot = 3)

        activity().act()

        assertEquals(listOf("eat 3"), fighter.steps)
    }

    @Test
    fun `at the barrier the player fights on`() {
        fighter.view = view(health = Health(5, 10), foodSlot = 3, targets = listOf(inReach))

        activity().act()

        assertEquals(listOf("attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `below the barrier with no food the player runs from what attacks them`() {
        lowOnHealth(threats = 1)

        activity().act()

        assertEquals(listOf("flee"), fighter.steps)
    }

    @Test
    fun `running away keeps the flow going`() {
        lowOnHealth(threats = 1)
        val activity = activity()

        activity.act()

        assertNull(activity.stopReason())
    }

    @Test
    fun `with nowhere to run the flow stops`() {
        lowOnHealth(threats = 1)
        fighter.canFlee = false
        val activity = activity()

        activity.act()

        assertEquals("Autopilot: stopped, out of food at 4/10 hitpoints.", activity.stopReason())
    }

    @Test
    fun `below the barrier with no food and nothing attacking the flow stops`() {
        lowOnHealth()
        val activity = activity()

        activity.act()

        assertEquals("Autopilot: stopped, out of food at 4/10 hitpoints.", activity.stopReason())
    }

    @Test
    fun `out of food nothing more is attacked`() {
        lowOnHealth()

        activity().act()

        assertEquals(emptyList<String>(), fighter.steps)
    }

    @Test
    fun `an idle player lets the step act`() {
        assertFalse(activity().isBusy())
    }

    @Test
    fun `a fight in progress keeps the player busy`() {
        fighter.busy = true

        assertTrue(activity().isBusy())
    }

    @Test
    fun `falling below the barrier mid-fight lets the step act`() {
        fighter.busy = true
        fighter.health = low

        assertFalse(activity().isBusy())
    }

    @Test
    fun `a fight that reached its kills lets the step act`() {
        fighter.busy = true
        fighter.kills = 3

        assertFalse(activity(amount = 3).isBusy())
    }

    @Test
    fun `reaching the kills stops the fighting`() {
        fighter.kills = 3

        activity(amount = 3).act()

        assertEquals(listOf("stop"), fighter.steps)
    }

    @Test
    fun `reaching the kills ends the step`() {
        fighter.kills = 3
        val activity = activity(amount = 3)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `short of its kills the step goes on`() {
        fighter.kills = 2
        val activity = activity(amount = 3)

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `a nonstop fight never ends`() {
        fighter.kills = 1000
        val activity = FightingActivity(fighter, eatBelow = 50)

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `an npc attacked twice with no kill in between is skipped`() {
        fighter.view = view(targets = listOf(inReach, far))
        val activity = activity()
        activity.act()

        activity.act()

        assertEquals(listOf("attack 3100,9510", "walk to 3104,9510"), fighter.steps)
    }

    @Test
    fun `attacking another npc next is no retry`() {
        fighter.view = view(targets = listOf(inReach))
        val activity = activity()
        activity.act()
        fighter.view = view(targets = listOf(inReach.copy(npcIndex = 1, position = Position(3101, 9510))))

        activity.act()

        assertEquals(listOf("attack 3100,9510", "attack 3101,9510"), fighter.steps)
    }

    @Test
    fun `an npc walked to twice with no kill in between is skipped`() {
        val activity = activity()
        activity.act()

        activity.act()

        assertEquals(listOf("walk to 3100,9510", "walk to 3104,9510"), fighter.steps)
    }

    @Test
    fun `an npc that moved since it was walked to is no retry`() {
        fighter.view = view(targets = listOf(near))
        val activity = activity()
        activity.act()
        fighter.view = view(targets = listOf(near.copy(position = Position(3100, 9511))))

        activity.act()

        assertEquals(listOf("walk to 3100,9510", "walk to 3100,9511"), fighter.steps)
    }

    @Test
    fun `once every npc around was skipped they all come back`() {
        fighter.view = view(targets = listOf(near))
        val activity = activity()
        activity.act()

        activity.act()

        assertEquals(listOf("walk to 3100,9510", "walk to 3100,9510"), fighter.steps)
    }

    @Test
    fun `an npc walked to and then in reach is attacked`() {
        val activity = activity()
        activity.act()
        fighter.view = view(targets = listOf(inReach, far))

        activity.act()

        assertEquals(listOf("walk to 3100,9510", "attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `an npc attacked again after a kill is not skipped`() {
        fighter.view = view(targets = listOf(inReach, far))
        val activity = activity()
        activity.act()
        fighter.kills = 1

        activity.act()

        assertEquals(listOf("attack 3100,9510", "attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `a kill brings skipped npcs back`() {
        fighter.view = view(targets = listOf(inReach, far))
        val activity = activity()
        activity.act()
        activity.act()
        fighter.kills = 1
        fighter.view = view(targets = listOf(inReach))

        activity.act()

        assertEquals("attack 3100,9510", fighter.steps.last())
    }

    @Test
    fun `an npc attacked after a meal is not a retry`() {
        fighter.view = view(targets = listOf(inReach))
        val activity = activity()
        activity.act()
        lowOnHealth(foodSlot = 0)
        activity.act()
        fighter.health = full
        fighter.view = view(targets = listOf(inReach))

        activity.act()

        assertEquals(listOf("attack 3100,9510", "eat 0", "attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `health below a share compares against full hitpoints`() {
        assertTrue(Health(7, 10).below(75))
    }
}
