package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.pathfinder.ParallelSearch;

public class MoveTask extends Task {

//    private final PathFinder mPathFinder;
    private final ParallelSearch defaultSearch;
    private final int MAX_TRACE = 3;

    private Direction back;
    public MoveTask(C c) {
        super(c);
//        mPathFinder = new Bug2PathFinder(c);
        defaultSearch = ParallelSearch.getDefaultSearch(c);
        back = null;
    }

    @Override
    public void run() {
        if (c.destination == null || !c.canMove()) return;
        if (c.destination.equals(c.uc.getLocation())) return;
        Direction dir = defaultSearch.calculateBestDirection(c.destination, back, getPathFinderOptimalStepCount());
        if (c.canMove(dir)) {
            c.move(dir);
            c.loc = c.uc.getLocation();
            back = dir.opposite();
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
