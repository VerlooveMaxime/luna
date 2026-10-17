package game.idle.movement

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.Spend
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TravelAllowanceTest {

    private val law = 563
    private val air = 556

    private fun teleport(id: String, kind: TeleportKind) =
        TeleportDefinition(id, kind, key = null, option = null, destination = Position(3200, 3200), nodeId = id, cost = 5)

    private val teleports = listOf(
        teleport("varrock", TeleportKind.SPELL),
        teleport("glory_edgeville", TeleportKind.JEWELLERY),
        teleport("home", TeleportKind.HOME),
    )

    private val carried = CapabilitySnapshot(
        skillLevels = mapOf(6 to 25),
        items = mapOf(COINS to 40, law to 3, air to 9),
        flags = setOf("ship_search_entrana"),
        wildernessLevel = 2,
        usableTeleports = setOf("varrock", "glory_edgeville", "home", "unknown"),
        charges = mapOf("AMULET_OF_GLORY" to 4),
        teleportSpends = mapOf(
            "varrock" to Spend(items = mapOf(law to 1, air to 3)),
            "glory_edgeville" to Spend(charges = mapOf("AMULET_OF_GLORY" to 1)),
        ),
    )

    private val everything = TravelAllowance(coins = true, runes = true, charges = true)

    private fun restrict(allowance: TravelAllowance, reserved: Spend = Spend.NONE) = allowance.restrict(carried, reserved, teleports)

    @Test
    fun `a flow allows nothing to be spent unless it says so`() {
        assertEquals(TravelAllowance(coins = false, runes = false, charges = false), TravelAllowance.NONE)
    }

    @Test
    fun `coins are hidden from the planner unless fares and tolls are allowed`() {
        assertEquals(0, restrict(TravelAllowance.NONE).coins)
    }

    @Test
    fun `coins are kept when fares and tolls are allowed`() {
        assertEquals(40, restrict(TravelAllowance(coins = true)).coins)
    }

    @Test
    fun `nothing is teleported by unless runes or charges are allowed`() {
        assertEquals(emptySet<String>(), restrict(TravelAllowance.NONE).usableTeleports)
    }

    @Test
    fun `spells are used when runes are allowed`() {
        assertEquals(setOf("varrock"), restrict(TravelAllowance(runes = true)).usableTeleports)
    }

    @Test
    fun `jewellery is used when charges are allowed`() {
        assertEquals(setOf("glory_edgeville"), restrict(TravelAllowance(charges = true)).usableTeleports)
    }

    @Test
    fun `neither the home teleport nor one the web lacks is ever used`() {
        assertEquals(setOf("varrock", "glory_edgeville"), restrict(everything).usableTeleports)
    }

    @Test
    fun `what a teleport spends is known only for the teleports used`() {
        assertEquals(setOf("varrock"), restrict(TravelAllowance(runes = true)).teleportSpends.keys)
    }

    @Test
    fun `runes a reflex needs are held back from the trip`() {
        assertEquals(2, restrict(everything, Spend(items = mapOf(law to 1))).items[law])
    }

    @Test
    fun `an item held back whole is gone from the trip`() {
        assertEquals(null, restrict(everything, Spend(items = mapOf(law to 3))).items[law])
    }

    @Test
    fun `charges a reflex needs are held back from the trip`() {
        assertEquals(mapOf("AMULET_OF_GLORY" to 3), restrict(everything, Spend(charges = mapOf("AMULET_OF_GLORY" to 1))).charges)
    }

    @Test
    fun `jewellery held back whole leaves no charges`() {
        assertEquals(emptyMap<String, Int>(), restrict(everything, Spend(charges = mapOf("AMULET_OF_GLORY" to 4))).charges)
    }

    @Test
    fun `charges are hidden from the planner unless they are allowed`() {
        assertEquals(emptyMap<String, Int>(), restrict(TravelAllowance(coins = true, runes = true)).charges)
    }

    @Test
    fun `levels, unlocks and the wilderness level are the player's own`() {
        val restricted = restrict(TravelAllowance.NONE)
        assertEquals(Triple(carried.skillLevels, carried.flags, 2), Triple(restricted.skillLevels, restricted.flags, restricted.wildernessLevel))
    }
}
