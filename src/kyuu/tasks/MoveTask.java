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

    Location[] hyperJumps;

    Location[][] givenPaths;
    Location currentDestination;
    int pathIter;
    int givenPathsLength;
    int currentGivenPath;

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
        pathIter = -1;
        givenPaths = new Location[5][];
        givenPathsLength = 0;
        currentGivenPath = -1;
        hyperJumps = new Location[0];
        rdb.pathReceiver = (int __) -> {
            int length = uc.pollBroadcast().getMessage();
            int targetId = uc.pollBroadcast().getMessage();
            if (targetId != c.id) {
                uc.eraseBroadcastBuffer(length * 2);
                return;
            }
            givenPaths[givenPathsLength] = new Location[length];
            for (int i = 0; i < length; i++) {
                givenPaths[givenPathsLength][i] = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
            }
            givenPathsLength++;
        };
    }

    @Override
    public void run() {
        if (c.destination == null || !c.canMove()) return;
        if (c.destination.equals(c.uc.getLocation())) return;

        if (currentGivenPath == -1) {
            readjustGivenPath();
        }

        if (currentGivenPath != -1) {
            if (givenPaths[currentGivenPath][givenPaths[currentGivenPath].length - 1].distanceSquared(c.destination) > 8) {
                // destination changed
                readjustGivenPath();
            }
        }

        if (currentGivenPath != -1) {
            if (pathIter < givenPaths[currentGivenPath].length && c.loc.equals(givenPaths[currentGivenPath][pathIter])) {
                pathIter++;
            }
            revalidatePathIter(currentGivenPath);
        }
        



        if (!c.destination.equals(prevDest)) {
            parallelSearchAllowed = true;
            parallelSearchValid = false;
        }

        currentDestination = c.destination;
        if (currentGivenPath != -1) {
            revalidatePathIter(currentGivenPath);
        }
        if (currentGivenPath != -1) {
            currentDestination = givenPaths[currentGivenPath][pathIter];
        }


        // consider jumps
        hyperJumps = uc.senseObjects(MapObject.HYPERJUMP, c.actionRange);
        while (tryJump()) {}


        while (currentGivenPath != -1 && c.canMove() && pathIter < givenPaths[currentGivenPath].length && c.loc.distanceSquared(givenPaths[currentGivenPath][pathIter]) <= c.actionRange && c.canMove(c.loc.directionTo(givenPaths[currentGivenPath][pathIter]))) {
            c.move(c.loc.directionTo(givenPaths[currentGivenPath][pathIter++]));
            revalidatePathIter(currentGivenPath);
            if (currentGivenPath != -1) {
                currentDestination = givenPaths[currentGivenPath][pathIter];
            }
        }

        if (!c.canMove()) {
            while (tryJump()) {}
            return;
        }

        if (parallelSearchAllowed) {
            Direction dir = (parallelSearchValid) ?
                    defaultSearch.nextBestDirection(c.loc, c.loc.directionTo(currentDestination)) :
                    defaultSearch.calculateBestDirection(currentDestination, back, getPathFinderOptimalStepCount());
            if (dir != null && c.canMove(dir)) {
                prevDest = currentDestination;
                parallelSearchValid = true;
                c.move(dir);
                c.loc = c.uc.getLocation();
                back = dir.opposite();
            } else {
                naivePathFinder.initTurn();
                naivePathFinder.move(currentDestination);
                parallelSearchValid = false;
                parallelSearchAllowed = false;
            }
        } else {
            naivePathFinder.initTurn();
            naivePathFinder.move(currentDestination);
        }

        while (tryJump()) {}
        c.uc.drawLineDebug(c.uc.getLocation(), currentDestination, 255, 255, 0);

    }

    private boolean tryJump() {
        if (hyperJumps.length == 0) {
            return false;
        }
        int bestDist = c.loc.distanceSquared(currentDestination);
        // baseline
        for (Direction dir: c.allDirs) {
            if (!c.canMove(dir)) {
                continue;
            }
            Location check = c.loc.add(dir);
            int dist = check.distanceSquared(currentDestination);
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
            bestDist = jump.landing.distanceSquared(currentDestination);
            bestJump = jump;
        }

        if (bestJump != null) {
            if (!bestJump.src.equals(c.loc)) {
                c.move(c.loc.directionTo(bestJump.src));
                if (currentGivenPath != -1 && pathIter < givenPaths[currentGivenPath].length && c.loc.equals(givenPaths[currentGivenPath][pathIter])) {
                    pathIter++;
                    revalidatePathIter(currentGivenPath);
                    if (currentGivenPath != -1) {
                        currentDestination = givenPaths[currentGivenPath][pathIter];
                    }
                }
            }
            uc.performAction(ActionType.JUMP, bestJump.dir, bestJump.steps);
            c.logger.log("jump %s -> %s", c.loc, bestJump.landing);
            c.loc = uc.getLocation();
            parallelSearchAllowed = true;
            parallelSearchValid = false;
            if (currentGivenPath != -1 && pathIter < givenPaths[currentGivenPath].length && c.loc.equals(givenPaths[currentGivenPath][pathIter])) {
                pathIter++;
                revalidatePathIter(currentGivenPath);
                if (currentGivenPath != -1) {
                    currentDestination = givenPaths[currentGivenPath][pathIter];
                }
            }
            return true;
        }
        return false;

    }

    private void readjustGivenPath() {
        currentGivenPath = -1;
        for (int i = 0; i < givenPathsLength; i++) {
            if (givenPaths[i] == null) {
                // deleted path
                continue;
            }
            if (givenPaths[i][givenPaths[i].length - 1].distanceSquared(c.destination) <= 8) {
                // found
                currentGivenPath = i;
                int nearestDist = Integer.MAX_VALUE;
                for (int j = givenPaths[i].length - 1; j >= 0; j--) {
                    int dist = c.loc.distanceSquared(givenPaths[i][j]);
                    if (dist <= nearestDist) {
                        nearestDist = dist;
                        pathIter = j;
                    }
                }
            }
        }
    }


    private void revalidatePathIter(int i) {
        if (givenPaths[i] == null) {
            return;
        }
        while (pathIter < givenPaths[i].length && uc.canSenseLocation(givenPaths[i][pathIter]) && uc.senseStructure(givenPaths[i][pathIter]) != null) {
            pathIter++;
        }
        while (pathIter < givenPaths[i].length && uc.canSenseLocation(givenPaths[i][pathIter]) &&
                uc.senseAstronaut(givenPaths[i][pathIter]) != null && uc.senseObjectAtLocation(givenPaths[i][pathIter]) != MapObject.HYPERJUMP) {
            pathIter++;
        }
        if (pathIter < givenPaths[i].length && uc.canSenseLocation(givenPaths[i][pathIter])) {
            if (uc.senseTileType(givenPaths[i][pathIter]) == TileType.WATER) {
                // invalid path
                pathIter = -1;
                givenPaths[i] = null;
                currentGivenPath = -1;
                return;
            }
        }
        if (pathIter >= givenPaths[i].length) {
            // arrived
            pathIter = -1;
            givenPaths[i] = null;
            currentGivenPath = -1;
        }
    }

    private Jump findBestJump(Location src, int baselineDist) {
        int bestDist = baselineDist;
        Direction bestDir = Direction.ZERO;
        Location bestLanding = src;
        int bestStep = 0;
        for (Direction dir: c.fourDirs) {
            for (int j = GameConstants.MAX_JUMP; j > 0; j--) {
                Location landing = src.add(dir.dx * j, dir.dy * j);
                int dist = landing.distanceSquared(currentDestination);
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
