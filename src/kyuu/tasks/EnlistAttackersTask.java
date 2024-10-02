package kyuu.tasks;

import aic2024.user.CarePackage;
import aic2024.user.Direction;
import aic2024.user.Location;
import kyuu.C;
import kyuu.Vector2D;

public class EnlistAttackersTask extends Task {


    int maxReinforcedSuits;
    int originalMaxReinforcedSuits;

    boolean prevDeployReinforcedSuits;

    public EnlistAttackersTask(C c) {
        super(c);
        maxReinforcedSuits = 0;
        for (Direction dir: c.allDirs) {
            Location check = c.loc.add(dir);
            if (!uc.isOutOfMap(check) && uc.canSenseLocation(check) && !c.isObstacle(uc.senseObjectAtLocation(check))) {
                maxReinforcedSuits++;
            }
        }
        originalMaxReinforcedSuits = maxReinforcedSuits;
        if (maxReinforcedSuits > 4) {
            maxReinforcedSuits = 4;
        }
        prevDeployReinforcedSuits = false;
    }

    @Override
    public void run() {
        enlistAttackers();
    }


    private void enlistAttackers() {
        if (rdb.enemyHqSize == 0) {
            return;
        }
        int nearestEnemyHqIdx = Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location targetHq = rdb.enemyHq[nearestEnemyHqIdx];
        boolean enoughSuits = uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) >= maxReinforcedSuits;
        int singleAtkCost = (Vector2D.manhattanDistance(c.loc, targetHq) * 5 / 4) + 32;
        int boomCost = singleAtkCost * maxReinforcedSuits;

        if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) >= maxReinforcedSuits - 1) {
            ldb.minReserveOxygen = boomCost;
        }

        boolean enoughResources = enoughSuits && uc.getStructureInfo().getOxygen() >= boomCost;

        int countEnlist = 0;
        for (Direction dir: c.getFirstDirs(c.loc.directionTo(targetHq))) {
            if (c.uc.canEnlistAstronaut(dir, singleAtkCost, CarePackage.REINFORCED_SUIT)) {
                countEnlist++;
            }
        }

        boolean canBoom = (enoughResources && countEnlist >= (maxReinforcedSuits - 2)) || prevDeployReinforcedSuits;

        if (!c.s.isReachableDirectly(targetHq) && !canBoom) {
            prevDeployReinforcedSuits = false;
            return;
        }

        c.logger.log("enlisting attackers with cost %d", singleAtkCost);
        for (Direction dir: c.getFirstDirs(c.loc.directionTo(targetHq))) {
            if (c.uc.canEnlistAstronaut(dir, singleAtkCost, CarePackage.REINFORCED_SUIT)) {
                c.uc.enlistAstronaut(dir, singleAtkCost, CarePackage.REINFORCED_SUIT);
            }
        }
        prevDeployReinforcedSuits = true;
        maxReinforcedSuits = originalMaxReinforcedSuits;
    }
}
