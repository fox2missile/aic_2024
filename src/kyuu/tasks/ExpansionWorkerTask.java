package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.ExpansionCommand;
import kyuu.message.ExpansionEstablishedMessage;

public class ExpansionWorkerTask extends Task {

    ExpansionCommand cmd;
    Task paxRetrievalTask;
    Task nearbyPaxRetrievalTask;
    Direction expansionDirection;

    boolean reachedTarget;
    boolean deepExpansion;

    boolean reachedDeepExpansion;

    Location deepExpansionTarget;

    Direction deepExpansionDirection;

    int deepExpansionExpandRate;

    public ExpansionWorkerTask(C c, ExpansionCommand cmd, Task paxRetrievalTask) {
        super(c);
        for (Direction dir: c.allDirs) {
            if (uc.canSenseLocation(c.loc.add(dir)) && uc.senseStructures(c.actionRange, c.team) != null) {
                expansionDirection = c.loc.add(dir).directionTo(cmd.target);
                break;
            }
        }
        this.paxRetrievalTask = paxRetrievalTask;
        this.nearbyPaxRetrievalTask = new RetrievePackageTask(c, c.visionRange / 4);
        this.cmd = cmd;
        this.reachedTarget = false;
        this.deepExpansion = uc.getAstronautInfo().getOxygen() >= 20;

        if (deepExpansion) {
            c.logger.log("deep expansion worker %s", cmd.target);
            deepExpansionExpandRate = 5;
            setupDeepExpansion();
        } else {
            c.logger.log("expansion worker %s", cmd.target);
        }

    }

    private void setupDeepExpansion() {
        this.reachedDeepExpansion = false;
        if (deepExpansionExpandRate <= 0) {
            c.logger.log("cannot expand anymore!");
            this.reachedDeepExpansion = true;
            return;
        }
        Direction[] firstDirs = c.getFirstDirs(expansionDirection);
        int deepExpansionDirectionChoose = c.seed % deepExpansionExpandRate;
        for (int i = 0; i < deepExpansionExpandRate; i++) {
            Direction deepDir = firstDirs[deepExpansionDirectionChoose];
            deepExpansionTarget = cmd.target.add(deepDir.dx * 10, deepDir.dy * 10);
            deepExpansionDirection = deepDir;
            if (uc.isOutOfMap(deepExpansionTarget)) {
                deepExpansionDirectionChoose = (deepExpansionDirectionChoose + 1) % deepExpansionExpandRate;
                continue;
            }
            break;
        }
        deepExpansionExpandRate -= 2;
    }

    @Override
    public void run() {
        if (cmd.state == dc.EXPANSION_STATE_INIT) {
            handleInit();
        } else {
            handleEstablished();
        }
    }

    private void handleEstablished() {
        if (deepExpansion) {
            handleDeepExpansion();
            return;
        }
        c.destination = null;
        nearbyPaxRetrievalTask.run();
        if (c.destination == null) {
            c.destination = cmd.target;
        }
        if (Vector2D.manhattanDistance(c.loc, cmd.target) <= 3) {
            paxRetrievalTask.run();
        }
    }

    private void handleDeepExpansion() {
        if (c.loc.equals(cmd.target) || (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseObjectAtLocation(cmd.target)) && Vector2D.manhattanDistance(c.loc, cmd.target) <= 3)) {
            reachedTarget = true;
        }
        if (!reachedTarget) {
            c.destination = cmd.target;
            return;
        }
        final float oxygen = uc.getAstronautInfo().getOxygen();
        if (oxygen <= 7 || (c.loc.equals(deepExpansionTarget) || (Vector2D.manhattanDistance(c.loc, deepExpansionTarget) <= 3 && uc.canSenseLocation(deepExpansionTarget) && c.isObstacle(uc.senseObjectAtLocation(deepExpansionTarget))))) {

            if (oxygen > 10) {
                setupDeepExpansion();
                c.logger.log("refresh deep expansion!");
            } else {
                reachedDeepExpansion = true;
                c.logger.log("reached deep expansion!");
            }

        }
        if (valuableTargetFound()) {
            reachedDeepExpansion = true;
            c.logger.log("reached deep expansion because found very important pax");
        }
        double chanceTerraform = 0.0;
        if (oxygen <= 3) {
            chanceTerraform = 1;
        } else if (oxygen == 4) {
            chanceTerraform = 0.9;
        } else if (oxygen == 5) {
            chanceTerraform = 0.8;
        } else if (oxygen == 6) {
            chanceTerraform = 0.7;
        } else if (oxygen == 7) {
            chanceTerraform = 0.6;
        } else if (oxygen == 8) {
            chanceTerraform = 0.5;
        } else if (oxygen == 9) {
            chanceTerraform = 0.4;
        } else if (oxygen == 10) {
            chanceTerraform = 0.3;
        }
        double dice = uc.getRandomDouble();
        if (chanceTerraform >= dice) {
            // terraform
            if (uc.canPerformAction(ActionType.TERRAFORM, Direction.ZERO, 1)) {
                uc.performAction(ActionType.TERRAFORM, Direction.ZERO, 1);
            }
        }
        if (!reachedDeepExpansion) {
            c.destination = deepExpansionTarget;
            return;
        }
        paxRetrievalTask.run();
    }

    private boolean valuableTargetFound() {
        for (CarePackageInfo pax: uc.senseCarePackages(c.visionRange)) {
            if (pax.getCarePackageType() == CarePackage.OXYGEN_TANK || pax.getCarePackageType() == CarePackage.SETTLEMENT || pax.getCarePackageType() == CarePackage.REINFORCED_SUIT ||
                    (pax.getCarePackageType() == CarePackage.PLANTS && uc.getAstronautInfo().getOxygen() <= 7)) {
                return true;
            }
        }
        return false;
    }
    
    private void handleInit() {
        if (c.loc.equals(cmd.target) || (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseObjectAtLocation(cmd.target)))) {
            if (uc.senseObjectAtLocation(c.loc) == MapObject.TERRAFORMED) {
                rdb.sendExpansionEstablishedMsg(new ExpansionEstablishedMessage(cmd.target, cmd.expansionId));
                return;
            }
        }

        c.destination = cmd.target;
        if (uc.senseObjectAtLocation(c.loc) != MapObject.TERRAFORMED && uc.canPerformAction(ActionType.TERRAFORM, Direction.ZERO, 1)) {
            uc.performAction(ActionType.TERRAFORM, Direction.ZERO, 1);
        }
    }
}
