package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.db.Expansion;
import kyuu.message.DomeBuiltNotification;
import kyuu.message.DomeDestroyedNotification;
import kyuu.message.ExpansionEstablishedMessage;

import java.util.Iterator;

public class ExpansionTask extends Task {

    final int COST_SURVEY = 15;
    final int TIMEOUT_SURVEY = 5;
    final int DANGER_COOLDOWN = 5;

    final int DEPTH_1_OXYGEN_REQUIREMENT = 100;
    final int DEPTH_2_OXYGEN_REQUIREMENT = 650;

    final int EXPANSION_COOLDOWN_SLOW = 20;
    final int EXPANSION_COOLDOWN_NORMAL = 10;
    final int EXPANSION_COOLDOWN_EXTREME = 5;
    
    boolean isRootSurvey; // 0 or 1
    int rootSurveyDir;
    int depth1SurveyDir;
    int depth2SurveyDir;

    int expansionCooldown;

    int prevDangerRound;

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
        prevDangerRound = -1;
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
                ExpansionEstablishedMessage msg = new ExpansionEstablishedMessage(
                        new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT),
                        expansionId
                );
                for (Iterator<Expansion> it = ldb.iterateExpansions(); it.hasNext(); ) {
                    Expansion ex = it.next();
                    for (int i = 0; i < c.allDirs.length; i++) {
                        if (rdb.expansionSites[ex.id][i] == null) {
                            continue;
                        }
                        if (Vector2D.chebysevDistance(rdb.expansionSites[ex.id][i], msg.target) < 3) {
                            c.logger.log("Expansion missed: %s", msg.target);
                            rdb.expansionMissed[ex.id][i] += 1;
                        }
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
        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.opponent);
        for (AstronautInfo e : enemies) {
            if (c.s.isReachableDirectly(e.getLocation())) {
                prevDangerRound = uc.getRound();
                break;
            }
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

        for (Iterator<Expansion> it = ldb.iterateExpansions(); it.hasNext(); ) {
            Expansion ex = it.next();
            for (int i = 0; i < c.allDirs.length; i++) {
                for (int h = 0; h < rdb.enemyHqSize; h++) {
                    if (rdb.expansionSites[ex.id][i] == null) {
                        continue;
                    }
                    if (rdb.enemyHq[h].distanceSquared(rdb.expansionSites[ex.id][i]) < 10 * 10 ||
                            4 * rdb.enemyHq[h].distanceSquared(rdb.expansionSites[ex.id][i]) < c.loc.distanceSquared(rdb.expansionSites[ex.id][i])) {
                        rdb.surveyorStates[ex.id][i] = dc.SURVEY_BAD;
                        rdb.lastBadSurvey[ex.id][i] = uc.getRound();
                        break;
                    }
                }
                for (int h = 0; h < rdb.baseCount; h++) {
                    if (rdb.expansionSites[ex.id][i] == null) {
                        continue;
                    }
                    if (c.rdb.expansionSites[ex.id][i].distanceSquared(c.rdb.baseLocs[h]) < 8 * 8 ||
                            (ex.depth > 0 && c.rdb.expansionSites[ex.id][i].distanceSquared(c.rdb.baseLocs[h]) < c.rdb.expansionSites[ex.id][i].distanceSquared(c.loc))) {
                        rdb.surveyorStates[ex.id][i] = dc.SURVEY_BAD;
                        rdb.lastBadSurvey[ex.id][i] = uc.getRound();
                        break;
                    }
                }
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
        int attempts = 1;
        int nearestEnemyHqDist = c.s.estimateNearestEnemyHqDist();
        if (nearestEnemyHqDist > 25) {
            attempts++;
        }
        if (nearestEnemyHqDist > 45) {
            attempts++;
        }
        for (int a = 0; a < attempts; a++) {
            for (Iterator<Expansion> it = ldb.iterateExpansions(); it.hasNext(); ) {
                Expansion ex = it.next();
                rdb.clearBadSurveyHistory(ex.id, 200);
                if (ldb.enlistFullyReserved()) {
                    return;
                }
                int oxygen = (int)uc.getStructureInfo().getOxygen();
                if ((ldb.oxygenProductionRate < 3 || oxygen < DEPTH_1_OXYGEN_REQUIREMENT) && ex.depth > 0) {
                    break;
                }
                if ((ldb.oxygenProductionRate < 7 || oxygen < DEPTH_2_OXYGEN_REQUIREMENT) && ex.depth > 1) {
                    break;
                }
                if (prevDangerRound != -1 && uc.getRound() - prevDangerRound < (DANGER_COOLDOWN * ex.depth)) {
                    break;
                }

                int i = rootSurveyDir;
                if (ex.depth == 1) {
                    i = depth1SurveyDir;
                } else if (ex.depth == 2) {
                    i = depth2SurveyDir;
                }
                if (rdb.surveyorStates[ex.id][i] != dc.SURVEY_NONE || rdb.surveyorStates[ex.id][i] == dc.SURVEY_BAD) {
                    continue;
                }
                if (ex.surveyorStart[i] > 0 && uc.getRound() - ex.surveyorStart[i] < TIMEOUT_SURVEY * (ex.depth + 1)) {
                    continue;
                }
                ldb.minReserveEnlistSlot++;
                int surveyCost = (ex.depth + 1) * COST_SURVEY;
                ldb.minReserveOxygen += surveyCost;
                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                    if (uc.canEnlistAstronaut(dir, surveyCost, null)) {
                        c.enlistAstronaut(dir, surveyCost, null, rdb.expansionSites[ex.id][i], ex.expansionTree);
                        rdb.sendSurveyCommand(
                                uc.senseAstronaut(c.loc.add(dir)).getID(),
                                rdb.expansionSites[ex.id][i], ex.id,
                                ex.expansionTree);
                        ex.surveyorStart[i] = uc.getRound();
                        ldb.minReserveEnlistSlot--;
                        ldb.minReserveOxygen -= (surveyCost);
                        break;
                    }
                }
            }
            rootSurveyDir = (rootSurveyDir + 1) % c.allDirs.length;
            depth1SurveyDir = (depth1SurveyDir + 1) % c.allDirs.length;
            depth2SurveyDir = (depth2SurveyDir + 1) % c.allDirs.length;
        }
    }

    private void updateSurveyDirection(Expansion ex) {
        if (ex.depth == 0) {
            rootSurveyDir = (rootSurveyDir + 1) % c.allDirs.length;
        } else if (ex.depth == 1) {
            depth1SurveyDir = (depth1SurveyDir + 1) % c.allDirs.length;
        } else if (ex.depth == 2) {
            depth2SurveyDir = (depth2SurveyDir + 1) % c.allDirs.length;
        }
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
            int oxygen = (int) uc.getStructureInfo().getOxygen();
            Expansion ex = all[expansionAttempts % all.length];
            boolean canSend = true;
            if (ex.depth == 1 && oxygen < DEPTH_1_OXYGEN_REQUIREMENT) {
                canSend = false;
            } else if (ex.depth == 2 && oxygen < DEPTH_2_OXYGEN_REQUIREMENT) {
                canSend = false;
            }
            if (canSend) {
                sendExpansionWorkers(ex);
            }
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

                c.enlistAstronaut(dir, givenOxygen, carePackage, rdb.expansionSites[ex.id][i], ex.expansionTree);
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
        if (ldb.oxygenProductionRate < 10) {
            return;
        }
        for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
            Expansion current = ldb.expansionsDepth1[k];
            int dirIdx = current.expansionDirectionIdx;
            if (rdb.expansionStates[current.id][dirIdx] >=  dc.EXPANSION_STATE_ESTABLISHED && !current.deepened[dirIdx]) {
                ldb.expansionsDepth2[ldb.expansionsDepth2Size++] = new Expansion(c, rdb.expansionSites[current.id][dirIdx], 9 + dirIdx, dirIdx, 2, current);
                current.deepened[dirIdx] = true;
            }
            // todo: three way expansion
        }
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
        if (ldb.oxygenProductionRate >= 10) {
            for (int k = 0; k < ldb.expansionsDepth1Size; k++) {
                if (buildDir != -1) {
                    break;
                }
                Direction expansionDir = c.allDirs[ldb.expansionsDepth1[k].expansionDirectionIdx];
                boolean expansionDirFour = expansionDir == Direction.NORTH || expansionDir == Direction.WEST
                        || expansionDir == Direction.SOUTH || expansionDir == Direction.EAST;
                // prioritize excellent location
                for (int i = 0; i < c.allDirs.length; i++) {
                    Location target = rdb.expansionSites[ldb.expansionsDepth1[k].id][i];
                    if (target == null) {
                        continue;
                    }

                    // following expansion dir or this is going diagonal, ensuring no duplicate dome
                    if (c.allDirs[i] != expansionDir) {
                        if (expansionDirFour && c.allDirs[i] != expansionDir.rotateLeft() && c.allDirs[i] != expansionDir.rotateRight()) {
                            continue;
                        }
                        if (!expansionDirFour) {
                            continue;
                        }
                    }

                    if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
                        continue;
                    }
                    if (rdb.expansionStates[ldb.expansionsDepth1[k].id][i] < dc.EXPANSION_STATE_BUILDING_DOME
                            && rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] == dc.SURVEY_EXCELLENT
                            && !rdb.isKnownDangerousLocation(target)) {
                        buildDir = i;
                        expansion = ldb.expansionsDepth1[k];
                        break;
                    }
                }

                if (buildDir != -1) {
                    break;
                }

                for (int i = 0; i < c.allDirs.length; i++) {
                    Location target = rdb.expansionSites[ldb.expansionsDepth1[k].id][i];
                    if (target == null) {
                        continue;
                    }

                    // following expansion dir or this is going diagonal, ensuring no duplicate dome
                    if (c.allDirs[i] != expansionDir) {
                        if (expansionDirFour && c.allDirs[i] != expansionDir.rotateLeft() && c.allDirs[i] != expansionDir.rotateRight()) {
                            continue;
                        }
                        if (!expansionDirFour) {
                            continue;
                        }
                    }

                    if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
                        continue;
                    }
                    if (rdb.expansionStates[ldb.expansionsDepth1[k].id][i] < dc.EXPANSION_STATE_BUILDING_DOME
                            && rdb.surveyorStates[ldb.expansionsDepth1[k].id][i] == dc.SURVEY_GOOD
                            && !rdb.isKnownDangerousLocation(target)) {
                        buildDir = i;
                        expansion = ldb.expansionsDepth1[k];
                        break;
                    }
                }
            }

        }

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
                c.enlistAstronaut(dir, cost, CarePackage.DOME, buildLoc, expansion.expansionTree);
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
