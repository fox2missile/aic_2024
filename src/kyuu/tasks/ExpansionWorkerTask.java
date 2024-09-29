package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.ExpansionCommand;
import kyuu.message.ExpansionEstablishedMessage;

public class ExpansionWorkerTask extends Task {

    ExpansionCommand cmd;
    Task paxRetrievalTask;
    Task nearbyPaxRetrievalTask;

    public ExpansionWorkerTask(C c, ExpansionCommand cmd, Task paxRetrievalTask) {
        super(c);
        c.logger.log("expansion worker %s", cmd.target);
        this.paxRetrievalTask = paxRetrievalTask;
        this.nearbyPaxRetrievalTask = new RetrievePackageTask(c, c.visionRange / 4);
        this.cmd = cmd;
    }

    @Override
    public void run() {
        if (cmd.state == dc.EXPANSION_STATE_INIT) {
            handleInit();
        } else {
            handleEstablished();
        }
    }

    private void handleEstablished() {
        c.destination = null;
        nearbyPaxRetrievalTask.run();
        if (c.destination == null) {
            c.destination = cmd.target;
        }
        if (Vector2D.manhattanDistance(c.loc, cmd.target) <= 3) {
            paxRetrievalTask.run();
        }
    }
    
    private void handleInit() {
        if (c.loc.equals(cmd.target) || (uc.canSenseLocation(cmd.target) && c.isObstacle(uc.senseObjectAtLocation(cmd.target)))) {
            if (uc.senseObjectAtLocation(c.loc) == MapObject.TERRAFORMED) {
                rdb.sendExpansionEstablishedMsg(new ExpansionEstablishedMessage(cmd.target));
                return;
            }
        }

        c.destination = cmd.target;
        if (uc.senseObjectAtLocation(c.loc) != MapObject.TERRAFORMED && uc.canPerformAction(ActionType.TERRAFORM, Direction.ZERO, 1)) {
            uc.performAction(ActionType.TERRAFORM, Direction.ZERO, 1);
        }
    }
}
