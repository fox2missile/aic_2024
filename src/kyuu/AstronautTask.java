package kyuu;

import aic2024.user.*;
import kyuu.message.EnemyHqMessage;
import kyuu.message.Message;
import kyuu.message.SeekSymmetryCommand;
import kyuu.message.SeekSymmetryComplete;
import kyuu.pathfinder.NaivePathFinder;
import kyuu.tasks.RetrievePackageTask;
import kyuu.tasks.ScanSectorTask;
import kyuu.tasks.Task;

public class AstronautTask extends Task {

//    Task moveTask;

    NaivePathFinder pathFinder;

    Task scanSectorTask;
    Task retrievePaxTask;

    SeekSymmetryCommand currentSeekSymmetryCommand;

    public AstronautTask(C c) {
        super(c);
        pathFinder = new NaivePathFinder(c);
        scanSectorTask = new ScanSectorTask(c);
        retrievePaxTask = new RetrievePackageTask(c);
        rdb.subscribeSeekSymmetryCommand = true;
        rdb.subscribeEnemyHq = true;
        c.logger.log("Spawn");
    }

    @Override
    public void run() {
        c.s.scan();

        rdb.resetEnemyHq();

        Message msg = rdb.retrieveNextMessage();
        while (msg != null) {
            if (msg instanceof SeekSymmetryCommand) {
                currentSeekSymmetryCommand = (SeekSymmetryCommand)msg;
            } else if (msg instanceof EnemyHqMessage) {
                currentSeekSymmetryCommand = null;
            }
            msg = rdb.retrieveNextMessage();
        }
        uc.cleanBroadcastBuffer();

        seekUnknownEnemyHq();

        if (currentSeekSymmetryCommand != null) {
            handleSymmetrySeekCmd();
        } else if (rdb.enemyHqSize > 0) {
            attackEnemyHq();
        } else {
            handleFreeRoam();
        }

        pathFinder.initTurn();
        if (c.destination != null) {
            pathFinder.move(c.destination);
            c.uc.drawLineDebug(c.uc.getLocation(), c.destination, 0, 255, 0);
            c.destination = null;
        }
    }

    private void seekUnknownEnemyHq() {
        for (StructureInfo s: uc.senseStructures(c.visionRange, c.team.getOpponent())) {
            if (s.getType() == StructureType.HQ) {
                if (rdb.addEnemyHq(s.getLocation())) {
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(s.getLocation(), dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ));
                }
            }
        }
    }


    private void handleFreeRoam() {
        retrievePaxTask.run();
        if (c.destination == null) {
            scanSectorTask.run();
        }
    }

    private void handleSymmetrySeekCmd() {
        if (!uc.canSenseLocation(currentSeekSymmetryCommand.target)) {
            if (uc.getAstronautInfo().getOxygen() < 2) {
                SeekSymmetryComplete msg = new SeekSymmetryComplete(currentSeekSymmetryCommand.target, dc.SYMMETRIC_SEEKER_COMPLETE_FAILED);
                rdb.sendSeekSymmetryCompleteMsg(msg);
                c.destination = null;
                currentSeekSymmetryCommand = null;
            } else {
                c.destination = currentSeekSymmetryCommand.target;
            }
        } else {
            StructureInfo s = uc.senseStructure(currentSeekSymmetryCommand.target);
            int status = s != null && s.getType() == StructureType.HQ && s.getTeam() != c.team ? dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ : dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING;
            SeekSymmetryComplete msg = new SeekSymmetryComplete(currentSeekSymmetryCommand.target, status);
            rdb.sendSeekSymmetryCompleteMsg(msg);
            c.destination = null;
            currentSeekSymmetryCommand = null;
        }
    }

    private void attackEnemyHq() {
        int nearestHqIdx = Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location target = rdb.enemyHq[nearestHqIdx];

        if (uc.canSenseLocation(target) && uc.senseStructure(target) == null) {
            rdb.sendEnemyHqDestroyedMessage(target);
            return;
        }

        if (uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1)) {
            uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1);
        } else {
            c.destination = target;
        }
    }
}
