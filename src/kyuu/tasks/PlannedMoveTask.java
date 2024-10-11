package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.BuildDomeCommand;
import kyuu.message.SettlementCommand;

public class PlannedMoveTask extends Task {

    private final Location[] path;
    private int pathProgress;

    public PlannedMoveTask(C c, Location[] path) {
        super(c);
        this.path = path;
        this.pathProgress = 0;
    }

    static PlannedMoveTask fromBuildDomeCommand(C c, BuildDomeCommand cmd) {
        Location[] path = new Location[cmd.path.length];
        System.arraycopy(cmd.path, 1, path, 0, cmd.path.length - 1);
        path[path.length - 1] = cmd.target;
        return new PlannedMoveTask(c, path);
    }

    static PlannedMoveTask fromSettlementCommand(C c, SettlementCommand cmd) {
        Location[] path = new Location[cmd.path.length];
        System.arraycopy(cmd.path, 1, path, 0, cmd.path.length - 1);
        path[path.length - 1] = cmd.target;
        return new PlannedMoveTask(c, path);
    }

    @Override
    public void run() {
        // warning no check array index out of bounds!
        if (Vector2D.chebysevDistance(c.loc, path[pathProgress]) < 3) {
            pathProgress++;
        }

        if (isFinished()) {
            return;
        }

        c.destination = path[pathProgress];
    }

    @Override
    public boolean isFinished() {
        return pathProgress >= path.length;
    }
}
