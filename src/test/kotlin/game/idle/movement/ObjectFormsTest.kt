package game.idle.movement

import com.google.common.collect.ImmutableList
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.VarpChildDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.OptionalInt

class ObjectFormsTest {

    private val cave = 1
    private val wall = 2
    private val shutBoulder = 3
    private val openBoulder = 4
    private val shutFirst = 5
    private val openFirst = 6
    private val hiddenFirst = 7
    private val noForms = 8
    private val unknown = 99

    private fun definition(id: Int, option: String = "null", forms: VarpChildDefinition? = null) =
        GameObjectDefinition(id, "Object $id", "", 1, 1, 0, false, false, true, OptionalInt.empty(), ImmutableList.of(option, "null"), false, forms)

    private fun forms(vararg ids: Int) = VarpChildDefinition(-1, 1, ImmutableList.copyOf(ids.toList()))

    private val definitions = listOf(
        definition(cave, "Enter"),
        definition(wall),
        definition(shutBoulder),
        definition(openBoulder, "Enter"),
        definition(shutFirst, forms = forms(shutBoulder, openBoulder)),
        definition(openFirst, forms = forms(openBoulder, shutBoulder)),
        definition(hiddenFirst, forms = forms(-1, openBoulder)),
        definition(noForms, "Open", forms = VarpChildDefinition(-1, -1, ImmutableList.of())),
    ).associateBy { it.id() }

    private val objects = ObjectForms(definitions::get)

    @Test
    fun `an object with forms changes form`() {
        assertTrue(objects.changesForm(shutFirst))
    }

    @Test
    fun `an object without forms keeps its form`() {
        assertFalse(objects.changesForm(cave))
    }

    @Test
    fun `an object with an empty list of forms keeps its form`() {
        assertFalse(objects.changesForm(noForms))
    }

    @Test
    fun `an object the cache does not hold keeps its form`() {
        assertFalse(objects.changesForm(unknown))
    }

    @Test
    fun `an object that keeps its form is clicked as it is`() {
        assertEquals(cave, objects.clickable(cave)?.id())
    }

    @Test
    fun `an object that keeps its form and shows no option cannot be clicked`() {
        assertNull(objects.clickable(wall))
    }

    @Test
    fun `an object whose first form shows an option is clicked in that form`() {
        assertEquals(openBoulder, objects.clickable(openFirst)?.id())
    }

    @Test
    fun `an object whose first form shows no option cannot be clicked`() {
        assertNull(objects.clickable(shutFirst))
    }

    @Test
    fun `an object whose first form is nothing cannot be clicked`() {
        assertNull(objects.clickable(hiddenFirst))
    }

    @Test
    fun `an object the cache does not hold cannot be clicked`() {
        assertNull(objects.clickable(unknown))
    }

    @Test
    fun `an object shown with a first option shows an option`() {
        assertTrue(objects.showsOption(noForms))
    }

    @Test
    fun `an object shown without one shows none`() {
        assertFalse(objects.showsOption(shutFirst))
    }
}
