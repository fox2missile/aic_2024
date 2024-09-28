package kyuu.pathfinder;

import aic2024.user.*;
import kyuu.C;


public abstract class PathFinder {

    protected final C c;
    protected Location mPlan;

    PathFinder(C c) {
        this.c = c;
        this.mPlan = null;
    }

    public boolean hasPlan(Location destination) {
        return mPlan != null && mPlan.equals(destination);
    }
    public abstract void plan(Location destination);

    public abstract void executeMove();
}
