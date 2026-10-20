package game.idle.autopilot.travel

import game.idle.flow.EntrancePassage
import game.idle.movement.ObjectForms
import game.obj.entrances.Entrance
import io.luna.game.model.Position

/** Luna's cave and dungeon entrances as a trip words them, from the cache and Luna's own [Entrance] table. */
object LunaEntrances {

    /** The animation Luna's entrances play to climb down; any other climb goes up. */
    private const val CLIMB_DOWN = 827

    /**
     * The entrance of [id] on [at] in the words of the form the player sees ([forms]): its first option, the one Luna's
     * entrances answer, and its name; null when they see no option on it.
     */
    fun passage(id: Int, at: Position, forms: ObjectForms = ObjectForms.SERVER): EntrancePassage? {
        val shown = forms.clickable(id) ?: return null
        return EntrancePassage.of(shown.actions.first(), shown.name, climbsDown = Entrance.forObject(id, at)?.animation == CLIMB_DOWN)
    }
}
