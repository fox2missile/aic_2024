package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.db.Expansion;
import kyuu.db.ExpansionEdge;
import kyuu.fast.FastLocSet;

import java.util.Iterator;

public class SettlementStrategyTask extends Task {

    FastLocSet settlementInProgress;
//    boolean inProgress;

    public SettlementStrategyTask(C c) {
        super(c);
        settlementInProgress = new FastLocSet();
//        inProgress = false;
    }


    @Override
    public void run() {
        if (rdb.baseCount >= 20 || uc.getStructureInfo().getCarePackagesOfType(CarePackage.SETTLEMENT) == 0 || uc.getStructureInfo().getOxygen() < 1000 || uc.senseAstronauts(9, c.opponent).length > 0) {
            return;
        }
        int realtimeAvailableEnlistSlot = c.s.getRealtimeAvailableEnlistSlot();
        if (realtimeAvailableEnlistSlot < 2) {
            return;
        }

        ExpansionEdge edge = findCandidate();
        if (edge == null) {
            return;
        }

        int deliveryCost = 10 + (13 * (edge.ex.depth + 1));
        Location settlementLoc = rdb.expansionSites[edge.ex.id][edge.directionIdx];

        int builderId = -1;
        int oxygenCarrierId = -1;

        // the settlement package
        for (Direction dir: c.getFirstDirs(c.loc.directionTo(settlementLoc))) {
            if (uc.canEnlistAstronaut(dir, deliveryCost, CarePackage.SETTLEMENT)) {
                uc.enlistAstronaut(dir, deliveryCost, CarePackage.SETTLEMENT);
                builderId = uc.senseAstronaut(c.loc.add(dir)).getID();
                break;
            }
        }

        // the oxygen
        CarePackage care = uc.getStructureInfo().getCarePackagesOfType(CarePackage.SURVIVAL_KIT) > 0 ? CarePackage.SURVIVAL_KIT : null;
        for (Direction dir: c.getFirstDirs(c.loc.directionTo(settlementLoc))) {
            if (uc.canEnlistAstronaut(dir, deliveryCost + 500, care)) {
                uc.enlistAstronaut(dir, deliveryCost + 500, care);
                oxygenCarrierId = uc.senseAstronaut(c.loc.add(dir)).getID();
                break;
            }
        }

        rdb.sendSettlementCommand(oxygenCarrierId, builderId, settlementLoc, edge.ex.expansionTree);
        rdb.sendSettlementCommand(builderId, oxygenCarrierId, settlementLoc, edge.ex.expansionTree);

        // todo: timeout to check if settlement success
        settlementInProgress.add(settlementLoc);
    }

    private ExpansionEdge findCandidate() {
        int bestScore = 0;
        ExpansionEdge bestEdge = null;

        Location nearestEnemyHq = null;
        if (rdb.enemyHqSize > 0) {
            nearestEnemyHq = rdb.enemyHq[Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize)];
        }

        for (Iterator<Expansion> it = ldb.iterateExpansions(); it.hasNext(); ) {
            Expansion ex = it.next();
            for (int i = 0; i < c.allDirs.length; i++) {
                if (rdb.expansionStates[ex.id][i] < dc.EXPANSION_STATE_ESTABLISHED) {
                    continue;
                }
                if (settlementInProgress.contains(rdb.expansionSites[ex.id][i])) {
                    continue;
                }

                boolean baseNearby = false;
                for (int j = 0; j < rdb.baseCount; j++) {
                    if (Vector2D.chebysevDistance(rdb.baseLocs[j], rdb.expansionSites[ex.id][i]) < 8) {
                        baseNearby = true;
                        break;
                    }
                }
                if (baseNearby) {
                    continue;
                }

                int score = 21;
                if (rdb.surveyorStates[ex.id][i] == dc.SURVEY_EXCELLENT) {
                    score += 100;
                }
                score -= (rdb.countNearbyRecentDangerSectors(rdb.expansionSites[ex.id][i], 350, 8) * 75);
                if (ex.depth == 1) {
                    score -= (rdb.countNearbyRecentDangerSectors(ex.expansionLoc, 350, 8) * 75);
                }

                if (nearestEnemyHq != null) {
                    score += (60 / Vector2D.chebysevDistance(ex.expansionLoc, nearestEnemyHq));
                }

                score -= (10 * (ex.depth + 1));

                if (score > bestScore) {
                    bestEdge = new ExpansionEdge(ex, i);
                    bestScore = score;
                }
            }
        }
        return bestEdge;
    }
}
