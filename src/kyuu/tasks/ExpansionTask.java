package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;

public class ExpansionTask extends Task {

    final int COST_SURVEY = 15;
    final int TIMEOUT_SURVEY = 20;

    final int EXPANSION_COOLDOWN_SLOW = 20;
    final int EXPANSION_COOLDOWN_NORMAL = 10;
    final int EXPANSION_COOLDOWN_EXTREME = 5;

    class Expansion {
        int id;

        int depth;
        Location expansionLoc;

        int[] surveyorStart;


        int[] expansionWorkers;
        int[] expansionStart;

        int expansionDirectionIdx;

        int[] expansionCounter;


        int currentExpansionDirIdx;

        Location[] expansionTree;
        boolean[] deepened;

        Expansion parent;


        Expansion(Location loc, int localExpansionId, int generalDirIdx, int depth, Expansion parent) {
            id = localExpansionId + (rdb.hqIdx * dc.EXPANSION_SIZE);
            this.parent = parent;

            expansionLoc = loc;
            expansionDirectionIdx = generalDirIdx;
            Direction generalDir = generalDirIdx != -1 ? c.allDirs[generalDirIdx] : null;
            this.depth = depth;
            expansionWorkers = new int[c.allDirs.length];
            expansionStart = new int[c.allDirs.length];
            expansionCounter = new int[c.allDirs.length];
            surveyorStart = new int[c.allDirs.length];
            deepened = new boolean[c.allDirs.length];
            currentExpansionDirIdx = 0;

            for (int i = 0; i < c.allDirs.length; i++) {
                // todo: allow more expansion dir
                if (generalDir != null && c.allDirs[i] != generalDir && c.allDirs[i] != generalDir.rotateRight() && c.allDirs[i] != generalDir.rotateLeft() && c.allDirs[i] != generalDir.rotateLeft().rotateLeft() && c.allDirs[i] != generalDir.rotateRight().rotateRight()) {
//                if (generalDir != null && c.allDirs[i] != generalDir && c.allDirs[i] != generalDir.rotateRight() && c.allDirs[i] != generalDir.rotateLeft()) {
//                if (generalDir != null && c.allDirs[i] != generalDir) {
                    rdb.surveyorStates[id][i] = dc.SURVEY_BAD;
                    rdb.lastBadSurvey[id][i] = -1;
                    continue;
                }
                Direction dir = c.allDirs[i];;
                rdb.expansionSites[id][i] = expansionLoc.add(dir.dx * 10, dir.dy * 10);
                if (uc.isOutOfMap(rdb.expansionSites[id][i])) {
                    rdb.surveyorStates[id][i] = dc.SURVEY_BAD;
                    rdb.lastBadSurvey[id][i] = -1;
                }

                int mapMagnitude = Math.max(uc.getMapWidth(), uc.getMapHeight());
                boolean parentCheck = parent != null;
                if (mapMagnitude > 35) {
                    parentCheck = true;
                }

                if (uc.isOutOfMap(rdb.expansionSites[id][i]) && parentCheck) {
                    boolean saved = false;
                    for (int j = 0; j < 3; j++) {
                        rdb.expansionSites[id][i] = expansionLoc.add(dir.dx * (10 - j), dir.dy * (10 - j));
                        if (!uc.isOutOfMap(rdb.expansionSites[id][i]) && !(uc.canSenseLocation(rdb.expansionSites[id][i]) && c.isObstacle(uc.senseObjectAtLocation(rdb.expansionSites[id][i])))) {
                            saved = true;
                            break;
                        }
                    }
                    if (saved) {
                        rdb.surveyorStates[id][i] = dc.SURVEY_NONE;
                        rdb.lastBadSurvey[id][i] = 0;
                    }
                }

                for (int h = 0; h < rdb.hqCount; h++) {
                    if (rdb.hqLocs[h].equals(c.loc)) {
                        continue;
                    }
                    if (Vector2D.chebysevDistance(rdb.hqLocs[h],rdb.expansionSites[id][i]) < 10) {
                        rdb.surveyorStates[id][i] = dc.SURVEY_BAD;
                        rdb.lastBadSurvey[id][i] = -1;
                    }
                }

            }

            if (depth == 0) {
                expansionTree = new Location[]{expansionLoc};
            } else if (depth == 1) {
                expansionTree = new Location[]{parent.expansionLoc, expansionLoc};
            } else if (depth == 2) {
                expansionTree = new Location[]{parent.expansionTree[0], parent.expansionTree[1], expansionLoc};
            }
        }


    }

    boolean isRootSurvey; // 0 or 1
    int depth1SurveyDir;

    int expansionCooldown;

    Expansion rootExpansion;
    Expansion[] expansionsDepth1; // may expand again
    int expansionsDepth1Size;
    Expansion[] expansionsDepth2; // cannot expand again
    int expansionsDepth2Size;

    int expansionSize;

    int lastDepth1Expansion;
    int lastDepth2Expansion;

    public ExpansionTask(C c) {
        super(c);
        rootExpansion = new Expansion(c.loc, 0, -1, 0, null);
        expansionsDepth1 = new Expansion[8];
        expansionsDepth1Size = 0;
        expansionsDepth2 = new Expansion[16];
        expansionsDepth2Size = 0;
        expansionSize = 1;
        isRootSurvey = true;
        depth1SurveyDir = 0;
        lastDepth1Expansion = 0;
        lastDepth2Expansion = 0;
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
                if (rdb.expansionSites[rootExpansion.id][i] == null) {
                    continue;
                }
                if (Vector2D.chebysevDistance(rdb.expansionSites[rootExpansion.id][i], rdb.enemyHq[h]) < 10) {
                    rdb.surveyorStates[rootExpansion.id][i] = dc.SURVEY_BAD;
                    rdb.lastBadSurvey[rootExpansion.id][i] = uc.getRound();
//                    nearEnemyHq = true;
                    break;
                }
            }
//            if (!nearEnemyHq && rdb.surveyorStates[rootExpansion.id][i] == dc.SURVEY_BAD && rdb.lastBadSurvey[rootExpansion.id][i] != -1) {
//                rdb.surveyorStates[rootExpansion.id][i] = dc.SURVEY_NONE;
//            }
        }

        // depth 1
        for (int k = 0; k < expansionsDepth1Size; k++) {
//            boolean nearEnemyHq = false;
            for (int i = 0; i < c.allDirs.length; i++) {
                for (int h = 0; h < rdb.enemyHqSize; h++) {
                    if (rdb.expansionSites[expansionsDepth1[k].id][i] == null) {
                        continue;
                    }
                    if (Vector2D.chebysevDistance(rdb.expansionSites[expansionsDepth1[k].id][i], rdb.enemyHq[h]) < 10) {
                        rdb.surveyorStates[expansionsDepth1[k].id][i] = dc.SURVEY_BAD;
                        rdb.lastBadSurvey[expansionsDepth1[k].id][i] = uc.getRound();
//                        nearEnemyHq = true;
                        break;
                    }
                }
//                if (!nearEnemyHq && rdb.surveyorStates[expansionsDepth1[k].id][i] == dc.SURVEY_BAD && rdb.lastBadSurvey[expansionsDepth1[k].id][i] != -1) {
//                    rdb.surveyorStates[expansionsDepth1[k].id][i] = dc.SURVEY_NONE;
//                }
            }

        }
    }

    private Expansion findChildExpansion(Location expansionTarget) {
        // only for depth == 0
        for (int k = 0; k < expansionsDepth1Size; k++) {
            if (Vector2D.chebysevDistance(expansionsDepth1[k].expansionLoc, expansionTarget) < 3) {
                return expansionsDepth1[k];
            }
        }
        return null;
    }

    private void sendOutSurveyors() {
        if (isRootSurvey) {
            rdb.clearBadSurveyHistory(rootExpansion.id, 200);
            for (int i = 0; i < c.allDirs.length; i++) {
                if (ldb.enlistFullyReserved()) {
                    return;
                }
                if (rdb.surveyorStates[rootExpansion.id][i] != dc.SURVEY_NONE || rdb.surveyorStates[rootExpansion.id][i] == dc.SURVEY_BAD) {
                    continue;
                }
                if (rootExpansion.surveyorStart[i] > 0 && uc.getRound() - rootExpansion.surveyorStart[i] < TIMEOUT_SURVEY) {
                    continue;
                }
                ldb.minReserveEnlistSlot++;
                ldb.minReserveOxygen += COST_SURVEY;
                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                    if (uc.canEnlistAstronaut(dir, COST_SURVEY, null)) {
                        c.enlistAstronaut(dir, COST_SURVEY, null);
                        rdb.sendSurveyCommand(uc.senseAstronaut(c.loc.add(dir)).getID(), rdb.expansionSites[rootExpansion.id][i], rootExpansion.id, new Location[]{rootExpansion.expansionLoc});
                        rootExpansion.surveyorStart[i] = uc.getRound();
                        ldb.minReserveEnlistSlot--;
                        ldb.minReserveOxygen -= COST_SURVEY;
                        break;
                    }
                }
            }
            isRootSurvey = false;
            return;
        }

        for (int k = 0; k < expansionsDepth1Size; k++) {
            if (ldb.enlistFullyReserved()) {
                return;
            }
            int i = depth1SurveyDir;
            rdb.clearBadSurveyHistory(expansionsDepth1[k].id, 100);
            if (rdb.surveyorStates[expansionsDepth1[k].id][i] != dc.SURVEY_NONE || rdb.surveyorStates[expansionsDepth1[k].id][i] == dc.SURVEY_BAD) {
                continue;
            }
            if (expansionsDepth1[k].surveyorStart[i] > 0 && uc.getRound() - expansionsDepth1[k].surveyorStart[i] < TIMEOUT_SURVEY * 2) {
                continue;
            }
            ldb.minReserveEnlistSlot++;
            ldb.minReserveOxygen += (2 * COST_SURVEY);
            for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                if (uc.canEnlistAstronaut(dir, COST_SURVEY * 2, null)) {
                    c.enlistAstronaut(dir, COST_SURVEY * 2, null);
                    rdb.sendSurveyCommand(
                            uc.senseAstronaut(c.loc.add(dir)).getID(),
                            rdb.expansionSites[expansionsDepth1[k].id][i], expansionsDepth1[k].id,
                            new Location[]{rootExpansion.expansionLoc, expansionsDepth1[k].expansionLoc});
                    expansionsDepth1[k].surveyorStart[i] = uc.getRound();
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

    }

    private Expansion getDepth1Expansion() {
        if (expansionsDepth1Size == 0) {
            return rootExpansion;
        }
        if (lastDepth1Expansion >= expansionsDepth1Size) {
            lastDepth1Expansion = 0;
        }
        return expansionsDepth1[lastDepth1Expansion++];
    }

    private Expansion getDepth2Expansion() {
        if (expansionsDepth2Size == 0) {
            return getDepth1Expansion();
        }
        if (lastDepth2Expansion >= expansionsDepth2Size) {
            lastDepth2Expansion = 0;
        }
        return expansionsDepth2[lastDepth2Expansion++];
    }

    private Expansion getCurrentExpansion() {
        if (uc.getRound() % 4 == 0) {
            return rootExpansion;
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
        for (int i = 0; i < c.allDirs.length; i++) {
            sendExpansionWorkers(getCurrentExpansion());
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

        int givenOxygen = 13 * (1 + ex.depth);

        CarePackage carePackage = null;
        if (ex.depth > 0 && uc.getStructureInfo().getCarePackagesOfType(CarePackage.SURVIVAL_KIT) > 0) {
            givenOxygen = (givenOxygen * 2) / 3;
            carePackage = CarePackage.SURVIVAL_KIT;
        }

        if (rdb.expansionStates[ex.id][i] == dc.EXPANSION_STATE_HAS_DOME) {
            givenOxygen -= 5;
        }

        if (ex.depth >= 1) {
            if (rdb.expansionStates[ex.parent.id][ex.expansionDirectionIdx] == dc.EXPANSION_STATE_HAS_DOME) {
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
            if (rdb.expansionStates[rootExpansion.id][i] >= dc.EXPANSION_STATE_ESTABLISHED && !rootExpansion.deepened[i]) {
                expansionsDepth1[expansionsDepth1Size++] = new Expansion(rdb.expansionSites[rootExpansion.id][i], 1 + i, i, 1, rootExpansion);
                rootExpansion.deepened[i] = true;
            }
        }

        // depth 1 -> depth 2
        for (int k = 0; k < expansionsDepth1Size; k++) {
            Expansion current = expansionsDepth1[k];
            int dirIdx = current.expansionDirectionIdx;
            if (rdb.expansionStates[current.id][dirIdx] >=  dc.EXPANSION_STATE_ESTABLISHED && !current.deepened[dirIdx]) {
                expansionsDepth2[expansionsDepth2Size++] = new Expansion(rdb.expansionSites[current.id][dirIdx], 9 + dirIdx, dirIdx, 1, current);
                current.deepened[dirIdx] = true;
            }
            // todo: three way expansion
        }
    }

    private void buildDome() {

        for (int i = 0; i < c.allDirs.length; i++) {
            if (rdb.expansionStates[rootExpansion.id][i] == dc.EXPANSION_STATE_BUILDING_DOME) {
                rdb.sendDomeInquiry(rdb.expansionSites[rootExpansion.id][i]);
            }
        }
        
        if (ldb.enlistFullyReserved()) {
            return;
        }

        if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.DOME) == 0 || uc.senseAstronauts(c.visionRange, c.opponent).length >= 1) {
            return;
        }

        if (rdb.alertCount < 5) {
            // we do not have enough info on where the enemies might be coming from
            return;
        }

        // prioritize excellent location
        int buildDir = -1;
        for (int i = 0; i < c.allDirs.length; i++) {
            Location target = rdb.expansionSites[rootExpansion.id][i];
            if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
                continue;
            }
            if (rdb.expansionStates[rootExpansion.id][i] < dc.EXPANSION_STATE_BUILDING_DOME
                    && rdb.surveyorStates[rootExpansion.id][i] == dc.SURVEY_EXCELLENT
                    && !rdb.isKnownDangerousLocation(target)) {
                buildDir = i;
                break;
            }
        }
        if (buildDir == -1) {
            for (int i = 0; i < c.allDirs.length; i++) {
                Location target = rdb.expansionSites[rootExpansion.id][i];
                if (target.x - 2 < 0 || target.x + 2 >= uc.getMapWidth() || target.y - 2 < 0 || target.y + 2 >= uc.getMapHeight()) {
                    continue;
                }
                if (rdb.expansionStates[rootExpansion.id][i] < dc.EXPANSION_STATE_BUILDING_DOME
                        && rdb.surveyorStates[rootExpansion.id][i] == dc.SURVEY_GOOD
                        && !rdb.isKnownDangerousLocation(target)) {
                    buildDir = i;
                    break;
                }
            }
        }
        if (buildDir != -1) {
            buildDome(buildDir);
        }
    }

    private void buildDome(int dirIdx) {
        Location buildLoc = rdb.expansionSites[rootExpansion.id][dirIdx];
        ldb.minReserveEnlistSlot++;
        ldb.minReserveOxygen += COST_SURVEY;
        for (Direction dir: c.getFirstDirs(c.allDirs[dirIdx])) {
            if (uc.canEnlistAstronaut(dir, COST_SURVEY, CarePackage.DOME)) {
                c.enlistAstronaut(dir, COST_SURVEY, CarePackage.DOME);
                int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                rdb.sendBuildDomeCommand(enlistId, buildLoc);
                rdb.expansionStates[rootExpansion.id][dirIdx] = dc.EXPANSION_STATE_BUILDING_DOME;
                ldb.minReserveEnlistSlot--;
                ldb.minReserveOxygen -= COST_SURVEY;
                break;
            }
        }
    }

}
