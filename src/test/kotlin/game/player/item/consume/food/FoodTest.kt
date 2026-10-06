package game.player.item.consume.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoodTest {

    @Test
    fun `bread can be eaten`() {
        assertEquals(Food.BREAD, Food.ID_TO_FOOD[2309])
    }

    @Test
    fun `bread heals 5 hitpoints as in 2006`() {
        assertEquals(5, Food.BREAD.heal)
    }
}
