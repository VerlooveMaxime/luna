package game.idle.autopilot.smithing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmithingActivityTest {

    private val near = anvil(3201, 3200, distance = 2)
    private val far = anvil(3210, 3200, distance = 9)
    private val inReach = near.copy(distance = 0, usableFromHere = true)

    private val smither = FakeSmither(smithingView(listOf(inReach)))
    private val activity = SmithingActivity(smither, level = 1)

    @Test
    fun `the bar is used on an anvil in reach`() {
        activity.act()

        assertEquals(listOf("use 0 on 3201,3200"), smither.steps)
    }

    @Test
    fun `with the window open the item is picked, as many as the window offers without an amount`() {
        smither.view = smithingView(listOf(inReach), windowOpen = true)

        activity.act()

        assertEquals(listOf("choose 10"), smither.steps)
    }

    @Test
    fun `with an amount the window's largest count that does not pass what is left is picked`() {
        smither.view = smithingView(listOf(inReach), windowOpen = true)

        SmithingActivity(smither, level = 1, amount = 7).act()

        assertEquals(listOf("choose 5"), smither.steps)
    }

    @Test
    fun `a single item left is picked once`() {
        smither.view = smithingView(listOf(inReach), windowOpen = true)

        SmithingActivity(smither, level = 1, amount = 1).act()

        assertEquals(listOf("choose 1"), smither.steps)
    }

    @Test
    fun `out of reach, the nearest anvil is walked to`() {
        smither.view = smithingView(listOf(far, near))

        activity.act()

        assertEquals(listOf("walk to 3201,3200"), smither.steps)
    }

    @Test
    fun `an anvil in reach comes before a nearer one out of reach`() {
        smither.view = smithingView(listOf(near.copy(distance = 1), inReach.copy(position = far.position)))

        activity.act()

        assertEquals(listOf("use 0 on 3210,3200"), smither.steps)
    }

    @Test
    fun `equally near anvils are picked west to east, then south to north`() {
        smither.view = smithingView(listOf(anvil(3201, 3205, distance = 2), near))

        activity.act()

        assertEquals(listOf("walk to 3201,3200"), smither.steps)
    }

    @Test
    fun `a block is reported with its reason`() {
        smither.view = smithingView(listOf(inReach), barSlot = null)

        activity.act()

        assertEquals(SmithingBlockedReason.NOTHING_TO_SMITH.message, activity.blocked())
    }

    @Test
    fun `a block clears on the next decision that is not one`() {
        smither.view = smithingView(listOf(inReach), barSlot = null)
        activity.act()
        smither.view = smithingView(listOf(inReach))

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `with no bars from the start the step waits and says why`() {
        smither.view = smithingView(listOf(inReach), barSlot = null)

        activity.act()

        assertFalse(activity.isDone())
        assertEquals(SmithingBlockedReason.NOTHING_TO_SMITH.message, activity.blocked())
    }

    @Test
    fun `the step is done once the bars run out after smithing some`() {
        activity.act()
        smither.made = 2
        smither.view = smithingView(listOf(inReach), barSlot = null)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `without a hammer nothing is smithed`() {
        smither.view = smithingView(listOf(inReach), hasHammer = false)

        activity.act()

        assertEquals(SmithingBlockedReason.NO_HAMMER.message, activity.blocked())
    }

    @Test
    fun `an item above the player's level is refused`() {
        val highLevel = SmithingActivity(smither, level = 15)

        highLevel.act()

        assertEquals(SmithingBlockedReason.LEVEL_TOO_LOW.message, highLevel.blocked())
    }

    @Test
    fun `with no anvil in sight away from the work spot, the player walks back`() {
        smither.view = smithingView(emptyList(), atLocation = false)

        activity.act()

        assertEquals(listOf("walk to location"), smither.steps)
    }

    @Test
    fun `with no anvil at the work spot the step says why once`() {
        smither.view = smithingView(emptyList())
        activity.act()

        activity.act()

        assertEquals(SmithingBlockedReason.NO_ANVIL.message, activity.blocked())
    }

    @Test
    fun `an anvil used twice without the window opening is passed over`() {
        smither.view = smithingView(listOf(inReach, far))
        activity.act()

        activity.act()

        assertEquals(listOf("use 0 on 3201,3200", "walk to 3210,3200"), smither.steps)
    }

    @Test
    fun `an anvil used again after making something is not passed over`() {
        activity.act()
        smither.made = 1

        activity.act()

        assertEquals(listOf("use 0 on 3201,3200", "use 0 on 3201,3200"), smither.steps)
    }

    @Test
    fun `an anvil walked to and then in reach is used`() {
        smither.view = smithingView(listOf(near))
        activity.act()
        smither.view = smithingView(listOf(inReach))

        activity.act()

        assertEquals(listOf("walk to 3201,3200", "use 0 on 3201,3200"), smither.steps)
    }

    @Test
    fun `another anvil right after one that made nothing is not a retry`() {
        activity.act()
        smither.view = smithingView(listOf(inReach.copy(position = far.position)))

        activity.act()

        assertEquals(listOf("use 0 on 3201,3200", "use 0 on 3210,3200"), smither.steps)
    }

    @Test
    fun `a step with an amount stops the smithing and ends once that many were made`() {
        val counting = SmithingActivity(smither, level = 1, amount = 2)
        counting.act()
        smither.made = 2

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("use 0 on 3201,3200", "stop"), smither.steps)
    }

    @Test
    fun `an idle smither leaves the step free to act`() {
        assertFalse(activity.isBusy())
    }

    @Test
    fun `a busy smither keeps the step busy`() {
        smither.busy = true

        assertTrue(activity.isBusy())
    }

    @Test
    fun `a step whose amount is reached is not busy, so it can stop the smithing`() {
        val counting = SmithingActivity(smither, level = 1, amount = 1)
        counting.act()
        smither.busy = true
        smither.made = 1

        assertFalse(counting.isBusy())
    }

    @Test
    fun `before its first look a step with an amount is busy while the smither is`() {
        smither.busy = true

        assertTrue(SmithingActivity(smither, level = 1, amount = 1).isBusy())
    }

    @Test
    fun `before its first act the step has done nothing`() {
        assertEquals(0, SmithingActivity(smither, level = 1).amountDone())
    }

    @Test
    fun `its amount done is the items smithed since the step began`() {
        val counting = SmithingActivity(smither, level = 1)
        counting.act()
        smither.made = 2

        assertEquals(2, counting.amountDone())
    }
}
