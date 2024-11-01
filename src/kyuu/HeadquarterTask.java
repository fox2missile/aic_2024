package kyuu;


import aic2024.user.*;
import kyuu.fast.FastLocSet;
import kyuu.message.*;
import kyuu.pathfinder.ParallelSearch;
import kyuu.tasks.*;

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


    HeadquarterTask(C c) {
        super(c);
        rdb.subscribeEnemyHq = true;
        rdb.subscribeSurveyComplete = true;
        rdb.subscribeExpansionEstablished = true;
        rdb.initExpansionData();
        packageSearch = ParallelSearch.getDefaultSearch(c);
        packageAssignerTask = new PackageAssignerTask(c);
        defenseAssignerTask = new DefenseAssginerTask(c);
        enlistAttackersTask = new EnlistAttackersTask(c);
        enlistSuppressorsTask = new EnlistSuppressorsTask(c);
        symmetrySeekerAssignerTask = new SymmetrySeekerAssignerTask(c);
    }


    @Override
    public void run() {
        c.s.scan();
        enemyHqBroadcasted = false;
        c.s.initAvailableEnlistSlot();
        ldb.minReserveOxygen = 0;
        ldb.minReserveEnlistSlot = 0;
        ldb.resetAssignedThisRound();


        if (uc.getRound() == 0) {
            rdb.sendHqInfo();
            handleEarlyPlantsGatherer();
        } else if (uc.getRound() == 1) {
            rdb.initHqLocs();
            rdb.sendHqInfo();
            defenseAssignerTask.run();
            expansionTask = new ExpansionTask(c);
        } else if (uc.getRound() == 2) {
            symmetrySeekerAssignerTask.initSymmetryCandidates();
            defenseAssignerTask.run();
        } else  {
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
            } else if (jumpStrategyTask == null) {
                jumpStrategyTask = new JumpStrategyTask(c);
            }

            broadcastEnemyHq();
            enlistAttackersTask.run();
            expansionTask.run();
            packageAssignerTask.run();

            if (rdb.enemyHqSize > 0) {
                jumpStrategyTask.run();
            }
            enlistSuppressorsTask.run();


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
