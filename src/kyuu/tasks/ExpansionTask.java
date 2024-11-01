package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;

public class ExpansionTask extends Task {

    final int COST_SURVEY = 20;

    final int EXPANSION_COOLDOWN_SLOW = 20;
    final int EXPANSION_COOLDOWN_NORMAL = 10;
    final int EXPANSION_COOLDOWN_EXTREME = 3;

    int[] surveyorTimeout;


    int[] expansionWorkers;
    int[] expansionTimeout;

    int[] deepExpansion;

    int expansionCooldown;

    int id;
    Location expansionLoc;

    public ExpansionTask(C c, Location loc, int localExpansionId) {
        super(c);
        expansionLoc = loc;
        id = localExpansionId;
        surveyorTimeout = new int[c.allDirs.length];
        for (int i = 0; i < c.allDirs.length; i++) {
            Direction dir = c.allDirs[i];;
            rdb.expansionSites[id][i] = c.loc.add(dir.dx * 10, dir.dy * 10);
            if (uc.isOutOfMap(rdb.expansionSites[id][i])) {
                rdb.surveyorStates[id][i] = dc.SURVEY_BAD;
            }
        }
        expansionWorkers = new int[c.allDirs.length];
        expansionTimeout = new int[c.allDirs.length];
        deepExpansion = new int[c.allDirs.length];
    }


    @Override
    public void run() {
        if (uc.getStructureInfo().getOxygen() < 200) {
            return;
        }
        expansionCooldown = EXPANSION_COOLDOWN_NORMAL;
        if (uc.getStructureInfo().getOxygen() > 500) {
            expansionCooldown = EXPANSION_COOLDOWN_EXTREME;
        }
        if (uc.senseAstronauts(c.visionRange, c.team).length > 10) {
            expansionCooldown = EXPANSION_COOLDOWN_SLOW;
        }
        eliminateExpansionSites();

        sendOutSurveyors();

        sendExpansionWorkers();
    }


    // eliminate expansion site nearest to enemy HQ
    private void eliminateExpansionSites() {
//        for (int i = 0; i < rdb.enemyHqSize; i++) {
//            int closestDirIdx = -1;
//            int nearestDist = Integer.MAX_VALUE;
//            for (int j = 0; j < c.allDirs.length; j++) {
//                int dist = Vector2D.manhattanDistance(expansionSites[j], rdb.enemyHq[i]);
//                if (dist < 20) {
//                    expansionSurveyState[j] = dc.SURVEY_BAD;
//                    break;
//                }
//                if (dist < nearestDist) {
//                    nearestDist = dist;
//                    closestDirIdx = j;
//                }
//            }
//            if (closestDirIdx != -1) {
//                expansionSurveyState[closestDirIdx] = dc.SURVEY_BAD;
//            }
//        }
    }

    private void sendOutSurveyors() {
        for (int i = 0; i < c.allDirs.length; i++) {
            if (rdb.surveyorStates[id][i] != dc.SURVEY_NONE) {
                continue;
            }
            if (surveyorTimeout[i] > 0) {
                surveyorTimeout[i]--;
                continue;
            }
            for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                if (uc.canEnlistAstronaut(dir, COST_SURVEY, null)) {
                    uc.enlistAstronaut(dir, COST_SURVEY, null);
                    rdb.sendSurveyCommand(uc.senseAstronaut(c.loc.add(dir)).getID(), rdb.expansionSites[id][i], id);
                    surveyorTimeout[i] = COST_SURVEY;
                    break;
                }
            }
        }
    }

    private void sendExpansionWorkers() {
        for (int i = 0; i < c.allDirs.length; i++) {
            if (rdb.surveyorStates[id][i] != dc.SURVEY_GOOD) {
                continue;
            }

            if (uc.getStructureInfo().getOxygen() < 200) {
                return;
            }
            if (!c.s.isAllyVisible(expansionWorkers[i])) {
                expansionWorkers[i] = 0;
                if (expansionTimeout[i] > 0) {
                    expansionTimeout[i]--;
                    continue;
                }
                int givenOxygen = 12;
                if (rdb.expansionStates[id][i] == dc.EXPANSION_STATE_ESTABLISHED) {
                    if (uc.getStructureInfo().getOxygen() > 400 && deepExpansion[i] % 4 != 0) {
                        givenOxygen = 20;
                    } else {
                        givenOxygen = 11;
                    }
                    deepExpansion[i]++;

                }
                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                    if (uc.canEnlistAstronaut(dir, givenOxygen, null)) {
                        uc.enlistAstronaut(dir, givenOxygen, null);
                        int enlistedId = uc.senseAstronaut(c.loc.add(dir)).getID();
                        rdb.sendExpansionCommand(enlistedId, rdb.expansionSites[id][i], rdb.expansionStates[id][i], id);
                        expansionWorkers[i] = enlistedId;
                        if (rdb.expansionStates[id][i] == dc.EXPANSION_STATE_ESTABLISHED) {
                            expansionTimeout[i] = expansionCooldown;
                        } else {
                            expansionTimeout[i] = 0;
                        }
                        break;
                    }
                }
            }



        }

        // send package worker and defense worker after their primary work, prioritize established expansion
        for (int i = 0; i < c.allDirs.length && ldb.assignedThisRoundSize > 0; i++) {
            if (rdb.surveyorStates[id][i] != dc.SURVEY_GOOD && rdb.expansionStates[id][i] != dc.EXPANSION_STATE_ESTABLISHED) {
                continue;
            }
            int assign = ldb.popAssignedThisRound();
            rdb.sendExpansionCommand(assign, rdb.expansionSites[id][i], rdb.expansionStates[id][i], id);
        }
        for (int i = 0; i < c.allDirs.length && ldb.assignedThisRoundSize > 0; i++) {
            if (rdb.surveyorStates[id][i] != dc.SURVEY_GOOD) {
                continue;
            }
            int assign = ldb.popAssignedThisRound();
            rdb.sendExpansionCommand(assign, rdb.expansionSites[id][i], rdb.expansionStates[id][i], id);
        }
    }
}
