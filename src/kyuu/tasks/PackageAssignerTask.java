package kyuu.tasks;

import aic2024.user.CarePackage;
import aic2024.user.CarePackageInfo;
import aic2024.user.Direction;
import aic2024.user.Location;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastLocSet;

public class PackageAssignerTask extends Task {


    FastLocSet wantedPackages;

    public PackageAssignerTask(C c) {
        super(c);
        wantedPackages = new FastLocSet();
    }

    @Override
    public void run() {
        while (getPackages()) {}
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
                    uc.enlistAstronaut(dir, retrieveCost, null);
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

    private static int getPaxScore(CarePackageInfo pax, int dist) {
        int score = 1 - (dist * dist);
        CarePackage carePackageType = pax.getCarePackageType();
        if (carePackageType == CarePackage.SETTLEMENT) {
            score += 300;
        } else if (carePackageType == CarePackage.DOME) {
            score += 10;
        } else if (carePackageType == CarePackage.HYPERJUMP) {
            score += 10;
        } else if (carePackageType == CarePackage.RADIO) {
            score += 10;
        } else if (carePackageType == CarePackage.REINFORCED_SUIT) {
            score += 50;
        } else if (carePackageType == CarePackage.SURVIVAL_KIT) {
            score += 50;
        } else if (carePackageType == CarePackage.OXYGEN_TANK) {
            score += 150;
        } else if (carePackageType == CarePackage.PLANTS) {
            score += 200;
        }
        return score;
    }
}
