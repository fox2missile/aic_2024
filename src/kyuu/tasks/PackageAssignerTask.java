package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastLocIntMap;
import kyuu.fast.FastLocSet;
import kyuu.message.RetrievePackageFailed;

public class PackageAssignerTask extends Task {


    FastLocSet wantedPackages;
    FastLocIntMap failedLocationCounter;

    final int[] defaultScores = {
            100, //SETTLEMENT
            20, //DOME
            40, //HYPERJUMP
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

    int[] priorityBoostMap;

    int[] scoreMap;

    public PackageAssignerTask(C c) {
        super(c);
        wantedPackages = new FastLocSet();
        failedLocationCounter = new FastLocIntMap();
        scoreMap = uc.getStructureInfo().getType() == StructureType.HQ ? defaultScores : settlementDefaultScores;
        priorityBoostMap = new int[CarePackage.values().length];
        rdb.retrievePackageFailedReceiver = (int fullMsg) -> {
            RetrievePackageFailed msg = rdb.parseGetPackageFailedMessage(fullMsg);
            if (failedLocationCounter.contains(msg.target)) {
                int currentCount = failedLocationCounter.getVal(msg.target);
                failedLocationCounter.addReplace(msg.target, currentCount + 1);
                wantedPackages.remove(msg.target);
            }
        };
    }

    @Override
    public void run() {
        adjustPriority();
        while (!ldb.enlistFullyReserved() && getPackages()) {}

    }

    private void adjustPriority() {
        priorityBoostMap = new int[CarePackage.values().length];
        if (ldb.oxygenProductionRate >= 5) {
            scoreMap = defaultScores;
            if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.RADIO) >= 5) {
                priorityBoostMap[CarePackage.RADIO.ordinal()] = -1000;
            }
            if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) > 30) {
                priorityBoostMap[CarePackage.REINFORCED_SUIT.ordinal()] = -100;
                priorityBoostMap[CarePackage.OXYGEN_TANK.ordinal()] = 100;
            }
            if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.DOME) >= 1) {
                priorityBoostMap[CarePackage.DOME.ordinal()] = -1000;
            }
            priorityBoostMap[CarePackage.HYPERJUMP.ordinal()] = (10 * (ldb.neededHyperJumps - uc.getStructureInfo().getCarePackagesOfType(CarePackage.HYPERJUMP)));
        }
        rdb.sendPackagePriorityNotice(priorityBoostMap);

    }

    private boolean getPackages() {
        int bestScore = 0;
        CarePackageInfo bestPax = null;
        FastLocSet currentPaxes = new FastLocSet();
        for (CarePackageInfo pax: uc.senseCarePackages(c.visionRange)) {
            currentPaxes.add(pax.getLocation());
            if (wantedPackages.contains(pax.getLocation())) {
                continue;
            }
            if (failedLocationCounter.getVal(pax.getLocation()) >= 3) {
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
                    Location paxLocation = bestPax.getLocation();
                    wantedPackages.add(paxLocation);
                    if (!failedLocationCounter.contains(paxLocation)) {
                        int startingCount = 0;
                        if (!c.s.isReachableDirectly(paxLocation)) {
                            startingCount = 2;
                        }
                        failedLocationCounter.add(paxLocation, startingCount);
                    }
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
        score += priorityBoostMap[carePackageType.ordinal()];
        return score;
    }
}
