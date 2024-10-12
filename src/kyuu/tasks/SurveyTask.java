package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.SurveyCommand;
import kyuu.message.SurveyComplete;
import kyuu.pathfinder.ParallelSearch;

public class SurveyTask extends Task {

    SurveyCommand cmd;
    ParallelSearch search;

    Location currentTarget;
    int srcIter;

    boolean tryAlter;
    Direction surveyDirection;

    Task plantsGatheringTask;

    public SurveyTask(C c, SurveyCommand cmd, Task plantsGatheringTask) {
        super(c);
        this.plantsGatheringTask = plantsGatheringTask;
        search = ParallelSearch.getDefaultSearch(c);
        tryAlter = false;
        for (StructureInfo s: uc.senseStructures(c.actionRange, c.team)) {
            surveyDirection = s.getLocation().directionTo(cmd.target);
        }
        this.cmd = cmd;
        if (cmd.sources.length >= 2) {
            this.currentTarget = cmd.sources[1];
            srcIter = 1;
        } else {
            this.currentTarget = cmd.target;
        }
        if (c.DEBUG) {
            for (int i = 1; i < cmd.sources.length; i++) {
                uc.drawLineDebug(cmd.sources[i - 1], cmd.sources[i], 0, 255, 128);
            }
            uc.drawLineDebug(cmd.sources[cmd.sources.length - 1], cmd.target, 0, 255, 128);
        }
    }

    @Override
    public void run() {
        c.s.trySendAlert();
        if (uc.senseStructures(c.visionRange, c.team).length == 0 && uc.getRound() < 500) {
            plantsGatheringTask.run();
            if (c.destination != null) {
                return;
            }
        }
        int dist = Vector2D.manhattanDistance(c.loc, currentTarget);
        c.destination = currentTarget;
        if (c.remainingSteps() < 2) {
            rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, dc.SURVEY_BAD, cmd.expansionId));
            return;
        }
        if (dist > 3) {
            // still far away from current target, nothing to check
            return;
        } else if (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseTileType(cmd.target))) { // remember: dist <= 3
            for (Direction dir: c.getFirstDirs(cmd.target.directionTo(c.loc))) {
                Location alt = cmd.target.add(dir);
                if (uc.canSenseLocation(alt) && !c.isObstacle(uc.senseTileType(alt))) {
                    c.destination = alt;
                    currentTarget = alt;
                    cmd.target = alt;
                    tryAlter = true;
                    return;
                }
            }

            // simple alternative not found
            if (tryAlter) {
                // already altered once
                rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, dc.SURVEY_BAD, cmd.expansionId));
                return;
            }

            // alter target
            currentTarget = currentTarget.add(surveyDirection);
            c.destination = currentTarget;
            tryAlter = true;
        } else if (!currentTarget.equals(cmd.target)) { // remember: dist <= 3
            srcIter++;
            if (srcIter >= cmd.sources.length) {
                currentTarget = cmd.target;
            } else {
                currentTarget = cmd.sources[srcIter];
            }
        } else if (c.loc.equals(cmd.target)) {
            int score = 30;
            for (Direction dir: c.fourDirs) {
                if (uc.isOutOfMap(c.loc.add(dir.dx * 5, dir.dy * 5))) {
                    score -= 5;
                }
            }
            if (c.remainingSteps() < 5) {
                score -= 5;
            }

            if (uc.senseObjects(MapObject.HOT_ZONE, c.visionRange).length >= 3) {
                score += 15;
            }

            if (uc.senseObjects(MapObject.HOT_ZONE, c.visionRange).length >= 7) {
                score += 15;
            }

            score -= ((uc.senseObjects(MapObject.WATER, c.visionRange).length * 2) / 3);

            int status = dc.SURVEY_BAD;

            if (score >= 0) {
                status = dc.SURVEY_GOOD;
            }
            if (score > 35) {
                status = dc.SURVEY_EXCELLENT;
            }

            rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, status, cmd.expansionId));
            return;
        } else if (srcIter == cmd.sources.length && search.calculateBestDirection(cmd.target, c.loc.directionTo(cmd.target).opposite(), 12) == null) {
            rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, dc.SURVEY_BAD, cmd.expansionId));
            return;
        }

    }
}
