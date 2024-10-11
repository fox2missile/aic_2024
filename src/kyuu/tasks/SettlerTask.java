package kyuu.tasks;

import kyuu.C;
import kyuu.message.SettlementCommand;

public abstract class SettlerTask extends Task {

    SettlementCommand cmd;
    PlannedMoveTask plannedMoveTask;
    boolean deployed;

    public SettlerTask(C c, SettlementCommand cmd) {
        super(c);
        this.cmd = cmd;
        this.deployed = false;
        this.plannedMoveTask = PlannedMoveTask.fromSettlementCommand(c, cmd);
    }
}
