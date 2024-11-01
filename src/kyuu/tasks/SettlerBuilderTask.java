package kyuu.tasks;

import aic2024.user.ActionType;
import aic2024.user.AstronautInfo;
import aic2024.user.Direction;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.SettlementCommand;

public class SettlerBuilderTask extends SettlerTask {

    public SettlerBuilderTask(C c, SettlementCommand cmd) {
        super(c, cmd);
    }

    @Override
    public void run() {
        if (!deployed) {
            if (!plannedMoveTask.isFinished()) {
                plannedMoveTask.run();
            } else {
                c.destination = cmd.target;
            }

            if (uc.senseStructures(c.visionRange, c.team).length == 0) {
                deployed = true;
            }
        }

        if (!deployed) {
            return;
        }

        int enemyRangeTolerance = 32;
        if (uc.senseStructures(c.visionRange, c.team).length > 0) {
            enemyRangeTolerance = 8;
        }

        if (uc.senseAstronauts(enemyRangeTolerance, c.opponent).length > 0 || uc.getAstronautInfo().getOxygen() <= 1) {
            buildSettlementNow();
            return;
        }

        AstronautInfo oxygenCarrier = null;
        for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.team)) {
            if (a.getID() == cmd.companionId) {
                oxygenCarrier = a;
                break;
            }
        }

        if (oxygenCarrier == null) {
            c.destination = c.loc;
            return;
        }

        if (c.loc.distanceSquared(oxygenCarrier.getLocation()) > c.actionRange) {
            c.destination = c.loc;
            return;
        }

        if (!plannedMoveTask.isFinished()) {
            plannedMoveTask.run();
        } else {
            c.destination = cmd.target;
        }

        if (c.loc.equals(cmd.target) || (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseObjectAtLocation(cmd.target)) && Vector2D.chebysevDistance(c.loc, cmd.target) <= 3)) {
            buildSettlementNow();
        }
    }

    private void buildSettlementNow() {
        for (Direction dir: c.allDirsZero) {
            if (uc.canPerformAction(ActionType.BUILD_SETTLEMENT, dir, 1)) {
                uc.performAction(ActionType.BUILD_SETTLEMENT, dir, 1);
                return;
            }
        }
    }
}
