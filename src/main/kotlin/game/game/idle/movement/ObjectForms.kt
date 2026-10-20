package game.idle.movement

import io.luna.game.model.def.GameObjectDefinition

/**
 * What a player sees of an object, as the 377 client picks it: the object itself, or for one that changes form by a
 * varbit or varp (a quest's boulder, a ladder only a ring shows) the form its value picks, nothing for a form of -1.
 * Luna stores none of these values, so every player sees the form at 0. A trip takes such an object only when the
 * player sees an option on it (Maxime, 2026-10-20); a quest that stores a value is read here.
 */
class ObjectForms(private val definition: (Int) -> GameObjectDefinition?) {

    /** Whether the object of [id] changes form by a varbit or varp. */
    fun changesForm(id: Int): Boolean = definition(id)?.varpDef?.childIdList.orEmpty().isNotEmpty()

    /**
     * The object of [id] as the player sees it when they see a first option on it, the one Luna's objects answer to a
     * trip's click; null otherwise.
     */
    fun clickable(id: Int): GameObjectDefinition? = shown(id)?.takeIf { it.actions.first() != NO_OPTION }

    /** Whether the player sees a first option on the object of [id]. */
    fun showsOption(id: Int): Boolean = clickable(id) != null

    /** The object of [id] as the player sees it, null when they see nothing there. */
    private fun shown(id: Int): GameObjectDefinition? {
        val shown = definition(id) ?: return null
        val forms = shown.varpDef?.childIdList.orEmpty()
        if (forms.isEmpty()) return shown
        val form: Int = forms[VALUE]
        return if (form == NONE) null else definition(form)
    }

    companion object {
        /** The value of every varbit and varp of an object's forms, which Luna never stores. */
        private const val VALUE = 0

        /** A form the client shows nothing for. */
        private const val NONE = -1

        /** Luna's decoder fills an option the cache leaves out with this word. */
        private const val NO_OPTION = "null"

        /** The forms of the server's objects, read from the cache's definitions. */
        val SERVER = ObjectForms { GameObjectDefinition.ALL.get(it).orElse(null) }
    }
}
