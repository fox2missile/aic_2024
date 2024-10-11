package kyuu.db;

import aic2024.user.Direction;

public class ExpansionEdge {
    public Expansion ex;
    public int directionIdx;

    public ExpansionEdge(Expansion ex, int directionIdx) {
        this.ex = ex;
        this.directionIdx = directionIdx;
    }
}
