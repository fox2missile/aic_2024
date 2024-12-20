package kyuu.pathfinder;


import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastIntSet;

public class NaivePathFinder {

    C c;
    Location target = null;
    boolean[] impassable = null;
    BugNav bugNav;

    Team team;

    final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHWEST,
            Direction.WEST,
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.EAST,
            Direction.NORTHEAST,
            Direction.ZERO
    };

    public NaivePathFinder(C c) {
        this.c = c;
        bugNav = new BugNav();
        bugNav.rotateRight = c.uc.getRandomDouble() < 0.5;
        team = c.uc.getTeam();
    }

    void setImpassable(boolean[] imp) {
        impassable = imp;
    }

    public void initTurn() {
        impassable = new boolean[directions.length];
    }

    boolean canMove(Direction dir) {
        if (!c.canMove(dir))
            return false;
        if (impassable[dir.ordinal()])
            return false;
        return true;
    }

    public void move(Location loc) {
        if (!c.canMove())
            return;
        target = loc;
        if (!bugNav.move())
            greedyPath();
    }

    Location getGreedyTargetAway(Location loc) {
        Direction opp_direction = c.uc.getLocation().directionTo(loc).opposite();
        Direction[] dirs = new Direction[] { opp_direction, opp_direction.rotateLeft(), opp_direction.rotateRight() };
        return c.uc.getLocation().add(getFirstMovableDir(dirs));
    }

    Direction getFirstMovableDir(Direction[] dirs) {
        for (Direction dir : dirs) {
            if (c.canMove(dir)) {
                return dir;
            }
        }
        return Direction.ZERO;
    }

    final double eps = 1e-5;

    void greedyPath() {
        try {
            Location myLoc = c.uc.getLocation();
            Direction bestDir = null;
            double bestEstimation = 0;
            int bestEstimationDist = 0;
            for (Direction dir : directions) {
                Location newLoc = myLoc.add(dir);
                if (c.uc.isOutOfMap(newLoc))
                    continue;

                if (!canMove(dir))
                    continue;
                if (!strictlyCloser(newLoc, myLoc, target))
                    continue;

                int newDist = newLoc.distanceSquared(target);

                // TODO: Better estimation?
                double estimation = 1 + Vector2D.chebysevDistance(target, newLoc);
                if (bestDir == null || estimation < bestEstimation - eps
                        || (Math.abs(estimation - bestEstimation) <= 2 * eps && newDist < bestEstimationDist)) {
                    bestEstimation = estimation;
                    bestDir = dir;
                    bestEstimationDist = newDist;
                }
            }
            if (bestDir != null)
                c.move(bestDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean strictlyCloser(Location newLoc, Location oldLoc, Location target) {
        int dOld = Vector2D.chebysevDistance(target, oldLoc), dNew = Vector2D.chebysevDistance(target, newLoc);
        if (dOld < dNew)
            return false;
        if (dNew < dOld)
            return true;
        return target.distanceSquared(newLoc) < target.distanceSquared(oldLoc);
    }

    class BugNav {

        final int INF = 1000000;
        final int MAX_MAP_SIZE = GameConstants.MAX_MAP_SIZE;
        final int MAX_CODE = 262144; // 2^18, 2^17 = 2^6 * 2^6 * 2^4 * 2 plus some buffer

        boolean shouldGuessRotation = true; // if I should guess which rotation is the best
        boolean rotateRight = true; // if I should rotate right or left
        Location lastObstacleFound = null; // latest obstacle I've found in my way
        int minDistToEnemy = INF; // minimum distance I've been to the enemy while going around an obstacle
        Location prevTarget = null; // previous target
        boolean hasRotatedAvoidingCurrent = false; // if I've rotated due to the current obstacle
        FastIntSet visited;
        int id = 12620;

        public BugNav() {
            visited = new FastIntSet(MAX_CODE);
        }

        boolean move() {
            try {
                // different target? ==> previous data does not help!
                if (prevTarget == null || target.distanceSquared(prevTarget) > 0) {
                    // robot.debug.println("New target: " + target, id);
                    resetPathfinding();
                }

                // If I'm at a minimum distance to the target, I'm free!
                Location myLoc = c.uc.getLocation();
                // int d = myLoc.distanceSquared(target);
                int d = Vector2D.chebysevDistance(myLoc, target);
                if (d < minDistToEnemy) {
                    // robot.debug.println("New min dist: " + d + " Old: " + minDistToEnemy, id);
                    resetPathfinding();
                    minDistToEnemy = d;
                }

                int code = getCode();

                if (visited.check(code)) {
                    // robot.debug.println("Contains code", id);
                    resetPathfinding();
                }
                visited.add(code);

                // Update data
                prevTarget = target;

                // If there's an obstacle I try to go around it [until I'm free] instead of
                // going to the target directly
                Direction dir = myLoc.directionTo(target);
                if (lastObstacleFound != null) {
                    // robot.debug.println("Last obstacle found: " + lastObstacleFound, id);
                    dir = myLoc.directionTo(lastObstacleFound);
                }
                if (canMove(dir)) {
                    // robot.debug.println("can move: " + dir, id);
                    resetPathfinding();
                }

                // I rotate clockwise or counterclockwise (depends on 'rotateRight'). If I try
                // to go out of the map I change the orientation
                // Note that we have to try at most 16 times since we can swap orientation in
                // the middle of the loop. (It can be done more efficiently)
                for (int i = 8; i-- > 0;) {
                    Location newLoc = myLoc.add(dir);
                    if (canMove(dir)) {
                        c.move(dir);
                        // robot.debug.println("Moving in dir: " + dir, id);
                        return true;
                    }


                    if (c.uc.isOutOfMap(newLoc)) {
                        rotateRight = !rotateRight;
                    } else if (c.s.hasObstacle(newLoc)) {
                        // This is the latest obstacle found if
                        // - I can't move there
                        // - It's on the map
                        // - It's not passable
                        lastObstacleFound = newLoc;
                        // robot.debug.println("Found obstacle: " + lastObstacleFound, id);
                    } else if (shouldGuessRotation) {
                        // Guessing rotation not on an obstacle is different.
                        shouldGuessRotation = false;
                        // robot.debug.println("Guessing rot dir", id);
                        // Rotate left and right and find the first dir that you can move in
                        Direction dirL = dir;
                        for (int j = 8; j-- > 0;) {
                            if (canMove(dirL))
                                break;
                            dirL = dirL.rotateLeft();
                        }

                        Direction dirR = dir;
                        for (int j = 8; j-- > 0;) {
                            if (canMove(dirR))
                                break;
                            dirR = dirR.rotateRight();
                        }

                        // Check which results in a location closer to the target
                        Location locL = myLoc.add(dirL);
                        Location locR = myLoc.add(dirR);

                        int lDist = Vector2D.chebysevDistance(target, locL);
                        int rDist = Vector2D.chebysevDistance(target, locR);
                        int lDistSq = target.distanceSquared(locL);
                        int rDistSq = target.distanceSquared(locR);

                        if (lDist < rDist) {
                            rotateRight = false;
                        } else if (rDist < lDist) {
                            rotateRight = true;
                        } else {
                            rotateRight = rDistSq < lDistSq;
                        }

                        // robot.debug.println("Guessed: " + rotateRight, id);
                    }

                    if (rotateRight)
                        dir = dir.rotateRight();
                    else
                        dir = dir.rotateLeft();
                }

                if (canMove(dir))
                    c.move(dir);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // robot.debug.println("Last exit", id);
            return true;
        }

        // clear some of the previous data
        void resetPathfinding() {
            // robot.debug.println("Resetting pathfinding", id);
            lastObstacleFound = null;
            minDistToEnemy = INF;
            visited.clear();
            shouldGuessRotation = true;
            hasRotatedAvoidingCurrent = false;
        }

        int getCode() {
            int x = c.uc.getLocation().x % GameConstants.MAX_MAP_SIZE;
            int y = c.uc.getLocation().y % GameConstants.MAX_MAP_SIZE;
            Direction obstacleDir = c.uc.getLocation().directionTo(target);
            if (lastObstacleFound != null)
                obstacleDir = c.uc.getLocation().directionTo(lastObstacleFound);
            int bit = rotateRight ? 1 : 0;
            return (((((x << 6) | y) << 4) | obstacleDir.ordinal()) << 1) | bit;
        }
    }

}