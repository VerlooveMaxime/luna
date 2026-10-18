package game.idle.movement

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.PlanLeg
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
    fun `each Travel choice is allowed by its own switch`() {
        val allowance = TravelAllowance(coins = true, runes = false, charges = true)

        assertEquals(listOf(true, false, true), TravelChoice.entries.map(allowance::allows))
    }

    @Test
    fun `switching a choice on changes that choice alone`() {
        assertEquals(
            listOf(TravelAllowance(coins = true), TravelAllowance(runes = true), TravelAllowance(charges = true)),
            TravelChoice.entries.map { TravelAllowance.NONE.with(it, allowed = true) },
        )
    }

    @Test
    fun `switching a choice off changes that choice alone`() {
        assertEquals(
            listOf(TravelAllowance(runes = true, charges = true), TravelAllowance(coins = true, charges = true), TravelAllowance(coins = true, runes = true)),
            TravelChoice.entries.map { TravelAllowance.ALL.with(it, allowed = false) },
        )
    }

    @Test
    fun `everything allowed is every choice`() {
        assertEquals(listOf(true, true, true), TravelChoice.entries.map(TravelAllowance.ALL::allows))
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

    private fun snapshot(items: Map<Int, Int> = emptyMap(), usable: Set<String> = emptySet(), charges: Map<String, Int> = emptyMap()) =
        CapabilitySnapshot(items = items, usableTeleports = usable, charges = charges)

    @Test
    fun `what everything allowed would spend is looked at when the flow keeps coins back`() {
        assertEquals(mapOf(COINS to 40), TravelAllowance.NONE.beyond(snapshot(items = mapOf(COINS to 40)), Spend.NONE, teleports)?.items)
    }

    @Test
    fun `what everything allowed would spend is looked at when the flow keeps a teleport back`() {
        assertEquals(setOf("varrock"), TravelAllowance.NONE.beyond(snapshot(usable = setOf("varrock")), Spend.NONE, teleports)?.usableTeleports)
    }

    @Test
    fun `what everything allowed would spend is looked at when the flow keeps charges back`() {
        assertEquals(mapOf("AMULET_OF_GLORY" to 4), TravelAllowance.NONE.beyond(snapshot(charges = mapOf("AMULET_OF_GLORY" to 4)), Spend.NONE, teleports)?.charges)
    }

    @Test
    fun `nothing kept back that the player could pay with is no reason to look further`() {
        assertEquals(null, TravelAllowance(coins = true).beyond(snapshot(items = mapOf(COINS to 40, law to 3)), Spend.NONE, teleports))
    }

    @Test
    fun `what a reflex holds back is held back either way`() {
        assertEquals(null, TravelAllowance.NONE.beyond(snapshot(items = mapOf(COINS to 10)), Spend(items = mapOf(COINS to 10)), teleports))
    }

    private fun leg(type: EdgeType, teleport: TeleportDefinition? = null, coins: Int = 0) =
        PlanLeg(type, Position(3200, 3200), Position(3210, 3200), cost = 1.0, teleport = teleport, requirements = Requirements(coins = coins))

    @Test
    fun `a toll and a fare are paid with coins`() {
        assertEquals(listOf(TravelChoice.COINS, TravelChoice.COINS), listOf(TravelChoice.of(leg(EdgeType.CROSSING, coins = 10)), TravelChoice.of(leg(EdgeType.SHIP, coins = 30))))
    }

    @Test
    fun `a free ship costs nothing`() {
        assertEquals(null, TravelChoice.of(leg(EdgeType.SHIP)))
    }

    @Test
    fun `a spell is cast with runes, jewellery rubbed for a charge`() {
        assertEquals(
            listOf(TravelChoice.RUNES, TravelChoice.CHARGES),
            listOf(TravelChoice.of(leg(EdgeType.TELEPORT, teleports[0])), TravelChoice.of(leg(EdgeType.TELEPORT, teleports[1]))),
        )
    }

    @Test
    fun `walks, passages and the home teleport cost nothing`() {
        assertEquals(listOf(null, null, null), listOf(TravelChoice.of(leg(EdgeType.WALK)), TravelChoice.of(leg(EdgeType.DOOR)), TravelChoice.of(leg(EdgeType.TELEPORT, teleports[2]))))
    }
}
