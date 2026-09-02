package me.bill.fakePlayerPlugin.fakeplayer.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;

import de.bsommerfeld.pathetic.api.pathing.INeighborStrategy;
import de.bsommerfeld.pathetic.api.pathing.NeighborStrategies;
import de.bsommerfeld.pathetic.api.wrapper.PathVector;

/**
 * The neighbor set used for all bot navigation searches: every normal 3x3x3 walking/climbing/step
 * move ({@link NeighborStrategies#DIAGONAL_3D}) plus a small, fixed set of level 2-block "gap jump"
 * vectors for parkour.
 *
 * <p>The gap vectors are always offered to the search - {@link ParkourGapValidator} is what actually
 * rejects them unless {@link BotPathfinder.PathOptions#parkour()} is enabled for the search, so a
 * single shared {@link de.bsommerfeld.pathetic.api.pathing.Pathfinder} can serve both parkour and
 * non-parkour bots.
 */
final class NavNeighbors {

    static final INeighborStrategy STRATEGY;

    static {
        List<PathVector> offsets = new ArrayList<>();
        NeighborStrategies.DIAGONAL_3D.getOffsets().forEach(offsets::add);

        // Straight 2-block gap jumps.
        offsets.add(new PathVector(2, 0, 0));
        offsets.add(new PathVector(-2, 0, 0));
        offsets.add(new PathVector(0, 0, 2));
        offsets.add(new PathVector(0, 0, -2));

        // Diagonal-ish gap jumps (2 long one axis, 1 the other).
        offsets.add(new PathVector(2, 0, 1));
        offsets.add(new PathVector(2, 0, -1));
        offsets.add(new PathVector(-2, 0, 1));
        offsets.add(new PathVector(-2, 0, -1));
        offsets.add(new PathVector(1, 0, 2));
        offsets.add(new PathVector(-1, 0, 2));
        offsets.add(new PathVector(1, 0, -2));
        offsets.add(new PathVector(-1, 0, -2));

        List<PathVector> immutable = Collections.unmodifiableList(offsets);
        STRATEGY = () -> immutable;
    }

    private NavNeighbors() {}
}
