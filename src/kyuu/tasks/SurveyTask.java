package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.SurveyCommand;
import kyuu.message.SurveyComplete;
import kyuu.pathfinder.ParallelSearch;

public class SurveyTask extends Task {

    Location target;
    ParallelSearch search;

    boolean tryAlter;
    Direction surveyDirection;
    public SurveyTask(C c, SurveyCommand cmd) {
        super(c);
        target = cmd.target;
        search = ParallelSearch.getDefaultSearch(c);
        tryAlter = false;
        for (StructureInfo s: uc.senseStructures(c.actionRange, c.team)) {
            surveyDirection = s.getLocation().directionTo(target);
        }
    }

    @Override
    public void run() {
        int dist = Vector2D.manhattanDistance(c.loc, target);
        c.destination = target;
        if (uc.getAstronautInfo().getOxygen() < 2) {
            rdb.sendSurveyCompleteMsg(new SurveyComplete(target, dc.SURVEY_BAD));
            return;
        }
        if (dist > 3) {
            return;
        } else if (uc.canSenseLocation(target) && c.isObstacle(uc.senseObjectAtLocation(target))) {
            for (Direction dir: c.getFirstDirs(target.directionTo(c.loc))) {
                Location alt = target.add(dir);
                if (uc.canSenseLocation(alt) && !c.isObstacle(uc.senseObjectAtLocation(alt))) {
                    c.destination = alt;
                    target = alt;
                    tryAlter = true;
                    return;
                }
            }

            // simple alternative not found
            if (tryAlter) {
                // already altered once
                rdb.sendSurveyCompleteMsg(new SurveyComplete(target, dc.SURVEY_BAD));
                return;
            }

            // alter target
            target = target.add(surveyDirection);
            c.destination = target;
            tryAlter = true;
        } else if (c.loc.equals(target)) {
            int badSpotsMax = 30;
            for (Direction dir: c.fourDirs) {
                if (uc.isOutOfMap(c.loc.add(dir.dx * 5, dir.dy * 5))) {
                    badSpotsMax -= 5;
                }
            }
            if (uc.getAstronautInfo().getOxygen() < 5) {
                badSpotsMax -= 5;
            }
            int status = uc.senseObjects(MapObject.WATER, c.visionRange).length > badSpotsMax ? dc.SURVEY_BAD : dc.SURVEY_GOOD;
            rdb.sendSurveyCompleteMsg(new SurveyComplete(target, status));
            return;
        } else if (search.calculateBestDirection(target, c.loc.directionTo(target).opposite(), 12) == null) {
            rdb.sendSurveyCompleteMsg(new SurveyComplete(target, dc.SURVEY_BAD));
            return;
        }

    }
}
