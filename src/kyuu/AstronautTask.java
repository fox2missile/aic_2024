package kyuu;

import aic2024.user.*;
import kyuu.message.*;
import kyuu.tasks.*;

public class AstronautTask extends Task {

    Task moveTask;

    Task scanSectorTask;
    Task retrievePaxTask;

    SeekSymmetryCommand currentSymmetryCmd;
    RetrievePackageCommand currentRetPaxCmd;
    DefenseTask currentDefTask;
    BuildDomeCommand currentBuildDomeCmd;
    BuildHyperJumpCommand currentBuildHyperJumpCmd;

    DomeBuiltNotification latestDomeBuiltNotification;
    Task currentSurveyTask;
    Task currentExpansionWorkerTask;

    private final int initialOxygen;

    boolean attackStarted;

    public AstronautTask(C c) {
        super(c);
        moveTask = new MoveTask(c);
        scanSectorTask = new ScanSectorTask(c);
        retrievePaxTask = new RetrievePackageTask(c);
        rdb.subscribeSeekSymmetryCommand = true;
        rdb.subscribeEnemyHq = true;
        rdb.subscribePackageRetrievalCommand = true;
        rdb.subscribeSurveyCommand = true;
        rdb.subscribeExpansionCommand = true;
        rdb.subscribeDefenseCommand = true;
        rdb.subscribeBuildDomeCommand = true;
        rdb.subscribeDomeBuilt = true;
        rdb.subscribeBuildHyperJumpCmd = true;
        initialOxygen = (int)Math.floor(uc.getAstronautInfo().getOxygen());
        c.logger.log("Spawn");
        attackStarted = false;
    }

    @Override
    public void run() {
        c.s.scan();
        int round = uc.getRound();

        rdb.resetEnemyHq();

        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.opponent);

        if (currentBuildHyperJumpCmd == null && currentBuildDomeCmd == null && currentSurveyTask == null && currentDefTask == null && uc.senseStructures(c.visionRange, c.team).length > 0 && enemies.length > 0) {
            int nearestIdx = Vector2D.getNearest(c.loc, enemies, enemies.length);
            currentDefTask = new DefenseTask(c, new DefenseCommand(enemies[nearestIdx].getLocation()));
        }

        InquireDomeMessage currentInquireDomeMsg = null;

        Message msg = rdb.retrieveNextMessage();
        while (msg != null) {
            if (msg instanceof SeekSymmetryCommand && rdb.enemyHqSize == 0) {
                currentSymmetryCmd = (SeekSymmetryCommand)msg;
            } else if (msg instanceof EnemyHqMessage) {
                if (currentSymmetryCmd != null) {
                    EnemyHqMessage enemyHq = (EnemyHqMessage) msg;
                    if (enemyHq.target.equals(currentSymmetryCmd.target)) {
                        currentSymmetryCmd = null;
                    }
                }
            } else if (msg instanceof RetrievePackageCommand) {
                currentRetPaxCmd = (RetrievePackageCommand) msg;
            } else if (msg instanceof DefenseCommand) {
                currentDefTask = new DefenseTask(c, (DefenseCommand) msg);
            } else if (msg instanceof SurveyCommand) {
                currentSurveyTask = new SurveyTask(c, (SurveyCommand) msg);
            } else if (msg instanceof ExpansionCommand) {
                currentExpansionWorkerTask = new ExpansionWorkerTask(c, ((ExpansionCommand) msg), retrievePaxTask);
            } else if (msg instanceof BuildDomeCommand) {
                currentBuildDomeCmd = (BuildDomeCommand) msg;
            } else if (msg instanceof BuildHyperJumpCommand) {
                currentBuildHyperJumpCmd = (BuildHyperJumpCommand) msg;
            } else if (msg instanceof InquireDomeMessage) {
                currentInquireDomeMsg = (InquireDomeMessage) msg;
            } else if (msg instanceof DomeBuiltNotification) {
                latestDomeBuiltNotification = (DomeBuiltNotification) msg;
            }
            msg = rdb.retrieveNextMessage();
        }
        uc.cleanBroadcastBuffer();
        if (uc.getAstronautInfo().isBeingConstructed()) {
            return;
        }

        seekUnknownEnemyHq();

        doActions();

        Location prevLoc = c.loc;


        moveTask.run();
        c.destination = null;

        if (!c.loc.equals(prevLoc)) {
            // second runs
            doActions();
        }

        moveTask.run();
        c.destination = null;

        if (uc.getRound() != round) {
            return;
        }

        // report dome
        if (currentInquireDomeMsg != null && latestDomeBuiltNotification == null) {
            boolean canReport = uc.getAstronautInfo().getCarePackage() == null;
            canReport = canReport && currentDefTask == null;
            canReport = canReport && currentRetPaxCmd == null;
            canReport = canReport && currentSurveyTask == null;
            canReport = canReport && currentSymmetryCmd == null;
            canReport = canReport && uc.getAstronautInfo().getOxygen() < 10;
            if (canReport) {
                    rdb.sendDomeBuiltMsg(new DomeBuiltNotification(currentInquireDomeMsg.target));
            }
        }
    }

    private void doActions() {
        if (currentDefTask != null) {
            currentDefTask.run();
            if (currentDefTask.isFinished()) {
                currentDefTask = null;
            }
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
        } else if (currentSurveyTask != null) {
            currentSurveyTask.run();
        } else if (currentExpansionWorkerTask != null) {
            currentExpansionWorkerTask.run();
        } else if (currentBuildDomeCmd != null) {
            handleBuildDome();
        } else if (currentBuildHyperJumpCmd != null) {
            handleBuildHyperJump();
        } else if (rdb.enemyHqSize > 0) {
            if (uc.getAstronautInfo().getCarePackage() == CarePackage.REINFORCED_SUIT) {
                if (!attackEnemyHq()) {
                    handleFreeRoam();
                }
            } else {
                handleSuppression();
            }

        } else {
            handleFreeRoam();
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

    private void handleBuildDome() {
        if (Vector2D.chebysevDistance(c.loc, currentBuildDomeCmd.target) > 2 && c.remainingSteps() > 1) {
            c.destination = currentBuildDomeCmd.target;
            return;
        }

        if (c.loc.equals(currentBuildDomeCmd.target)) {
            if (uc.canPerformAction(ActionType.BUILD_DOME, Direction.ZERO, 1)) {
                uc.performAction(ActionType.BUILD_DOME, Direction.ZERO, 1);
                return;
            }
            for (Direction dir: c.allDirs) {
                if (uc.canPerformAction(ActionType.BUILD_DOME, dir, 1)) {
                    uc.performAction(ActionType.BUILD_DOME, dir, 1);
                    return;
                }
            }
        }

        for (Direction dir: c.getFirstDirs(c.loc.directionTo(currentBuildDomeCmd.target))) {
            if (uc.canPerformAction(ActionType.BUILD_DOME, dir, 1)) {
                uc.performAction(ActionType.BUILD_DOME, dir, 1);
                return;
            }
        }
    }

    private void handleBuildHyperJump() {
        c.destination = currentBuildHyperJumpCmd.target;

        if (c.loc.equals(currentBuildHyperJumpCmd.target)) {
            if (uc.canPerformAction(ActionType.BUILD_HYPERJUMP, Direction.ZERO, 1)) {
                uc.performAction(ActionType.BUILD_HYPERJUMP, Direction.ZERO, 1);
                c.logger.log("Built hyper jump!");
                return;
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
        if (dist > 5 && Vector2D.chebysevDistance(c.loc, target) > c.remainingSteps()) {
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

    private void handleSuppression() {
        c.destination = null;
        int nearestHqIdx = Vector2D.getNearestChebysev(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location target = rdb.enemyHq[nearestHqIdx];


        if (uc.canSenseLocation(target)) {
            if (uc.senseStructure(target) == null) {
                rdb.sendEnemyHqDestroyedMessage(target);
                return;
            }
            while (c.loc.distanceSquared(target) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1)) {
                uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1);
            }
        }

        // attack anyone nearby
        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.team.getOpponent());
        if (enemies.length > 0) {
            int attackIdx = -1;
            int highestValue = 0;
            for (int i = 0; i < enemies.length; i++) {
                int value = (int) Math.ceil(enemies[i].getOxygen());
                CarePackage pax = enemies[i].getCarePackage();
                if (pax == CarePackage.REINFORCED_SUIT) {
                    value *= value;
                } else if (pax != null) {
                    value += 10;
                }
                if (value > highestValue) {
                    attackIdx = i;
                    highestValue = value;
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

        if (c.remainingSteps() < 5 && c.destination == null &&  Vector2D.chebysevDistance(c.loc, target) > c.remainingSteps()) {
            retrievePaxTask.run();
            if (c.destination == null) {
                c.destination = target;
            }
        } else {
            c.destination = target;
        }

        if (c.remainingSteps() <= 1) {
            int bestScore = c.loc.distanceSquared(target);
            Direction bestDir = Direction.ZERO;
            if (!uc.canPerformAction(ActionType.TERRAFORM, bestDir, 1)) {
                bestScore = -1;
            }

            // nearer distance -> lower score

            Direction check = c.loc.directionTo(target);
            int score = c.loc.add(check).distanceSquared(target);
            if (score > bestScore && uc.canPerformAction(ActionType.TERRAFORM, check, 1)) {
                bestScore = score;
                bestDir = check;
            }

            check = c.loc.directionTo(target).opposite();
            score = c.loc.add(check).distanceSquared(target);
            if (score > bestScore && uc.canPerformAction(ActionType.TERRAFORM, check, 1)) {
//                bestScore = score;
                bestDir = check;
            }

            if (uc.canPerformAction(ActionType.TERRAFORM, bestDir, 1)) {
                uc.performAction(ActionType.TERRAFORM, bestDir, 1);
            } else {
                for (Direction dir: c.getFirstDirs(c.loc.directionTo(target).opposite())) {
                    if (uc.canPerformAction(ActionType.TERRAFORM, dir, 1)) {
                        uc.performAction(ActionType.TERRAFORM, dir, 1);
                    }
                }
            }

        }

    }
}
