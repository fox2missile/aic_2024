package kyuu;

import aic2024.user.BroadcastInfo;
import aic2024.user.Location;
import kyuu.db.Expansion;
import kyuu.tasks.*;

import java.util.Iterator;

public class SettlementTask extends Task {

    Task defenseTask;
    Task expansionTask;
    Task packageAssignerTask;
    Task settlementStrategyTask;
    Task enlistAttackersTask;
    Task jumpStrategyTask;
    Task enlistSuppressorsTask;

    float prevOxygen;

    public SettlementTask(C c) {
        super(c);
        defenseTask = new DefenseAssginerTask(c);
        packageAssignerTask = new PackageAssignerTask(c);
        enlistAttackersTask = new EnlistAttackersTask(c);
        settlementStrategyTask = new SettlementStrategyTask(c);
        jumpStrategyTask = new JumpStrategyTask(c);
        enlistSuppressorsTask = new EnlistSuppressorsTask(c);
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

        if (uc.getRound() - c.spawnRound > 10) {
            prevOxygen = uc.getStructureInfo().getOxygen();
        }
    }

    private void doActions() {
        defenseTask.run();
        c.s.trySendAlert();
        if (uc.getRound() == c.spawnRound) {
            // settlement must wait to receive all messages by the HQs
            return;
        } else if (uc.getRound() == c.spawnRound + 1) {
            rdb.introduceSettlement();
            rdb.subscribeEnemyHq = true;
            rdb.subscribeSurveyComplete = true;
            rdb.subscribeExpansionEstablished = true;
            rdb.newSettlementReceiver = (int __) -> {
                BroadcastInfo idxBroadcast = uc.pollBroadcast();
                Location settlementLoc = idxBroadcast.getLocation();
                int idx = idxBroadcast.getMessage();
                if (rdb.baseLocs[idx] != null) {
                    c.logger.log("a base had died and a new settlement is taking the idx");
                } else {
                    rdb.baseCount++;
                }
                rdb.baseLocs[idx] = settlementLoc;
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
        if (ldb.oxygenProductionRate > 3) {
            enlistSuppressorsTask.run();
        }
        rdb.sendBaseHeartbeat();

    }
}
