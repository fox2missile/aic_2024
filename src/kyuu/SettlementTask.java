package kyuu;

import aic2024.user.BroadcastInfo;
import aic2024.user.Location;
import kyuu.db.Expansion;
import kyuu.tasks.*;

import java.util.Iterator;

public class SettlementTask extends Task {

    final int spawnRound;
    Task defenseTask;
    Task expansionTask;
    Task packageAssignerTask;
    Task settlementStrategyTask;
    Task enlistAttackersTask;
    Task jumpStrategyTask;

    float prevOxygen;

    public SettlementTask(C c) {
        super(c);
        rdb.subscribeEnemyHq = true;
        rdb.subscribeSurveyComplete = true;
        rdb.subscribeExpansionEstablished = true;
        spawnRound = uc.getRound();
        defenseTask = new DefenseAssginerTask(c);
        packageAssignerTask = new PackageAssignerTask(c);
        enlistAttackersTask = new EnlistAttackersTask(c);
        settlementStrategyTask = new SettlementStrategyTask(c);
        jumpStrategyTask = new JumpStrategyTask(c);
        prevOxygen = -1;


    }

    @Override
    public void run() {
        c.s.scan();

        if (prevOxygen != -1) {
            ldb.oxygenProductionRate = uc.getStructureInfo().getOxygen() - prevOxygen;
        }

        c.s.initAvailableEnlistSlot();
        ldb.minReserveOxygen = 0;
        ldb.minReserveEnlistSlot = 0;
        ldb.resetAssignedThisRound();

        doActions();

        if (uc.getRound() - spawnRound > 10) {
            prevOxygen = uc.getStructureInfo().getOxygen();
        }
    }

    private void doActions() {
        defenseTask.run();
        if (uc.getRound() == spawnRound) {
            // settlement must wait to receive all messages by the HQs
            return;
        } else if (uc.getRound() == spawnRound + 1) {
            rdb.introduceSettlement();
            rdb.newSettlementReceiver = (int __) -> {
                BroadcastInfo idxBroadcast = uc.pollBroadcast();
                Location settlementLoc = idxBroadcast.getLocation();
                int idx = idxBroadcast.getMessage();
                if (rdb.baseLocs[idx] != null) {
                    c.logger.log("warning: inconsistencies");
                }
                rdb.baseLocs[idx] = settlementLoc;
                rdb.baseCount++;
                c.logger.log("New settlement at %s", settlementLoc);

                // todo: transfer all the expansion to the settlement
                // for now just mark the expansion as bad
                for (Iterator<Expansion> it = ldb.iterateExpansions(); it.hasNext(); ) {
                    Expansion ex = it.next();
                    if (Vector2D.chebysevDistance(ex.expansionLoc, settlementLoc) < 5) {
                        for (int i = 0; i < c.allDirs.length; i++) {
                            rdb.surveyorStates[ex.id][i] = dc.SURVEY_BAD;
                            rdb.lastBadSurvey[ex.id][i] = -1;
                        }
                    } else {
                        for (int i = 0; i < c.allDirs.length; i++) {
                            if (rdb.expansionSites[ex.id][i] == null) {
                                continue;
                            }
                            if (Vector2D.chebysevDistance(rdb.expansionSites[ex.id][i], settlementLoc) < 5) {
                                rdb.surveyorStates[ex.id][i] = dc.SURVEY_BAD;
                                rdb.lastBadSurvey[ex.id][i] = -1;
                            }
                        }
                    }

                }
            };
            c.logger.log("Settlement base idx: %d", rdb.baseIdx);
            expansionTask = new ExpansionTask(c);
        }

        while (rdb.retrieveNextMessage() != null) {}

        settlementStrategyTask.run();
        enlistAttackersTask.run();
        expansionTask.run();
        packageAssignerTask.run();
        jumpStrategyTask.run();
        rdb.sendBaseHeartbeat();

    }
}
