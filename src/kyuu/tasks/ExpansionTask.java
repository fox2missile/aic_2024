package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.db.Expansion;
import kyuu.message.DomeBuiltNotification;
import kyuu.message.DomeDestroyedNotification;
import kyuu.message.ExpansionEstablishedMessage;

public class ExpansionTask extends Task {

    final int COST_SURVEY = 15;
    final int TIMEOUT_SURVEY = 20;

    final int EXPANSION_COOLDOWN_SLOW = 20;
    final int EXPANSION_COOLDOWN_NORMAL = 10;
    final int EXPANSION_COOLDOWN_EXTREME = 5;
    
    boolean isRootSurvey; // 0 or 1
    int depth1SurveyDir;
    int depth2SurveyDir;

    int expansionCooldown;



    int expansionSize;

    int lastDepth1Expansion;
    int lastDepth2Expansion;

    int expansionAttempts;

    public ExpansionTask(C c) {
        super(c);

        rdb.initExpansionData();
        expansionSize = 1;
        isRootSurvey = true;
        depth1SurveyDir = 0;
        lastDepth1Expansion = 0;
        lastDepth2Expansion = 0;
        expansionAttempts = 0;
        ldb.allocateExpansionData();
        setupReceivers();
    }

    private void setupReceivers() {
        rdb.domeBuiltReceiver = (int fullMsg) -> {
            DomeBuiltNotification builtNotif = rdb.parseDomeBuiltNotification(fullMsg);
            // try all expansion id, no worries not that many, the deepest expansion depth is excluded
            // 1. root expansion
            updateExpansionStateIfMatch(builtNotif.target, ldb.rootExpansion.id, dc.EXPANSION_STATE_HAS_DOME);
            // 2. depth 1
            for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
                if (Vector2D.chebysevDistance(builtNotif.target, ldb.expansionsDepth1[k].expansionLoc) > 15) {
                    continue;
                }
                updateExpansionStateIfMatch(builtNotif.target, ldb.expansionsDepth1[k].id, dc.EXPANSION_STATE_HAS_DOME);
            }
        };

        rdb.domeDestroyedReceiver = (int fullMsg) -> {
            DomeDestroyedNotification desNotif = rdb.parseDomeDestroyedNotification(fullMsg);
            if (rdb.isSubscribingExpansionId(desNotif.expansionId)) {
                updateExpansionStateIfMatch(desNotif.target, desNotif.expansionId, dc.EXPANSION_STATE_ESTABLISHED);
            }
        };

        rdb.expansionMissedReceiver = (int fullMsg) -> {
            int expansionId = fullMsg & dc.EXPANSION_MISSED_EXPANSION_ID_MASKER;
            if (rdb.isSubscribingExpansionId(expansionId)) {
                ExpansionEstablishedMessage ex = new ExpansionEstablishedMessage(
                        new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT),
                        expansionId
                );
                for (int i = 0; i < c.allDirs.length; i++) {
                    if (Vector2D.chebysevDistance(rdb.expansionSites[expansionId][i], ex.target) < 3) {
                        c.logger.log("Expansion missed: %s", ex.target);
                        rdb.expansionMissed[expansionId][i] += 1;
                    }
                }
            }
        };
    }

    // If location match
    private void updateExpansionStateIfMatch(Location loc, int expansionId, int newState) {
        for (int i = 0; i < c.allDirs.length; i++) {
            if (rdb.expansionSites[expansionId][i] == null) {
                continue;
            }
            if (Vector2D.chebysevDistance(rdb.expansionSites[expansionId][i], loc) < 3) {
                if (c.DEBUG && newState == dc.EXPANSION_STATE_HAS_DOME) {
                    c.logger.log("Dome built: %s", loc);
                }
                rdb.expansionStates[expansionId][i] = newState;
            }
        }
    }

    @Override
    public void run() {
        int minOxygen = Math.max(ldb.minReserveOxygen, 90);
        if (uc.getStructureInfo().getOxygen() < minOxygen) {
            return;
        }
        expansionCooldown = EXPANSION_COOLDOWN_NORMAL;
        if (uc.getStructureInfo().getOxygen() > minOxygen + 410) {
            expansionCooldown = EXPANSION_COOLDOWN_EXTREME;
        }
        if (uc.senseAstronauts(c.visionRange, c.team).length > 10) {
            expansionCooldown = EXPANSION_COOLDOWN_SLOW;
        }

        eliminateSurveyLocs();

        sendOutSurveyors();

        sendExpansionWorkers();

        deepenExpansion();

        buildDome();
    }

    private void eliminateSurveyLocs() {
        if (rdb.enemyHqSize == 0) {
            return;
        }

        // root survey
        for (int i = 0; i < c.allDirs.length; i++) {
//            boolean nearEnemyHq = false;
            for (int h = 0; h < rdb.enemyHqSize; h++) {
                if (rdb.expansionSites[ldb.rootExpansion.id][i] == null) {
                    continue;
                }
                if (Vector2D.chebysevDistance(rdb.expansionSites[ldb.rootExpansion.id][i], rdb.enemyHq[h]) < 10) {
                    rdb.surveyorStates[ldb.rootExpansion.id][i] = dc.SURVEY_BAD;
                    rdb.lastBadSurvey[ldb.rootExpansion.id][i] = uc.getRound();
//                    nearEnemyHq = true;
                    break;
                }
            }
//            if (!nearEnemyHq && rdb.surveyorStates[ldb.rootExpansion.id][i] == dc.SURVEY_BAD && rdb.lastBadSurvey[ldb.rootExpansion.id][i] != -1) {
//                rdb.surveyorStates[ldb.rootExpansion.id][i] = dc.SURVEY_NONE;
//            }
        }

        // depth 1
        for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
//            boolean nearEnemyHq = false;
            for (int i = 0; i < c.allDirs.length; i++) {
                for (int h = 0; h < rdb.enemyHqSize; h++) {
                    if (rdb.expansionSites[ldb.expansionsDepth1[k].id][i] == null) {
                        continue;
                    }
                    if (Vector2D.chebysevDistance(rdb.expansionSites[ldb.expansionsDepth1[k].id][i], rdb.enemyHq[h]) < 10) {
                        rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] = dc.SURVEY_BAD;
                        rdb.lastBadSurvey[ldb.expansionsDepth1[k].id][i] = uc.getRound();
//                        nearEnemyHq = true;
                        break;
                    }
                }
//                if (!nearEnemyHq && rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] == dc.SURVEY_BAD && rdb.lastBadSurvey[ldb.expansionsDepth1[k].id][i] != -1) {
//                    rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] = dc.SURVEY_NONE;
//                }
            }

        }
    }

    private Expansion findChildExpansion(Location expansionTarget) {
        // only for depth == 0
        for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
            if (Vector2D.chebysevDistance(ldb.expansionsDepth1[k].expansionLoc, expansionTarget) < 3) {
                return ldb.expansionsDepth1[k];
            }
        }
        return null;
    }

    private void sendOutSurveyors() {
        if (isRootSurvey) {
            rdb.clearBadSurveyHistory(ldb.rootExpansion.id, 200);
            for (int i = 0; i < c.allDirs.length; i++) {
                if (ldb.enlistFullyReserved()) {
                    return;
                }
                if (rdb.surveyorStates[ldb.rootExpansion.id][i] != dc.SURVEY_NONE || rdb.surveyorStates[ldb.rootExpansion.id][i] == dc.SURVEY_BAD) {
                    continue;
                }
                if (ldb.rootExpansion.surveyorStart[i] > 0 && uc.getRound() - ldb.rootExpansion.surveyorStart[i] < TIMEOUT_SURVEY) {
                    continue;
                }
                ldb.minReserveEnlistSlot++;
                ldb.minReserveOxygen += COST_SURVEY;
                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                    if (uc.canEnlistAstronaut(dir, COST_SURVEY, null)) {
                        c.enlistAstronaut(dir, COST_SURVEY, null);
                        rdb.sendSurveyCommand(uc.senseAstronaut(c.loc.add(dir)).getID(), rdb.expansionSites[ldb.rootExpansion.id][i], ldb.rootExpansion.id, new Location[]{ldb.rootExpansion.expansionLoc});
                        ldb.rootExpansion.surveyorStart[i] = uc.getRound();
                        ldb.minReserveEnlistSlot--;
                        ldb.minReserveOxygen -= COST_SURVEY;
                        break;
                    }
                }
            }
            isRootSurvey = false;
            return;
        }

        if (ldb.oxygenProductionRate < 5) {
            return;
        }
//        if (ldb.expansionsDepth2Size == 0 || uc.getRound() % 2 == 0) {
            for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
                if (ldb.enlistFullyReserved()) {
                    return;
                }
                int i = depth1SurveyDir;
                rdb.clearBadSurveyHistory(ldb.expansionsDepth1[k].id, 100);
                if (rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] != dc.SURVEY_NONE || rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] == dc.SURVEY_BAD) {
                    continue;
                }
                if (ldb.expansionsDepth1[k].surveyorStart[i] > 0 && uc.getRound() - ldb.expansionsDepth1[k].surveyorStart[i] < TIMEOUT_SURVEY * 2) {
                    continue;
                }
                ldb.minReserveEnlistSlot++;
                ldb.minReserveOxygen += (2 * COST_SURVEY);
                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                    if (uc.canEnlistAstronaut(dir, COST_SURVEY * 2, null)) {
                        c.enlistAstronaut(dir, COST_SURVEY * 2, null);
                        rdb.sendSurveyCommand(
                                uc.senseAstronaut(c.loc.add(dir)).getID(),
                                rdb.expansionSites[ldb.expansionsDepth1[k].id][i], ldb.expansionsDepth1[k].id,
                                new Location[]{ldb.rootExpansion.expansionLoc, ldb.expansionsDepth1[k].expansionLoc});
                        ldb.expansionsDepth1[k].surveyorStart[i] = uc.getRound();
                        ldb.minReserveEnlistSlot--;
                        ldb.minReserveOxygen -= (2 * COST_SURVEY);
                        break;
                    }
                }
            }
            depth1SurveyDir++;
            if (depth1SurveyDir >= c.allDirs.length) {
                depth1SurveyDir = 0;
            }
            if (depth1SurveyDir % 4 == 0) {
                isRootSurvey = true;
            }
//        } else {
//            for (int k = 0; k < ldb.expansionsDepth2Size; k++) {
//                if (ldb.enlistFullyReserved()) {
//                    return;
//                }
//                int i = depth2SurveyDir;
//                rdb.clearBadSurveyHistory(ldb.expansionsDepth2[k].id, 100);
//                if (rdb.surveyorStates[ldb.expansionsDepth2[k].id][i] != dc.SURVEY_NONE || rdb.surveyorStates[ldb.expansionsDepth2[k].id][i] == dc.SURVEY_BAD) {
//                    continue;
//                }
//                if (ldb.expansionsDepth2[k].surveyorStart[i] > 0 && uc.getRound() - ldb.expansionsDepth2[k].surveyorStart[i] < TIMEOUT_SURVEY * 2) {
//                    continue;
//                }
//                ldb.minReserveEnlistSlot++;
//                ldb.minReserveOxygen += (2 * COST_SURVEY);
//                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
//                    if (uc.canEnlistAstronaut(dir, COST_SURVEY * 3, null)) {
//                        c.enlistAstronaut(dir, COST_SURVEY * 3, null);
//                        rdb.sendSurveyCommand(
//                                uc.senseAstronaut(c.loc.add(dir)).getID(),
//                                rdb.expansionSites[ldb.expansionsDepth2[k].id][i], ldb.expansionsDepth2[k].id,
//                                new Location[]{ldb.rootExpansion.expansionLoc, ldb.expansionsDepth2[k].expansionLoc});
//                        ldb.expansionsDepth2[k].surveyorStart[i] = uc.getRound();
//                        ldb.minReserveEnlistSlot--;
//                        ldb.minReserveOxygen -= (2 * COST_SURVEY);
//                        break;
//                    }
//                }
//            }
//            depth2SurveyDir++;
//            if (depth2SurveyDir >= c.allDirs.length) {
//                depth2SurveyDir = 0;
//            }
//        }
    }

    private Expansion getDepth1Expansion() {
        if (ldb.expansionsDepth1Size == 0) {
            return ldb.rootExpansion;
        }
        if (lastDepth1Expansion >= ldb.expansionsDepth1Size) {
            lastDepth1Expansion = 0;
        }
        return ldb.expansionsDepth1[lastDepth1Expansion++];
    }

    private Expansion getDepth2Expansion() {
        if (ldb.expansionsDepth2Size == 0) {
            return getDepth1Expansion();
        }
        if (lastDepth2Expansion >= ldb.expansionsDepth2Size) {
            lastDepth2Expansion = 0;
        }
        return ldb.expansionsDepth2[lastDepth2Expansion++];
    }

    private Expansion getCurrentExpansion() {
        if (uc.getRound() % 4 == 0) {
            return ldb.rootExpansion;
        } else if (uc.getRound() % 4 == 1 || uc.getRound() % 4 == 2) {
            return getDepth1Expansion();
        } else {
            return getDepth2Expansion();
        }
    }

    private void sendExpansionWorkers() {
        if (ldb.enlistFullyReserved()) {
            return;
        }
        Expansion[] all = new Expansion[1 + ldb.expansionsDepth1Size + ldb.expansionsDepth2Size];
        all[0] = ldb.rootExpansion;
        System.arraycopy(ldb.expansionsDepth1, 0, all, 1, ldb.expansionsDepth1Size);
        System.arraycopy(ldb.expansionsDepth2, 0, all, 1 + ldb.expansionsDepth1Size, ldb.expansionsDepth2Size);
        for (int i = 0; i < ldb.availableEnlistSlot; i++) {
            sendExpansionWorkers(all[expansionAttempts % all.length]);
            expansionAttempts++;
        }
    }

    private void sendExpansionWorkers(Expansion ex) {
        int i = ex.currentExpansionDirIdx;
        ex.currentExpansionDirIdx = (ex.currentExpansionDirIdx + 1) % c.allDirs.length;
        if (rdb.surveyorStates[ex.id][i] < dc.SURVEY_GOOD) {
            return;
        }

        if (uc.getStructureInfo().getOxygen() < 200) {
            return;
        }
        if (c.s.isAllyVisible(ex.expansionWorkers[i])) {
            return;
        }
        ex.expansionWorkers[i] = 0;
        int adjustedExpansionCooldown = expansionCooldown * rdb.expansionMissed[ex.id][i];
        if (ex.expansionStart[i] != 0 && uc.getRound() - ex.expansionStart[i] < adjustedExpansionCooldown) {
            return;
        }

        boolean useEarlyPavement = true;

        if (rdb.enemyHqSize == 0) {
            useEarlyPavement = false;
        } else {
            if (c.loc.distanceSquared(rdb.enemyHq[Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize)]) < 40 * 40) {
                useEarlyPavement = false;
            }
        }

        if (useEarlyPavement) {
            if (ex.depth == 0 && rdb.surveyorStates[ex.id][i] == dc.SURVEY_GOOD && ex.expansionCounter[i] >= 3 && rdb.expansionStates[ex.id][i] < dc.EXPANSION_STATE_ESTABLISHED) {
                c.logger.log("Expansion established: %s", rdb.expansionSites[ex.id][i]);
                rdb.expansionStates[ex.id][i] = dc.EXPANSION_STATE_ESTABLISHED;
            }

            if (ex.depth == 0 && rdb.surveyorStates[ex.id][i] == dc.SURVEY_EXCELLENT && ex.expansionCounter[i] >= 7 && rdb.expansionStates[ex.id][i] < dc.EXPANSION_STATE_ESTABLISHED) {
                c.logger.log("Expansion established: %s", rdb.expansionSites[ex.id][i]);
                rdb.expansionStates[ex.id][i] = dc.EXPANSION_STATE_ESTABLISHED;
            }

            if (ex.depth != 0 && rdb.expansionStates[ex.id][i] < dc.EXPANSION_STATE_ESTABLISHED) {
                c.logger.log("Expansion established: %s", rdb.expansionSites[ex.id][i]);
                rdb.expansionStates[ex.id][i] = dc.EXPANSION_STATE_ESTABLISHED;
            }
        } else {
            if (rdb.expansionStates[ex.id][i] < dc.EXPANSION_STATE_ESTABLISHED) {
                c.logger.log("Expansion established: %s", rdb.expansionSites[ex.id][i]);
                rdb.expansionStates[ex.id][i] = dc.EXPANSION_STATE_ESTABLISHED;
            }
        }

        int givenOxygen = 13 * (1 + ex.depth);

        CarePackage carePackage = null;
        if (ex.depth > 0 && uc.getStructureInfo().getCarePackagesOfType(CarePackage.SURVIVAL_KIT) > 0) {
            givenOxygen = (givenOxygen * 2) / 3;
            carePackage = CarePackage.SURVIVAL_KIT;
        }

        if (rdb.expansionStates[ex.id][i] == dc.EXPANSION_STATE_HAS_DOME) {
            givenOxygen -= 5;
        }

        if (ex.depth >= 1 && rdb.expansionStates[ex.parent.id][ex.expansionDirectionIdx] == dc.EXPANSION_STATE_HAS_DOME) {
            givenOxygen -= 5;

            if (ex.depth >= 2 && rdb.expansionStates[ex.parent.parent.id][ex.parent.expansionDirectionIdx] == dc.EXPANSION_STATE_HAS_DOME) {
                givenOxygen -= 5;
            }
        }

        givenOxygen = Math.max(givenOxygen, 10);


        ldb.minReserveEnlistSlot++;
        ldb.minReserveOxygen += givenOxygen;

        for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
            if (uc.canEnlistAstronaut(dir, givenOxygen, carePackage)) {

                Expansion child = findChildExpansion(rdb.expansionSites[ex.id][i]);
                Location possibleNextExpansion = null;
                if (child != null) {
                    for (int j = 0; j < c.allDirs.length; j++) {
                        if (rdb.surveyorStates[child.id][j] >= dc.SURVEY_GOOD) {
                            possibleNextExpansion = rdb.expansionSites[child.id][j];
                        }
                    }
                }

                c.enlistAstronaut(dir, givenOxygen, carePackage);
                int enlistedId = uc.senseAstronaut(c.loc.add(dir)).getID();
                rdb.sendExpansionCommand(enlistedId, rdb.expansionSites[ex.id][i], rdb.expansionStates[ex.id][i], ex.id, ex.expansionTree, possibleNextExpansion);
                ex.expansionWorkers[i] = enlistedId;
                ex.expansionCounter[i]++;
                ldb.minReserveEnlistSlot--;
                ldb.minReserveOxygen -= givenOxygen;
                if (rdb.expansionStates[ex.id][i] >= dc.EXPANSION_STATE_ESTABLISHED) {
                    ex.expansionStart[i] = uc.getRound();
                } else {
                    ex.expansionStart[i] = 0; // this allows expansion to resume immediately once the previous worker disappears
                }
                break;
            }
        }


        // send package worker and defense worker after their primary work, prioritize established expansion
        if (ldb.assignedThisRoundSize > 0 && rdb.expansionStates[ex.id][i] >= dc.EXPANSION_STATE_ESTABLISHED) {
            int assign = ldb.popAssignedThisRound();
            rdb.sendExpansionCommand(assign, rdb.expansionSites[ex.id][i], rdb.expansionStates[ex.id][i], ex.id, ex.expansionTree, null);
        }

        if (ldb.assignedThisRoundSize > 0 && rdb.surveyorStates[ex.id][i] >= dc.SURVEY_GOOD) {
            int assign = ldb.popAssignedThisRound();
            rdb.sendExpansionCommand(assign, rdb.expansionSites[ex.id][i], rdb.expansionStates[ex.id][i], ex.id, ex.expansionTree, null);
        }



    }

    private void deepenExpansion() {
        // root -> depth 1
        for (int i = 0; i < c.allDirs.length; i++) {
            if (rdb.expansionStates[ldb.rootExpansion.id][i] >= dc.EXPANSION_STATE_ESTABLISHED && !ldb.rootExpansion.deepened[i]) {
                ldb.expansionsDepth1[ldb.expansionsDepth1Size++] = new Expansion(c, rdb.expansionSites[ldb.rootExpansion.id][i], 1 + i, i, 1, ldb.rootExpansion);
                ldb.rootExpansion.deepened[i] = true;
            }
        }

        // depth 1 -> depth 2
//        for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
//            Expansion current = ldb.expansionsDepth1[k];
//            int dirIdx = current.expansionDirectionIdx;
//            if (rdb.expansionStates[current.id][dirIdx] >=  dc.EXPANSION_STATE_ESTABLISHED && !current.deepened[dirIdx]) {
//                ldb.expansionsDepth2[ldb.expansionsDepth2Size++] = new Expansion(rdb.expansionSites[current.id][dirIdx], 9 + dirIdx, dirIdx, 2, current);
//                current.deepened[dirIdx] = true;
//            }
//            // todo: three way expansion
//        }
    }

    private void buildDome() {

        inquireDomeProgress();
        
        if (ldb.enlistFullyReserved()) {
            return;
        }

        if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.DOME) == 0 || uc.senseAstronauts(c.visionRange, c.opponent).length >= 1) {
            return;
        }

        if (rdb.alertCount < 5 && uc.getRound() < 100) {
            // we do not have enough info on where the enemies might be coming from
            return;
        }

        // root -> depth1
        // prioritize excellent location
        int buildDir = -1;
        Expansion expansion = null;
        for (int i = 0; i < c.allDirs.length; i++) {
            Location target = rdb.expansionSites[ldb.rootExpansion.id][i];
            if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
                continue;
            }
            if (rdb.expansionStates[ldb.rootExpansion.id][i] < dc.EXPANSION_STATE_BUILDING_DOME
                    && rdb.surveyorStates[ldb.rootExpansion.id][i] == dc.SURVEY_EXCELLENT
                    && !rdb.isKnownDangerousLocation(target)) {
                buildDir = i;
                expansion = ldb.rootExpansion;
                break;
            }
        }
        if (buildDir == -1) {
            for (int i = 0; i < c.allDirs.length; i++) {
                Location target = rdb.expansionSites[ldb.rootExpansion.id][i];
                if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
                    continue;
                }
                if (rdb.expansionStates[ldb.rootExpansion.id][i] < dc.EXPANSION_STATE_BUILDING_DOME
                        && rdb.surveyorStates[ldb.rootExpansion.id][i] == dc.SURVEY_GOOD
                        && !rdb.isKnownDangerousLocation(target)) {
                    buildDir = i;
                    expansion = ldb.rootExpansion;
                    break;
                }
            }
        }

        // depth1 -> depth2
//        for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
//            if (buildDir != -1) {
//                break;
//            }
//            Direction expansionDir = c.allDirs[ldb.expansionsDepth1[k].expansionDirectionIdx];
//            boolean expansionDirFour = expansionDir == Direction.NORTH || expansionDir == Direction.WEST
//                                        || expansionDir == Direction.SOUTH || expansionDir == Direction.EAST;
//            // prioritize excellent location
//            for (int i = 0; i < c.allDirs.length; i++) {
//                Location target = rdb.expansionSites[ldb.expansionsDepth1[k].id][i];
//                if (target == null) {
//                    continue;
//                }
//
//                // following expansion dir or this is going diagonal, ensuring no duplicate dome
//                if (c.allDirs[i] != expansionDir) {
//                    if (expansionDirFour     && c.allDirs[i] != expansionDir.rotateLeft() && c.allDirs[i] != expansionDir.rotateRight()) {
//                        continue;
//                    }
//                    if (!expansionDirFour) {
//                        continue;
//                    }
//                }
//
//                if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
//                    continue;
//                }
//                if (rdb.expansionStates[ldb.expansionsDepth1[k].id][i] < dc.EXPANSION_STATE_BUILDING_DOME
//                        && rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] == dc.SURVEY_EXCELLENT
//                        && !rdb.isKnownDangerousLocation(target)) {
//                    buildDir = i;
//                    expansion = ldb.expansionsDepth1[k];
//                    break;
//                }
//            }
//
//            if (buildDir != -1) {
//                break;
//            }
//
//            for (int i = 0; i < c.allDirs.length; i++) {
//                Location target = rdb.expansionSites[ldb.expansionsDepth1[k].id][i];
//                if (target == null) {
//                    continue;
//                }
//
//                // following expansion dir or this is going diagonal, ensuring no duplicate dome
//                if (c.allDirs[i] != expansionDir) {
//                    if (expansionDirFour && c.allDirs[i] != expansionDir.rotateLeft() && c.allDirs[i] != expansionDir.rotateRight()) {
//                        continue;
//                    }
//                    if (!expansionDirFour) {
//                        continue;
//                    }
//                }
//
//                if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
//                    continue;
//                }
//                if (rdb.expansionStates[ldb.expansionsDepth1[k].id][i] < dc.EXPANSION_STATE_BUILDING_DOME
//                        && rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] == dc.SURVEY_GOOD
//                        && !rdb.isKnownDangerousLocation(target)) {
//                    buildDir = i;
//                    expansion = ldb.expansionsDepth1[k];
//                    break;
//                }
//            }
//        }

        if (buildDir != -1) {
            buildDome(expansion, buildDir);
        }
    }

    private void buildDome(Expansion expansion, int dirIdx) {
        Location buildLoc = rdb.expansionSites[expansion.id][dirIdx];
        int cost = COST_SURVEY * (1 + expansion.depth);
        ldb.minReserveEnlistSlot++;
        ldb.minReserveOxygen += cost;
        for (Direction dir: c.getFirstDirs(c.allDirs[dirIdx])) {
            if (uc.canEnlistAstronaut(dir, cost, CarePackage.DOME)) {
                c.enlistAstronaut(dir, cost, CarePackage.DOME);
                int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                rdb.sendBuildDomeCommand(enlistId, buildLoc, expansion.id, expansion.expansionTree);
                rdb.expansionStates[expansion.id][dirIdx] = dc.EXPANSION_STATE_BUILDING_DOME;
                ldb.minReserveEnlistSlot--;
                ldb.minReserveOxygen -= cost;
                break;
            }
        }
    }

    private void inquireDomeProgress() {
        for (int i = 0; i < c.allDirs.length; i++) {
            if (rdb.expansionStates[ldb.rootExpansion.id][i] == dc.EXPANSION_STATE_BUILDING_DOME) {
                rdb.sendDomeInquiry(rdb.expansionSites[ldb.rootExpansion.id][i]);
            }
        }
        for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
            for (int i = 0; i < c.allDirs.length; i++) {
                if (rdb.expansionStates[ldb.expansionsDepth1[k].id][i] == dc.EXPANSION_STATE_BUILDING_DOME) {
                    rdb.sendDomeInquiry(rdb.expansionSites[ldb.expansionsDepth1[k].id][i]);
                }
            }
        }
    }

}
