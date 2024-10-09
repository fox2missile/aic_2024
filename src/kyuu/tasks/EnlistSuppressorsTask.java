package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastIntIntMap;

import java.util.ArrayDeque;
import java.util.Deque;

public class EnlistSuppressorsTask extends Task {


    FastIntIntMap suppressors;

    int cooldown;
    int cooldownMultiplier;

    Deque<Integer> suppressorRounds;

    final int TARGET_SUPPRESSORS_COUNT = 15;

    int currentDice;

    public EnlistSuppressorsTask(C c) {
        super(c);
        cooldown = 10;
        suppressors = new FastIntIntMap();
        cooldownMultiplier = 1;
        // want to at least keep minimum number of suppressors at all times
        suppressorRounds = new ArrayDeque<>(TARGET_SUPPRESSORS_COUNT);
        currentDice = 0;
    }

    private int estimateMapMagnitude() {
        if (rdb.enemyHqSize > 0) {
            Location nearestEnemyHq = rdb.enemyHq[Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize)];
            return c.loc.distanceSquared(nearestEnemyHq);
        }

        // estimate from symmetry
        int biggestMagnitude = 0;
        for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
            boolean validSymmetry = false;
            if (i % 3 == dc.SYMMETRY_HORIZONTAL && ldb.horizontalSymmetryPossible) {
                validSymmetry = true;
            } else if (i % 3 == dc.SYMMETRY_VERTICAL && ldb.verticalSymmetryPossible) {
                validSymmetry = true;
            } else if (i % 3 == dc.SYMMETRY_ROTATIONAL && ldb.rotationalSymmetryPossible) {
                validSymmetry = true;
            }
            if (validSymmetry) {
                biggestMagnitude = Math.max(biggestMagnitude, c.loc.distanceSquared(ldb.symmetryCandidates[i]));
            }
        }
        return biggestMagnitude;
    }

    private int estimateReserveOxygen() {
        int magnitude = estimateMapMagnitude();
        return Math.min(magnitude, 400);
    }

    Location getNextSuppressionTarget(int dice) {
        if (rdb.enemyHqSize == 0) {
            return getBlindSuppressionTarget(dice);
        }
        return getHqSuppressionTarget(dice);
    }

    Location getHqSuppressionTarget(int dice) {
        int nearestEnemyHqIdx = Vector2D.getNearestChebysev(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location enemyHq = rdb.enemyHq[nearestEnemyHqIdx];
        Location[] validTargets = new Location[8];
        int validTargetsLength = 0;
        if (dice % 9 == 0) {
            return enemyHq;
        }

        int hqExpansionId = rdb.getThisHqExpansionId();
        int i = dice % c.allDirs.length;
        if (rdb.surveyorStates[hqExpansionId][i] != dc.SURVEY_BAD) {
            Location original = rdb.expansionSites[hqExpansionId][i];
            if (ldb.isHorizontalSymmetry()) {
                validTargets[validTargetsLength++] = c.mirrorHorizontal(original);
            }
            if (ldb.isVerticalSymmetry()) {
                validTargets[validTargetsLength++] = c.mirrorVertical(original);
            }
            if (ldb.isRotationalSymmetry()) {
                validTargets[validTargetsLength++] = c.mirrorRotational(original);
            }
        }
        if (validTargetsLength == 0) {
            return enemyHq;
        }

        return validTargets[dice % validTargetsLength];
    }

    // enemy HQ not known
    Location getBlindSuppressionTarget(int dice) {
        Location[] validTargets = new Location[ldb.symmetryCandidates.length];
        int validTargetsLength = 0;
        for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
            if (uc.getRound() % ldb.symmetryCandidates.length != i) {
                continue;
            }
            boolean validHorizontal = i % 3 == dc.SYMMETRY_HORIZONTAL && ldb.horizontalSymmetryPossible;
            boolean validVertical = i % 3 == dc.SYMMETRY_VERTICAL && ldb.verticalSymmetryPossible;
            boolean validRotational = i % 3 == dc.SYMMETRY_ROTATIONAL && ldb.rotationalSymmetryPossible;
            if (validHorizontal || validVertical || validRotational) {
                validTargets[validTargetsLength++] = ldb.symmetryCandidates[i];
            }
        }
        if (validTargetsLength == 0) {
            return null;
        }

        return validTargets[uc.getRound() % validTargetsLength];
    }

    @Override
    public void run() {
        if (rdb.enemyHqSize == 0) {
            return;
        }

        if (uc.senseAstronauts(c.visionRange, c.opponent).length == 0) {
            for (AstronautInfo ally: uc.senseAstronauts(c.visionRange, c.team)) {
                if (ally.getOxygen() < 2 && suppressors.contains(ally.getID())) {
                    cooldown *= 2;
                    return;
                }
            }
        }

        if (ldb.enlistFullyReserved() || uc.getStructureInfo().getOxygen() < estimateReserveOxygen() || uc.getRound() % cooldown != 0) {
            return;
        }

        int nearestEnemyHqIdx = Vector2D.getNearestChebysev(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location enemyHq = rdb.enemyHq[nearestEnemyHqIdx];

        int stepDist = Vector2D.chebysevDistance(c.loc, enemyHq);
        int dist = c.loc.distanceSquared(enemyHq);
        if (dist > 2 * 30 * 30) {
            return;
        }

        int givenOxygen = Math.max(stepDist, 10);

        for (Direction dir: Direction.values()) {
            if (c.uc.canEnlistAstronaut(dir, givenOxygen, null)) {
                c.enlistAstronaut(dir, givenOxygen, null);
                int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                suppressors.add(enlistId, 1);
                rdb.sendSuppressionCommand(enlistId, enemyHq);
                if (ldb.enlistFullyReserved()) {
                    return;
                }
            }
        }
    }

}
