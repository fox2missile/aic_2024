package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.db.DbConst;
import kyuu.db.LocalDatabase;

public abstract class Task {

    protected C c;
    protected LocalDatabase ldb;
    protected DbConst dc;
    protected UnitController uc;
    public Task(C c) {
        this.c = c;
        this.uc = c.uc;
        this.ldb = c.ldb;
        this.dc = c.dc;
    }
    public abstract void run();
}
