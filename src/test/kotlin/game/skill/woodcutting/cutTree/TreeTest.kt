package game.skill.woodcutting.cutTree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Luna shipped other values; IdleRS keeps the 2006 ones, so an upstream cherry-pick cannot bring them back quietly. */
class TreeTest {

    @Test
    fun `each tree gives its 2006 experience per log`() {
        val expected = mapOf(
            Tree.NORMAL to 25.0,
            Tree.OAK to 37.5,
            Tree.WILLOW to 67.5,
            Tree.TEAK to 85.0,
            Tree.MAPLE to 100.0,
            Tree.MAHOGANY to 125.0,
            Tree.YEW to 175.0,
            Tree.MAGIC to 250.0,
        )

        assertEquals(expected, Tree.entries.associateWith { it.exp })
    }
}
