package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.*;

public class JumpStrategyTask extends Task {

    Location[] needJumps;
    int needJumpsLength;
    FastLocIntMap builders;

    int nextBuild;

    public JumpStrategyTask(C c) {
        super(c);
        needJumps = new Location[c.allDirs.length];
        needJumpsLength = 0;
        builders = new FastLocIntMap();
        planJumps();
        nextBuild = 0;
    }

    private void planJumps() {
        int nearestEnemyHqIdx = Vector2D.getNearestChebysev(c.loc, rdb.enemyHq, rdb.enemyHqSize);

        Direction[] enemyHqDirs = c.getFirstDirs(c.loc.directionTo(rdb.enemyHq[nearestEnemyHqIdx]));

        for (Direction dir: enemyHqDirs) {
            Location check = c.loc.add(dir);

            while (uc.canSenseLocation(check) && !c.isObstacle(uc.senseObjectAtLocation(check))) {
                check = check.add(dir);
            }
            Location candidate = check.add(dir.opposite());

            if (!uc.canSenseLocation(check)) {
                continue;
            }

            for (int j = 0; j < GameConstants.MAX_JUMP - 1 && uc.canSenseLocation(check) && c.isObstacle(uc.senseObjectAtLocation(check)); j++) {
                check = check.add(dir);
            }

            if (uc.isOutOfMap(check.add(dir.dx * 3, dir.dy * 3)) || !uc.canSenseLocation(check)) {
                continue;
            }

            needJumps[needJumpsLength++] = candidate;
        }
    }

    @Override
    public void run() {
        int currentBuildId = nextBuild;
        for (int i = 0; i < needJumpsLength; i++) {
            int k = (i + currentBuildId) % needJumpsLength;
            if (ldb.enlistFullyReserved() || uc.getStructureInfo().getCarePackagesOfType(CarePackage.HYPERJUMP) == 0) {
                return;
            }
            if (uc.senseObjectAtLocation(needJumps[k]) == MapObject.HYPERJUMP) {
                builders.remove(needJumps[k]);
                continue;
            }
            if (builders.contains(needJumps[k]) && c.s.isAllyVisible(builders.getVal(needJumps[k]))) {
                continue;
            }
            // first builder or previous builder failed
            for (Direction dir: c.getFirstDirs(c.loc.directionTo(needJumps[k]))) {
                if (uc.canEnlistAstronaut(dir, 10, CarePackage.HYPERJUMP)) {
                    uc.enlistAstronaut(dir, 10, CarePackage.HYPERJUMP);
                    int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                    rdb.sendBuildHyperJumpCommand(enlistId, needJumps[k]);
                    nextBuild = (k + 1) % needJumpsLength;
                    break;
                }
            }

        }

        // spawn location
        for (Direction dir: c.allDirs) {
            if (ldb.enlistFullyReserved() || uc.getStructureInfo().getCarePackagesOfType(CarePackage.HYPERJUMP) == 0) {
                return;
            }
            if (uc.canEnlistAstronaut(dir, 10, CarePackage.HYPERJUMP)) {
                uc.enlistAstronaut(dir, 10, CarePackage.HYPERJUMP);
                int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                rdb.sendBuildHyperJumpCommand(enlistId, c.loc.add(dir));
            }
        }
    }
}
