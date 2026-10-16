package game.idle.flow

import engine.combat.prayer.CombatPrayer
import engine.combat.prayer.CombatPrayer.PROTECT_FROM_MELEE
import engine.combat.prayer.CombatPrayer.THICK_SKIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrayerHoldTest {

    private val body = FakeReflexBody()
    private val hold = PrayerHold()

    private fun lit(vararg prayers: Pair<Int, CombatPrayer>) {
        prayers.forEach { (number, prayer) ->
            body.lit += prayer
            hold.lit(prayer, number)
        }
    }

    @Test
    fun `nothing is pending while every held prayer is wanted`() {
        lit(1 to THICK_SKIN)

        assertFalse(hold.pending(body) { setOf(THICK_SKIN) })
    }

    @Test
    fun `a held prayer on that no reflex wants is pending`() {
        lit(1 to THICK_SKIN)

        assertTrue(hold.pending(body) { emptySet() })
    }

    @Test
    fun `a held prayer that went out elsewhere is not pending`() {
        lit(1 to THICK_SKIN)
        body.lit -= THICK_SKIN

        assertFalse(hold.pending(body) { emptySet() })
    }

    @Test
    fun `with nothing held on, what is wanted is not worked out`() {
        assertFalse(hold.pending(body) { error("not needed") })
    }

    @Test
    fun `releasing puts out the held prayers no reflex wants, naming the reflex that lit each`() {
        lit(1 to THICK_SKIN, 2 to PROTECT_FROM_MELEE)

        assertEquals(listOf(PrayerChange(2, PROTECT_FROM_MELEE, on = false)), hold.release(body) { setOf(THICK_SKIN) })
        assertEquals(setOf(THICK_SKIN), body.lit)
    }

    @Test
    fun `with nothing held a release works nothing out`() {
        assertEquals(emptyList<PrayerChange>(), hold.release(body) { error("not needed") })
    }

    @Test
    fun `a held prayer that went out elsewhere is let go without a word`() {
        lit(1 to THICK_SKIN)
        body.lit -= THICK_SKIN

        assertEquals(emptyList<PrayerChange>(), hold.release(body) { emptySet() })
        assertFalse(hold.pending(body) { emptySet() })
    }

    @Test
    fun `a released prayer is no longer held`() {
        lit(1 to THICK_SKIN)
        hold.release(body) { emptySet() }
        body.lit += THICK_SKIN

        assertFalse(hold.pending(body) { emptySet() })
    }

    @Test
    fun `a prayer on that the flow did not light is never put out`() {
        body.lit += THICK_SKIN

        assertEquals(emptyList<PrayerChange>(), hold.releaseAll(body))
        assertEquals(setOf(THICK_SKIN), body.lit)
    }

    @Test
    fun `the run's end puts out every held prayer`() {
        lit(1 to THICK_SKIN, 3 to PROTECT_FROM_MELEE)

        assertEquals(listOf(PrayerChange(1, THICK_SKIN, on = false), PrayerChange(3, PROTECT_FROM_MELEE, on = false)), hold.releaseAll(body))
        assertEquals(emptySet<Any>(), body.lit)
    }

    @Test
    fun `lighting a prayer again keeps the latest reflex`() {
        lit(1 to THICK_SKIN)
        hold.lit(THICK_SKIN, 2)

        assertEquals(listOf(PrayerChange(2, THICK_SKIN, on = false)), hold.releaseAll(body))
    }
}
