package kyuu.db;

import aic2024.user.UnitController;
import kyuu.C;

public abstract class Database {
    C c;
    UnitController uc;
    DbConst dc;

    public Database(C c) {
        this.c = c;
        this.dc = c.dc;
        this.uc = c.uc;
    }
}
