package kyuu.tasks;

import aic2024.user.ActionType;
import aic2024.user.Direction;
import aic2024.user.StructureInfo;
import aic2024.user.StructureType;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.BuildDomeCommand;

public class BuildDomeTask extends Task {

    BuildDomeCommand cmd;
    Task plannedMoveTask;

    boolean finished;
    public BuildDomeTask(C c, BuildDomeCommand cmd) {
        super(c);
        this.cmd = cmd;
        this.plannedMoveTask = PlannedMoveTask.fromBuildDomeCommand(c, cmd);
        this.finished = false;
    }

    @Override
    public void run() {
        if (!plannedMoveTask.isFinished()) {
            plannedMoveTask.run();
            if (!plannedMoveTask.isFinished()) {
                return;
            }
        }

        if (Vector2D.chebysevDistance(c.loc, cmd.target) > 2 && c.remainingSteps() > 1) {
            c.destination = cmd.target;
            return;
        }

        // corner case: target location still far away but running out of oxygen, in that case,
        // just build the dome where it is
        // except if it is near one of our HQ.

        for (StructureInfo s: uc.senseStructures(c.visionRange, c.team)) {
            if (s.getType() == StructureType.HQ) {
                finished = true;
                return;
            }
        }

        if (c.loc.equals(cmd.target)) {
            if (uc.canPerformAction(ActionType.BUILD_DOME, Direction.ZERO, 1)) {
                uc.performAction(ActionType.BUILD_DOME, Direction.ZERO, 1);
                finished = true;
                return;
            }
            for (Direction dir: c.allDirs) {
                if (uc.canPerformAction(ActionType.BUILD_DOME, dir, 1)) {
                    uc.performAction(ActionType.BUILD_DOME, dir, 1);
                    finished = true;
                    return;
                }
            }
        }

        for (Direction dir: c.getFirstDirs(c.loc.directionTo(cmd.target))) {
            if (uc.canPerformAction(ActionType.BUILD_DOME, dir, 1)) {
                uc.performAction(ActionType.BUILD_DOME, dir, 1);
                finished = true;
                return;
            }
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }
}
