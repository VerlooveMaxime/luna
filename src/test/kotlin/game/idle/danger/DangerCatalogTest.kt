package game.idle.danger

import game.idle.location.Area
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DangerCatalogTest {

    private val darkWizard = Aggression.BelowCombat(15)
    private val wizardSpawn = AggressiveSpawn("Dark wizard", 7, Tile(3084, 3236), reach = 10, darkWizard)
    private val otherWizard = wizardSpawn.copy(tile = Tile(3085, 3238))
    private val blackKnight = AggressiveSpawn("Black Knight", 33, Tile(3101, 3220), reach = 10, Aggression.BelowCombat(67))
    private val scorpion = AggressiveSpawn("Scorpion", 14, Tile(3090, 3230), reach = 10, Aggression.Always)
    private val upstairs = AggressiveSpawn("Ghost", 19, Tile(3087, 3229, 1), reach = 10, Aggression.Always)
    private val catalog = DangerCatalog(listOf(blackKnight, wizardSpawn, otherWizard, scorpion, upstairs))

    private val willows = Area(Tile(3087, 3229), radius = 10)

    @Test
    fun `the threats to an area are the kinds of npc whose reach touches it, the lowest level first, once each`() {
        assertEquals(
            listOf(Threat("Dark wizard", 7, darkWizard), Threat("Scorpion", 14, Aggression.Always), Threat("Black Knight", 33, Aggression.BelowCombat(67))),
            catalog.threats(willows),
        )
    }

    @Test
    fun `a spawn whose reach stops short of the area is no threat`() {
        assertEquals(listOf(Threat("Dark wizard", 7, darkWizard), Threat("Scorpion", 14, Aggression.Always)), catalog.threats(Area(Tile(3087, 3229), radius = 3)))
    }

    @Test
    fun `a spawn on another floor is no threat`() {
        assertEquals(emptyList<Threat>(), catalog.threats(Area(Tile(3087, 3229, 2), radius = 10)))
    }

    @Test
    fun `a tile alone is an area of radius 0`() {
        assertEquals(listOf("Dark wizard"), catalog.threats(Area(Tile(3074, 3240), radius = 0)).map { it.name })
    }

    @Test
    fun `every kind of aggressive npc is listed once`() {
        assertEquals(listOf("Dark wizard", "Scorpion", "Ghost", "Black Knight"), catalog.all.map { it.name })
    }

    @Test
    fun `an npc attacking below a combat level threatens a player under it, not one at it`() {
        assertEquals(listOf(true, false), listOf(darkWizard.threatens(14), darkWizard.threatens(15)))
    }

    @Test
    fun `an npc attacking any level threatens everyone`() {
        assertEquals(true, Aggression.Always.threatens(126))
    }

    @Test
    fun `an npc held off by a god's items threatens everyone, what is worn not judged`() {
        assertEquals(true, Aggression.UnlessWorn("Zamorak").threatens(126))
    }

    @Test
    fun `a threat's band line names the npc, its level and whom it attacks`() {
        assertEquals("Dark wizard wanders here, level 7, attacks below combat 15", Threat("Dark wizard", 7, darkWizard).line)
    }

    @Test
    fun `a threat's slot line is shorter`() {
        assertEquals("Dark wizard, level 7, attacks below 15", Threat("Dark wizard", 7, darkWizard).short)
    }

    @Test
    fun `an npc attacking any level says so on both lines`() {
        val threat = Threat("Scorpion", 14, Aggression.Always)

        assertEquals(listOf("Scorpion wanders here, level 14, attacks any level", "Scorpion, level 14, attacks any level"), listOf(threat.line, threat.short))
    }

    @Test
    fun `an npc held off by a god's items names the god`() {
        val threat = Threat("Zamorak wizard", 45, Aggression.UnlessWorn("Zamorak"))

        assertEquals(
            listOf("Zamorak wizard wanders here, level 45, attacks unless a Zamorak item is worn", "Zamorak wizard, level 45, attacks unless Zamorak is worn"),
            listOf(threat.line, threat.short),
        )
    }

    @Test
    fun `a threat judges the player as its aggression does`() {
        assertEquals(listOf(true, false), listOf(Threat("Dark wizard", 7, darkWizard).threatens(3), Threat("Dark wizard", 7, darkWizard).threatens(17)))
    }

    @Test
    fun `a spawn's area reaches as far as its reach`() {
        assertEquals(Area(Tile(3084, 3236), 10), wizardSpawn.area)
    }
}
