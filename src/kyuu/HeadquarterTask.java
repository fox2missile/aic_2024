package kyuu;


import aic2024.user.*;
import kyuu.db.Expansion;
import kyuu.message.*;
import kyuu.pathfinder.ParallelSearch;
import kyuu.tasks.*;

import java.util.Iterator;

public class HeadquarterTask extends Task {





    boolean enemyHqBroadcasted;

    ParallelSearch packageSearch;

    Task packageAssignerTask;
    DefenseAssginerTask defenseAssignerTask;
    SymmetrySeekerAssignerTask symmetrySeekerAssignerTask;
    Task expansionTask;
    Task enlistAttackersTask;
    Task enlistSuppressorsTask;
    Task jumpStrategyTask;

    Task settlementStrategyTask;
    Task pathPlannerTask;

    float prevOxygen;


    HeadquarterTask(C c) {
        super(c);
        rdb.subscribeEnemyHq = true;
        rdb.subscribeSurveyComplete = true;
        rdb.subscribeExpansionEstablished = true;
        packageSearch = ParallelSearch.getDefaultSearch(c);
        packageAssignerTask = new PackageAssignerTask(c);
        defenseAssignerTask = new DefenseAssginerTask(c);
        enlistAttackersTask = new EnlistAttackersTask(c);
        enlistSuppressorsTask = new EnlistSuppressorsTask(c);
        symmetrySeekerAssignerTask = new SymmetrySeekerAssignerTask(c);
        settlementStrategyTask = new SettlementStrategyTask(c);
        jumpStrategyTask = new JumpStrategyTask(c);
        pathPlannerTask = new PathPlannerTask(c);
        prevOxygen = -1;
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
    }


    @Override
    public void run() {
        c.s.scan();
        enemyHqBroadcasted = false;
        c.s.initAvailableEnlistSlot();
        ldb.minReserveOxygen = 0;
        ldb.minReserveEnlistSlot = 0;
        ldb.resetAssignedThisRound();
        ldb.recentEnlistsLength = 0;

        if (prevOxygen != -1) {
            ldb.oxygenProductionRate = uc.getStructureInfo().getOxygen() - prevOxygen;
        }

        doActions();

        prevOxygen = uc.getStructureInfo().getOxygen();

    }

    private void doActions() {
        if (uc.getRound() == 0) {
            rdb.introduceHq();
            handleEarlyPlantsGatherer();
        } else if (uc.getRound() == 1) {
            rdb.initHqLocs();
            rdb.introduceHq();
            defenseAssignerTask.run();
            expansionTask = new ExpansionTask(c);
            rdb.flushBroadcastBuffer();
        } else if (uc.getRound() == 2) {
            symmetrySeekerAssignerTask.initSymmetryCandidates();
            defenseAssignerTask.run();
            rdb.flushBroadcastBuffer();
        } else  {
            rdb.sendBaseHeartbeat();
            defenseAssignerTask.run();
            Message msg = rdb.retrieveNextMessage();
            while (msg != null) {
                if (msg instanceof EnemyHqMessage) {
                    enemyHqBroadcasted = true;
                } else if (msg instanceof SeekSymmetryCommand) {

                } else if (msg instanceof AlertMessage) {
                    defenseAssignerTask.assignDefenders((AlertMessage) msg);
                }
                msg = rdb.retrieveNextMessage();
            }
            if (rdb.enemyHqSize == 0) {
                symmetrySeekerAssignerTask.run();
            }
            broadcastEnemyHq();
            settlementStrategyTask.run();
            enlistAttackersTask.run();
            expansionTask.run();
            packageAssignerTask.run();

            jumpStrategyTask.run();

            enlistSuppressorsTask.run();
            rdb.flushBroadcastBuffer();
            pathPlannerTask.run();

//            for (Iterator<Location> it = rdb.recentDangerSectors.getBackIterator(); it.hasNext(); ) {
//                Location sector = it.next();
//                uc.drawPointDebug(c.getSectorCenter(sector), 255, 127, 0);
//            }

        }
    }

    private void handleEarlyPlantsGatherer() {
        if (uc.senseStructures(c.visionRange, c.opponent).length > 0) {
            return;
        }
        Direction symmetryDir = c.loc.directionTo(new Location(c.mapWidth - c.loc.x - 1, c.mapHeight - c.loc.y - 1));
        int enlistCount = 0;
        Direction[] firstDirs = c.getFirstDirs(symmetryDir);
        for (int i = 0; i < firstDirs.length && enlistCount < 3; i++) {
            Direction dir = firstDirs[i];
            if (uc.canEnlistAstronaut(dir, 30, null)) {
                c.enlistAstronaut(dir, 30, null);
                enlistCount++;
            }
        }
    }

    private void broadcastEnemyHq() {
        if (enemyHqBroadcasted || rdb.enemyHqSize == 0) {
            return;
        }

        for (int i = 0; i < rdb.enemyHqSize; i++) {
            uc.drawPointDebug(rdb.enemyHq[i], 255, 0, 0);
            rdb.sendEnemyHqLocMessage(rdb.enemyHq[i]);
        }
    }
}
