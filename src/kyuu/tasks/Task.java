package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.db.DbConst;
import kyuu.db.LocalDatabase;
import kyuu.db.RemoteDatabase;

public abstract class Task {

    protected C c;
    protected LocalDatabase ldb;
    protected RemoteDatabase rdb;
    protected DbConst dc;
    protected UnitController uc;
    public int priority;
    public int progress;

    public Task(C c) {
        this.c = c;
        if (c != null) {
            this.uc = c.uc;
            this.ldb = c.ldb;
            this.dc = c.dc;
            this.rdb = c.rdb;
        }
        this.priority = 0;
        this.progress = 0;
    }
    public abstract void run();

    public boolean isFinished() {
        return false;
    }
}
