package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.fast.FastIntLocMap;
import kyuu.fast.FastLocIntMap;
import kyuu.pathfinder.GlobalPathFinderTask;

import java.util.Iterator;

public class PathPlannerTask extends Task {

    FastLocIntMap map;
    int currentEnemyHq;
    FastIntLocMap workersLoc;
    GlobalPathFinderTask[] pathFinderComplete;
    GlobalPathFinderTask[] pathFinderInProgress;
    final int ENEMY_HQ_PF_IDX_BEGIN;
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

    public PathPlannerTask(C c) {
        super(c);
        prevHyperJumps = 0;
        rePlanPathTimer = -1;
        currentEnemyHq = 1;
        firstScanDone = false;
        map = new FastLocIntMap();
        MAX_PATH_FINDERS = (dc.EXPANSION_SIZE) + dc.MAX_HQ;
        ENEMY_HQ_PF_IDX_BEGIN = dc.EXPANSION_SIZE;
        workersLoc = new FastIntLocMap();
        pathFinderComplete = new GlobalPathFinderTask[MAX_PATH_FINDERS];
        pathFinderInProgress = new GlobalPathFinderTask[MAX_PATH_FINDERS];
        sectorStatus = new FastLocIntMap();
        rdb.seekSymmetryCompleteReceiver2 = (int __) -> {
            copySymmetry();
        };
        rdb.worldObstacleReceiver = (int __) -> {
            BroadcastInfo bc = uc.pollBroadcast();
            int length = bc.getMessage();
            workersLoc.addReplace(bc.getID(), bc.getLocation());
            for (int i = 0; i < length; i++) {
                map.addReplace(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()), dc.TILE_OBSTACLE);
            }
            if (rePlanPathTimer <= -1) {
                rePlanPathTimer = 5;
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
            for (Location loc: uc.senseObjects(MapObject.WATER, c.visionRange)) {
                map.addReplace(loc, dc.TILE_OBSTACLE);
            }
            map.addReplace(c.loc, dc.TILE_OBSTACLE);
        }
    }

    private void copySymmetry() {
        for (Iterator<Location> it = map.getIterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (map.getVal(loc) != dc.TILE_OBSTACLE) {
                continue;
            }
            if (ldb.horizontalSymmetryPossible) {
                map.addReplace(c.mirrorHorizontal(loc), map.getVal(loc));
            } else if (ldb.verticalSymmetryPossible) {
                map.addReplace(c.mirrorVertical(loc), map.getVal(loc));
            } else if (ldb.rotationalSymmetryPossible) {
                map.addReplace(c.mirrorRotational(loc), map.getVal(loc));
            }
        }
    }

    @Override
    public void run() {
        if (!firstScanDone) {
            scanNearby();
            firstScanDone = true;
        }
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

    }

    private void rePlanPath() {
        c.logger.log("Re-planning paths!");
        scanNearby();
        for (int i = MAX_PATH_FINDERS - 1; i >= 0; i--) {
            if (pathFinderComplete[i] != null && pathFinderInProgress[i] == null) {
                pathFinderInProgress[i] = new GlobalPathFinderTask(c, pathFinderComplete[i].start, pathFinderComplete[i].destination, map);
            }
        }
    }

    private void tryAssignWorker() {
        Location target = getNextTarget();
        currentEnemyHq = 1 + (currentEnemyHq % 3);
        int givenOxygen = 50;
        for (Direction dir: c.allDirs) {
            if (uc.canEnlistAstronaut(dir, givenOxygen, CarePackage.RADIO)) {
                c.enlistAstronaut(dir, givenOxygen, CarePackage.RADIO);
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
                pathFinderInProgress[ENEMY_HQ_PF_IDX_BEGIN] = new GlobalPathFinderTask(c, c.loc, enemyHq1, map);
            }
        }
        if (enemyHq2 == null) {
            if (rdb.enemyHqSize >= 2) {
                enemyHq2 = rdb.enemyHq[1];
                pathFinderInProgress[ENEMY_HQ_PF_IDX_BEGIN + 1] = new GlobalPathFinderTask(c, c.loc, enemyHq2, map);
            }
        }
        if (enemyHq3 == null) {
            if (rdb.enemyHqSize >= 3) {
                enemyHq3 = rdb.enemyHq[2];
                pathFinderInProgress[ENEMY_HQ_PF_IDX_BEGIN + 2] = new GlobalPathFinderTask(c, c.loc, enemyHq3, map);
            }
        }

        for (int i = MAX_PATH_FINDERS - 1; i >= 0; i--) {
            if (pathFinderInProgress[i] == null) {
                continue;
            }
            if (pathFinderInProgress[i].isFinished()) {
                pathFinderComplete[i] = pathFinderInProgress[i];
                c.logger.log("Finish pathfinding from %s to %s", pathFinderComplete[i].start, pathFinderComplete[i].destination);
                pathFinderInProgress[i] = null;
            } else {
                pathFinderInProgress[i].run();
            }
        }
    }
}
