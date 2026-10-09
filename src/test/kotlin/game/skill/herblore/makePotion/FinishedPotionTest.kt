package game.skill.herblore.makePotion

import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Luna's potion rows against the cache; IdleRS fixed the attack potion, both brews and antifire. */
class FinishedPotionTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun name(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    /** The unfinished potion by id (the cache names them all alike), the secondary and the potion by name. */
    private fun recipe(potion: FinishedPotion): Triple<Int, String, String> = Triple(potion.unf, name(potion.secondary), name(potion.id))

    @Test
    fun `every potion is mixed from an unfinished potion`() {
        assertEquals(setOf("Unfinished potion"), FinishedPotion.ALL.map { name(it.unf) }.toSet())
    }

    @Test
    fun `no two potions make the same item`() {
        assertEquals(FinishedPotion.ALL.size, FinishedPotion.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `an attack potion is a guam potion and an eye of newt`() {
        assertEquals(Triple(91, "Eye of newt", "Attack potion(3)"), recipe(FinishedPotion.ATTACK_POTION))
    }

    @Test
    fun `a zamorak brew is a torstol potion and jangerberries`() {
        assertEquals(Triple(111, "Jangerberries", "Zamorak brew(3)"), recipe(FinishedPotion.ZAMORAK_BREW))
    }

    @Test
    fun `a saradomin brew is a toadflax potion and a crushed nest`() {
        assertEquals(Triple(3002, "Crushed nest", "Saradomin brew(3)"), recipe(FinishedPotion.SARADOMIN_BREW))
    }

    @Test
    fun `an antifire potion takes dragon scale dust`() {
        assertEquals("Dragon scale dust", name(FinishedPotion.ANTIFIRE_POTION.secondary))
    }
}
