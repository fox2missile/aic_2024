package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.DomeDestroyedNotification;
import kyuu.message.ExpansionCommand;
import kyuu.message.ExpansionEstablishedMessage;
import kyuu.message.ExpansionMissedMessage;

public class ExpansionWorkerTask extends Task {

    ExpansionCommand cmd;
    Task paxRetrievalTask;
    Task nearbyPaxRetrievalTask;
    Direction expansionDirection;
    Task plantsGatheringTask;

    boolean reachedTarget;

    int srcIter;

    Location currentTarget;


    public ExpansionWorkerTask(C c, ExpansionCommand cmd, Task paxRetrievalTask, Task plantsGatheringTask) {
        super(c);
        for (Direction dir: c.allDirs) {
            if (uc.canSenseLocation(c.loc.add(dir)) && uc.senseStructures(c.actionRange, c.team) != null) {
                expansionDirection = c.loc.add(dir).directionTo(cmd.target);
                break;
            }
        }
        this.plantsGatheringTask = plantsGatheringTask;
        this.paxRetrievalTask = paxRetrievalTask;
        this.nearbyPaxRetrievalTask = RetrievePackageTask.createNearbyPackageTask(c);
        this.cmd = cmd;
        this.reachedTarget = false;

        if (cmd.sources.length >= 2) {
            this.currentTarget = cmd.sources[1];
            srcIter = 1;
        } else {
            this.currentTarget = cmd.target;
        }
    }


    @Override
    public void run() {
        if (uc.senseStructures(c.visionRange, c.team).length == 0 && uc.getRound() < 500) {
            plantsGatheringTask.run();
            if (c.destination != null) {
                return;
            }
        }
        c.s.trySendAlert();
        c.logger.log("expansion worker %s", cmd.target);
        if (cmd.state == dc.EXPANSION_STATE_INIT) {
            handleInit();
        } else {
            handleEstablished();
        }
    }

    private void handleEstablished() {
        c.destination = null;


        if (cmd.state == dc.EXPANSION_STATE_HAS_DOME && Vector2D.chebysevDistance(cmd.target, c.loc) < 3 && c.remainingSteps() < 5) {
            boolean domeFound = false;
            for (Location loc: uc.senseObjects(MapObject.DOME, c.visionRange)) {
                if (Vector2D.chebysevDistance(cmd.target, loc) < 3) {
                    domeFound = true;
                }
            }
            if (!domeFound) {
                rdb.sendDomeDestroyedMsg(new DomeDestroyedNotification(cmd.target, cmd.expansionId));
            }

        }

        if (c.remainingSteps() < 5 || reachedTarget) {
            paxRetrievalTask.run();
        }
        if (c.destination == null && c.remainingSteps() < 10) {
            nearbyPaxRetrievalTask.run();
        }

        if (c.remainingSteps() <= 1) {
            int bestScore = c.loc.distanceSquared(currentTarget);
            Direction bestDir = Direction.ZERO;
            if (!uc.canPerformAction(ActionType.TERRAFORM, bestDir, 1)) {
                bestScore = -1;
            }

            // nearer distance -> lower score

            Direction check = c.loc.directionTo(currentTarget);
            int score = c.loc.add(check).distanceSquared(currentTarget);
            if (score > bestScore && uc.canPerformAction(ActionType.TERRAFORM, check, 1)) {
                bestScore = score;
                bestDir = check;
            }

            check = c.loc.directionTo(currentTarget).opposite();
            score = c.loc.add(check).distanceSquared(currentTarget);
            if (score > bestScore && uc.canPerformAction(ActionType.TERRAFORM, check, 1)) {
//                bestScore = score;
                bestDir = check;
            }

            if (uc.canPerformAction(ActionType.TERRAFORM, bestDir, 1)) {
                uc.performAction(ActionType.TERRAFORM, bestDir, 1);
            } else {
//                for (Direction dir: c.getFirstDirs(expansionDirection.opposite())) {
//                    if (uc.canPerformAction(ActionType.TERRAFORM, dir, 1)) {
//                        uc.performAction(ActionType.TERRAFORM, dir, 1);
//                    }
//                }
            }

            rdb.sendExpansionMissedMsg(new ExpansionMissedMessage(cmd.target, cmd.expansionId));
        }

        if (!currentTarget.equals(cmd.target) && Vector2D.chebysevDistance(c.loc, currentTarget) <= 2 && !currentTarget.equals(cmd.possibleNext)) {
            srcIter++;
            if (srcIter < cmd.sources.length) {
                currentTarget = cmd.sources[srcIter];
            } else {
                currentTarget = cmd.target;
            }
        }


        if (currentTarget.equals(cmd.target) && Vector2D.chebysevDistance(c.loc, cmd.target) <= 3) {
            reachedTarget = true;
            paxRetrievalTask.run();
            if (c.destination == null && cmd.possibleNext != null) {
                currentTarget = cmd.possibleNext;
            }
        }



        if (c.destination == null) {
            c.destination = currentTarget;
        }

    }

    private boolean valuableTargetFound() {
        for (CarePackageInfo pax: uc.senseCarePackages(c.visionRange)) {
            if (pax.getCarePackageType() == CarePackage.OXYGEN_TANK || pax.getCarePackageType() == CarePackage.SETTLEMENT || pax.getCarePackageType() == CarePackage.REINFORCED_SUIT ||
                    (pax.getCarePackageType() == CarePackage.PLANTS && c.remainingSteps() <= 7)) {
                return true;
            }
        }
        return false;
    }
    
    private void handleInit() {
        if (c.loc.equals(cmd.target) || (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseTileType(cmd.target)))) {
            if (uc.senseObjectAtLocation(c.loc) == MapObject.TERRAFORMED) {
                // todo: next 1 or 2 worker will do the same thing..
                rdb.sendExpansionEstablishedMsg(new ExpansionEstablishedMessage(cmd.target, cmd.expansionId));
                return;
            }
        }

        c.destination = cmd.target;
        if (!c.loc.equals(c.startLoc) && uc.senseObjectAtLocation(c.loc) != MapObject.TERRAFORMED && uc.canPerformAction(ActionType.TERRAFORM, Direction.ZERO, 1)) {
            uc.performAction(ActionType.TERRAFORM, Direction.ZERO, 1);
        }
    }
}
