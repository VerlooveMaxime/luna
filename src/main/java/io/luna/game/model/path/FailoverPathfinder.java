package io.luna.game.model.path;

import io.luna.game.model.Locatable;

/**
 * Searches with a first pathfinder, and gives the search to a second one when the first can't do it or finds no route.
 */
final class FailoverPathfinder<T extends Locatable> extends GamePathfinder<T> {

    private final GamePathfinder<T> first;
    private final GamePathfinder<T> second;

    FailoverPathfinder(GamePathfinder<T> first, GamePathfinder<T> second) {
        super(first.collisionManager);
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean supports(T origin, T target) {
        return first.supports(origin, target) || second.supports(origin, target);
    }

    @Override
    public PathResult<T> find(T origin, T target) {
        if (first.supports(origin, target)) {
            PathResult<T> result = first.find(origin, target);
            if (result.getType() != PathResultType.FAILED) {
                return result;
            }
        }
        return second.find(origin, target);
    }
}
