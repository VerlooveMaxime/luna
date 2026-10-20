package game.idle.movement

import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.walk.BankView
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TripMeansTest {

    private val law = 563

    private fun snapshot(
        items: Map<Int, Int> = mapOf(COINS to 40),
        usable: Set<String> = setOf("varrock"),
        charges: Map<String, Int> = mapOf("AMULET_OF_GLORY" to 4),
        spends: Map<String, Spend> = mapOf("varrock" to Spend(items = mapOf(law to 1))),
    ) = CapabilitySnapshot(items = items, usableTeleports = usable, charges = charges, teleportSpends = spends)

    private fun means(
        carried: CapabilitySnapshot = snapshot(),
        tiers: Map<BankAccess, CapabilitySnapshot> = mapOf(BankAccess.COINS to snapshot(items = mapOf(COINS to 90))),
        view: BankView = BankView(freeSlots = 3),
    ) = TripMeans(carried, tiers, view)

    @Test
    fun `means counting on the same are the same`() {
        assertTrue(means().sameAs(means()))
    }

    @Test
    fun `means without the bank alike are the same`() {
        assertTrue(means(tiers = emptyMap()).sameAs(means(tiers = emptyMap())))
    }

    @Test
    fun `more carried is other means`() {
        assertFalse(means().sameAs(means(carried = snapshot(items = mapOf(COINS to 41)))))
    }

    @Test
    fun `another teleport usable is other means`() {
        assertFalse(means().sameAs(means(carried = snapshot(usable = emptySet()))))
    }

    @Test
    fun `other charges are other means`() {
        assertFalse(means().sameAs(means(carried = snapshot(charges = emptyMap()))))
    }

    @Test
    fun `a teleport spending otherwise is other means`() {
        assertFalse(means().sameAs(means(carried = snapshot(spends = emptyMap()))))
    }

    @Test
    fun `another tier of the bank is other means`() {
        assertFalse(means().sameAs(means(tiers = mapOf(BankAccess.ALL to snapshot(items = mapOf(COINS to 90))))))
    }

    @Test
    fun `the same tier counting more of the bank is other means`() {
        assertFalse(means().sameAs(means(tiers = mapOf(BankAccess.COINS to snapshot(items = mapOf(COINS to 91))))))
    }

    @Test
    fun `without the bank the bag's room does not matter`() {
        assertTrue(means(tiers = emptyMap()).sameAs(means(tiers = emptyMap(), view = BankView(freeSlots = 28))))
    }

    @Test
    fun `more room in the bag is other means`() {
        assertFalse(means().sameAs(means(view = BankView(freeSlots = 28))))
    }
}
