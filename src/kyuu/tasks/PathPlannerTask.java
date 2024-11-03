package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.db.EnlistDestination;
import kyuu.db.Expansion;
import kyuu.fast.*;
import kyuu.pathfinder.GlobalPathFinderTask;

import java.util.Iterator;

public class PathPlannerTask extends Task {

    FastLocIntMap map;
    int currentEnemyHq;
    FastIntLocMap workersLoc;
    GlobalPathFinderTask[] pathFinderComplete;
    GlobalPathFinderTask[] pathFinderInProgress;
    int pathFindersLength;
    final int EXPANSION_PF_SIZE = 8 + (8 * 5) + (8 * 5);
    Location enemyHq1;
    Location enemyHq2;
    Location enemyHq3;

    FastLocIntMap sectorStatus;

    final int MAX_PATH_FINDERS;
    final int PATH_PLANNING_STATUS_PENDING = 0;
    final int PATH_PLANNING_STATUS_IN_PROGRESS = 1;
    final int PATH_PLANNING_STATUS_PLANNED = 2;

    boolean firstScanDone;
    int rePlanPathTimer;
    int prevHyperJumps;

    FastLocLocSet mappedExpansionLocs;

    public PathPlannerTask(C c) {
        super(c);
        mappedExpansionLocs = new FastLocLocSet();
        prevHyperJumps = 0;
        rePlanPathTimer = -1;
        currentEnemyHq = 1;
        firstScanDone = false;
        map = new FastLocIntMap();
        MAX_PATH_FINDERS = EXPANSION_PF_SIZE + dc.MAX_HQ;
        workersLoc = new FastIntLocMap();
        pathFinderComplete = new GlobalPathFinderTask[MAX_PATH_FINDERS];
        pathFinderInProgress = new GlobalPathFinderTask[MAX_PATH_FINDERS];
        pathFindersLength = 0;
        sectorStatus = new FastLocIntMap();
        rdb.seekSymmetryCompleteReceiver2 = (int __) -> {
            if (ldb.symmetryFound) {
                copySymmetry();
                rdb.seekSymmetryCompleteReceiver2 = null;
            }
        };
        rdb.worldObstacleReceiver = (int __) -> {
            BroadcastInfo bc = uc.pollBroadcast();
            c.logger.log("Mapper: %d", bc.getID());
            int length = bc.getMessage();
            if (workersLoc.contains(bc.getID())) {
                workersLoc.addReplace(bc.getID(), bc.getLocation());
            }
            if (uc.getRound() - c.spawnRound > 10 && length > 50) {
                // the broadcast was meant for new settlements
                uc.eraseBroadcastBuffer(length * 2);
                return;
            }
            for (int i = 0; i < length; i++) {
                Location loc  = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                map.addReplace(loc, dc.TILE_OBSTACLE);
                if (rdb.seekSymmetryCompleteReceiver2 == null) {
                    copySymmetry(loc);
                }
            }
            if (rePlanPathTimer <= -1) {
                rePlanPathTimer = 5;
            }
        };
        rdb.newSettlementReceiver2 = (int __) -> {
            if (rdb.isPrimaryBase()) {
                rdb.sendWorldObstaclesFromBase(map);
            }

        };
    }

    private void scanNearby() {
        for (Location loc: uc.senseObjects(MapObject.TERRAFORMED, c.visionRange)) {
            map.addReplace(loc, dc.TILE_TERRAFORMED);
        }
        Location[] hyperJumps = uc.senseObjects(MapObject.HYPERJUMP, c.visionRange);
        prevHyperJumps = hyperJumps.length;
        for (Location loc: hyperJumps) {
            map.addReplace(loc, dc.TILE_HYPER_JUMP);
        }
        if (!firstScanDone) {
            Location[] obstacles = uc.senseObjects(MapObject.WATER, c.visionRange);
            for (Location loc: obstacles) {
                map.addReplace(loc, dc.TILE_OBSTACLE);
                if (rdb.seekSymmetryCompleteReceiver2 == null) {
                    copySymmetry(loc);
                }
            }
            rdb.sendWorldObstaclesFromBase(obstacles);
            map.addReplace(c.loc, dc.TILE_OBSTACLE);
        }
        for (Location loc: hyperJumps) {
            for (Direction dir: c.fourDirs) {
                Location landing = loc;
                for (int i = 0; i < 3; i++) {
                    landing = landing.add(dir);
                    int tile = map.getVal(landing);
                    if (tile == dc.TILE_OBSTACLE) {
                        continue;
                    }
                    if (tile == -1) {
                        tile = dc.TILE_LAND;
                    }
                    if (tile % dc.TILE_HYPER_JUMP_LANDING != 0) {
                        tile *= dc.TILE_HYPER_JUMP_LANDING;
                        map.addReplace(landing, tile);
                    }
                }
            }
        }
    }

    private void copySymmetry() {
        for (Iterator<Location> it = map.getIterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (map.getVal(loc) != dc.TILE_OBSTACLE) {
                continue;
            }
            copySymmetry(loc);
        }
    }

    private void copySymmetry(Location loc) {
        if (ldb.horizontalSymmetryPossible) {
            map.addReplace(c.mirrorHorizontal(loc), dc.TILE_OBSTACLE);
        } else if (ldb.verticalSymmetryPossible) {
            map.addReplace(c.mirrorVertical(loc), dc.TILE_OBSTACLE);
        } else if (ldb.rotationalSymmetryPossible) {
            map.addReplace(c.mirrorRotational(loc), dc.TILE_OBSTACLE);
        }
    }

    @Override
    public void run() {
        if (!firstScanDone) {
            scanNearby();
            firstScanDone = true;
        }
        broadcastKnownPaths();
        rdb.flushBroadcastBuffer();
        int hyperJumpsNow = uc.senseObjects(MapObject.HYPERJUMP, c.visionRange).length;
        if (hyperJumpsNow != prevHyperJumps) {
            rePlanPath();
            rePlanPathTimer = -1;
        }
        prevHyperJumps = hyperJumpsNow;
        if (rePlanPathTimer >= 0) {
            rePlanPathTimer--;
            if (rePlanPathTimer == 0) {
                rePlanPath();
            }
        }
        tryPlanPath();

        if (enemyHq1 != null && uc.getStructureInfo().getOxygen() > 350 && uc.getStructureInfo().getCarePackagesOfType(CarePackage.RADIO) > 0 && ldb.availableEnlistSlot > 0) {
            tryAssignWorker();
        }
        rdb.flushBroadcastBuffer();

    }

    private void broadcastKnownPaths() {
        if (ldb.knownDestinations == 0) {
            return;
        }
        for (int i = 0; i < ldb.recentEnlistsLength; i++) {
            EnlistDestination ed = ldb.recentEnlists[i];
            if (ed.summarizedPath == null) {
                int pathIdx = ldb.getPath(ed.start, ed.destination, ed.approxDestination);
                if (pathIdx != -1) {
                    rdb.sendPath(ed.enlistedId, pathIdx);
                }
            } else {
                // multi-path
                for (int j = 0; j < ed.summarizedPath.length - 1; j++) {
                    int pathIdx = ldb.getPath(ed.summarizedPath[j], ed.summarizedPath[j + 1]);
                    if (pathIdx != -1) {
                        rdb.sendPath(ed.enlistedId, pathIdx);
                    }
                }
                // final path
                int pathIdx = ldb.getPath(ed.summarizedPath[ed.summarizedPath.length - 1], ed.destination);
                if (pathIdx != -1) {
                    rdb.sendPath(ed.enlistedId, pathIdx);
                }
            }
        }
    }

    private void rePlanPath() {
        c.logger.log("Re-planning paths!");
        scanNearby();
        for (int i = 0; i < pathFindersLength; i++) {
            if (pathFinderComplete[i] != null && (pathFinderInProgress[i] == null || pathFinderInProgress[i].progress == 0)) {
                pathFinderInProgress[i] = new GlobalPathFinderTask(c, pathFinderComplete[i].start, pathFinderComplete[i].destination, map, pathFinderComplete[i].extraPriority);
            }
        }
    }

    private void tryAssignWorker() {
        Location target = getNextTarget();
        currentEnemyHq = 1 + (currentEnemyHq % 3);
        int givenOxygen = 50;
        for (Direction dir: c.allDirs) {
            if (uc.canEnlistAstronaut(dir, givenOxygen, CarePackage.RADIO)) {
                c.enlistAstronaut(dir, givenOxygen, CarePackage.RADIO, target);
                int workerId = uc.senseAstronaut(c.loc.add(dir)).getID();
                workersLoc.addReplace(workerId, c.loc.add(dir));
                rdb.sendWorldMapperCommand(workerId, target);
            }
        }
    }

    private Location getNextTarget() {
        Location target;
        if (currentEnemyHq == 1) {
            target = enemyHq1;
        } else if (currentEnemyHq == 2) {
            if (enemyHq2 != null) {
                target = enemyHq2;
            } else {
                target = enemyHq1;
            }
        } else {
            if (enemyHq3 != null) {
                target = enemyHq3;
            } else if (enemyHq2 != null) {
                target = enemyHq2;
            } else {
                target = enemyHq1;
            }
        }
        return target;
    }

    private void tryPlanPath() {
        if (enemyHq1 == null) {
            if (rdb.enemyHqSize >= 1) {
                enemyHq1 = rdb.enemyHq[0];
                pathFinderInProgress[pathFindersLength++] = new GlobalPathFinderTask(c, c.loc, enemyHq1, map, 1);
            }
        }
        if (enemyHq2 == null) {
            if (rdb.enemyHqSize >= 2) {
                enemyHq2 = rdb.enemyHq[1];
                pathFinderInProgress[pathFindersLength++] = new GlobalPathFinderTask(c, c.loc, enemyHq2, map, 1);
            }
        }
        if (enemyHq3 == null) {
            if (rdb.enemyHqSize >= 3) {
                enemyHq3 = rdb.enemyHq[2];
                pathFinderInProgress[pathFindersLength++] = new GlobalPathFinderTask(c, c.loc, enemyHq3, map, 1);
            }
        }

        exIter:
        for (Iterator<Expansion> it = ldb.iterateExpansions(); it.hasNext(); ) {
            Expansion ex = it.next();

            for (int i = 0; i < c.allDirs.length; i++) {

                Location expansionSite = rdb.expansionSites[ex.id][i];
                if (expansionSite == null) {
                    continue;
                }
                int state = rdb.surveyorStates[ex.id][i];
                if (state != dc.SURVEY_BAD) {
                    if (!mappedExpansionLocs.contains(ex.expansionLoc, expansionSite)) {
                        mappedExpansionLocs.add(ex.expansionLoc, expansionSite);
                        pathFinderInProgress[pathFindersLength++] = new GlobalPathFinderTask(c, ex.expansionLoc, expansionSite, map, 0);
                    }
                }

            }

        }

        FastTaskQueue pathFinderQueue = new FastTaskQueue(MAX_PATH_FINDERS);
        for (int i = 0; i < pathFindersLength; i++) {
            if (pathFinderInProgress[i] == null) {
                continue;
            }
            if (pathFinderInProgress[i].isFinished()) {
                pathFinderComplete[i] = pathFinderInProgress[i];
                c.logger.log("Finish pathfinding from %s to %s", pathFinderComplete[i].start, pathFinderComplete[i].destination);
                pathFinderInProgress[i] = null;
            } else {
                pathFinderQueue.offer(pathFinderInProgress[i]);
            }
        }

        while (!pathFinderQueue.isEmpty()) {
            pathFinderQueue.poll().run();
        }
    }
}
