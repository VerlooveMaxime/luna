package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The three forms a builder reason takes; that every kind's reasons fit the slot is `ReasonsFitTest`. */
class ReasonsTest {

    @Test
    fun `a row with nothing picked is needed by its label`() {
        assertEquals("Needs a tree", Reasons.needs("Tree"))
    }

    @Test
    fun `a row of several is needed without an article`() {
        assertEquals("Needs raw food", Reasons.needs("Raw food", countable = false))
    }

    @Test
    fun `a value no option has is not one of the row`() {
        assertEquals("'palm' is not a tree", Reasons.notA("Tree", "palm"))
    }

    @Test
    fun `a value of a row of several is not that`() {
        assertEquals("'1511' is not food", Reasons.notA("Food", "1511", countable = false))
    }

    @Test
    fun `a row's rule names the row`() {
        assertEquals("Within takes 1 to 32 tiles", Reasons.takes("Within", "1 to 32 tiles"))
    }

    @Test
    fun `a plural row takes a plural verb`() {
        assertEquals("Kills take 1 or more", Reasons.takes("Kills", "1 or more"))
    }

    @Test
    fun `a broken rule quotes the text that broke it`() {
        assertEquals("Until takes 1 to 100 percent, not 'half'", Reasons.takes("Until", "1 to 100 percent", "half"))
    }

    @Test
    fun `a vowel takes an`() {
        assertEquals("Needs an item", Reasons.needs("Item"))
    }

    @Test
    fun `a consonant takes a`() {
        assertEquals("a", Reasons.article("tree"))
    }

    @Test
    fun `a word spelt out takes the article of its first letter's name`() {
        assertEquals("Needs an npc", Reasons.needs("Npc"))
    }

    @Test
    fun `a word spelt out from a letter named with a consonant takes a`() {
        assertEquals("a", Reasons.article("pvp"))
    }

    @Test
    fun `a word whose vowel is y is not spelt out`() {
        assertEquals("a", Reasons.article("fly"))
    }

    @Test
    fun `the first word of a phrase decides its article`() {
        assertEquals("'banker' is not an npc to fight", Reasons.notA("Npc to fight", "banker"))
    }
}
