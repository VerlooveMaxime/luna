package game.idle

import api.plugin.dsl.plugin

plugin {
    name = "IdleRS"
    description =
        """
        All IdleRS-owned content: the server-side autopilot that trains skills for a logged-in player,
        stage progression and resets. Kept in one plugin so upstream Luna packages stay untouched.
        """
    version = "0.1"
    authors += "Maxime"
}
