package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepAmountTest {

    private fun amount(text: String) = StepSettings("chop", mapOf("amount" to text))

    @Test
    fun `a step with an amount has that count`() {
        assertEquals(5, StepAmount.read(amount("5")))
    }

    @Test
    fun `a step without an amount does as much as it can`() {
        assertNull(StepAmount.read(StepSettings("chop")))
    }

    @Test
    fun `a count is 1 or more`() {
        assertEquals("the amount takes 1 or more, not '0'", assertThrows<FlowError> { StepAmount.read(amount("0")) }.message)
        assertEquals("the amount takes 1 or more, not 'lots'", assertThrows<FlowError> { StepAmount.read(amount("lots")) }.message)
    }

    @Test
    fun `a count has no cap but what Enter amount takes`() {
        assertEquals(2147483647, StepAmount.read(amount("2147483647")))
    }

    @Test
    fun `a count past what Enter amount takes is refused`() {
        assertEquals("the amount takes 1 or more, not '2147483648'", assertThrows<FlowError> { StepAmount.read(amount("2147483648")) }.message)
    }

    @Test
    fun `a count below 100,000 shows as it is`() {
        assertEquals("99999", StepAmount.short(99999))
    }

    @Test
    fun `a count from 100,000 shows in thousands, as the 377 shows a stack`() {
        assertEquals(listOf("100K", "9999K"), listOf(StepAmount.short(100_000), StepAmount.short(9_999_999)))
    }

    @Test
    fun `a count from ten million shows in millions`() {
        assertEquals("2147M", StepAmount.short(Int.MAX_VALUE))
    }

    @Test
    fun `a text that is not a count shows as it is`() {
        assertEquals("lots", StepAmount.short("lots"))
    }

    @Test
    fun `a summary writes a count before the resource and nothing for as much as it can`() {
        assertEquals(listOf("5 ", ""), listOf(StepAmount.prefix(amount("5")), StepAmount.prefix(StepSettings("chop"))))
    }

    @Test
    fun `the configure screen types an amount of 1 or more, a button removing it`() {
        assertEquals(
            listOf("Kills (left): typed amount 1..2147483647, button 'No end'"),
            described(listOf(StepAmount.field("Kills", unbounded = "no end", button = "No end"))),
        )
    }

    @Test
    fun `the configure screen shows an amount per lap, or the step's own word without one`() {
        val field = StepAmount.field("Amount", unbounded = "until the bag is full", button = "Full")

        assertEquals(listOf("5 per lap", "until the bag is full"), listOf(field.shown("5"), field.shown(null)))
    }

    @Test
    fun `the configure screen shows a big amount as a stack does`() {
        assertEquals("150K per lap", StepAmount.field("Amount", unbounded = "all of them", button = "All").shown("150000"))
    }

    @Test
    fun `a typed amount out of range is refused with its rule`() {
        assertEquals("the amount takes 1 or more", StepAmount.field("Amount", unbounded = "all of them", button = "All").rule)
    }

    @Test
    fun `a slot reads an amount per lap`() {
        assertEquals("5 per lap", StepAmount.detail(amount("5"), unbounded = "all of them"))
    }

    @Test
    fun `a slot reads an amount with what it counts`() {
        assertEquals("5 kills per lap", StepAmount.detail(amount("5"), unbounded = "no end", counted = "kills"))
    }

    @Test
    fun `a slot reads a big amount as a stack does`() {
        assertEquals(listOf("20M per lap", "20M kills per lap"), listOf(StepAmount.detail(amount("20000000"), "all"), StepAmount.detail(amount("20000000"), "no end", "kills")))
    }

    @Test
    fun `a slot reads no amount as the step's own word`() {
        assertEquals("no end", StepAmount.detail(StepSettings("fight"), unbounded = "no end", counted = "kills"))
    }
}
