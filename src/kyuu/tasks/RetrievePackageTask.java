package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;

import java.util.Objects;

public class RetrievePackageTask extends Task {

    CarePackageInfo target;
    int[] packagesBaseScore;

    final int[] defaultScores = {
        0, //SETTLEMENT
        0, //DOME
        0, //HYPERJUMP
        0, //RADIO
        200, //REINFORCED_SUIT
        50, //SURVIVAL_KIT
        200, //OXYGEN_TANK
        400, //PLANTS
    };
    int[] scoreMap;



    public RetrievePackageTask(C c, int[] scoreMap) {
        super(c);
        target = null;
        if (scoreMap == null) {
            this.scoreMap = defaultScores;
        } else {
            this.scoreMap = scoreMap;
        }

    }

    public RetrievePackageTask(C c) {
        this(c, null);
    }

    public static RetrievePackageTask createNearbyPackageTask(C c) {
        return new RetrievePackageTask(c, new int[]{
                0, //SETTLEMENT
                0, //DOME
                0, //HYPERJUMP
                0, //RADIO
                200, //REINFORCED_SUIT
                0, //SURVIVAL_KIT
                200, //OXYGEN_TANK
                400, //PLANTS
        });
    }

    @Override
    public void run() {
        if (target != null) {
            retreivePax();
        }
        int bestScore = Integer.MIN_VALUE;
        CarePackageInfo bestPax = null;
        for (CarePackageInfo pax: uc.senseCarePackages(c.visionRange)) {
            int dist = Vector2D.chebysevDistance(c.loc, pax.getLocation());
            if (dist > c.remainingSteps()) {
                continue;
            }
            int score = getScore(pax, dist);
            if (score > bestScore) {
                bestPax = pax;
                bestScore = score;
            }
        }
        if (bestPax != null) {
            target = bestPax;
            c.destination = bestPax.getLocation();
            c.logger.log("pax: %s - %s", target.getCarePackageType(), target.getLocation());
        } else {
            c.s.trySendAlert();
        }
    }

    private int getScore(CarePackageInfo pax, int dist) {
        int score = 1 - (dist * dist);
        CarePackage carePackageType = pax.getCarePackageType();
        if (carePackageType == CarePackage.PLANTS) {
            score += (1000 - uc.getRound());
        } else {
            score += scoreMap[carePackageType.ordinal()];
        }
        return score;
    }

    private void retreivePax() {
        Direction dir = c.loc.directionTo(target.getLocation());
        if (c.loc.distanceSquared(target.getLocation()) <= c.actionRange && uc.canPerformAction(ActionType.RETRIEVE, dir, 1)) {
            uc.performAction(ActionType.RETRIEVE, dir, 1);
            target = null;
        }
    }
}
