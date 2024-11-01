package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
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

    class Jump {
        Location src;
        Direction dir;
        int steps;

        Location landing;

        public Jump(Location src, Direction dir, int steps, Location landing) {
            this.src = src;
            this.dir = dir;
            this.steps = steps;
            this.landing = landing;
        }
    }

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


        // consider jumps
        Location[] hyperJumps = uc.senseObjects(MapObject.HYPERJUMP, c.actionRange);
        if (hyperJumps.length > 0) {
            int bestDist = c.loc.distanceSquared(c.destination);
            // baseline
            for (Direction dir: c.allDirs) {
                if (!c.canMove(dir)) {
                    continue;
                }
                Location check = c.loc.add(dir);
                int dist = check.distanceSquared(c.destination);
                if (dist < bestDist) {
                    bestDist = dist;
                    // don't even need to save the dir
                }
            }

            Jump bestJump = null;
            for (Location jumpLoc: hyperJumps) {
                if (!jumpLoc.equals(c.loc) && !c.canMove(c.loc.directionTo(jumpLoc))) {
                    continue;
                }
                Jump jump = findBestJump(jumpLoc, bestDist);
                if (jump == null) {
                    continue;
                }
                bestDist = jump.landing.distanceSquared(c.destination);
                bestJump = jump;
            }

            if (bestJump != null) {
                if (!bestJump.src.equals(c.loc)) {
                    c.move(c.loc.directionTo(bestJump.src));
                }
                uc.performAction(ActionType.JUMP, bestJump.dir, bestJump.steps);
                c.logger.log("jump %s -> %s", c.loc, bestJump.landing);
                c.loc = uc.getLocation();
                parallelSearchAllowed = true;
                parallelSearchValid = false;

            }
        }

        if (!c.canMove()) {
            return;
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

        c.uc.drawLineDebug(c.uc.getLocation(), c.destination, 255, 255, 0);

    }

    private Jump findBestJump(Location src, int baselineDist) {
        int bestDist = baselineDist;
        Direction bestDir = Direction.ZERO;
        Location bestLanding = src;
        int bestStep = 0;
        for (Direction dir: c.fourDirs) {
            for (int j = GameConstants.MAX_JUMP; j > 0; j--) {
                Location landing = src.add(dir.dx * j, dir.dy * j);
                int dist = landing.distanceSquared(c.destination);
                if (dist < baselineDist && uc.canSenseLocation(landing) && !uc.isOutOfMap(landing)
                        && !c.isObstacle(uc.senseTileType(landing))
                        && uc.senseAstronaut(landing) == null
                        && uc.senseStructure(landing) == null) {
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestDir = dir;
                        bestStep = j;
                        bestLanding = landing;
                    }
                }
            }
        }
        if (bestDir != Direction.ZERO) {
            return new Jump(src, bestDir, bestStep, bestLanding);
        }
        return null;
    }

    private int getPathFinderOptimalStepCount() {
        if (c.s.immediateBlockers > 4) {
            return 6;
        }

        return 12;
    }


}
