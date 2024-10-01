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

    boolean tryAlter;
    Direction surveyDirection;
    public SurveyTask(C c, SurveyCommand cmd) {
        super(c);
        search = ParallelSearch.getDefaultSearch(c);
        tryAlter = false;
        for (StructureInfo s: uc.senseStructures(c.actionRange, c.team)) {
            surveyDirection = s.getLocation().directionTo(cmd.target);
        }
        this.cmd = cmd;
    }

    @Override
    public void run() {
        c.s.trySendAlert();
        int dist = Vector2D.manhattanDistance(c.loc, cmd.target);
        c.destination = cmd.target;
        if (uc.getAstronautInfo().getOxygen() < 2) {
            rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, dc.SURVEY_BAD, cmd.expansionId));
            return;
        }
        if (dist > 3) {
            return;
        } else if (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseObjectAtLocation(cmd.target))) {
            for (Direction dir: c.getFirstDirs(cmd.target.directionTo(c.loc))) {
                Location alt = cmd.target.add(dir);
                if (uc.canSenseLocation(alt) && !c.isObstacle(uc.senseObjectAtLocation(alt))) {
                    c.destination = alt;
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
            cmd.target = cmd.target.add(surveyDirection);
            c.destination = cmd.target;
            tryAlter = true;
        } else if (c.loc.equals(cmd.target)) {
            int score = 30;
            for (Direction dir: c.fourDirs) {
                if (uc.isOutOfMap(c.loc.add(dir.dx * 5, dir.dy * 5))) {
                    score -= 5;
                }
            }
            if (uc.getAstronautInfo().getOxygen() < 5) {
                score -= 5;
            }

            if (uc.senseObjects(MapObject.HOT_ZONE, c.visionRange).length >= 3) {
                score += 15;
            }

            if (uc.senseObjects(MapObject.HOT_ZONE, c.visionRange).length >= 7) {
                score += 15;
            }

            score -= uc.senseObjects(MapObject.WATER, c.visionRange).length;

            int status = dc.SURVEY_BAD;

            if (score >= 0) {
                status = dc.SURVEY_GOOD;
            }
            if (score > 35) {
                status = dc.SURVEY_EXCELLENT;
            }

            rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, status, cmd.expansionId));
            return;
        } else if (search.calculateBestDirection(cmd.target, c.loc.directionTo(cmd.target).opposite(), 12) == null) {
            rdb.sendSurveyCompleteMsg(new SurveyComplete(cmd.target, dc.SURVEY_BAD, cmd.expansionId));
            return;
        }

    }
}
