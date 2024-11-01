package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.pathfinder.NaivePathFinder;
import kyuu.pathfinder.ParallelSearch;

public class MoveTask extends Task {

//    private final PathFinder mPathFinder;
    NaivePathFinder naivePathFinder;
    private final ParallelSearch defaultSearch;
    private final int MAX_TRACE = 3;
    private Location prevDest;
    boolean parallelSearchValid;
    boolean parallelSearchAllowed;

    private Direction back;
    public MoveTask(C c) {
        super(c);
//        mPathFinder = new Bug2PathFinder(c);
        defaultSearch = ParallelSearch.getDefaultSearch(c);
        naivePathFinder = new NaivePathFinder(c);
        back = null;
        prevDest = null;
        parallelSearchValid = false;
        parallelSearchAllowed = true;
    }

    @Override
    public void run() {
        if (c.destination == null || !c.canMove()) return;
        if (c.destination.equals(c.uc.getLocation())) return;

        if (!c.destination.equals(prevDest)) {
            parallelSearchAllowed = true;
            parallelSearchValid = false;
        }

        if (parallelSearchAllowed) {
            Direction dir = (parallelSearchValid) ?
                    defaultSearch.nextBestDirection(c.loc, c.loc.directionTo(c.destination)) :
                    defaultSearch.calculateBestDirection(c.destination, back, getPathFinderOptimalStepCount());
            if (dir != null && c.canMove(dir)) {
                prevDest = c.destination;
                parallelSearchValid = true;
                c.move(dir);
                c.loc = c.uc.getLocation();
                back = dir.opposite();
            } else {
                naivePathFinder.initTurn();
                naivePathFinder.move(c.destination);
                parallelSearchValid = false;
                parallelSearchAllowed = false;
            }
        } else {
            naivePathFinder.initTurn();
            naivePathFinder.move(c.destination);
        }
//        mPathFinder.plan(c.destination);
//        mPathFinder.executeMove();
        c.uc.drawLineDebug(c.uc.getLocation(), c.destination, 255, 255, 0);

    }

    private int getPathFinderOptimalStepCount() {
        if (c.s.immediateBlockers > 4) {
            return 6;
        }

        return 12;
    }


}
