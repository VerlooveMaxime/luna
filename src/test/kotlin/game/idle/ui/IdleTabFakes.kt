package game.idle.ui

import game.idle.flow.SavedFlows

/** Every glyph 5 px wide, so a text measures five times its length. */
val EVEN_FONT = ClientFont(IntArray(256) { 5 })

/** The Idle tab for [slots] saved-flow slots, its texts measured in [EVEN_FONT]. */
fun idleTab(slots: Int = 2): IdleTab = IdleTab(SavedFlows(slots), EVEN_FONT, EVEN_FONT)
