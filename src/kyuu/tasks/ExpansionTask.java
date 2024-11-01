package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;

public class ExpansionTask extends Task {

    int[] expansionSurveyState;


    final Location[] expansionSites;
    final int COST_SURVEY = 20;
    final int EXPANSION_COOLDOWN = 30;

    int[] surveyorTimeout;


    int[] expansionWorkers;
    int[] expansionTimeout;


    public ExpansionTask(C c) {
        super(c);
        expansionSurveyState = new int[c.allDirs.length];
        expansionSites = new Location[c.allDirs.length];
        surveyorTimeout = new int[c.allDirs.length];
        for (int i = 0; i < c.allDirs.length; i++) {
            Direction dir = c.allDirs[i];;
            expansionSites[i] = c.loc.add(dir.dx * 10, dir.dy * 10);
            if (uc.isOutOfMap(expansionSites[i])) {
                expansionSurveyState[i] = dc.SURVEY_BAD;
            }
        }
        rdb.surveySites = expansionSites;
        expansionWorkers = new int[c.allDirs.length];
        expansionTimeout = new int[c.allDirs.length];
    }


    @Override
    public void run() {
        if (uc.getStructureInfo().getOxygen() < 200) {
            return;
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
            if (rdb.surveyorState[i] != dc.SURVEY_NONE) {
                expansionSurveyState[i] = rdb.surveyorState[i];
            }
            if (expansionSurveyState[i] != dc.SURVEY_NONE) {
                continue;
            }
            if (surveyorTimeout[i] > 0) {
                surveyorTimeout[i]--;
                continue;
            }
            for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                if (uc.canEnlistAstronaut(dir, COST_SURVEY, null)) {
                    uc.enlistAstronaut(dir, COST_SURVEY, null);
                    rdb.sendSurveyCommand(uc.senseAstronaut(c.loc.add(dir)).getID(), expansionSites[i]);
                    surveyorTimeout[i] = COST_SURVEY;
                    break;
                }
            }
        }
    }

    private void sendExpansionWorkers() {
        for (int i = 0; i < c.allDirs.length; i++) {
            if (expansionSurveyState[i] != dc.SURVEY_GOOD) {
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
                if (rdb.expansionStates[i] == dc.EXPANSION_STATE_ESTABLISHED) {
                    givenOxygen = 11;
                }
                for (Direction dir: c.getFirstDirs(c.allDirs[i])) {
                    if (uc.canEnlistAstronaut(dir, givenOxygen, null)) {
                        uc.enlistAstronaut(dir, givenOxygen, null);
                        int enlistedId = uc.senseAstronaut(c.loc.add(dir)).getID();
                        rdb.sendExpansionCommand(enlistedId, expansionSites[i], rdb.expansionStates[i]);
                        expansionWorkers[i] = enlistedId;
                        if (rdb.expansionStates[i] == dc.EXPANSION_STATE_ESTABLISHED) {
                            expansionTimeout[i] = EXPANSION_COOLDOWN;
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
            if (expansionSurveyState[i] != dc.SURVEY_GOOD && rdb.expansionStates[i] != dc.EXPANSION_STATE_ESTABLISHED) {
                continue;
            }
            int assign = ldb.popAssignedThisRound();
            rdb.sendExpansionCommand(assign, expansionSites[i], rdb.expansionStates[i]);
        }
        for (int i = 0; i < c.allDirs.length && ldb.assignedThisRoundSize > 0; i++) {
            if (expansionSurveyState[i] != dc.SURVEY_GOOD) {
                continue;
            }
            int assign = ldb.popAssignedThisRound();
            rdb.sendExpansionCommand(assign, expansionSites[i], rdb.expansionStates[i]);
        }
    }
}
