package game.harness

/**
 * Builds the menu options a client would show from a definition's action list, keeping only those the server
 * handles (see [HarnessPackets]), so every listed option can be passed straight to a click endpoint.
 */
object MenuOptions {

    // Luna's cache decoders store an absent action as the literal string "null".
    private const val EMPTY_ACTION = "null"

    // The client blanks this action on objects, NPCs and ground items, but not on inventory items.
    private const val HIDDEN_ACTION = "hidden"

    // The client falls back to these labels when the definition leaves the slot empty.
    private const val DEFAULT_ITEM_OPTION_5 = "Drop"
    private const val DEFAULT_GROUND_OPTION_3 = "Take"

    fun forObject(actions: List<String>): List<OptionView> =
        numbered(withoutHidden(actions), HarnessPackets.OBJECT_OPCODES.keys)

    fun forNpc(actions: List<String>): List<OptionView> =
        numbered(withoutHidden(actions), HarnessPackets.NPC_OPCODES.keys)

    fun forInventoryItem(actions: List<String>): List<OptionView> =
        numbered(withDefault(actions, option = 5, label = DEFAULT_ITEM_OPTION_5), HarnessPackets.ITEM_OPCODES.keys)

    fun forGroundItem(actions: List<String>): List<OptionView> {
        val shown = withDefault(withoutHidden(actions), option = 3, label = DEFAULT_GROUND_OPTION_3)
        return numbered(shown, HarnessPackets.GROUND_ITEM_OPCODES.keys)
    }

    private fun numbered(actions: List<String>, handled: Set<Int>): List<OptionView> =
        actions.mapIndexedNotNull { index, action ->
            val option = index + 1
            if (option in handled && isPresent(action)) OptionView(option, action) else null
        }

    private fun withoutHidden(actions: List<String>): List<String> =
        actions.map { if (it.equals(HIDDEN_ACTION, ignoreCase = true)) EMPTY_ACTION else it }

    private fun withDefault(actions: List<String>, option: Int, label: String): List<String> {
        val padded = actions + List(maxOf(0, option - actions.size)) { EMPTY_ACTION }
        return padded.mapIndexed { index, action -> if (index == option - 1 && !isPresent(action)) label else action }
    }

    private fun isPresent(action: String): Boolean = action.isNotBlank() && action != EMPTY_ACTION
}
