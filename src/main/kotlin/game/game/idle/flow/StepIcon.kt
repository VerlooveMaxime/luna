package game.idle.flow

/**
 * The icon a kind of step shows in the flow builder (S01): a skill's icon from the skills tab, another sprite of the
 * client's media archive, or an item's inventory icon.
 */
sealed interface StepIcon {

    /** The icon the skills tab shows for Luna's skill [skill]. */
    data class Skill(val skill: Int) : StepIcon

    /** Sprite [index] of [name] in the client's media archive. */
    data class Media(val name: String, val index: Int) : StepIcon

    data class Item(val id: Int) : StepIcon
}
