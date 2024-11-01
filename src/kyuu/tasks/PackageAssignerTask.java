package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastLocSet;

public class PackageAssignerTask extends Task {


    FastLocSet wantedPackages;

    final int[] defaultScores = {
            100, //SETTLEMENT
            2, //DOME
            10, //HYPERJUMP
            -1, //RADIO
            200, //REINFORCED_SUIT
            50, //SURVIVAL_KIT
            200, //OXYGEN_TANK
            400, //PLANTS
    };

    final int[] settlementDefaultScores = {
            100, //SETTLEMENT
            -1, //DOME
            10, //HYPERJUMP
            -1, //RADIO
            200, //REINFORCED_SUIT
            -1, //SURVIVAL_KIT
            200, //OXYGEN_TANK
            400, //PLANTS
    };

    int[] scoreMap;

    public PackageAssignerTask(C c) {
        super(c);
        wantedPackages = new FastLocSet();
        scoreMap = uc.getStructureInfo().getType() == StructureType.HQ ? defaultScores : settlementDefaultScores;
    }

    @Override
    public void run() {
        while (!ldb.enlistFullyReserved() && getPackages()) {}

        adjustPriority();
    }

    private void adjustPriority() {
        if (ldb.oxygenProductionRate >= 5) {
            scoreMap = defaultScores;
            int[] prio = new int[CarePackage.values().length];
            if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.RADIO) > 5) {
                prio[CarePackage.RADIO.ordinal()] -= 1000;
            }
            if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) > 30) {
                prio[CarePackage.REINFORCED_SUIT.ordinal()] -= 100;
                prio[CarePackage.OXYGEN_TANK.ordinal()] += 100;
            }
            prio[CarePackage.HYPERJUMP.ordinal()] += (10 * (ldb.neededHyperJumps - uc.getStructureInfo().getCarePackagesOfType(CarePackage.HYPERJUMP)));
            rdb.sendPackagePriorityNotice(prio);
        }

    }

    private boolean getPackages() {
        uc.senseCarePackages(c.visionRange);
        int bestScore = Integer.MIN_VALUE;
        CarePackageInfo bestPax = null;
        FastLocSet currentPaxes = new FastLocSet();
        for (CarePackageInfo pax: uc.senseCarePackages(c.visionRange)) {
            currentPaxes.add(pax.getLocation());
            if (wantedPackages.contains(pax.getLocation())) {
                continue;
            }
            if (!c.s.isReachableDirectly(pax.getLocation())) {
                continue;
            }
            int dist = Vector2D.manhattanDistance(c.loc, pax.getLocation());
            int estCost = dist * 3 / 2;
            if (estCost > uc.getStructureInfo().getOxygen()) {
                continue;
            }
            int score = getPaxScore(pax, dist);
            if (score > bestScore) {
                bestPax = pax;
                bestScore = score;
            }
        }

        // cleanup
        for (Location pax: wantedPackages.getKeys()) {
            if (!currentPaxes.contains(pax)) {
                wantedPackages.remove(pax);
            }
        }

        if (bestPax != null) {
            int retrieveCost = Vector2D.manhattanDistance(c.loc, bestPax.getLocation()) * 3 / 2;
            if (retrieveCost < 11) {
                retrieveCost = 11;
            }
            Direction dirTarget = c.loc.directionTo(bestPax.getLocation());
            if (dirTarget == Direction.ZERO) {
                dirTarget = Direction.NORTH;
            }
            for (Direction dir: c.getFirstDirs(dirTarget)) {
                if (uc.canEnlistAstronaut(dir, retrieveCost, null)) {
                    c.enlistAstronaut(dir, retrieveCost, null);
                    int enlistedId = uc.senseAstronaut(c.loc.add(dir)).getID();
                    rdb.sendGetPackagesCommand(enlistedId, bestPax.getLocation());
                    wantedPackages.add(bestPax.getLocation());
                    uc.drawLineDebug(c.loc, bestPax.getLocation(), 0, 255, 0);
                    ldb.pushAssignedThisRound(enlistedId);
                    return true;
                }
            }
        }
        return false;
    }

    private int getPaxScore(CarePackageInfo pax, int dist) {
        int score = 1 - (dist * dist);
        CarePackage carePackageType = pax.getCarePackageType();
        if (carePackageType == CarePackage.PLANTS) {
            score += (1000 - uc.getRound());
        } else {
            score += scoreMap[carePackageType.ordinal()];
        }
        return score;
    }
}
