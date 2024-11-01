package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;

import java.util.Objects;

public class RetrievePackageTask extends Task {

    CarePackageInfo target;
    float visionRange;

    public RetrievePackageTask(C c, float visionRange) {
        super(c);
        target = null;
        this.visionRange = visionRange;
    }

    @Override
    public void run() {
        if (target != null) {
            retreivePax();
        }
        int bestScore = Integer.MIN_VALUE;
        CarePackageInfo bestPax = null;
        for (CarePackageInfo pax: uc.senseCarePackages(visionRange)) {
            int dist = Vector2D.manhattanDistance(c.loc, pax.getLocation());
            if (dist > uc.getAstronautInfo().getOxygen()) {
                continue;
            }
            int score = 1 - (dist * dist);
            CarePackage carePackageType = pax.getCarePackageType();
            if (carePackageType == CarePackage.SETTLEMENT) {
                score += 400;
            } else if (carePackageType == CarePackage.DOME) {
                score += 10;
            } else if (carePackageType == CarePackage.HYPERJUMP) {
                score += 10;
            } else if (carePackageType == CarePackage.RADIO) {
                score += 10;
            } else if (carePackageType == CarePackage.REINFORCED_SUIT) {
                score += 200;
            } else if (carePackageType == CarePackage.SURVIVAL_KIT) {
                score += 50;
            } else if (carePackageType == CarePackage.OXYGEN_TANK) {
                score += 200;
            } else if (carePackageType == CarePackage.PLANTS) {
                score += 100;
            }
            if (score > bestScore) {
                bestPax = pax;
                bestScore = score;
            }
        }
        if (bestPax != null) {
            target = bestPax;
            c.destination = bestPax.getLocation();
            c.logger.log("pax: %s - %s", target.getCarePackageType(), target.getLocation());
        }
    }

    private void retreivePax() {
        Direction dir = c.loc.directionTo(target.getLocation());
        if (uc.canPerformAction(ActionType.RETRIEVE, dir, 1)) {
            uc.performAction(ActionType.RETRIEVE, dir, 1);
            target = null;
        }
    }
}
