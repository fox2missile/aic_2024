package kyuu;

import aic2024.user.*;
import kyuu.message.*;
import kyuu.tasks.*;

public class AstronautTask extends Task {

    Task moveTask;

    Task scanSectorTask;
    Task retrievePaxTask;

    RetrievePackageCommand currentRetPaxCmd;
    DefenseTask currentDefTask;
    BuildDomeTask currentBuildDomeTask;
    InquireDomeMessage currentInquireDomeMsg;
    BuildHyperJumpCommand currentBuildHyperJumpCmd;
    SuppressionCommand currentSuppressionCmd;
    SuppressionCommand defaultSuppressionCmd;

    DomeBuiltNotification latestDomeBuiltNotification;
    Task currentSurveyTask;
    Task currentExpansionWorkerTask;
    Task plantsGatheringTask;
    Task settlementPaxTask;
    Task currentSettlerTask;
    Task currentSymmetryTask;
    Direction spawnDir;

    private final int initialOxygen;

    boolean attackStarted;

    public AstronautTask(C c) {
        super(c);
        moveTask = new MoveTask(c);
        plantsGatheringTask = RetrievePackageTask.createPlantsGatheringTask(c);
        settlementPaxTask = RetrievePackageTask.createSettlementPaxGatheringTask(c);
        scanSectorTask = new ScanSectorTask(c);
        retrievePaxTask = new RetrievePackageTask(c);
        rdb.subscribeEnemyHq = true;
        rdb.subscribeSurveyCommand = true;
        rdb.subscribeExpansionCommand = true;
        rdb.subscribeDefenseCommand = true;
        rdb.subscribeBuildHyperJumpCmd = true;
        initialOxygen = (int)Math.floor(uc.getAstronautInfo().getOxygen());
        c.logger.log("Spawn");
        attackStarted = false;
        for (StructureInfo s: uc.senseStructures(c.actionRange, c.team)) {
            if (s.getType() == StructureType.HQ) {
                spawnDir = s.getLocation().directionTo(c.loc);
            }
        }

        setupReceivers();
    }

    private void setupReceivers() {
        rdb.seekSymmetryCommandReceiver = (int __) -> {
            int targetId = uc.pollBroadcast().getMessage();
            if (targetId != c.id) {
                uc.eraseBroadcastBuffer(dc.MSG_SIZE_SYMMETRIC_SEEKER_CMD - 1);
                return;
            }
            currentSymmetryTask = new SymmetrySeekerTask(c, new SeekSymmetryCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage())));
        };

        rdb.suppressionCommandReceiver = (int __) -> {
            int targetId = uc.pollBroadcast().getMessage();
            if (targetId != c.id) {
                uc.eraseBroadcastBuffer(dc.MSG_SIZE_SUPPRESSION_CMD - 1);
                return;
            }
            currentSuppressionCmd = new SuppressionCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
        };

        rdb.buildDomeCommandReceiver = (int __) -> {
            if (c.id == uc.pollBroadcast().getMessage()) {
                Location target = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                int expansionId = uc.pollBroadcast().getMessage();
                int pathSize = uc.pollBroadcast().getMessage();
                Location[] path = new Location[pathSize];
                for (int i = 0; i < pathSize; i++) {
                    path[i] = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                }
                uc.eraseBroadcastBuffer(2 * (dc.MAX_EXPANSION_DEPTH - pathSize));
                currentBuildDomeTask = new BuildDomeTask(c, new BuildDomeCommand(target, expansionId, path));
                currentDefTask = null;
            } else {
                uc.eraseBroadcastBuffer(dc.MSG_SIZE_BUILD_DOME_CMD - 1); // -1 ID
            }
        };

        rdb.settlementCommandReceiver = (int __) -> {
            if (c.id == uc.pollBroadcast().getMessage()) {
                int companionId = uc.pollBroadcast().getMessage();
                Location target = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                int pathSize = uc.pollBroadcast().getMessage();
                Location[] path = new Location[pathSize];
                for (int i = 0; i < pathSize; i++) {
                    path[i] = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                }
                uc.eraseBroadcastBuffer(2 * (dc.MAX_EXPANSION_DEPTH - pathSize));
                SettlementCommand cmd = new SettlementCommand(target, companionId, path);
                if (uc.getAstronautInfo().getCarePackage() == CarePackage.SETTLEMENT) {
                    currentSettlerTask = new SettlerBuilderTask(c, cmd);
                } else {
                    currentSettlerTask = new SettlerOxygenTask(c, cmd);
                }
                currentDefTask = null;
            } else {
                uc.eraseBroadcastBuffer(dc.MSG_SIZE_SETTLEMENT_CMD - 1); // -1 ID
            }
        };

        rdb.domeInquiryReceiver = (int __) -> {
            Location inquiryLoc = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
            if (uc.canSenseLocation(inquiryLoc)) {
                for (Location loc: uc.senseObjects(MapObject.DOME, c.visionRange)) {
                    if (Vector2D.chebysevDistance(loc, inquiryLoc) < 3) {
                        currentInquireDomeMsg = new InquireDomeMessage(inquiryLoc);
                    }
                }
            }
        };

        rdb.domeBuiltReceiver = (int fullMsg) -> {
            DomeBuiltNotification notif = rdb.parseDomeBuiltNotification(fullMsg);
            if (!uc.canSenseLocation(notif.target)) {
                latestDomeBuiltNotification = notif;
            }
        };

        rdb.retrievePackageCommandReceiver = (int __) -> {
            if (c.id == uc.pollBroadcast().getMessage()) {
                currentRetPaxCmd = new RetrievePackageCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
            } else {
                uc.eraseBroadcastBuffer(dc.MSG_SIZE_GET_PACKAGES_CMD - 1); // -1 ID
            }
        };
    }

    @Override
    public void run() {
        c.s.scan();
        int round = uc.getRound();

        rdb.resetEnemyHq();

        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.opponent);

        if (currentSettlerTask == null && currentBuildHyperJumpCmd == null && currentBuildDomeTask == null && currentSurveyTask == null && currentDefTask == null && uc.senseStructures(c.visionRange, c.team).length > 0 && enemies.length > 0) {
            int nearestIdx = Vector2D.getNearest(c.loc, enemies, enemies.length);
            currentDefTask = new DefenseTask(c, new DefenseCommand(enemies[nearestIdx].getLocation()));
        }

        latestDomeBuiltNotification = null;

        Message msg = rdb.retrieveNextMessage();
        while (msg != null) {
            if (msg instanceof DefenseCommand) {
                currentDefTask = new DefenseTask(c, (DefenseCommand) msg);
            } else if (msg instanceof SurveyCommand) {
                currentSurveyTask = new SurveyTask(c, (SurveyCommand) msg, plantsGatheringTask);
            } else if (msg instanceof ExpansionCommand) {
                currentExpansionWorkerTask = new ExpansionWorkerTask(c, ((ExpansionCommand) msg), retrievePaxTask, plantsGatheringTask, settlementPaxTask);
            } else if (msg instanceof BuildHyperJumpCommand) {
                currentBuildHyperJumpCmd = (BuildHyperJumpCommand) msg;
            }
            msg = rdb.retrieveNextMessage();
        }
        uc.cleanBroadcastBuffer();
        if (uc.getAstronautInfo().isBeingConstructed()) {
            return;
        }

        seekUnknownEnemyHq();

        if (rdb.enemyHqSize > 0) {
            defaultSuppressionCmd = new SuppressionCommand(rdb.enemyHq[Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize)]);
        }

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
            // bytecode problem
            return;
        }

        // report dome
        if (currentInquireDomeMsg != null && latestDomeBuiltNotification == null) {
            boolean canReport = uc.getAstronautInfo().getCarePackage() == null;
            canReport = canReport && currentDefTask == null;
            canReport = canReport && currentRetPaxCmd == null;
            canReport = canReport && currentSurveyTask == null;
            canReport = canReport && currentSymmetryTask == null;
            canReport = canReport && uc.getAstronautInfo().getOxygen() < 10;
            if (canReport) {
                rdb.sendDomeBuiltMsg(new DomeBuiltNotification(currentInquireDomeMsg.target));
            }
            currentInquireDomeMsg = null;
        }
    }

    private void doActions() {
        if (currentSymmetryTask != null && currentSymmetryTask.isFinished()) {
            currentSymmetryTask = null;
        }

        if (currentDefTask != null) {
            currentDefTask.run();
            if (currentDefTask.isFinished()) {
                currentDefTask = null;
            }
        } else if (currentSymmetryTask != null) {
            currentSymmetryTask.run();
        } else if (currentSettlerTask != null) {
            currentSettlerTask.run();
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
        } else if (currentBuildDomeTask != null) {
            currentBuildDomeTask.run();
            if (currentBuildDomeTask.isFinished()) {
                currentBuildDomeTask = null;
                handleSuppression(defaultSuppressionCmd);
            }
        } else if (currentBuildHyperJumpCmd != null) {
            handleBuildHyperJump();
        } else if (currentSuppressionCmd != null) {
            handleSuppression(currentSuppressionCmd);
        } else if (rdb.enemyHqSize > 0) {
            if (uc.getAstronautInfo().getCarePackage() == CarePackage.REINFORCED_SUIT) {
                if (!attackEnemyHq()) {
                    handleFreeRoam();
                }
            } else if (defaultSuppressionCmd != null) {
                handleSuppression(defaultSuppressionCmd);
            } else {
                handleFreeRoam();
            }
        } else {
            handleFreeRoam();
        }
    }

    private void seekUnknownEnemyHq() {
        for (StructureInfo s: uc.senseStructures(c.visionRange, c.team.getOpponent())) {
            if (s.getType() == StructureType.HQ) {
                if (rdb.addEnemyHq(s.getLocation())) {
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(s.getLocation(), dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ, false, false, false));
                }
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

        Direction moveDir = spawnDir;
        if (uc.getRound() > 15 && rdb.enemyHqSize > 0) {
            int nearestEnemyHqIdx = Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize);
            moveDir = c.loc.directionTo(rdb.enemyHq[nearestEnemyHqIdx]);
        }


        settlementPaxTask.run();
        if (c.destination == null) {
            plantsGatheringTask.run();
        }
        if (c.destination == null) {
            c.destination = c.loc.add(moveDir);
        }
        if (c.destination != null && uc.getRound() > 300) {
            retrievePaxTask.run();
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
            return true;
        } else {
            c.destination = currentRetPaxCmd.target;
        }

        if (c.remainingSteps() <= 1) {
            rdb.sendGetPackageFailedMessage(currentRetPaxCmd.target);
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

        if (Vector2D.chebysevDistance(c.loc, target) == 1) {
            c.s.attackEnemyBaseDirectly(target);
            if (tryReportEnemyHqDestroyed(target)) {
                return true;
            }
        }

        if (Vector2D.chebysevDistance(c.loc, target) == 2) {
            c.s.attackEnemyBaseStepNeeded(target);
            c.destination = c.loc;
            if (tryReportEnemyHqDestroyed(target)) {
                return true;
            }
            return true;
        }

        c.destination = target;
        if (c.loc.distanceSquared(target) <= c.actionRange) {
            c.destination = null;
        }


        return true;
    }

    private void handleSuppression(SuppressionCommand cmd) {
        c.destination = null;
        Location target = cmd.target;

        settlementPaxTask.run();
        if (c.destination == null && uc.getRound() < 500) {
            plantsGatheringTask.run();
        }
        if (c.destination != null) {
            return;
        }

        if (uc.canSenseLocation(target)) {

            if (tryReportEnemyHqDestroyed(target)) {
                return;
            }

            StructureInfo s = uc.senseStructure(target);
            if (s != null && s.getTeam() == c.opponent) {
                if (Vector2D.chebysevDistance(c.loc, target) == 1) {
                    c.s.attackEnemyBaseDirectly(target);
                }

                if (Vector2D.chebysevDistance(c.loc, target) == 2) {
                    c.s.attackEnemyBaseStepNeeded(target);
                    c.destination = c.loc;
                    return;
                }
            }

        }

        // attack anyone nearby
        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.team.getOpponent());
        int selfValue = (int)uc.getAstronautInfo().getOxygen();
        if (uc.getAstronautInfo().getCarePackage() == CarePackage.SURVIVAL_KIT) {
            selfValue *= 2;
        }
        if (enemies.length > 0) {
            int attackIdx = -1;
            int highestValue = 0;
            int nearestDist = Integer.MAX_VALUE;
            for (int i = 0; i < enemies.length; i++) {
                if (Vector2D.chebysevDistance(c.loc, enemies[i].getLocation()) > c.remainingSteps()) {
                    continue;
                }
                int value = c.s.getEnemyAstronautValue(enemies[i]);
                // suppressors only attack if they have higher value
                if (value >= selfValue && value >= highestValue) {
                    int dist = c.loc.distanceSquared(enemies[i].getLocation());
                    if (value > highestValue || dist < nearestDist) {
                        attackIdx = i;
                        highestValue = value;
                        nearestDist = c.loc.distanceSquared(enemies[i].getLocation());
                    }
                }
            }
            if (attackIdx != -1) {
                c.destination = enemies[attackIdx].getLocation();
                if (c.loc.distanceSquared(c.destination) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1)) {
                    uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1);
                }
//                if (c.canMove(c.loc.directionTo(c.destination))) {
//                    c.move(c.loc.directionTo(c.destination));
//                    while (c.loc.distanceSquared(target) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1)) {
//                        uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1);
//                    }
//                }
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

            rdb.sendSuppressorHeartbeat();
        }

    }

    private boolean tryReportEnemyHqDestroyed(Location target) {
        StructureInfo s = uc.senseStructure(target);
        if (s == null && rdb.isKnownEnemyHq(target)) {
            rdb.sendEnemyHqDestroyedMessage(target);
            return true;
        }
        return false;
    }


}
