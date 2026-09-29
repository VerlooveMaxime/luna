package game.harness

import api.plugin.dsl.plugin

plugin {
    name = "Agent harness"
    description =
        """
        Loopback HTTP API that lets agents and scripts log in headless players, read what a player sees and
        act for it, including for a human's real client session. Off unless data/idle/harness.jsonc or the
        IDLERS_HARNESS environment variable enables it.
        """
    version = "0.1"
    authors += "Maxime"
}
