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

    boolean enemyHqDiscovered;

    public JumpStrategyTask(C c) {
        super(c);
        needJumps = new Location[c.allDirs.length];
        needJumpsLength = 0;
        builders = new FastLocIntMap();
        planJumps();
        nextBuild = 0;
        enemyHqDiscovered = false;
    }

    private void planJumps() {
        for (Direction dir: c.fourDirs) {
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
        for (Direction dir: c.diagonalDirs) {
            Location check = c.loc.add(dir);

            while (uc.canSenseLocation(check) && !c.isObstacle(uc.senseObjectAtLocation(check))) {
                check = check.add(dir);
            }
            Location candidate = check.add(dir.opposite());

            if (!uc.canSenseLocation(check)) {
                continue;
            }

            for (Direction jumpDir: new Direction[]{dir.rotateRight(), dir.rotateLeft()}) {
                Location checkJump = candidate.add(jumpDir);
                int j;
                for (j = 0; j < GameConstants.MAX_JUMP - 1 && uc.canSenseLocation(checkJump) && c.isObstacle(uc.senseObjectAtLocation(checkJump)); j++) {
                    checkJump = checkJump.add(dir);
                }

                if (j == 0 || uc.isOutOfMap(checkJump.add(dir.dx * 3, dir.dy * 3)) || !uc.canSenseLocation(checkJump)) {
                    continue;
                }

                needJumps[needJumpsLength++] = candidate;
                break;
            }
        }
    }

    private void planJumpsAdjustEnemyHq() {
        needJumpsLength = 0;
        int nearestEnemyHqIdx = Vector2D.getNearestChebysev(c.loc, rdb.enemyHq, rdb.enemyHqSize);

        Direction[] enemyHqDirs = c.getFirstFourDirs(c.loc.directionTo(rdb.enemyHq[nearestEnemyHqIdx]));

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
        for (Direction dir: c.diagonalDirs) {
            Location check = c.loc.add(dir);

            while (uc.canSenseLocation(check) && !c.isObstacle(uc.senseObjectAtLocation(check))) {
                check = check.add(dir);
            }
            Location candidate = check.add(dir.opposite());

            if (!uc.canSenseLocation(check)) {
                continue;
            }

            for (Direction jumpDir: new Direction[]{dir.rotateRight(), dir.rotateLeft()}) {
                Location checkJump = candidate.add(jumpDir);
                int j;
                for (j = 0; j < GameConstants.MAX_JUMP - 1 && uc.canSenseLocation(checkJump) && c.isObstacle(uc.senseObjectAtLocation(checkJump)); j++) {
                    checkJump = checkJump.add(dir);
                }

                if (j == 0 || uc.isOutOfMap(checkJump.add(dir.dx * 3, dir.dy * 3)) || !uc.canSenseLocation(checkJump)) {
                    continue;
                }

                needJumps[needJumpsLength++] = candidate;
                break;
            }
        }
    }

    @Override
    public void run() {

        if (rdb.enemyHqSize > 0 && !enemyHqDiscovered) {
            enemyHqDiscovered = true;
            planJumpsAdjustEnemyHq();
        }

        int currentBuildId = nextBuild;
        ldb.neededHyperJumps = 0;
        for (int i = 0; i < needJumpsLength; i++) {
            int k = (i + currentBuildId) % needJumpsLength;
            if (uc.senseObjectAtLocation(needJumps[k]) == MapObject.HYPERJUMP) {
                builders.remove(needJumps[k]);
                continue;
            }
            if (builders.contains(needJumps[k]) && c.s.isAllyVisible(builders.getVal(needJumps[k]))) {
                continue;
            }
            ldb.neededHyperJumps++;
            if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.HYPERJUMP) == 0) {
                return;
            }

            if (ldb.enlistFullyReserved()) {
                ldb.neededHyperJumps--;
                return;
            }

            // first builder or previous builder failed
            for (Direction dir: c.getFirstDirs(c.loc.directionTo(needJumps[k]))) {
                if (uc.canEnlistAstronaut(dir, 10, CarePackage.HYPERJUMP)) {
                    c.enlistAstronaut(dir, 10, CarePackage.HYPERJUMP);
                    int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                    rdb.sendBuildHyperJumpCommand(enlistId, needJumps[k]);
                    nextBuild = (k + 1) % needJumpsLength;
                    ldb.neededHyperJumps--;
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
                c.enlistAstronaut(dir, 10, CarePackage.HYPERJUMP);
                int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                rdb.sendBuildHyperJumpCommand(enlistId, c.loc.add(dir));
            }
        }
    }
}
