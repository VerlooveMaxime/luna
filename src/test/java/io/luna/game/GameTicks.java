package io.luna.game;

import io.luna.game.model.World;

/** Drives a {@link GameService} that was never started, one tick at a time, from the test thread. */
public final class GameTicks {

    private GameTicks() {
    }

    /** Makes the calling thread the game thread, as the service's own startup does. */
    public static void adoptCallingThread(GameService service) {
        service.startUp();
    }

    /**
     * Forgets every logout request not yet finished. A finished one saves the player to {@code data/game/saved_players},
     * the real save directory, so tests drop them before the next tick can.
     */
    public static void dropLogoutRequests(World world) {
        world.getLogoutService().pending.clear();
    }

    /**
     * One full tick: the work queued through {@code sync}, then {@code World.process}. Refused while a logout request
     * is pending, because the tick would finish it and save the player to the real save directory.
     */
    public static void tick(GameService service) {
        var pendingLogouts = service.getContext().getWorld().getLogoutService().pending.keySet();
        if (!pendingLogouts.isEmpty()) {
            throw new IllegalStateException("a tick now would save " + pendingLogouts + " to data/game/saved_players");
        }
        service.runOneIteration();
    }
}
