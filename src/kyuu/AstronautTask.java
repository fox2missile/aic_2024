package kyuu;

import aic2024.user.*;
import kyuu.message.*;
import kyuu.tasks.*;

public class AstronautTask extends Task {

    Task moveTask;

//    NaivePathFinder pathFinder;

    Task scanSectorTask;
    Task retrievePaxTask;

    SeekSymmetryCommand currentSymmetryCmd;
    RetrievePackageCommand currentRetPaxCmd;
    DefenseCommand currentDefCmd;
    Task currentSurveyTask;
    Task currentExpansionWorkerTask;

    private final int initialOxygen;

    boolean attackStarted;

    public AstronautTask(C c) {
        super(c);
//        pathFinder = new NaivePathFinder(c);
        moveTask = new MoveTask(c);
        scanSectorTask = new ScanSectorTask(c);
        retrievePaxTask = new RetrievePackageTask(c, c.visionRange);
        rdb.subscribeSeekSymmetryCommand = true;
        rdb.subscribeEnemyHq = true;
        rdb.subscribePackageRetrievalCommand = true;
        rdb.subscribeSurveyCommand = true;
        rdb.subscribeExpansionCommand = true;
        rdb.subscribeDefenseCommand = true;
        initialOxygen = (int)Math.floor(uc.getAstronautInfo().getOxygen());
        c.logger.log("Spawn");
        attackStarted = false;
    }

    @Override
    public void run() {
        c.s.scan();

        rdb.resetEnemyHq();

        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.opponent);

        if (currentDefCmd == null && uc.senseStructures(c.visionRange, c.team).length > 0 && enemies.length > 0) {
            int nearestIdx = Vector2D.getNearest(c.loc, enemies, enemies.length);
            currentDefCmd = new DefenseCommand(enemies[nearestIdx].getLocation());
        }

        Message msg = rdb.retrieveNextMessage();
        while (msg != null) {
            if (msg instanceof SeekSymmetryCommand && rdb.enemyHqSize == 0) {
                currentSymmetryCmd = (SeekSymmetryCommand)msg;
            } else if (msg instanceof EnemyHqMessage) {
                currentSymmetryCmd = null;
            } else if (msg instanceof RetrievePackageCommand) {
                currentRetPaxCmd = (RetrievePackageCommand) msg;
            } else if (msg instanceof DefenseCommand) {
                currentDefCmd = (DefenseCommand) msg;
            } else if (msg instanceof SurveyCommand) {
                currentSurveyTask = new SurveyTask(c, (SurveyCommand) msg);
            } else if (msg instanceof ExpansionCommand) {
                currentExpansionWorkerTask = new ExpansionWorkerTask(c, ((ExpansionCommand) msg), retrievePaxTask);
            }
            msg = rdb.retrieveNextMessage();
        }
        uc.cleanBroadcastBuffer();
        if (uc.getAstronautInfo().isBeingConstructed()) {
            return;
        }

        seekUnknownEnemyHq();

        if (currentDefCmd != null) {
            handleDefense();
        } else if (currentSymmetryCmd != null) {
            handleSymmetrySeekCmd();
        } else if (currentRetPaxCmd != null) {
            if (!handleRetrievePax()) {
                if (currentExpansionWorkerTask != null) {
                    currentExpansionWorkerTask.run();
                } else {
                    handleFreeRoam();
                }
            }
        }  else if (currentSurveyTask != null) {
            currentSurveyTask.run();
        }   else if (currentExpansionWorkerTask != null) {
            currentExpansionWorkerTask.run();
        } else if (rdb.enemyHqSize > 0) {
            if (!attackEnemyHq()) {
                handleFreeRoam();
            }
        } else {
            handleFreeRoam();
        }

//        pathFinder.initTurn();
        if (c.destination != null) {
//            pathFinder.move(c.destination);
            moveTask.run();
            c.uc.drawLineDebug(c.uc.getLocation(), c.destination, 0, 255, 0);
            c.destination = null;
        }
    }

    private void seekUnknownEnemyHq() {
        for (StructureInfo s: uc.senseStructures(c.visionRange, c.team.getOpponent())) {
            if (s.getType() == StructureType.HQ) {
                if (rdb.addEnemyHq(s.getLocation())) {
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(s.getLocation(), dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ));
                }
            }
        }
    }


    private void handleFreeRoam() {
        retrievePaxTask.run();
        if (c.destination == null) {
            scanSectorTask.run();
        }
    }

    private void handleSymmetrySeekCmd() {
        if (!uc.canSenseLocation(currentSymmetryCmd.target)) {
            if (uc.getAstronautInfo().getOxygen() < 2) {
                SeekSymmetryComplete msg = new SeekSymmetryComplete(currentSymmetryCmd.target, dc.SYMMETRIC_SEEKER_COMPLETE_FAILED);
                rdb.sendSeekSymmetryCompleteMsg(msg);
                c.destination = null;
                currentSymmetryCmd = null;
            } else {
                c.destination = currentSymmetryCmd.target;
            }
        } else {
            StructureInfo s = uc.senseStructure(currentSymmetryCmd.target);
            int status = s != null && s.getType() == StructureType.HQ && s.getTeam() != c.team ? dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ : dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING;
            SeekSymmetryComplete msg = new SeekSymmetryComplete(currentSymmetryCmd.target, status);
            rdb.sendSeekSymmetryCompleteMsg(msg);
            c.destination = null;
            currentSymmetryCmd = null;
        }
    }


    private boolean handleRetrievePax() {
        if (uc.canSenseLocation(currentRetPaxCmd.target) && uc.senseCarePackage(currentRetPaxCmd.target) == null) {
            currentRetPaxCmd = null;
            return false;
        }
        if (c.loc.distanceSquared(currentRetPaxCmd.target) <= c.actionRange && uc.canPerformAction(ActionType.RETRIEVE, c.loc.directionTo(currentRetPaxCmd.target), 1)) {
            uc.performAction(ActionType.RETRIEVE, c.loc.directionTo(currentRetPaxCmd.target), 1);
            currentRetPaxCmd = null;
        } else {
            c.destination = currentRetPaxCmd.target;
        }
        return true;
    }

    private void handleDefense() {
        // attack anyone nearby
        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.team.getOpponent());
        if (enemies.length > 0) {
            int nearestIdx = Vector2D.getNearest(c.loc, enemies, enemies.length);
            c.destination = enemies[nearestIdx].getLocation();
            if (c.loc.distanceSquared(c.destination) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1)) {
                uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1);
            }
        }
        c.destination = currentDefCmd.target;
        if (Vector2D.manhattanDistance(c.loc, currentDefCmd.target) <= 3 && enemies.length == 0) {
            currentDefCmd = null;
        }
    }

    private boolean attackEnemyHq() {

        if (!attackStarted && uc.senseStructures(c.actionRange * 4, c.team).length == 0 && uc.senseStructures(c.visionRange, c.team).length >= 1) {
            int countAttackers = 0;
            for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.team)) {
                if (a.getCarePackage() == CarePackage.REINFORCED_SUIT && !a.isBeingConstructed()) {
                    countAttackers++;
                }
            }
            attackStarted = true;
            if (countAttackers < 6) {
                c.logger.log("waiting for friends");
                c.destination = null;
                return true;
            }
        }

//        if (initialOxygen < 75) {
//            for (CarePackageInfo pax: uc.senseCarePackages(c.actionRange)) {
//                if (pax.getCarePackageType() == CarePackage.OXYGEN_TANK && uc.canPerformAction(ActionType.RETRIEVE, c.loc.directionTo(pax.getLocation()), 1)) {
//                    uc.performAction(ActionType.RETRIEVE, c.loc.directionTo(pax.getLocation()), 1);
//                }
//            }
//        }

        int nearestHqIdx = Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location target = rdb.enemyHq[nearestHqIdx];
        int dist = Vector2D.chebysevDistance(c.loc, target);
        if (dist > 5 && Vector2D.chebysevDistance(c.loc, target) > uc.getAstronautInfo().getOxygen()) {
            return false;
        }

        if (uc.canSenseLocation(target)) {
            if (uc.senseStructure(target) == null) {
                rdb.sendEnemyHqDestroyedMessage(target);
                return true;
            }
            while (c.loc.distanceSquared(target) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1)) {
                uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1);
            }
        }

        // attack anyone nearby
        AstronautInfo[] enemies = uc.senseAstronauts(c.actionRange, c.team.getOpponent());
        if (enemies.length > 0) {
            int attackIdx = -1;
            for (int i = 0; i < enemies.length; i++) {
                if (enemies[i].getLocation().distanceSquared(target) <= c.actionRange * 2 && c.loc.distanceSquared(enemies[i].getLocation()) <= c.actionRange) {
                    attackIdx = i;
                    break;
                }
            }
            if (attackIdx != -1) {
                c.destination = enemies[attackIdx].getLocation();
                if (c.loc.distanceSquared(c.destination) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1)) {
                    uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1);
                }
                if (c.canMove(c.loc.directionTo(c.destination))) {
                    c.move(c.loc.directionTo(c.destination));
                    while (c.loc.distanceSquared(target) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1)) {
                        uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1);
                    }
                }
            }

        }

        c.destination = target;
        if (c.loc.distanceSquared(target) <= c.actionRange) {
            c.destination = null;
        }


        return true;
    }
}
