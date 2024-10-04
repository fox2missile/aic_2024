package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastIntIntMap;
import kyuu.fast.FastIntSet;

public class EnlistSuppressionTask extends Task {


    FastIntIntMap suppressors;

    int cooldown;

    public EnlistSuppressionTask(C c) {
        super(c);
        cooldown = 10;
        suppressors = new FastIntIntMap();
    }

    @Override
    public void run() {
        if (rdb.enemyHqSize == 0) {
            return;
        }

        if (uc.senseAstronauts(c.visionRange, c.opponent).length == 0) {
            for (AstronautInfo ally: uc.senseAstronauts(c.visionRange, c.team)) {
                if (ally.getOxygen() < 2 && suppressors.contains(ally.getID())) {
                    cooldown *= 2;
                    return;
                }
            }
        }

        if (ldb.enlistFullyReserved() || uc.getStructureInfo().getOxygen() < 400 || uc.getRound() % cooldown != 0) {
            return;
        }

        int nearestEnemyHqIdx = Vector2D.getNearestChebysev(c.loc, rdb.enemyHq, rdb.enemyHqSize);
        Location enemyHq = rdb.enemyHq[nearestEnemyHqIdx];

        int dist = Vector2D.chebysevDistance(c.loc, enemyHq);
        if (dist > 30) {
            return;
        }

        int givenOxygen = Math.max(dist, 10);

        for (Direction dir: Direction.values()) {
            if (c.uc.canEnlistAstronaut(dir, givenOxygen, null)) {
                c.enlistAstronaut(dir, givenOxygen, null);
                suppressors.add(uc.senseAstronaut(c.loc.add(dir)).getID(), 1);
                if (ldb.enlistFullyReserved()) {
                    return;
                }
            }
        }
    }

}
