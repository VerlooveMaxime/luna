package game.idle.autopilot.making

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MakeActivityTest {

    private val dough = Recipe(2307, "bread dough", 1933, 1929)
    private val ingredients = MakeView(useSlot = 0, onSlot = 1, windowOpen = false, productOption = null)
    private val window = MakeView(useSlot = 0, onSlot = 1, windowOpen = true, productOption = 0)
    private val empty = MakeView(useSlot = null, onSlot = 1, windowOpen = false, productOption = null)

    private val maker = FakeMaker(ingredients)
    private val activity = MakeActivity(maker, dough)

    @Test
    fun `the two ingredients are combined`() {
        activity.act()

        assertEquals(listOf("use 0 on 1"), maker.steps)
    }

    @Test
    fun `the make window is answered with the product, as many as possible`() {
        maker.view = window

        activity.act()

        assertEquals(listOf("choose 0 x28"), maker.steps)
    }

    @Test
    fun `with an amount the window is answered with what is left to make`() {
        val counting = MakeActivity(maker, dough, amount = 5)
        counting.act()
        maker.products = 2
        maker.view = window

        counting.act()

        assertEquals(listOf("use 0 on 1", "choose 0 x3"), maker.steps)
    }

    @Test
    fun `a window that does not offer the product blocks once with a message`() {
        maker.view = window.copy(productOption = null)

        activity.act()
        activity.act()

        assertEquals(listOf("tell Autopilot: this window does not make bread dough."), maker.steps)
    }

    @Test
    fun `without an amount the step ends once an ingredient runs out after making some`() {
        activity.act()
        maker.products = 3
        maker.view = empty

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `with nothing to combine from the start the step waits and says so once`() {
        maker.view = empty

        activity.act()
        activity.act()

        assertFalse(activity.isDone())
        assertEquals(listOf("tell Autopilot: you have nothing to make bread dough with."), maker.steps)
    }

    @Test
    fun `missing the item to use it on is nothing to combine too`() {
        maker.view = ingredients.copy(onSlot = null)

        activity.act()

        assertEquals(listOf("tell Autopilot: you have nothing to make bread dough with."), maker.steps)
    }

    @Test
    fun `combining twice with nothing made blocks once instead of trying forever`() {
        activity.act()
        activity.act()
        activity.act()
        activity.act()

        assertEquals(listOf("use 0 on 1", "tell Autopilot: you cannot make bread dough yet."), maker.steps)
    }

    @Test
    fun `combining again after something was made is progress`() {
        activity.act()
        maker.products = 1

        activity.act()

        assertEquals(listOf("use 0 on 1", "use 0 on 1"), maker.steps)
    }

    @Test
    fun `with an amount the step stops making and ends once that many were made`() {
        val counting = MakeActivity(maker, dough, amount = 2)
        counting.act()
        maker.products = 2

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("use 0 on 1", "stop"), maker.steps)
    }

    @Test
    fun `making that reaches the amount stops being busy so the step can stop it`() {
        val counting = MakeActivity(maker, dough, amount = 1)
        counting.act()
        maker.busy = true

        assertTrue(counting.isBusy())

        maker.products = 1

        assertFalse(counting.isBusy())
    }

    @Test
    fun `before its first act a step follows the maker's busy`() {
        maker.busy = true

        assertTrue(MakeActivity(maker, dough, amount = 1).isBusy())
        assertFalse(MakeActivity(FakeMaker(ingredients), dough).isBusy())
    }
}
